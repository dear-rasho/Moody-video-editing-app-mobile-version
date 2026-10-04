package com.moody.moodyvideoeditor.utils

object ExportSettings {

    // ═══════════════════════════════════════════════════════════
    //  VIDEO RESOLUTIONS
    // ═══════════════════════════════════════════════════════════
    data class Preset(
        val key: String,
        val label: String,
        val width: Int,
        val height: Int
    )

    val RESOLUTIONS = listOf(
        Preset("480p", "480p", 854, 480),
        Preset("720p", "720p", 1280, 720),
        Preset("1080p", "1080p", 1920, 1080),
        Preset("2K", "2K", 2560, 1440),
        Preset("4K", "4K", 3840, 2160)
    )

    val FPS_OPTIONS = listOf(24, 25, 30, 60)

    // ═══════════════════════════════════════════════════════════
    //  🆕 AUDIO FORMATS
    // ═══════════════════════════════════════════════════════════
    data class AudioFormat(
        val key: String,
        val label: String,
        val ext: String,
        val mimeType: String
    )

    val AUDIO_FORMATS = listOf(
        AudioFormat("mp3", "MP3", "mp3", "audio/mpeg"),
        AudioFormat("m4a", "M4A (AAC)", "m4a", "audio/mp4")
    )

    val AUDIO_BITRATES = listOf(
        128 to "128 kbps",
        192 to "192 kbps",
        256 to "256 kbps",
        320 to "320 kbps"
    )

    // ═══════════════════════════════════════════════════════════
    //  🆕 IMAGE FORMATS (sequence export)
    // ═══════════════════════════════════════════════════════════
    data class ImageFormat(
        val key: String,
        val label: String,
        val ext: String
    )

    val IMAGE_FORMATS = listOf(
        ImageFormat("png", "PNG", "png"),
        ImageFormat("jpeg", "JPEG", "jpg")
    )

    val JPEG_QUALITY_OPTIONS = listOf(
        85 to "85% (Fast)",
        90 to "90% (Balanced)",
        95 to "95% (High)",
        100 to "100% (Lossless)"
    )

    val JPEG_QUALITY_OPTIONS_FIXED = listOf(
        85 to "85% (Fast)",
        90 to "90% (Balanced)",
        95 to "95% (High)",
        100 to "100% (Best)"
    )

    // ═══════════════════════════════════════════════════════════
    //  🆕 EXPORT MODES
    // ═══════════════════════════════════════════════════════════
    object ExportModes {
        const val VIDEO = "video"           // MP4 / MOV (existing)
        const val AUDIO = "audio"           // MP3 / M4A (NEW)
        const val IMAGE_SEQUENCE = "image"  // PNG / JPEG sequence (NEW)
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════
    fun findResolution(key: String): Preset =
        RESOLUTIONS.firstOrNull { it.key == key } ?: RESOLUTIONS[1]

    fun findAudioFormat(key: String): AudioFormat =
        AUDIO_FORMATS.firstOrNull { it.key == key } ?: AUDIO_FORMATS[0]

    fun findImageFormat(key: String): ImageFormat =
        IMAGE_FORMATS.firstOrNull { it.key == key } ?: IMAGE_FORMATS[0]

    fun targetDimensions(
        resolution: String,
        aspectRatio: String
    ): Pair<Int, Int> {
        val preset = findResolution(resolution)
        val ratio = RatioHelper.ratioValue(aspectRatio)

        return if (ratio >= 1f) {
            val w = preset.width
            val h = (w / ratio).toInt()
            makeEven(w) to makeEven(h)
        } else {
            val h = preset.width
            val w = (h * ratio).toInt()
            makeEven(w) to makeEven(h)
        }
    }

    fun autoBitrate(resolution: String, fps: Int): Int {
        val base = when (resolution) {
            "480p" -> 2500
            "720p" -> 5000
            "1080p" -> 10000
            "2K" -> 16000
            "4K" -> 35000
            else -> 8000
        }
        val fpsFactor = when (fps) {
            24 -> 0.9f
            25 -> 0.95f
            30 -> 1f
            60 -> 1.7f
            else -> 1f
        }
        return (base * fpsFactor).toInt()
    }

    /**
     * Auto bitrate for image sequence exports.
     * Lower fps → fewer images → can use higher quality
     */
    fun autoImageQuality(fps: Int): Int {
        return when {
            fps <= 24 -> 95
            fps <= 30 -> 90
            else -> 85
        }
    }

    private fun makeEven(v: Int): Int = if (v % 2 == 0) v else v + 1
}