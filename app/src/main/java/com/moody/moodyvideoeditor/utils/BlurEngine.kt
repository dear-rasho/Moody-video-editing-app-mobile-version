package com.moody.moodyvideoeditor.utils

// ═══════════════════════════════════════════════════════════════
//  BLUR ENGINE — 10 CapCut-style blur effects
//  Time-based animation: intensity ramps between 0 ↔ peak over
//  animDuration seconds.
// ═══════════════════════════════════════════════════════════════

data class BlurConfig(
    val type: String,
    val intensity: Float = 50f,        // 0..100 — PEAK blur amount
    val animDuration: Float = 2.0f,    // seconds — time to ramp
    val speed: Float = 1.0f,           // 0.2..5.0 — overall speed multiplier
    val angle: Float = 0f,             // 0..360 — motion blur direction
    val centerX: Float = 0.5f,         // 0..1 — radial/spin pivot X
    val centerY: Float = 0.5f,         // 0..1 — radial/spin pivot Y
    val blockSize: Float = 20f,        // mosaic block size
    val secondary: Float = 50f         // secondary param (threshold, glow amount, etc.)
)

object BlurEngine {

    // Preview cap (dp) — real export goes up to 50dp
    private const val MAX_PREVIEW_DP = 35f

    /**
     * Compute current blur radius in dp.
     * @param localSec       seconds since effect layer started
     * @param durationSec    total effect layer duration
     */
    fun computeRadiusDp(
        cfg: BlurConfig,
        localSec: Float,
        durationSec: Float
    ): Float {
        val I = cfg.intensity.coerceIn(0f, 100f) / 100f
        val maxR = MAX_PREVIEW_DP * I
        if (maxR <= 0.1f) return 0f

        val sp = cfg.speed.coerceIn(0.2f, 5f)
        val anim = (cfg.animDuration * (1f / sp)).coerceAtLeast(0.1f)
        val progress = (localSec / anim).coerceIn(0f, 1f)
        val t = localSec * sp

        return when (cfg.type) {
            // ─── 1. Blur In — full intensity → 0 (reveal) ───
            "blurIn" -> maxR * (1f - progress)

            // ─── 2. Blur Out — 0 → full intensity (dissolve) ───
            "blurOut" -> maxR * progress

            // ─── 3. Halo Blur — heavy blur + bright burst → clears ───
            "halo" -> maxR * (1f - progress) * 1.3f

            // ─── 4. Motion Blur — 1D directional (preview: approximate as full) ───
            "motion" -> maxR

            // ─── 5. Radial Blur — zoom burst (preview: approximate as full) ───
            "radial" -> maxR

            // ─── 6. Spin Blur — rotational (preview: approximate as full) ───
            "spin" -> maxR

            // ─── 7. Mosaic — pixelate (preview: skip, use FFmpeg for export) ───
            "mosaic" -> 0f

            // ─── 8. Soft Focus — dreamy glow (constant) ───
            "softFocus" -> maxR * 0.7f

            // ─── 9. Edge Blur — vignette blur (whole canvas preview) ───
            "edgeBlur" -> maxR * 0.5f

            // ─── 10. Chroma Blur — RGB split (preview: soft, export: exact) ───
            "chromaBlur" -> maxR * 0.4f

            else -> 0f
        }
    }
}