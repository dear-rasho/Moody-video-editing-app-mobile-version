package com.moody.moodyvideoeditor.utils

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
        val t = (intensity / 100f).coerceIn(0f, 2f)

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

            "robot" -> "afftfilt=real='hypot(re,im)*sin(0)':imag='hypot(re,im)*cos(0)':win_size=512:overlap=0.75"

            "echo" -> "aecho=${0.8f * t}:${0.9f * t}:${
                (1000 * t).toInt().coerceAtLeast(10)
            }:${0.3f * t}"

            "reverb" -> "aecho=${0.8f * t}:${0.88f * t}:60:${0.4f * t}"
            "cave" -> "aecho=${0.8f * t}:${0.88f * t}:${
                (500 * t).toInt().coerceAtLeast(10)
            }:${0.5f * t}"

            "stadium" -> "aecho=${0.8f * t}:${0.9f * t}:${
                (2000 * t).toInt().coerceAtLeast(10)
            }:${0.4f * t}"

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

            // SOUND FX — synthesized tones
            "pop" -> "sine=frequency=${(200 + 400 * t).toInt()}:duration=0.1"
            "click" -> "sine=frequency=${(1500 * t).toInt().coerceAtLeast(500)}:duration=0.03"
            "tick" -> "sine=frequency=${(2000 * t).toInt().coerceAtLeast(800)}:duration=0.02"
            "ding" -> "sine=frequency=${(1200 * t).toInt().coerceAtLeast(500)}:duration=0.3"
            "bell" -> "sine=frequency=${(800 * t).toInt().coerceAtLeast(300)}:duration=0.8"
            "coin" -> "sine=frequency=${(1800 * t).toInt().coerceAtLeast(800)}:duration=0.15"

            else -> ""
        }
    }

    fun previewVolumeMultiplier(fx: String, intensity: Float): Float {
        if (fx == "none") return 1f
        val t = (intensity / 100f).coerceIn(0f, 2f)
        return when (fx) {
            "whisper" -> 0.5f + 0.5f * t
            "radio" -> 1.0f + 0.5f * t
            else -> 1f
        }
    }
}