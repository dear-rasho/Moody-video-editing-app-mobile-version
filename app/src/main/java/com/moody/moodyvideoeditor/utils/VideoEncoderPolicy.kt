package com.moody.moodyvideoeditor.utils

import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode

internal object VideoEncoderPolicy {
    private const val TAG = "EXPORT_ENCODER"
    private const val H264_HW = "h264_mediacodec"
    private const val HEVC_HW = "hevc_mediacodec"
    private const val SOFTWARE = "libx264"
    private val ffmpegVideoEncoders: Set<String> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        queryFfmpegVideoEncoders()
    }

    fun codecArguments(
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int
    ): List<String> = codecArgumentCandidates(width, height, fps, bitrateKbps).first()

    fun codecArgumentCandidates(
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int
    ): List<List<String>> {
        val is4K = maxOf(width, height) >= 3840
        val requestedOrder = if (is4K) {
            listOf(HEVC_HW, H264_HW)
        } else {
            listOf(H264_HW, HEVC_HW)
        }
        val candidates = mutableListOf<List<String>>()

        for (encoder in requestedOrder) {
            if (encoder !in ffmpegVideoEncoders) {
                Log.w(TAG, "$encoder is not present in the packaged FFmpeg encoders")
                continue
            }
            val component = hardwareComponent(encoder, width, height, fps, bitrateKbps)
            if (component == null) {
                Log.i(
                    TAG,
                    "$encoder is not supported by a hardware MediaCodec at " +
                            "${width}x$height@$fps ${bitrateKbps}kbps"
                )
                continue
            }

            Log.i(
                TAG,
                "Candidate $encoder has matching Android hardware codec $component " +
                        "for ${width}x$height@$fps ${bitrateKbps}kbps"
            )
            candidates += hardwareArguments(encoder, fps)
        }

        candidates += softwareArguments()
        val selected = candidates.first().getOrNull(1)
        Log.i(
            TAG,
            "Selected FFmpeg encoder candidate=$selected for ${width}x$height@$fps " +
                    "${bitrateKbps}kbps; ordered candidates=" +
                    candidates.mapNotNull { it.getOrNull(1) }.joinToString(" -> ")
        )
        return candidates
    }

    fun fallbackCodecArguments(
        arguments: List<String>,
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int
    ): List<List<String>> {
        val codecIndex = arguments.indexOf("-c:v")
        val currentEncoder = arguments.getOrNull(codecIndex + 1) ?: return emptyList()
        val candidates = codecArgumentCandidates(width, height, fps, bitrateKbps)
        val currentIndex = candidates.indexOfFirst { it.getOrNull(1) == currentEncoder }
        return if (currentIndex >= 0) candidates.drop(currentIndex + 1) else emptyList()
    }

    fun isCodecInitializationFailure(logs: String): Boolean {
        val message = logs.lowercase()
        return "mediacodec" in message ||
                "error while opening encoder" in message ||
                "failed to configure encoder" in message ||
                "error initializing output stream" in message ||
                "unknown encoder" in message ||
                "encoder not found" in message ||
                "unrecognized option" in message ||
                "option not found" in message
    }

    fun replaceCodecArguments(
        arguments: List<String>,
        replacement: List<String>
    ): List<String>? {
        val codecIndex = arguments.indexOf("-c:v")
        if (codecIndex < 0 || replacement.size < 2) return null
        val bitrateIndex = (codecIndex + 2 until arguments.size)
            .firstOrNull { arguments[it] == "-b:v" } ?: return null

        return buildList(arguments.size + replacement.size) {
            addAll(arguments.subList(0, codecIndex))
            addAll(replacement)
            addAll(arguments.subList(bitrateIndex, arguments.size))
        }
    }

    internal fun parseFfmpegVideoEncoders(output: String): Set<String> =
        output.lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .filter {
                it.size >= 2 &&
                        it[0].startsWith("V", ignoreCase = true) &&
                        it[1] != "="
            }
            .map { it[1] }
            .toSet()

    private fun queryFfmpegVideoEncoders(): Set<String> {
        return try {
            val session = FFmpegKit.executeWithArguments(arrayOf("-hide_banner", "-encoders"))
            val output = session.allLogsAsString.orEmpty()
            if (!ReturnCode.isSuccess(session.returnCode)) {
                Log.e(
                    TAG,
                    "FFmpeg encoder probe failed with ${session.returnCode}: " +
                            output.takeLast(1200)
                )
                emptySet()
            } else {
                val encoders = parseFfmpegVideoEncoders(output)
                Log.i(
                    TAG,
                    "Packaged FFmpeg ${FFmpegKitConfig.getFFmpegVersion()} video encoders: " +
                            encoders.sorted().joinToString()
                )
                encoders
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not query encoders in the packaged FFmpeg build", e)
            emptySet()
        }
    }

    private fun hardwareArguments(encoder: String, fps: Int): List<String> {
        val args = mutableListOf(
            "-c:v", encoder,
            "-bitrate_mode", "vbr",
            "-operating_rate", fps.toString(),
            "-g", (fps * 2).toString(),
            "-ndk_async", "1"
        )
        if (encoder == HEVC_HW) args.addAll(listOf("-tag:v", "hvc1"))
        return args
    }

    private fun softwareArguments(): List<String> {
        val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        return listOf(
            "-c:v", SOFTWARE,
            "-preset", "ultrafast",
            "-tune", "zerolatency",
            "-x264-params",
            "threads=$threads:sync-lookahead=0:rc-lookahead=0:bframes=0"
        )
    }

    private fun hardwareComponent(
        encoder: String,
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int
    ): String? {
        val mime = when (encoder) {
            HEVC_HW -> "video/hevc"
            H264_HW -> "video/avc"
            else -> return null
        }
        val bitrate = (bitrateKbps.toLong() * 1000L)
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()

        return try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstNotNullOfOrNull { codec ->
                if (!codec.isEncoder ||
                    codec.supportedTypes.none { it.equals(mime, ignoreCase = true) } ||
                    !isHardwareCodec(codec)
                ) {
                    return@firstNotNullOfOrNull null
                }

                val videoCapabilities = try {
                    codec.getCapabilitiesForType(mime).videoCapabilities
                } catch (_: IllegalArgumentException) {
                    null
                } ?: return@firstNotNullOfOrNull null

                if (videoCapabilities.areSizeAndRateSupported(width, height, fps.toDouble()) &&
                    bitrate in videoCapabilities.bitrateRange
                ) {
                    codec.name
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not inspect Android MediaCodec support for $encoder", e)
            null
        }
    }

    private fun isHardwareCodec(codec: android.media.MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return codec.isHardwareAccelerated && !codec.isSoftwareOnly
        }
        val normalizedName = codec.name.lowercase()
        return !normalizedName.startsWith("omx.google.") &&
                !normalizedName.startsWith("c2.google.") &&
                !normalizedName.startsWith("c2.android.") &&
                !normalizedName.contains(".sw.")
    }
}
