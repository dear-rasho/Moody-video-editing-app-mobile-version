package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.FreezeState
import java.io.File
import java.util.UUID

object FreezeEngine {

    fun captureFreezeClip(
        context: Context,
        source: EditorClip,
        atTimeMs: Long,
        durationMs: Long
    ): EditorClip {
        require(source.isVisualClip && source.type.startsWith("video/")) {
            "Freeze frames can only be created from video clips."
        }
        require(durationMs > 0L) { "Freeze duration must be greater than zero." }
        require(atTimeMs >= source.timelineStartMs && atTimeMs < source.timelineEndMs) {
            "The playhead must be inside the selected video clip."
        }

        val sourceTimeMs = (
                source.sourceStartMs +
                        ((atTimeMs - source.timelineStartMs) * source.speed).toLong()
                ).coerceIn(
            source.sourceStartMs,
            (source.sourceEndMs - 1L).coerceAtLeast(source.sourceStartMs)
        )
        val retriever = MediaMetadataRetriever()
        val frame = try {
            retriever.setDataSource(context, source.uri)
            retriever.getFrameAtTime(
                sourceTimeMs * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: throw IllegalStateException("Could not capture a video frame at the playhead.")
        } finally {
            retriever.release()
        }

        val freezeDirectory = File(context.filesDir, "freeze-frames")
        if (!freezeDirectory.exists() && !freezeDirectory.mkdirs()) {
            frame.recycle()
            throw IllegalStateException("Could not create freeze-frame storage.")
        }
        val frameFile = File(freezeDirectory, "freeze-${UUID.randomUUID()}.png")
        try {
            val saved = frameFile.outputStream().use { output ->
                frame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            }
            if (!saved) throw IllegalStateException("Could not save the captured freeze frame.")
        } catch (error: Exception) {
            frameFile.delete()
            throw error
        } finally {
            frame.recycle()
        }

        return source.copy(
            id = UUID.randomUUID().toString(),
            uri = Uri.fromFile(frameFile),
            name = "❄️ Freeze ${durationMs / 1000f}s",
            type = "image/png",
            sourceStartMs = 0L,
            sourceEndMs = durationMs,
            timelineStartMs = atTimeMs,
            speed = 1f,
            sourceTotalMs = Long.MAX_VALUE,
            linkedId = null,
            linkGroupId = null,
            keyframes = emptyMap(),
            freeze = FreezeState(durationMs = durationMs, atTimeMs = atTimeMs)
        )
    }

    fun splitAroundFreeze(
        source: EditorClip,
        freezeClip: EditorClip,
        atTimeMs: Long,
        tailLinkedId: String? = null
    ): List<EditorClip> {
        val splitSourceMs = (
                source.sourceStartMs +
                        ((atTimeMs - source.timelineStartMs) * source.speed).toLong()
                ).coerceIn(source.sourceStartMs, source.sourceEndMs)
        val localCutSec = (
                (splitSourceMs - source.sourceStartMs) /
                        source.speed.coerceAtLeast(0.01f) / 1_000f
                )
        val durationMs = freezeClip.durationMs
        val headKeyframes = source.keyframes.mapNotNull { (property, keyframes) ->
            val kept = keyframes.filter { it.time <= localCutSec }
            if (kept.isEmpty()) null else property to kept
        }.toMap()
        val tailKeyframes = source.keyframes.mapNotNull { (property, keyframes) ->
            val kept = keyframes.filter { it.time > localCutSec }
                .map { it.copy(time = (it.time - localCutSec).coerceAtLeast(0f)) }
            if (kept.isEmpty()) null else property to kept
        }.toMap()

        val segments = buildList {
            if (splitSourceMs > source.sourceStartMs) {
                add(source.copy(sourceEndMs = splitSourceMs, keyframes = headKeyframes))
            }
            add(freezeClip)
            if (splitSourceMs < source.sourceEndMs) {
                add(
                    source.copy(
                        id = UUID.randomUUID().toString(),
                        sourceStartMs = splitSourceMs,
                        timelineStartMs = atTimeMs + durationMs,
                        linkedId = tailLinkedId,
                        linkGroupId = null,
                        keyframes = tailKeyframes
                    )
                )
            }
        }
        return segments
    }

    fun splitLinkedAudioAroundFreeze(
        audio: EditorClip,
        atTimeMs: Long,
        freezeDurationMs: Long
    ): List<EditorClip> {
        if (!audio.isAudio || audio.isAudioEffectClip ||
            atTimeMs <= audio.timelineStartMs || atTimeMs >= audio.timelineEndMs
        ) {
            return listOf(audio)
        }

        val sourceCutMs = (
                audio.sourceStartMs +
                        ((atTimeMs - audio.timelineStartMs) * audio.speed).toLong()
                ).coerceIn(audio.sourceStartMs, audio.sourceEndMs)
        if (sourceCutMs <= audio.sourceStartMs || sourceCutMs >= audio.sourceEndMs) {
            return listOf(audio)
        }

        return listOf(
            audio.copy(sourceEndMs = sourceCutMs),
            audio.copy(
                id = UUID.randomUUID().toString(),
                sourceStartMs = sourceCutMs,
                timelineStartMs = atTimeMs + freezeDurationMs,
                linkGroupId = null
            )
        )
    }
}
