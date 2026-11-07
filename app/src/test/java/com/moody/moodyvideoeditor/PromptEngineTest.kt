package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.utils.CmdType
import com.moody.moodyvideoeditor.utils.PromptEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptEngineTest {

    @Test
    fun parsesExplicitTransitionForSelectedClip() {
        val result = PromptEngine.parse("transition fade 0.8")

        assertTrue(result.unknown.isEmpty())
        assertEquals(1, result.commands.size)
        assertEquals(CmdType.TRANSITION, result.commands.single().type)
        assertEquals("fade", result.commands.single().key)
        assertEquals(0.8f, result.commands.single().value1)
    }

    @Test
    fun preservesLinearCommandsAroundTimestampedTextBlocks() {
        val result = PromptEngine.parse(
            "ratio 9:16\n" +
                    "[00:00 - 00:04] text \"Intro\"\n" +
                    "transition all dissolve 0.6"
        )

        assertTrue(result.unknown.isEmpty())
        assertEquals(
            listOf(CmdType.RATIO, CmdType.TEXT, CmdType.TRANSITION_ALL),
            result.commands.map { it.type }
        )
        assertEquals(0L, result.commands[1].startMs)
        assertEquals(4_000L, result.commands[1].endMs)
        assertEquals(null, result.commands[2].startMs)
    }

    @Test
    fun parsesPreviouslyUnexposedLayerControls() {
        val result = PromptEngine.parse(
            "blend multiply 65, opacity 80, mask circle, mask feather 12, chroma green"
        )

        assertTrue(result.unknown.isEmpty())
        assertEquals(
            listOf(
                CmdType.CLIP_SETTING,
                CmdType.CLIP_SETTING,
                CmdType.MASK_TYPE,
                CmdType.MASK_PROPERTY,
                CmdType.CHROMA_KEY
            ),
            result.commands.map { it.type }
        )
    }

    @Test
    fun parsesAudioSettingsLayerCommands() {
        val result = PromptEngine.parse(
            "audio settings, audio setting echo 60, audio setting studio reverb 35, " +
                    "audio setting noise reduction 60"
        )

        assertTrue(result.unknown.isEmpty())
        assertEquals(CmdType.CREATE_AUDIO_SETTINGS, result.commands[0].type)
        assertEquals("echo", result.commands[1].key)
        assertEquals("studioReverb", result.commands[2].key)
        assertEquals("noiseReduction", result.commands[3].key)
    }

    @Test
    fun parsesKeyframeEasingAtPlayhead() {
        val result = PromptEngine.parse("keyframe ease easeInOut")

        assertTrue(result.unknown.isEmpty())
        assertEquals(1, result.commands.size)
        assertEquals(CmdType.KEYFRAME_EASE, result.commands.single().type)
        assertEquals("easeinout", result.commands.single().stringValue)
    }

    @Test
    fun parsesCommonSelectedClipManagement() {
        val result = PromptEngine.parse(
            "duplicate clip, delete clip, move clip to layer 3"
        )

        assertTrue(result.unknown.isEmpty())
        assertEquals(
            listOf(CmdType.CLIP_MANAGEMENT, CmdType.CLIP_MANAGEMENT, CmdType.CLIP_MANAGEMENT),
            result.commands.map { it.type }
        )
        assertEquals("duplicate", result.commands[0].key)
        assertEquals("delete", result.commands[1].key)
        assertEquals(3f, result.commands[2].value1)
    }
}
