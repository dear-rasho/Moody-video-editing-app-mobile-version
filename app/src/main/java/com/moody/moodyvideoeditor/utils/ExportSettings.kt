package com.moody.moodyvideoeditor.utils

object ExportSettings {

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

    fun findResolution(key: String): Preset =
        RESOLUTIONS.firstOrNull { it.key == key } ?: RESOLUTIONS[1]

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

    private fun makeEven(v: Int): Int = if (v % 2 == 0) v else v + 1
}