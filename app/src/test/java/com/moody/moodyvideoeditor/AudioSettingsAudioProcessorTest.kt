package com.moody.moodyvideoeditor

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.moody.moodyvideoeditor.utils.AudioSettingsAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSettingsAudioProcessorTest {

    @Test
    fun maximumReverbAddsDelayedAudioToPreviewOutput() {
        val processor = AudioSettingsAudioProcessor()
        val format = AudioProcessor.AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT)
        processor.setEffects(echo = 0f, reverb = 1f, studioReverb = 0f)
        processor.configure(format)
        processor.flush()

        val frames = 44_100 / 2
        val input = ByteBuffer.allocateDirect(frames * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        repeat(frames) { frame ->
            input.putShort(if (frame == 0) 24_000.toShort() else 0.toShort())
        }
        input.flip()
        processor.queueInput(input)

        val output = processor.output.order(ByteOrder.nativeOrder())
        val delayedFrame = 44_100 * 37 / 1000
        assertTrue(output.getShort(delayedFrame * Short.SIZE_BYTES).toInt() > 0)
    }
}
