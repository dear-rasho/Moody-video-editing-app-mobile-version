package com.moody.moodyvideoeditor.utils

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
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

    // Set to true only when debugging encoder selection. When false, the
    // heavy MediaCodec inventory dump is skipped entirely (it used to run
    // 2-3 times per export and add ~170ms of log spam).
    private const val DEBUG_DIAGNOSTICS = false

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
        logMediaCodecDiagnostics(width, height, fps, bitrateKbps)

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

    private fun logMediaCodecDiagnostics(
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int
    ) {
        if (!DEBUG_DIAGNOSTICS) return

        val requestedBitrate = (bitrateKbps.toLong() * 1000L)
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()

        try {
            val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            for (codec in codecs) {
                val supportedTypes = codec.supportedTypes
                val hardwareFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "isHardwareAccelerated=${codec.isHardwareAccelerated}, " +
                            "isSoftwareOnly=${codec.isSoftwareOnly}"
                } else {
                    "isHardwareAccelerated=unavailable, isSoftwareOnly=unavailable"
                }
                Log.i(
                    TAG,
                    "MediaCodec inventory: name=${codec.name}, isEncoder=${codec.isEncoder}, " +
                            "supportedTypes=${supportedTypes.joinToString()}, $hardwareFlags"
                )

                for (mime in listOf("video/avc", "video/hevc")) {
                    val supportsMime = supportedTypes.any { it.equals(mime, ignoreCase = true) }
                    if (!codec.isEncoder && !supportsMime) continue

                    val hardwareCheckPassed = isHardwareCodec(codec)
                    val rejectionReasons = mutableListOf<String>()
                    if (!codec.isEncoder) rejectionReasons += "codec is not an encoder"
                    if (!supportsMime) rejectionReasons += "requested MIME type is unsupported"
                    if (!hardwareCheckPassed) {
                        rejectionReasons += "codec fails the current hardware classification check"
                    }

                    var supportedWidths = "unavailable"
                    var supportedHeights = "unavailable"
                    var sizeAndRateSupported = "not checked"
                    var bitrateRange = "unavailable"
                    var bitrateSupported = "not checked"

                    if (supportsMime) {
                        try {
                            val videoCapabilities =
                                codec.getCapabilitiesForType(mime).videoCapabilities
                            if (videoCapabilities == null) {
                                rejectionReasons +=
                                    "codec did not expose video capabilities for the MIME type"
                            } else {
                                supportedWidths = videoCapabilities.supportedWidths.toString()
                                supportedHeights = videoCapabilities.supportedHeights.toString()
                                bitrateRange = videoCapabilities.bitrateRange.toString()
                                val supportsRequestedSizeAndRate =
                                    videoCapabilities.areSizeAndRateSupported(
                                        width,
                                        height,
                                        fps.toDouble()
                                    )
                                val supportsRequestedBitrate =
                                    requestedBitrate in videoCapabilities.bitrateRange
                                sizeAndRateSupported = supportsRequestedSizeAndRate.toString()
                                bitrateSupported = supportsRequestedBitrate.toString()

                                if (!supportsRequestedSizeAndRate) {
                                    rejectionReasons +=
                                        "requested size/rate ${width}x$height@$fps is unsupported"
                                }
                                if (!supportsRequestedBitrate) {
                                    rejectionReasons +=
                                        "requested bitrate ${bitrateKbps}kbps is outside the codec range"
                                }
                            }
                        } catch (e: Exception) {
                            rejectionReasons +=
                                "could not read video capabilities: ${e.message ?: e.javaClass.simpleName}"
                        }
                    } else {
                        rejectionReasons += "video capabilities were not queried without MIME support"
                    }

                    Log.i(
                        TAG,
                        "MediaCodec capability: name=${codec.name}, mime=$mime, " +
                                "isEncoder=${codec.isEncoder}, supportsMime=$supportsMime, " +
                                "hardwareCheckPassed=$hardwareCheckPassed, " +
                                "supportedWidths=$supportedWidths, supportedHeights=$supportedHeights, " +
                                "requestedSizeRateSupported=$sizeAndRateSupported, " +
                                "bitrateRange=$bitrateRange, " +
                                "requestedBitrate=${bitrateKbps}kbps/$requestedBitrate, " +
                                "requestedBitrateSupported=$bitrateSupported, " +
                                "rejectionReason=${rejectionReasons.ifEmpty { listOf("none") }.joinToString("; ")}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaCodec diagnostics failed", e)
        }
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
        // FFmpeg's h264_mediacodec / hevc_mediacodec do NOT accept
        // -bitrate_mode, -operating_rate or -ndk_async. Passing them causes
        // "unrecognized option" and forces a fallback to libx264 even when
        // the hardware encoder was correctly selected.
        val args = mutableListOf(
            "-c:v", encoder,
            "-g", (fps * 2).toString()
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

                if (bitrate !in videoCapabilities.bitrateRange) {
                    return@firstNotNullOfOrNull null
                }

                // Fast path: Android agrees the size/rate is supported.
                if (videoCapabilities.areSizeAndRateSupported(width, height, fps.toDouble())) {
                    return@firstNotNullOfOrNull codec.name
                }

                // Fallback: MediaTek encoders expose wide raw ranges but their
                // areSizeAndRateSupported() table is conservative and returns false
                // for configurations the encoder accepts at runtime. Verify via a
                // real configure() probe before rejecting.
                val widthOk = width in videoCapabilities.supportedWidths
                val heightOk = height in videoCapabilities.supportedHeights
                if (!widthOk || !heightOk) {
                    return@firstNotNullOfOrNull null
                }

                Log.w(
                    TAG,
                    "${codec.name}: areSizeAndRateSupported(${width}x$height@$fps)=false " +
                            "but raw ranges accept it — running configure() probe"
                )
                if (probeEncoder(codec.name, mime, width, height, fps, bitrate)) {
                    Log.i(TAG, "${codec.name}: configure() probe succeeded")
                    codec.name
                } else {
                    Log.w(TAG, "${codec.name}: configure() probe failed")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not inspect Android MediaCodec support for $encoder", e)
            null
        }
    }

    /**
     * Actually creates and configures the encoder with the target format.
     * Returns true only if configure() succeeds. This is the ground truth —
     * the MediaCodec capability APIs on MediaTek are known to return false
     * negatives for configurations the encoder accepts at runtime.
     */
    private fun probeEncoder(
        codecName: String,
        mime: String,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int
    ): Boolean {
        var codec: MediaCodec? = null
        return try {
            val format = MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                )
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            codec = MediaCodec.createByCodecName(codecName)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            true
        } catch (e: Exception) {
            Log.w(TAG, "probeEncoder($codecName) failed: ${e.message}")
            false
        } finally {
            try {
                codec?.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun isHardwareCodec(codec: MediaCodecInfo): Boolean {
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