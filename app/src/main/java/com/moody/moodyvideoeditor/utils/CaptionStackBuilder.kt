package com.moody.moodyvideoeditor.utils

import android.util.Log
import com.moody.moodyvideoeditor.data.TextState

// ═══════════════════════════════════════════════════════════════
//  CAPTION STACK BUILDER
//
//  Diagonal layout: FIRST word → TOP-LEFT, LAST word → BOTTOM-RIGHT
//
//  Visual example:
//    'how                       ← TOP (first word, starts first)
//          are you              ← MIDDLE
//                  friends '    ← BOTTOM (last word, starts last)
//
//  - Y: first layer at TOP (Y=38), last at BOTTOM (Y=62)
//  - X: first layer at LEFT (X=32), last at RIGHT (X=68)
//  - All layers end at same time
//  - Animation duration scales with layer duration
// ═══════════════════════════════════════════════════════════════

object CaptionStackBuilder {

    private const val TAG = "CaptionStackBuilder"

    // ─── Vertical bounds ───
    private const val Y_TOP = 38f          // first word (starts first)
    private const val Y_BOTTOM = 62f       // last word  (starts last)

    // ─── Horizontal bounds (diagonal) ───
    private const val X_LEFT = 32f         // first word
    private const val X_RIGHT = 68f        // last word

    // ─── Animation constraints ───
    private const val MIN_ANIM_DURATION = 0.2f
    private const val MAX_ANIM_DURATION = 1.2f

    // Layer must be visible for at least this long
    private const val MIN_VISIBLE_MS = 500L

    data class StackLayer(
        val text: String,
        val textState: TextState,
        val startMs: Long,
        val endMs: Long
    )

    // ═══════════════════════════════════════════════════════════
    //  MAIN
    // ═══════════════════════════════════════════════════════════

    fun buildStack(
        rawText: String,
        segmentStartMs: Long,
        segmentEndMs: Long,
        template: CaptionTemplate
    ): List<StackLayer> {
        if (rawText.isBlank()) return emptyList()
        if (segmentEndMs <= segmentStartMs) return emptyList()

        val words = rawText.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val stackDurationMs = segmentEndMs - segmentStartMs
        val layerCount = template.layerCount()
        if (layerCount <= 0) return emptyList()

        // ─── Sort template layers by startOffsetMs ───
        // idx=0 → earliest start → TOP-LEFT
        // idx=N-1 → latest start → BOTTOM-RIGHT
        val sortedLayers = template.layers.sortedBy { it.startOffsetMs }

        // ─── Scale offsets to fit segment ───
        val maxTemplateOffset = sortedLayers.maxOfOrNull { it.startOffsetMs } ?: 0L
        val maxAllowedOffset = (stackDurationMs - MIN_VISIBLE_MS)
            .coerceAtLeast(0L)

        val offsetScale: Float = when {
            maxTemplateOffset <= 0L -> 1f
            maxTemplateOffset <= maxAllowedOffset -> 1f
            else -> maxAllowedOffset.toFloat() / maxTemplateOffset.toFloat()
        }

        // ─── Distribute words evenly across layers ───
        val wordGroups = distributeWords(words, layerCount)

        // ─── Build each layer ───
        return sortedLayers.mapIndexed { idx, layer ->
            val layerWords = wordGroups.getOrElse(idx) { emptyList() }
            val layerText = layerWords.joinToString(" ").ifBlank {
                words.getOrElse(idx) { "" }
            }

            // ─── Y: idx=0 → TOP, idx=N-1 → BOTTOM ───
            val yPosition = if (layerCount == 1) {
                (Y_TOP + Y_BOTTOM) / 2f
            } else {
                Y_TOP + (idx.toFloat() / (layerCount - 1)) * (Y_BOTTOM - Y_TOP)
            }

            // ─── X: idx=0 → LEFT, idx=N-1 → RIGHT ───
            val xPosition = if (layerCount == 1) {
                (X_LEFT + X_RIGHT) / 2f
            } else {
                X_LEFT + (idx.toFloat() / (layerCount - 1)) * (X_RIGHT - X_LEFT)
            }

            // ─── Actual timing ───
            val actualOffsetMs = (layer.startOffsetMs * offsetScale).toLong()
            val layerStartMs = segmentStartMs + actualOffsetMs
            val layerEndMs = segmentEndMs
            val layerDurationMs = (layerEndMs - layerStartMs).coerceAtLeast(100L)

            // ─── Scale animation: shorter layer → faster animation ───
            val animScale = layerDurationMs.toFloat() /
                    stackDurationMs.toFloat().coerceAtLeast(1f)
            val scaledAnimDur = (layer.animationDuration * animScale)
                .coerceIn(MIN_ANIM_DURATION, MAX_ANIM_DURATION)

            Log.e(
                TAG,
                "Layer $idx: \"$layerText\" | " +
                        "X=${"%.1f".format(xPosition)} " +
                        "Y=${"%.1f".format(yPosition)} | " +
                        "start=+${actualOffsetMs}ms | " +
                        "anim=${"%.2f".format(scaledAnimDur)}s"
            )

            StackLayer(
                text = layerText,
                textState = TextState(
                    content = layerText,
                    fontFamily = layer.fontFamily,
                    fontSize = layer.fontSize,
                    fontWeight = "bold",
                    fontStyle = "normal",
                    color = layer.color,
                    glowEnabled = layer.glowEnabled,
                    glowColor = layer.glowColor,
                    glowRadius = layer.glowRadius,
                    strokeEnabled = layer.strokeEnabled,
                    strokeWidth = layer.strokeWidth,
                    strokeColor = layer.strokeColor,
                    alignment = "center",
                    positionX = xPosition,       // auto
                    positionY = yPosition,       // auto
                    anchorX = 50f,
                    anchorY = 50f,
                    maxWidth = 95f,
                    scale = layer.scale,
                    opacity = 100f,
                    animation = layer.animation,
                    animationDuration = scaledAnimDur
                ),
                startMs = layerStartMs,
                endMs = layerEndMs
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  WORD DISTRIBUTION
    // ═══════════════════════════════════════════════════════════

    private fun distributeWords(
        words: List<String>,
        layerCount: Int
    ): List<List<String>> {
        if (layerCount <= 0) return listOf(words)
        val total = words.size
        if (total == 0) return List(layerCount) { emptyList() }

        if (total <= layerCount) {
            return (0 until layerCount).map { i ->
                if (i < total) listOf(words[i]) else emptyList()
            }
        }

        val baseCount = total / layerCount
        val extra = total % layerCount

        val result = mutableListOf<List<String>>()
        var cursor = 0

        for (i in 0 until layerCount) {
            val count = if (i < extra) baseCount + 1 else baseCount
            val end = (cursor + count).coerceAtMost(total)
            result.add(words.subList(cursor, end))
            cursor = end
        }

        if (cursor < total) {
            result[result.lastIndex] =
                result.last() + words.subList(cursor, total)
        }

        return result
    }
}