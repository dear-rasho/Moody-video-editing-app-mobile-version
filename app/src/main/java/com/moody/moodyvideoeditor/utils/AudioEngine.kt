package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.EditorClip
import java.util.Locale

// Audio FX → FFmpeg filter chain builder.
// Intensity (0..200) scales filter parameters.
object AudioEngine {

    data class AudioPreset(
        val key: String,
        val label: String,
        val icon: String,
        val description: String
    )

    val AUDIO_FX: List<AudioPreset> = listOf(
        AudioPreset("none", "None", "∅", "No effect"),
        AudioPreset("studio", "Studio", "🎙️", "Polished broadcast voice"),
        AudioPreset("warm", "Warm", "🔥", "Boost low-end warmth"),
        AudioPreset("bright", "Bright", "☀️", "Crisp highs"),
        AudioPreset("vocal", "Vocal", "🗣️", "Vocal presence boost"),
        AudioPreset("podcast", "Podcast", "🎧", "Radio voice clarity"),
        AudioPreset("deep", "Deep", "🌊", "Lower pitch"),
        AudioPreset("monster", "Monster", "👹", "Deep demonic voice"),
        AudioPreset("chipmunk", "Chipmunk", "🐿️", "High squeaky voice"),
        AudioPreset("baby", "Baby", "👶", "Baby-like voice"),
        AudioPreset("robot", "Robot", "🤖", "Robotic voice"),
        AudioPreset("echo", "Echo", "📢", "Simple echo"),
        AudioPreset("reverb", "Reverb", "🏛️", "Room reverb"),
        AudioPreset("cave", "Cave", "🕳️", "Long cave reverb"),
        AudioPreset("stadium", "Stadium", "🏟️", "Big stadium reverb"),
        AudioPreset("noise_reduction", "Noise Reduction", "🔇", "Reduce steady background noise"),
        AudioPreset("telephone", "Telephone", "☎️", "Old phone line"),
        AudioPreset("underwater", "Underwater", "🌊", "Muffled underwater"),
        AudioPreset("whisper", "Whisper", "🤫", "Soft whisper"),
        AudioPreset("radio", "Radio", "📻", "AM radio feel")
    )

    val SOUND_FX: List<AudioPreset> = listOf(
        AudioPreset("none", "None", "∅", "No effect"),
        AudioPreset("pop", "Pop", "🎈", "Bubble pop"),
        AudioPreset("click", "Click", "🖱️", "Mouse click"),
        AudioPreset("tick", "Tick", "⏱️", "Clock tick"),
        AudioPreset("ding", "Ding", "🔔", "Bright ding"),
        AudioPreset("bell", "Bell", "🛎️", "Bell ring"),
        AudioPreset("coin", "Coin", "🪙", "Coin drop"),
        AudioPreset("whoosh", "Whoosh", "💨", "Fast whoosh"),
        AudioPreset("swoosh", "Swoosh", "🌀", "Swoosh"),
        AudioPreset("slide", "Slide", "➡️", "Slide transition"),
        AudioPreset("rise", "Rise", "📈", "Rising tone"),
        AudioPreset("fall", "Fall", "📉", "Falling tone"),
        AudioPreset("boom", "Boom", "💥", "Deep boom"),
        AudioPreset("thud", "Thud", "🪵", "Heavy thud"),
        AudioPreset("clap", "Clap", "👏", "Applause"),
        AudioPreset("heartbeat", "Heartbeat", "❤️", "Heartbeat"),
        AudioPreset("zap", "Zap", "⚡", "Electric zap"),
        AudioPreset("laser", "Laser", "🔫", "Laser"),
        AudioPreset("glitch", "Glitch", "📺", "Glitch noise"),
        AudioPreset("alarm", "Alarm", "🚨", "Alarm beep"),
        AudioPreset("boing", "Boing", "🪀", "Spring boing")
    )

    fun buildAudioFilter(fx: String, intensity: Float): String {
        if (fx == "none" || fx.isBlank()) return ""
        val safeIntensity = intensity.takeIf { it.isFinite() } ?: 100f
        val t = (safeIntensity / 100f).coerceIn(0f, 2f)

        return when (fx) {
            "studio" -> "dynaudnorm=f=200:g=${(15 * t).coerceIn(1f, 30f).toInt()}"
            "warm" -> "equalizer=f=200:width_type=o:width=2:g=${(2 * t).coerceIn(0f, 10f)}"
            "bright" -> "equalizer=f=3000:width_type=o:width=2:g=${(3 * t).coerceIn(0f, 10f)}"
            "vocal" -> "equalizer=f=2500:width_type=o:width=2:g=${(4 * t).coerceIn(0f, 10f)}"
            "podcast" -> "dynaudnorm=f=200:g=${(10 * t).coerceIn(1f, 25f).toInt()}"

            "deep" -> {
                val rate = 1f - 0.15f * t
                val tempo = 1f / rate
                "asetrate=${(44100 * rate).toInt()},aresample=44100,atempo=${
                    tempo.coerceIn(
                        0.5f,
                        2f
                    )
                }"
            }

            "monster" -> {
                val rate = 1f - 0.30f * t
                val tempo = 1f / rate
                "asetrate=${(44100 * rate).toInt()},aresample=44100,atempo=${
                    tempo.coerceIn(
                        0.5f,
                        2f
                    )
                }"
            }

            "chipmunk" -> {
                val rate = 1f + 0.50f * t
                val tempo = 1f / rate
                "asetrate=${(44100 * rate).toInt()},aresample=44100,atempo=${
                    tempo.coerceIn(
                        0.5f,
                        2f
                    )
                }"
            }

            "baby" -> {
                val rate = 1f + 0.60f * t
                val tempo = 1f / rate
                "asetrate=${(44100 * rate).toInt()},aresample=44100,atempo=${
                    tempo.coerceIn(
                        0.5f,
                        2f
                    )
                }"
            }

            "robot" -> "tremolo=f=${(24f + 18f * t).coerceIn(1f, 200f)}:d=0.8,acrusher=bits=${(10f - 3f * t).toInt().coerceIn(5, 10)}:mix=${(0.25f + 0.2f * t).coerceIn(0f, 1f)}"

            "echo" -> buildEchoFilter(
                (0.8f * t).coerceIn(0f, 1f),
                (0.9f * t).coerceIn(0f, 1f),
                (1000 * t).toInt().coerceAtLeast(10),
                (0.3f * t).coerceIn(0f, 1f)
            )

            "reverb" -> buildEchoFilter(
                (0.8f * t).coerceIn(0f, 1f),
                (0.88f * t).coerceIn(0f, 1f),
                60,
                (0.4f * t).coerceIn(0f, 1f)
            )

            "cave" -> buildEchoFilter(
                (0.8f * t).coerceIn(0f, 1f),
                (0.88f * t).coerceIn(0f, 1f),
                (500 * t).toInt().coerceAtLeast(10),
                (0.5f * t).coerceIn(0f, 1f)
            )

            "stadium" -> buildEchoFilter(
                0.8f * t,
                0.9f * t,
                (2000 * t).toInt().coerceAtLeast(10),
                0.4f * t
            )

            "noise_reduction" -> "highpass=f=70,afftdn=nr=${(8f + 11f * t).coerceIn(8f, 30f)}:nf=-35:tn=1:gs=5"

            "telephone" -> "highpass=f=300,lowpass=f=${
                (3000 + 1000 * (1f - t)).toInt().coerceIn(1000, 8000)
            }"

            "underwater" -> "lowpass=f=${(500 + 500 * (1f - t)).toInt().coerceIn(200, 5000)}"
            "whisper" -> "highpass=f=${
                (1000 * t).toInt().coerceIn(100, 3000)
            },volume=${(0.5 + 0.5 * t)}"

            "radio" -> "highpass=f=400,lowpass=f=${
                (4000 - 1000 * (1f - t)).toInt().coerceIn(1000, 6000)
            },volume=${(1.0 + 0.5 * t)}"

            else -> ""
        }
    }

    fun appendTimelineAudioEffects(
        filterParts: MutableList<String>,
        clips: List<EditorClip>,
        rangeStartMs: Long,
        baseLabel: String,
        outLabel: String
    ): String {
        var currentLabel = baseLabel
        val audioFxLayers = clips.filter { it.isAudioFxClip }.sortedBy { it.timelineStartMs }
        val activeAudioFxLayers = audioFxLayers.mapNotNull { layer ->
            val start = ((layer.timelineStartMs - rangeStartMs).coerceAtLeast(0L)) / 1000.0
            val end = ((layer.timelineEndMs - rangeStartMs).coerceAtLeast(0L)) / 1000.0
            val filter = buildAudioFilter(layer.audioFx, layer.audioFxIntensity)
            if (end - start < 0.02 || filter.isBlank()) null else Triple(start, end, filter)
        }

        if (activeAudioFxLayers.isNotEmpty()) {
            val splitLabels = (0..activeAudioFxLayers.size).map { "fxsplt$it" }
            filterParts.add(
                "[$currentLabel]asplit=${splitLabels.size}" +
                        splitLabels.joinToString("") { "[$it]" }
            )
            val baseMute = activeAudioFxLayers.joinToString(",") { (start, end, _) ->
                "volume=0:enable='between(t,%.3f,%.3f)'".format(Locale.US, start, end)
            }
            filterParts.add("[${splitLabels[0]}]$baseMute[fxn0]")

            activeAudioFxLayers.forEachIndexed { index, (start, end, filter) ->
                val outsideMute =
                    "volume=0:enable='not(between(t,%.3f,%.3f))'".format(Locale.US, start, end)
                filterParts.add(
                    "[${splitLabels[index + 1]}]$outsideMute,$filter[fxn${index + 1}]"
                )
            }

            currentLabel = "audioFxMixed"
            filterParts.add(
                "${(0..activeAudioFxLayers.size).joinToString("") { "[fxn$it]" }}" +
                        "amix=inputs=${activeAudioFxLayers.size + 1}:duration=longest[$currentLabel]"
            )
        }

        val soundFxLayers = clips.filter { it.isSoundFxClip }.sortedBy { it.timelineStartMs }
        val soundLabels = mutableListOf<String>()
        soundFxLayers.forEachIndexed { index, layer ->
            val timelineStartMs = (layer.timelineStartMs - rangeStartMs).coerceAtLeast(0L)
            val layerDurationSeconds = (layer.durationMs / 1000.0).coerceAtLeast(0.02)
            val source = buildSoundFxSource(
                layer.soundFx,
                layer.soundFxIntensity,
                layerDurationSeconds
            ) ?: return@forEachIndexed
            val sourceDuration = source.substringAfterLast(":d=")
                .toDoubleOrNull() ?: layerDurationSeconds
            val fadeStart = (sourceDuration * 0.75).coerceAtLeast(0.0)
            val fadeDuration = (sourceDuration - fadeStart).coerceAtLeast(0.01)
            val sourceLabel = "soundFxSource$index"
            val outputLabel = "soundFxAudio$index"
            filterParts.add("$source[$sourceLabel]")
            filterParts.add(
                "[$sourceLabel]aformat=sample_rates=44100:channel_layouts=stereo," +
                        "afade=t=out:st=%.4f:d=%.4f,".format(Locale.US, fadeStart, fadeDuration) +
                        "adelay=$timelineStartMs|$timelineStartMs[$outputLabel]"
            )
            soundLabels.add("[$outputLabel]")
        }

        if (soundLabels.isNotEmpty()) {
            filterParts.add(
                "[$currentLabel]${soundLabels.joinToString("")}" +
                        "amix=inputs=${soundLabels.size + 1}:duration=longest:" +
                        "dropout_transition=0[$outLabel]"
            )
            return outLabel
        }

        if (currentLabel != baseLabel && currentLabel != outLabel) {
            filterParts.add("[$currentLabel]anull[$outLabel]")
            return outLabel
        }
        return currentLabel
    }

    fun buildSoundFxSource(fx: String, intensity: Float, layerDurationSeconds: Double): String? {
        val profile = when (fx) {
            "pop" -> Triple(900f, 120f, 0.12)
            "click" -> Triple(2200f, 700f, 0.04)
            "tick" -> Triple(2800f, 900f, 0.06)
            "ding" -> Triple(1400f, 950f, 0.35)
            "bell" -> Triple(1000f, 720f, 0.75)
            "coin" -> Triple(2000f, 1300f, 0.22)
            "whoosh" -> Triple(180f, 70f, 0.50)
            "swoosh" -> Triple(250f, 90f, 0.40)
            "slide" -> Triple(260f, 900f, 0.50)
            "rise" -> Triple(180f, 1400f, 0.60)
            "fall" -> Triple(1400f, 160f, 0.60)
            "boom" -> Triple(100f, 38f, 0.65)
            "thud" -> Triple(150f, 45f, 0.22)
            "clap" -> Triple(1200f, 500f, 0.12)
            "heartbeat" -> Triple(75f, 55f, 0.80)
            "zap" -> Triple(1600f, 100f, 0.30)
            "laser" -> Triple(1200f, 100f, 0.25)
            "glitch" -> Triple(700f, 2400f, 0.35)
            "alarm" -> Triple(700f, 1100f, 0.50)
            "boing" -> Triple(650f, 180f, 0.40)
            else -> return null
        }

        val safeIntensity = intensity.takeIf { it.isFinite() } ?: 100f
        val amplitude = (0.28f * (safeIntensity / 100f).coerceIn(0f, 2f))
            .coerceIn(0f, 0.56f)
        val duration = layerDurationSeconds
            .takeIf { it.isFinite() && it > 0.0 }
            ?.coerceAtMost(profile.third)
            ?: profile.third
        val durationText = String.format(java.util.Locale.US, "%.4f", duration)
        val startFrequency = String.format(java.util.Locale.US, "%.2f", profile.first)
        val frequencyDelta = String.format(
            java.util.Locale.US,
            "%.2f",
            profile.second - profile.first
        )
        val amplitudeText = String.format(java.util.Locale.US, "%.4f", amplitude)
        val expression =
            "$amplitudeText*sin(2*PI*($startFrequency*t+($frequencyDelta)*t*t/(2*$durationText)))"
        return "aevalsrc=exprs='$expression':s=44100:d=$durationText"
    }

    private fun buildEchoFilter(
        inputGain: Float,
        outputGain: Float,
        delayMs: Int,
        decay: Float
    ): String = "aecho=${inputGain.coerceIn(0f, 1f)}:" +
            "${outputGain.coerceIn(0f, 1f)}:${delayMs.coerceAtLeast(1)}:" +
            "${decay.coerceIn(0f, 1f)}"

    fun previewVolumeMultiplier(fx: String, intensity: Float): Float {
        if (fx == "none") return 1f
        val safeIntensity = intensity.takeIf { it.isFinite() } ?: 100f
        val t = (safeIntensity / 100f).coerceIn(0f, 2f)
        return when (fx) {
            "whisper" -> 0.5f + 0.5f * t
            "radio" -> 1.0f + 0.5f * t
            else -> 1f
        }
    }
}