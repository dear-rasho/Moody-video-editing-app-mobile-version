package com.moody.moodyvideoeditor.utils

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.util.Log
import com.moody.moodyvideoeditor.data.AdjustmentData
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

object ImageAdjustmentProcessor {

    private const val TAG = "ImgAdjProcessor"

    fun hasSharpenOrColorChannels(adj: AdjustmentData): Boolean {
        return adj.sharpen > 0.01f ||
                adj.reds != 0f || adj.oranges != 0f || adj.yellows != 0f ||
                adj.greens != 0f || adj.cyans != 0f || adj.blues != 0f ||
                adj.purples != 0f || adj.magentas != 0f || adj.skinTones != 0f
    }

    fun hasNoiseOrVignette(adj: AdjustmentData): Boolean {
        return adj.noise > 0f || adj.vignette != 0f
    }

    fun process(
        source: Bitmap,
        adj: AdjustmentData,
        detailAdjustment: AdjustmentData = adj,
        colorMatrix: ColorMatrix? = null
    ): Bitmap {
        if (!hasSharpenOrColorChannels(adj) && !hasNoiseOrVignette(detailAdjustment) &&
            colorMatrix == null
        ) return source
        if (source.width <= 0 || source.height <= 0) return source

        var work: Bitmap? = null
        var result: Bitmap? = null
        try {
            val maxDim = 1440
            val workingBitmap = if (source.width > maxDim || source.height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(source.width, source.height)
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else source
            work = workingBitmap

            val w = workingBitmap.width
            val h = workingBitmap.height
            val pixels = IntArray(w * h)
            workingBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            colorMatrix?.let { applyColorMatrix(pixels, it) }
            if (hasColorChannels(adj)) {
                applyColorChannels(pixels, adj)
            }

            val outputPixels = if (adj.sharpen > 0.01f) {
                AdjustmentPixelEngine.applySharpen(
                    pixels,
                    w,
                    h,
                    (adj.sharpen / 25f).coerceIn(0f, 2f)
                )
            } else pixels

            applyDetails(outputPixels, w, h, detailAdjustment)

            val outputBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            result = outputBitmap
            outputBitmap.setPixels(outputPixels, 0, w, 0, 0, w, h)

            if (workingBitmap !== source) {
                workingBitmap.recycle()
                work = null
            }
            if (w != source.width || h != source.height) {
                val scaled = Bitmap.createScaledBitmap(
                    outputBitmap, source.width, source.height, true
                )
                outputBitmap.recycle()
                result = null
                return scaled
            }
            val output = outputBitmap
            result = null
            return output
        } catch (t: Throwable) {
            work?.takeIf { it !== source && !it.isRecycled }?.recycle()
            result?.takeIf { !it.isRecycled }?.recycle()
            Log.e(TAG, "process failed", t)
            return source
        }
    }

    private fun applyColorMatrix(pixels: IntArray, colorMatrix: ColorMatrix) {
        val matrix = colorMatrix.array
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val alpha = pixel ushr 24
            if (alpha == 0) continue

            val red = ((pixel shr 16) and 0xFF).toFloat()
            val green = ((pixel shr 8) and 0xFF).toFloat()
            val blue = (pixel and 0xFF).toFloat()
            val outRed = (matrix[0] * red + matrix[1] * green + matrix[2] * blue +
                    matrix[3] * alpha + matrix[4]).toInt().coerceIn(0, 255)
            val outGreen = (matrix[5] * red + matrix[6] * green + matrix[7] * blue +
                    matrix[8] * alpha + matrix[9]).toInt().coerceIn(0, 255)
            val outBlue = (matrix[10] * red + matrix[11] * green + matrix[12] * blue +
                    matrix[13] * alpha + matrix[14]).toInt().coerceIn(0, 255)
            pixels[index] = (alpha shl 24) or (outRed shl 16) or
                    (outGreen shl 8) or outBlue
        }
    }

    private fun applyDetails(
        pixels: IntArray,
        width: Int,
        height: Int,
        adjustment: AdjustmentData
    ) {
        val noise = (adjustment.noise / 100f).coerceIn(0f, 1f)
        val vignette = (adjustment.vignette / 100f).coerceIn(-1f, 1f)
        if (noise <= 0f && vignette == 0f) return

        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = sqrt(centerX * centerX + centerY * centerY).coerceAtLeast(1f)

        for (y in 0 until height) {
            val normalizedY = (y - centerY) / maxRadius
            for (x in 0 until width) {
                val index = y * width + x
                val pixel = pixels[index]
                val alpha = pixel ushr 24
                if (alpha == 0) continue

                var red = (pixel shr 16) and 0xFF
                var green = (pixel shr 8) and 0xFF
                var blue = pixel and 0xFF

                if (noise > 0f) {
                    var hash = index.toLong() xor -7046029254386353131L
                    hash = (hash xor (hash ushr 13)) * 0x5BD1E995L
                    hash = hash xor (hash ushr 15)
                    val random = (hash and 0xFFFF).toFloat() / 65535f
                    if (random > 0.45f - noise * 0.30f) {
                        val gray = ((hash ushr 16) and 0xFF).toInt()
                        val blend = (noise * 0.7f * (0.4f + random * 0.6f))
                            .coerceIn(0f, 0.85f)
                        red = (red + (gray - red) * blend).toInt().coerceIn(0, 255)
                        green = (green + (gray - green) * blend).toInt().coerceIn(0, 255)
                        blue = (blue + (gray - blue) * blend).toInt().coerceIn(0, 255)
                    }
                }

                if (vignette != 0f) {
                    val normalizedX = (x - centerX) / maxRadius
                    val radius = sqrt(normalizedX * normalizedX + normalizedY * normalizedY)
                    val edgeT = ((radius - 0.35f) / (0.98f - 0.35f)).coerceIn(0f, 1f)
                    val edge = edgeT * edgeT * (3f - 2f * edgeT)
                    val blend = if (vignette < 0f) {
                        edge * abs(vignette) * 0.95f
                    } else {
                        edge * vignette * 0.5f
                    }
                    val target = if (vignette < 0f) 0f else 255f
                    red = (red + (target - red) * blend).toInt().coerceIn(0, 255)
                    green = (green + (target - green) * blend).toInt().coerceIn(0, 255)
                    blue = (blue + (target - blue) * blend).toInt().coerceIn(0, 255)
                }

                pixels[index] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
    }

    private fun hasColorChannels(adj: AdjustmentData): Boolean {
        return adj.reds != 0f || adj.oranges != 0f || adj.yellows != 0f ||
                adj.greens != 0f || adj.cyans != 0f || adj.blues != 0f ||
                adj.purples != 0f || adj.magentas != 0f || adj.skinTones != 0f
    }

    // ═══════════════════════════════════════════════════════════
    //  COLOR CHANNELS — RGB-based manipulation (STRONG)
    //  Saturated colors pe bhi asar hoga
    // ═══════════════════════════════════════════════════════════

    private fun applyColorChannels(pixels: IntArray, adj: AdjustmentData) {
        val reds = AdjustmentPixelEngine.normalizedColorChannelStrength(adj.reds)
        val oranges = AdjustmentPixelEngine.normalizedColorChannelStrength(adj.oranges)
        val yellows = AdjustmentPixelEngine.normalizedColorChannelStrength(adj.yellows)
        val greens = AdjustmentPixelEngine.normalizedColorChannelStrength(adj.greens)
        val cyans = AdjustmentPixelEngine.normalizedColorChannelStrength(adj.cyans)
        val blues = AdjustmentPixelEngine.normalizedColorChannelStrength(adj.blues)
        val purples = AdjustmentPixelEngine.normalizedColorChannelStrength(adj.purples)
        val magentas = AdjustmentPixelEngine.normalizedColorChannelStrength(adj.magentas)
        val skinTones = AdjustmentPixelEngine.normalizedColorChannelStrength(adj.skinTones)

        val hsv = FloatArray(3)

        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            if (a == 0) continue

            var r = ((p shr 16) and 0xFF).toFloat()
            var g = ((p shr 8) and 0xFF).toFloat()
            var b = (p and 0xFF).toFloat()

            android.graphics.Color.RGBToHSV(
                r.toInt(), g.toInt(), b.toInt(), hsv
            )
            val hue = hsv[0]
            val sat = hsv[1]

            // ─── REDS ───
            if (reds != 0f) {
                val w = hueWeight(hue, 0f, 30f, sat)
                if (w > 0.001f) {
                    val amount = reds * w
                    if (amount > 0f) {
                        // Push toward pure red
                        r = r + (255f - r) * amount * 0.9f
                        g = g * (1f - amount * 0.45f)
                        b = b * (1f - amount * 0.45f)
                    } else {
                        // Pull toward gray (less red)
                        val avg = (r + g + b) / 3f
                        r = r + (avg - r) * (-amount) * 0.9f
                        g = g + (avg - g) * (-amount) * 0.5f
                        b = b + (avg - b) * (-amount) * 0.5f
                    }
                }
            }

            // ─── ORANGES ───
            if (oranges != 0f) {
                val w = hueWeight(hue, 30f, 25f, sat)
                if (w > 0.001f) {
                    val amount = oranges * w
                    if (amount > 0f) {
                        r = r + (255f - r) * amount * 0.7f
                        g = g + (180f - g) * amount * 0.5f
                        b = b * (1f - amount * 0.5f)
                    } else {
                        val avg = (r + g + b) / 3f
                        r = r + (avg - r) * (-amount) * 0.7f
                        g = g + (avg - g) * (-amount) * 0.7f
                    }
                }
            }

            // ─── YELLOWS ───
            if (yellows != 0f) {
                val w = hueWeight(hue, 60f, 30f, sat)
                if (w > 0.001f) {
                    val amount = yellows * w
                    if (amount > 0f) {
                        r = r + (255f - r) * amount * 0.75f
                        g = g + (255f - g) * amount * 0.75f
                        b = b * (1f - amount * 0.6f)
                    } else {
                        val avg = (r + g + b) / 3f
                        r = r + (avg - r) * (-amount) * 0.7f
                        g = g + (avg - g) * (-amount) * 0.7f
                    }
                }
            }

            // ─── GREENS ───
            if (greens != 0f) {
                val w = hueWeight(hue, 120f, 45f, sat)
                if (w > 0.001f) {
                    val amount = greens * w
                    if (amount > 0f) {
                        g = g + (255f - g) * amount * 0.9f
                        r = r * (1f - amount * 0.45f)
                        b = b * (1f - amount * 0.45f)
                    } else {
                        val avg = (r + g + b) / 3f
                        g = g + (avg - g) * (-amount) * 0.9f
                        r = r + (avg - r) * (-amount) * 0.5f
                        b = b + (avg - b) * (-amount) * 0.5f
                    }
                }
            }

            // ─── CYANS ───
            if (cyans != 0f) {
                val w = hueWeight(hue, 180f, 30f, sat)
                if (w > 0.001f) {
                    val amount = cyans * w
                    if (amount > 0f) {
                        g = g + (255f - g) * amount * 0.7f
                        b = b + (255f - b) * amount * 0.7f
                        r = r * (1f - amount * 0.6f)
                    } else {
                        val avg = (r + g + b) / 3f
                        g = g + (avg - g) * (-amount) * 0.7f
                        b = b + (avg - b) * (-amount) * 0.7f
                    }
                }
            }

            // ─── BLUES ───
            if (blues != 0f) {
                val w = hueWeight(hue, 240f, 30f, sat)
                if (w > 0.001f) {
                    val amount = blues * w
                    if (amount > 0f) {
                        b = b + (255f - b) * amount * 0.9f
                        r = r * (1f - amount * 0.45f)
                        g = g * (1f - amount * 0.45f)
                    } else {
                        val avg = (r + g + b) / 3f
                        b = b + (avg - b) * (-amount) * 0.9f
                        r = r + (avg - r) * (-amount) * 0.5f
                        g = g + (avg - g) * (-amount) * 0.5f
                    }
                }
            }

            // ─── PURPLES ───
            if (purples != 0f) {
                val w = hueWeight(hue, 280f, 25f, sat)
                if (w > 0.001f) {
                    val amount = purples * w
                    if (amount > 0f) {
                        r = r + (220f - r) * amount * 0.7f
                        b = b + (255f - b) * amount * 0.7f
                        g = g * (1f - amount * 0.6f)
                    } else {
                        val avg = (r + g + b) / 3f
                        r = r + (avg - r) * (-amount) * 0.7f
                        b = b + (avg - b) * (-amount) * 0.7f
                    }
                }
            }

            // ─── MAGENTAS ───
            if (magentas != 0f) {
                val w = hueWeight(hue, 300f, 25f, sat)
                if (w > 0.001f) {
                    val amount = magentas * w
                    if (amount > 0f) {
                        r = r + (255f - r) * amount * 0.75f
                        b = b + (255f - b) * amount * 0.75f
                        g = g * (1f - amount * 0.6f)
                    } else {
                        val avg = (r + g + b) / 3f
                        r = r + (avg - r) * (-amount) * 0.7f
                        b = b + (avg - b) * (-amount) * 0.7f
                    }
                }
            }

            // ─── SKIN TONES ───
            if (skinTones != 0f) {
                val w = hueWeight(hue, 25f, 20f, sat)
                if (w > 0.001f) {
                    val amount = skinTones * w
                    r = (r + amount * 30f).coerceIn(0f, 255f)
                    g = (g + amount * 15f).coerceIn(0f, 255f)
                }
            }

            pixels[i] = (a shl 24) or
                    (r.toInt().coerceIn(0, 255) shl 16) or
                    (g.toInt().coerceIn(0, 255) shl 8) or
                    b.toInt().coerceIn(0, 255)
        }
    }

    private fun hueWeight(hue: Float, targetHue: Float, range: Float, sat: Float): Float {
        var diff = abs(hue - targetHue)
        diff = min(diff, 360f - diff)
        val hueMatch = 1f - smoothstep(range * 0.25f, range, diff)
        val satWeight = smoothstep(0.02f, 0.15f, sat)
        return hueMatch * satWeight
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

}