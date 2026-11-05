package com.moody.moodyvideoeditor.utils

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

@UnstableApi
class AudioSettingsAudioProcessor : BaseAudioProcessor() {
    @Volatile
    private var echoAmount = 0f

    @Volatile
    private var reverbAmount = 0f

    @Volatile
    private var studioReverbAmount = 0f

    private var delayLines = emptyArray<FloatArray>()
    private var delayFrames = IntArray(0)
    private var writeFrame = 0

    private companion object {
        val REVERB_TAPS_MS = intArrayOf(37, 71, 113, 173, 251, 347)
        val REVERB_WEIGHTS = floatArrayOf(0.42f, 0.32f, 0.24f, 0.18f, 0.12f, 0.08f)
        val STUDIO_TAPS_MS = intArrayOf(11, 19, 31, 47, 67, 89)
        val STUDIO_WEIGHTS = floatArrayOf(0.42f, 0.32f, 0.23f, 0.16f, 0.10f, 0.06f)
        val ECHO_TAPS_MS = intArrayOf(220, 440, 660)
        val ECHO_WEIGHTS = floatArrayOf(0.55f, 0.34f, 0.20f)
    }

    fun setEffects(echo: Float, reverb: Float, studioReverb: Float) {
        echoAmount = safeAmount(echo)
        reverbAmount = safeAmount(reverb)
        studioReverbAmount = safeAmount(studioReverb)
    }

    private fun safeAmount(amount: Float) =
        amount.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if ((inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
                    inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) ||
            inputAudioFormat.sampleRate <= 0 ||
            inputAudioFormat.channelCount <= 0
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }

        val sampleRate = inputAudioFormat.sampleRate
        delayFrames = (REVERB_TAPS_MS + STUDIO_TAPS_MS + ECHO_TAPS_MS)
            .map { (sampleRate * it / 1000).coerceAtLeast(1) }
            .toIntArray()
        val capacity = delayFrames.maxOrNull()!! + 1
        delayLines = Array(inputAudioFormat.channelCount) { FloatArray(capacity) }
        writeFrame = 0
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val channels = inputAudioFormat.channelCount
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val input = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        val bytesPerSample = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            Float.SIZE_BYTES
        } else {
            Short.SIZE_BYTES
        }
        val frameBytes = channels * bytesPerSample
        val capacity = delayLines.firstOrNull()?.size ?: 0
        val echo = echoAmount
        val reverb = reverbAmount
        val studio = studioReverbAmount
        val combined = maxOf(echo, reverb, studio)

        while (input.remaining() >= frameBytes) {
            for (channel in 0 until channels) {
                val dry = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
                    input.float.coerceIn(-1f, 1f)
                } else {
                    input.short / 32768f
                }
                var reverbWet = 0f
                var studioWet = 0f
                var echoWet = 0f
                for (tap in REVERB_TAPS_MS.indices) {
                    val delayed = delayedSample(channel, writeFrame, delayFrames[tap], capacity)
                    reverbWet += delayed * REVERB_WEIGHTS[tap]
                }
                val studioOffset = REVERB_TAPS_MS.size
                for (tap in STUDIO_TAPS_MS.indices) {
                    val delayed = delayedSample(
                        channel,
                        writeFrame,
                        delayFrames[studioOffset + tap],
                        capacity
                    )
                    studioWet += delayed * STUDIO_WEIGHTS[tap]
                }
                val echoOffset = REVERB_TAPS_MS.size + STUDIO_TAPS_MS.size
                var echoFeedback = 0f
                for (tap in ECHO_TAPS_MS.indices) {
                    val delayed = delayedSample(
                        channel,
                        writeFrame,
                        delayFrames[echoOffset + tap],
                        capacity
                    )
                    echoWet += delayed * ECHO_WEIGHTS[tap]
                    echoFeedback += delayed * ECHO_WEIGHTS[tap]
                }

                val processed = (
                        dry * (1f - 0.2f * combined) +
                                reverbWet * reverb +
                                studioWet * studio +
                                echoWet * echo
                        )
                    .coerceIn(-1f, 1f)
                delayLines[channel][writeFrame] =
                    (dry + echoFeedback * echo * 0.22f).coerceIn(-1f, 1f)
                if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
                    output.putFloat(processed)
                } else {
                    output.putShort((processed * Short.MAX_VALUE).roundToInt().toShort())
                }
            }
            writeFrame = (writeFrame + 1) % capacity
        }

        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    private fun delayedSample(channel: Int, writePosition: Int, delay: Int, capacity: Int): Float {
        var readPosition = writePosition - delay
        if (readPosition < 0) readPosition += capacity
        return delayLines[channel][readPosition]
    }
}

@UnstableApi
internal class AudioSettingsRenderersFactory(
    context: Context,
    private val processor: AudioSettingsAudioProcessor
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioProcessors(arrayOf(processor))
        .build()
}
