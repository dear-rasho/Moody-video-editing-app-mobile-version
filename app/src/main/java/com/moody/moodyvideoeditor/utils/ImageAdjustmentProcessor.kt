package com.moody.moodyvideoeditor.utils

import android.graphics.Bitmap
import android.util.Log
import com.moody.moodyvideoeditor.data.AdjustmentData
import kotlin.math.abs
import kotlin.math.min

object ImageAdjustmentProcessor {

    private const val TAG = "ImgAdjProcessor"

    fun hasSharpenOrColorChannels(adj: AdjustmentData): Boolean {
        return adj.sharpen > 0.01f ||
                adj.reds != 0f || adj.oranges != 0f || adj.yellows != 0f ||
                adj.greens != 0f || adj.cyans != 0f || adj.blues != 0f ||
                adj.purples != 0f || adj.magentas != 0f || adj.skinTones != 0f
    }

    fun process(source: Bitmap, adj: AdjustmentData): Bitmap {
        if (!hasSharpenOrColorChannels(adj)) return source
        if (source.width <= 0 || source.height <= 0) return source

        try {
            val maxDim = 1440
            val work = if (source.width > maxDim || source.height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(source.width, source.height)
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else source

            val w = work.width
            val h = work.height
            val pixels = IntArray(w * h)
            work.getPixels(pixels, 0, w, 0, 0, w, h)

            if (hasColorChannels(adj)) {
                applyColorChannels(pixels, adj)
            }

            val outputPixels = if (adj.sharpen > 0.01f) {
                applySharpen(pixels, w, h, adj.sharpen / 100f)
            } else pixels

            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            result.setPixels(outputPixels, 0, w, 0, 0, w, h)

            return if (work != source && (w != source.width || h != source.height)) {
                Bitmap.createScaledBitmap(result, source.width, source.height, true)
            } else result
        } catch (t: Throwable) {
            Log.e(TAG, "process failed", t)
            return source
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
        val reds = adj.reds / 100f
        val oranges = adj.oranges / 100f
        val yellows = adj.yellows / 100f
        val greens = adj.greens / 100f
        val cyans = adj.cyans / 100f
        val blues = adj.blues / 100f
        val purples = adj.purples / 100f
        val magentas = adj.magentas / 100f
        val skinTones = adj.skinTones / 100f

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
                    r = (r + amount * w * 30f).coerceIn(0f, 255f)
                    g = (g + amount * w * 15f).coerceIn(0f, 255f)
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

    // ═══════════════════════════════════════════════════════════
    //  SHARPEN — STRONG 9-point unsharp
    // ═══════════════════════════════════════════════════════════

    private fun applySharpen(
        pixels: IntArray,
        w: Int,
        h: Int,
        strength: Float
    ): IntArray {
        if (w < 3 || h < 3) return pixels

        // Booster: slider 1.0 (100%) → multiplier 2.0
        // Slider 3.0 (300%) → multiplier 3.0 (capped)
        val s = (strength * 2f).coerceIn(0f, 3f)
        val result = IntArray(pixels.size)

        for (x in 0 until w) {
            result[x] = pixels[x]
            result[(h - 1) * w + x] = pixels[(h - 1) * w + x]
        }
        for (y in 0 until h) {
            result[y * w] = pixels[y * w]
            result[y * w + w - 1] = pixels[y * w + w - 1]
        }

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val center = pixels[idx]

                val top = pixels[(y - 1) * w + x]
                val bottom = pixels[(y + 1) * w + x]
                val left = pixels[y * w + (x - 1)]
                val right = pixels[y * w + (x + 1)]

                val cA = (center ushr 24) and 0xFF
                val cR = (center shr 16) and 0xFF
                val cG = (center shr 8) and 0xFF
                val cB = center and 0xFF

                val avgR = (((top shr 16) and 0xFF) + ((bottom shr 16) and 0xFF) +
                        ((left shr 16) and 0xFF) + ((right shr 16) and 0xFF)) / 4
                val avgG = (((top shr 8) and 0xFF) + ((bottom shr 8) and 0xFF) +
                        ((left shr 8) and 0xFF) + ((right shr 8) and 0xFF)) / 4
                val avgB = ((top and 0xFF) + (bottom and 0xFF) +
                        (left and 0xFF) + (right and 0xFF)) / 4

                val newR = (cR + (cR - avgR) * s).toInt().coerceIn(0, 255)
                val newG = (cG + (cG - avgG) * s).toInt().coerceIn(0, 255)
                val newB = (cB + (cB - avgB) * s).toInt().coerceIn(0, 255)

                result[idx] = (cA shl 24) or (newR shl 16) or (newG shl 8) or newB
            }
        }

        return result
    }
}