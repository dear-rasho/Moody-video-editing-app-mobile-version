package com.moody.moodyvideoeditor.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HelpGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("prompt") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(0xFF0A0A0A))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                "📖 Guide & Prompts",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TabChip("prompt", "🤖 AI Prompt", activeTab) { activeTab = it }
            TabChip("commands", "⌨️ Commands", activeTab) { activeTab = it }
            TabChip("guide", "📚 Guide", activeTab) { activeTab = it }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (activeTab) {
                "prompt" -> PromptContent(context)
                "commands" -> CommandsContent()
                "guide" -> GuideContent()
            }
        }
    }
}

@Composable
private fun TabChip(
    key: String,
    label: String,
    active: String,
    onPick: (String) -> Unit
) {
    val isActive = active == key
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
            .pointerInput(key) {
                detectTapGestures { onPick(key) }
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  AI PROMPT TAB
// ═══════════════════════════════════════════════════════════════
@Composable
private fun PromptContent(context: Context) {
    var copied by remember { mutableStateOf(false) }
    val aiPrompt = buildFullAiPrompt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1F3A))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "🤖 How to use",
            color = Color(0xFF60EFFF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "1. Copy the prompt below (full — huge)\n" +
                    "2. Paste into ChatGPT / Claude / Gemini\n" +
                    "3. Tell AI what edit you want (English/Roman Urdu/Hindi)\n" +
                    "4. Copy AI's commands\n" +
                    "5. Paste in Code Mode → Apply",
            color = Color(0xFFCCCCCC),
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF181818))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "📋 Full AI Prompt",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${aiPrompt.length} chars",
                color = Color(0xFF666666),
                fontSize = 9.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F0F0F))
                .verticalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            Text(
                aiPrompt,
                color = Color(0xFFAAAAAA),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (copied) Color(0xFF22C55E) else Color(0xFF7C3AED))
            .pointerInput(Unit) {
                detectTapGestures {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Moody AI Prompt", aiPrompt)
                    )
                    copied = true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (copied) "✅ Copied! Paste in ChatGPT" else "📋 Copy Full Prompt",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF181818))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "💡 Request Examples (paste after prompt):",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        listOf(
            "Make it cinematic with fade-in text",
            "Add shake effect and red tint on beat",
            "Text 'HELLO' with glow cyan and popIn animation",
            "Slow motion 0.5x with cool blue filter",
            "60-second motivational reel about stress and action",
            "Retro VHS vibe with scanlines and vintage",
            "Add sticker 🔥 at top-right with popIn animation",
            "Multi-clip transitions — C1 fade, C2 slide left, C3 zoom in",
            "9:16 vertical reel with 8K cinematic filter"
        ).forEach { example ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("→", color = Color(0xFF7C3AED), fontSize = 11.sp)
                Text(
                    example,
                    color = Color(0xFFCCCCCC),
                    fontSize = 10.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  MEGA AI PROMPT — Fully Updated
// ═══════════════════════════════════════════════════════════════
private fun buildFullAiPrompt(): String = """
You are an AI Prompt Generator for "Moody Video Editor" — an offline mobile video editor app.

## YOUR JOB
User will tell you what edit they want in plain language (English / Hindi / Urdu / Roman Urdu).
You will output ONLY the editor commands. No explanations, no markdown, no extra text.

## OUTPUT RULES
- Output ONLY commands
- Multiple commands: comma-separated on one line
- Time-ranged edits: use [MM:SS - MM:SS] blocks (one per block)
- Colors: hex (#ff0066) OR names (red, blue, green, yellow, orange, purple, cyan, magenta, pink, white, black)
- Be concise — no commentary
- If unsure, make a reasonable creative choice
- Timestamped blocks can be MIXED with trailing linear commands (transitions, ratio, filters)

═══════════════════════════════════════════════════════════════
  COMMAND REFERENCE — ALL SUPPORTED FEATURES
═══════════════════════════════════════════════════════════════

### 📝 TEXT
Syntax:
  text "Hello" [options]

Options (chain with spaces):
  size NUMBER            → font size (8-300)
  color #hex | COLORNAME → text fill color
  font NAME              → font family (see FONT CATEGORIES below)
  italic                 → italic style (avoid "bold" — use stroke/glow for emphasis)
  align left|center|right
  anchor top-left | top-center | top-right | center-left | center |
         center-right | bottom-left | bottom-center | bottom-right
  position X Y           → 0-100 percent
  animation NAME         → see ANIMATIONS list below
  opacity 0-100
  rotation DEG           → -180 to +180

  ▸ TEXT OUTLINE (stroke):
    stroke WIDTH COLOR
    stroke 3 #000000
    stroke 5 red
    stroke 8 white

  ▸ TEXT GLOW (neon):
    glow COLOR RADIUS
    glow #ff0066 40
    glow cyan 30
    glow white 20

  ▸ TEXT SHADOW:
    shadow COLOR BLUR X Y
    shadow black 8 2 2
    shadow #000000 15 0 4

  ▸ TEXT GRADIENT (color ramp):
    gradient COLOR1 COLOR2 [ANGLE]
    gradient red blue
    gradient #ff0066 #00ffcc 90

  ▸ TYPOGRAPHY:
    tracking NUMBER         → letter spacing (-10 to +30)
    lineheight NUMBER       → line spacing multiplier (1.0-3.0)
    rotation DEG            → rotate text

FONT CATEGORIES (mention category, editor auto-picks best font):
  font handwriting   → Dancing Script, Brush Script, Pacifico, Caveat, Kalam
  font system        → Arial, Helvetica, Roboto, Segoe UI, Inter
  font serif         → Times New Roman, Georgia, Garamond, Playfair, Baskerville, Cinzel
  font mono          → Courier New, Consolas, Monaco, JetBrains Mono, Fira Code
  font display       → Oswald, Bebas Neue, Anton, Bungee, Audiowide
  font elegant       → Playfair, Cormorant, Cinzel, Bodoni, Abril Fatface
  font modern        → Poppins, Montserrat, Raleway, DM Sans, Manrope, Sora
  font titles        → Bebas Neue, Cinzel, Alfa Slab One, Abril Fatface
  font music         → Bebas Neue, Anton, Permanent Marker, Rock Salt
  font playful       → Comic Sans, Baloo 2, Fredoka, Chewy, Luckiest Guy
  font retro         → Lobster, Righteous, Bungee Shade, Monoton, Ultra
  font educational   → Open Sans, Lato, Source Sans 3, Noto Sans
  font cinematic     → Cinzel, Playfair, Cormorant, Prata, Spectral, Lora
  font minimal       → Inter, Roboto, Open Sans, Work Sans, DM Sans

⚠️ IMPORTANT — AVOID USING "bold" — it causes animation rendering issues.
   Use stroke + glow + shadow + size increase for emphasis instead.

### 😀 STICKER COMMANDS
Syntax:
  sticker EMOJI [properties]

Emoji:
  sticker 🔥
  sticker ⚡
  sticker ✨
  sticker 💪
  sticker 😀

Properties (chain with spaces):
  position X Y             → 0-100 percent
  scale NUMBER             → 10-500 (default 100)
  rotation DEG             → -180 to +180
  opacity 0-100
  animation NAME           → popIn, bounceIn, overshootPop, etc.
  duration SEC             → animation duration (0.3-3.0)

Example:
  sticker 🔥 position 15 22 scale 90 animation popIn duration 0.6
  sticker ⚡ position 85 22 scale 90 animation bounceIn duration 0.5

### 🎞️ TEXT ANIMATIONS (100+)
Basic:    fadeIn, fadeUp, fadeDown, slideLeft, slideRight, slideUp, slideDown,
          popIn, bounceIn, flicker, cinematicBlur, typewriter, decoder
Reveals:  wordReveal, characterRise, maskVertical, maskHorizontal, centerOut,
          lineDraw, blurryReveal, smokeDissolve, trailFade
Glitch:   glitch, rgbSplit, sliceGlitch, blockGlitch, staticNoise,
          vcrDistort, shakeJitter, cyberpunk, matrixRain, interlaced
Waves:    wave, bounceWave, sineWave, liquidMelt, flagWave, waterRipple,
          heatWave, elasticWave, pulsingWave, turbulent, circularWave
Bounces:  overshootPop, elasticDrop, jellyBounce, microBounce, stompBounce,
          squeezeStretch, float, diagonalJump, gravityFall, heavyLanding,
          doubleBounce, bouncySpin, snapBack, springString, sideKick
Sliders:  flyDiagonalTL, flyDiagonalBR, crossSlide, accelSlide, decelSlide,
          splitSlide, zigzagSlide, smoothGlide, infiniteScroll, pushSlide
Rotations: flip3DX, flip3DY, rotate3D, yAxisSwap, xAxisSwap, vortexSpin,
          zAxisSpin, spiralIn, tornado, skewSpin, pendulum, propeller,
          barrelRoll, cubeRoll, gentleTilt, twister
Zooms:    zoomIn, zoomOut, cinematicZoom, hyperZoomOut, pulseScale,
          elasticZoom, lensFlareZoom, shrinkReveal, popScale, depthZoom, snapZoom
Special:  scribble, neonGlow, gradientShift, ghostTrail, silhouette,
          explosion, implosion, pulse, shake

### 🎚️ ADJUSTMENTS (24 sliders, -100 to +100)
  brightness, contrast, exposure, whites, blacks, shadows, highlights,
  saturation, vibrance, clarity, temperature, tint, noise, sharpen, vignette,
  reds, oranges, yellows, greens, cyans, blues, purples, magentas, skintones

  Usage:
    brightness 20, contrast 25, saturation 30
    temperature 10, tint -8, vibrance 35

  Hindi/Urdu synonyms:
    lal  → reds
    hara → greens
    neela → blues
    peela → yellows

### 🔍 FILTERS (9 primitives)
  brightness 0-200 (default 100)
  contrast 0-200
  saturation 0-200
  hue 0-360
  grayscale 0-100
  sepia 0-100
  invert 0-100
  blur 0-20
  opacity 0-100

### ✨ EFFECTS (150+ presets — single word, all lowercase)
Motion:   shake, tremor, quake, earthquake, hit, impact, jolt, rumble, vibration,
          jitter, chaos, bounce, punch, kick, throb, beat, spring, elastic,
          headbang, pulse, heartbeat, breath, pump, thump, drum,
          zoomPulse, zoomHard, zoomSoft, push, pull, rush, slam,
          wobble, swing, sway, rock, spin, roll, whirl,
          glitch, noise, digital, rgbSplit, pixel, stutter, tear, vhs, staticFx,
          signalLoss, flicker, strobe, flashFast, tv, lightning, blink, spark
Color:    warm, cool, vintage, cinematic, bw, dreamy, vivid, faded, dramatic,
          noir, moody, darkDrama (use moody for dark cinematic), 
          tealOrange, hollywood, blockbuster, filmLook, epic,
          thriller, bleach, bleachBypass, sepiaMem, sepiaDeep, retro8mm,
          kodak, polaroid, oldFilm, antique, monochrome, graySoft, grayHard,
          inkwell, filmNoir, cyberpunk, vaporwave, synthwave, plasma,
          electric, techno, neonCity, retrowave, gold, sunrise, sunset,
          goldenHour, amber, ember, copper, autumn, moonlight, midnight,
          ice, frost, ocean, sky, deepBlue, grunge, gritty, somber,
          infrared, matrix, thermal, xray, duotone, spectrum, hyperSat,
          softFocus, pastel, creamy, haze, bloom, ethereal, hdr, punchy,
          dynamic, vividHard, contrastMax, sepia, sepiaWarm, brownTone,
          coffee, flashWhite, flashSoft, lightBurst, overexpose
Overlay:  oRain, oSnow, oDust, oSparks, oEmbers, oStars, oBokeh, oFireFlies,
          oFog, oSmoke, oHaze, oMist, oNoise, oFilmGrain, oBlackNoise,
          oWhiteNoise, oScanlines, oStaticTV, oLightLeak, oLensFlare,
          oBloom, oSunburst, oGodRays, oFlicker, oStrobe, oPulseFx,
          oBlink, oBlueLake, oWarmWash, oCoolWash, oTealWash, oRoseWash,
          oVignette, oBlackBars, oVhsLines, oGlitchBars

### 🔲 TRANSFORM
  scale VALUE              (10-500, default 100)
  rotation VALUE           (-360 to +360)
  positionX VALUE          (0-100)
  positionY VALUE          (0-100)
  cropL / cropR / cropT / cropB VALUE (0-45)

### ⏩ SPEED
  speed 0.25x / 0.5x / 1x / 1.5x / 2x / 3x / 4x

### ✂️ TRIM
  trim left        → cut from playhead to start
  trim right       → cut from playhead to end
  split            → divide at playhead

### 🟢 CHROMA KEY
  green screen
  chroma #00ff00
  chroma #00ff00 similarity 30 smoothness 20
  chroma #0000ff similarity 40 smoothness 30 spill 50

### 🎨 COLOR WHEEL
  shadows    HUE SAT INTENSITY       (e.g. shadows red 50 40)
  midtones   HUE SAT INTENSITY
  highlights HUE SAT INTENSITY
  hdr 0-200                          (HDR white boost)

### ⇄ TRANSITIONS (100+ types)

#### ▸ SINGLE-CLIP TRANSITIONS (applies to selected clip)
  fade in 0.5
  dissolve 0.8
  fade black 0.5
  fade white 0.5
  blur 0.5
  slide left 0.6
  push left 0.5
  zoom in 0.5
  zoom out 0.5
  wipe right 0.5
  circleIn 0.5
  glitch 0.5
  flashWhite 0.3
  spinCW 0.5
  swirl 0.6

#### ▸ PER-CLIP TRANSITIONS ON LAYER (RECOMMENDED — no clip selection needed)
Syntax:
  L{layer} transitions, C{clip} {transition name}, C{clip} {transition name}, ...

- L{layer}: 1 = V1, 2 = V2, 3 = V3, etc.
- C{clip}: 1-based clip index in that layer (sorted by timeline position)
- {name}: any transition name (fade, slide left, push right, zoom in, glitch, ...)
- Use "skip" or "none" to skip a clip
- Extra C-entries beyond available clips → silently ignored
- First clip of layer → transition saved but no visual effect (no left neighbor)

Single layer (5 clips, 4 transitions visible):
  L1 transitions, C1 fade, C2 slide left, C3 push right, C4 zoom in, C5 dissolve

Two layers:
  L1 transitions, C1 fade, C2 push, C3 fade
  L2 transitions, C1 dissolve, C2 zoom out, C3 glitch

Mixed with other commands:
  L1 transitions, C1 fade, C2 slide left, ratio 9:16, brightness 20

Skip one clip:
  L1 transitions, C1 fade, C2 skip, C3 zoom in

#### ▸ ALL JUNCTIONS (same pattern on every adjacent pair)
  transition all fade 0.5
  transition all dissolve 0.6

#### ▸ AT SPECIFIC TIME
  transition at 3 fade 0.5
  transition at 6 dissolve 0.8

#### ▸ LAYER PATTERN (cycling)
  layer v1 transitions dissolve, slide left, zoom in
  layer v1 transitions dissolve, null, zoom in
  layer v1 transitions dissolve, slide, zoom loop

#### Available Transition Names (partial list — 100+ total)
  fade, dissolve, fadeblack, fadewhite, blur,
  slideleft, slideright, slideup, slidedown,
  pushleft, pushright, pushup, pushdown,
  zoomin, zoomout, crosszoom, gaussianzoom,
  wipeleft, wiperight, wipeup, wipedown, linearwipe,
  circlein, circleout, clockwipe, irisbox, radial,
  glitch, rgbsplit, vhs, signalloss, pixelize,
  whiteflash, lightleak, filmburn, lensflare,
  spinCW, spinCCW, swirl, barrelroll, twirl,
  heart, starburst, diamond, spiral, ink splash

### 🖌️ BRUSH
  brush draw                    → enable draw mode
  brush stop                    → disable draw mode
  brush clear                   → clear all strokes
  brush pen / marker / chalk / neon / glow / spray

  brush TYPE color COLOR width WIDTH
  brush neon color cyan width 25
  brush marker color red width 40
  brush pen color #00ff66 width 10

  brush gradient COLOR1 to COLOR2
  brush gradient red to blue
  brush gradient #ff0066 to #00ffcc
  brush solid                   → turn off gradient
  ### 🎵 VISUALIZER (audio-reactive layer)

Syntax:
  visualizer COMMAND

Commands:
  visualizer add                    → create visualizer for selected audio clip
  visualizer remove                 → delete current visualizer
  visualizer preset NAME            → change preset
  visualizer color1 #hex | COLORNAME → ring color A
  visualizer color2 #hex | COLORNAME → ring color B
  visualizer size N                 → 15..60 (percent of screen)
  visualizer pos X Y                → 0..100 (0,0 = top-left)
  visualizer opacity N              → 0..100
  visualizer glow on | off          → enable/disable glow
  visualizer reaction N             → 0..2 beat sensitivity
  visualizer text "Hello"           → show text inside circle
  visualizer text "Hi" size 60      → text + custom size
  visualizer show text              → enable text display
  visualizer show image             → enable image display
  visualizer hide text              → hide text
  visualizer hide image             → hide image
  visualizer order text-top         → text on top of image
  visualizer order image-top        → image on top of text

Preset names (aliases):
  neon / glow / neonring          → Neon Glow Ring
  spectrum / freq / bars          → Frequency Spectrum Ring
  particle / orbit                → Particle Orbit Ring
  liquid / wave                   → Liquid Wave Ring
  double / doubleorbit            → Double Orbit Rings
  dots / dotted / dottedradial    → Dotted Radial Wave
  vinyl / record / vinylrecord    → Vinyl Record Spin
  center / art / centerart        → Audio Reactive Center Art
  broken / segments / brokenring  → Broken Segment Ring
  vortex / tunnel / vortextunnel  → Vortex Tunnel

⚠️ IMPORTANT:
  Visualizer auto-detects beats from the LINKED AUDIO clip.
  User MUST select an audio clip BEFORE running "visualizer add".

Example:
  visualizer add, visualizer preset vortex, visualizer color1 #ff0066, visualizer color2 cyan,
   visualizer size 50, visualizer glow on, visualizer text "🔥", visualizer show text

### 🎙️ AUDIO FX
  audio studio, audio warm, audio bright, audio vocal, audio podcast,
  audio deep, audio monster, audio chipmunk, audio baby, audio robot,
  audio echo, audio reverb, audio cave, audio stadium, audio telephone,
  audio underwater, audio whisper, audio radio

### 🔊 VOLUME
  volume 0.5     (0.0 to 1.0)
  volume 1.0
  mute

### ⚙️ SPECIAL COMMANDS
  tighten track         → close gaps from playhead
  clear keyframes       → remove all keyframes
  ratio 16:9 / 9:16 / 1:1 / 4:5 / 3:4 / 21:9

═══════════════════════════════════════════════════════════════
  TIMESTAMPED MULTI-LAYER BLOCKS + TRAILING LINEAR COMMANDS
═══════════════════════════════════════════════════════════════
For time-ranged edits, use [MM:SS - MM:SS] format.
After all timestamped blocks, you can add TRAILING LINEAR commands
(transitions, ratio, adjustments, effects) — they work correctly.

Example structure:
  [00:00 - 00:04] text "STRESSED" font serif size 110 color white position 50 45 animation popScale
  [00:01 - 00:04] text "You're not" font minimal size 42 color white position 50 20 animation fadeUp
  [00:02 - 00:04] text "from doing too much" font modern size 42 color white position 50 72 animation characterRise
  [00:00 - 00:12] sticker 🔥 position 15 22 scale 90 animation popIn duration 0.6
  [00:00 - 00:12] sticker ⚡ position 85 22 scale 90 animation popIn duration 0.6

  L1 transitions, C1 fade, C2 slide left, C3 push right
  ratio 9:16, moody, hdr, contrast 20, saturation 15, vibrance 30, vignette 40

═══════════════════════════════════════════════════════════════
  COMPLETE EXAMPLES
═══════════════════════════════════════════════════════════════

### Example 1 — Cinematic Intro
User: "Make a 5-second cinematic intro with fade-in title"

Output:
[00:00 - 00:05] text "YOUR STORY" font cinematic size 72 italic color white align center position 50 50 animation fadeIn, glow white 20
[00:00 - 00:05] cinematic, brightness 98, contrast 115, saturation 90, shadows 20, highlights -10
[00:00 - 00:05] vignette 40
[00:00 - 00:05] fade in 1.0

### Example 2 — Neon TikTok Text
User: "Add neon text with glow"

Output:
[00:00 - 00:05] text "TURN UP" font display size 96 color white align center position 50 50 animation popScale glow #ff0066 50 stroke 4 #ff0066 shadow black 15 0 4 tracking -4
[00:00 - 00:05] contrast 20, saturation 40, vibrance 60
[00:00 - 00:05] sticker 🔥 position 15 22 scale 90 animation popIn duration 0.6

### Example 3 — Retro Gradient Title
User: "80s retro style title with gradient"

Output:
[00:00 - 00:05] text "MOODY" font retro size 140 color white align center animation popIn gradient #ff0066 #ffcc00 45 glow cyan 50 stroke 6 black shadow black 20 6 6
[00:00 - 00:05] retro8mm, contrast 8, saturation 18
[00:00 - 00:05] oScanlines, oVhsLines

### Example 4 — Full Motivational Reel (20s vertical)
User: "20-second motivational reel with multi-track images and stylish text"

Output:
[00:00 - 00:04] text "So you're not" font minimal size 55 color white align center position 50 25 animation fadeUp tracking -1
[00:01 - 00:04] text "TRULY" font serif size 110 color #ffcc00 align center position 50 50 animation typewriter tracking -2 stroke 3 #000000
[00:02 - 00:04] text "stressed" font handwriting size 70 color white align center position 50 75 animation characterRise rotation -3
[00:04 - 00:08] text "you are" font mono size 45 color #00ffcc align center position 50 28 animation typewriter tracking 2
[00:05 - 00:08] text "OBSESSED" font serif size 100 italic color white align center position 50 52 animation overshootPop tracking -3 glow #ff0066 35
[00:06 - 00:08] text "with the fire inside" font handwriting size 55 color #ffcc00 align center position 50 78 animation blurryReveal
[00:08 - 00:12] text "That spark" font minimal size 50 color white align center position 50 30 animation slideDown tracking -1
[00:09 - 00:12] text "WON'T" font display size 115 color white align center position 50 52 animation stompBounce tracking -3 stroke 4 #ff0066
[00:10 - 00:12] text "let you sleep at night" font serif size 48 italic color #60EFFF align center position 50 78 animation wordReveal
[00:12 - 00:16] text "so wake up" font handwriting size 60 color #ffcc00 align center position 50 28 animation popIn rotation -8
[00:13 - 00:16] text "AND BUILD" font serif size 108 color white align center position 50 52 animation popScale tracking -3 glow #00ffcc 40
[00:14 - 00:16] text "your dream" font mono size 52 color #ff0066 align center position 50 78 animation trailFade
[00:16 - 00:20] text "START" font minimal size 55 color white align center position 50 25 animation fadeUp tracking -1
[00:17 - 00:20] text "TODAY" font serif size 130 color #ffcc00 align center position 50 50 animation bounceIn tracking -4 stroke 3 #000000 shadow black 20 0 4
[00:18 - 00:20] text "your future self is waiting" font handwriting size 45 color white align center position 50 78 animation characterRise
[00:16 - 00:20] sticker 🔥 position 12 22 scale 95 animation popIn duration 0.7
[00:16 - 00:20] sticker ⚡ position 88 22 scale 95 animation bounceIn duration 0.6
[00:17 - 00:20] sticker ✨ position 12 82 scale 90 animation overshootPop duration 0.8
[00:17 - 00:20] sticker 💪 position 88 82 scale 95 animation popIn duration 0.7

L1 transitions, C1 fade, C2 slide left, C3 push right, C4 zoom in, C5 dissolve

ratio 9:16, moody, hdr, brightness 5, contrast 22, saturation 18, vibrance 35, clarity 22, sharpen 28, vignette 38, temperature 6, tint -6

═══════════════════════════════════════════════════════════════
  ⚡ PATTERNS TO NOTICE
═══════════════════════════════════════════════════════════════

1. ratio 9:16 for vertical reels (first or in trailing section)
2. Time blocks — multiple texts overlap for layered typography
3. Font variety — display for big words, minimal for small, serif for elegance,
   handwriting for emotion, mono for technical
4. Animation variety — fadeUp, popScale, characterRise, overshootPop, stompBounce
5. Color strategy — white base, accent colors for emphasis (gold, cyan, pink)
6. Tracking negative for tight big text (-3, -4), positive for spaced small (2)
7. Position layering — Y values (25, 50, 75) stack vertically
8. AVOID "bold" — use stroke + glow + shadow + size for emphasis
9. Sticker animations always include duration (0.5-0.8s)
10. Filters at end in trailing section — moody, hdr, contrast, saturation, vignette

Style recipe for similar request:
  - Big serif for KEY words (with stroke/glow)
  - Smaller minimal/mono for connecting phrases
  - Handwriting for emotional emphasis
  - Time-block each word 2-4 seconds
  - Stack vertically (25, 50, 75 Y positions)
  - Accent colors (#ffcc00, #ff0066, #60EFFF)
  - Multi-clip transitions with L1 transitions C1 ... syntax
  - Add effect at end (moody, hdr, vignette, contrast)

### 📌 EXPORT TIPS
- Export dialog has a toggle: OFF = full timeline, ON = custom range
- Transitions work best with adjacent clips (no gaps)
- Multi-track visual clips (V2+) are not included in base FFmpeg export — only V1 base track
- Text/stickers from ALL tracks are overlaid correctly
- Test export with toggle OFF first to verify full timeline

Now wait for the user's request. Do not output anything until they tell you what they want.
""".trimIndent()

// ═══════════════════════════════════════════════════════════════
//  COMMANDS TAB
// ═══════════════════════════════════════════════════════════════
@Composable
private fun CommandsContent() {

    CommandCategory(
        "📝 Text — Basic", listOf(
            "text \"Hello\" size 48 color #ff0066",
            "text \"Hi\" size 72 animation typewriter",
            "text \"Title\" font cinematic size 96 italic",
            "font handwriting size 24",
            "font \"Playfair Display\" size 48",
            "align left / center / right",
            "anchor top-left / bottom-right / center",
            "position 50 50",
            "tracking -3",
            "lineheight 1.5",
            "rotation -10"
        )
    )

    CommandCategory(
        "🌟 Text Effects — Stroke/Glow/Shadow", listOf(
            "stroke 6 black",
            "stroke 8 red",
            "stroke 3 #00ffcc",
            "nostroke",
            "glow #ff0066 50",
            "glow cyan 40",
            "glow white 30",
            "noglow",
            "shadow black 15 5 5",
            "shadow #000000 20 0 8",
            "noshadow"
        )
    )

    CommandCategory(
        "🌈 Text Gradient", listOf(
            "gradient red to blue",
            "gradient #ff0066 #00ffcc",
            "gradient magenta cyan 45",
            "gradient #ffcc00 #ff0066 90",
            "solid"
        )
    )

    CommandCategory(
        "🎞️ Text Animations", listOf(
            "animation fadeIn",
            "animation typewriter",
            "animation popIn",
            "animation popScale",
            "animation slideLeft",
            "animation bounceIn",
            "animation characterRise",
            "animation blurryReveal",
            "animation smokeDissolve",
            "animation wordReveal",
            "animation sliceGlitch",
            "animation overshootPop",
            "animation stompBounce",
            "animation neonGlow",
            "animation glitch",
            "animation trailFade"
        )
    )
    CommandCategory(
        "🎵 Visualizer", listOf(
            "visualizer add",
            "visualizer remove",
            "visualizer preset vortex",
            "visualizer preset spectrum",
            "visualizer preset vinyl",
            "visualizer color1 #ff0066",
            "visualizer color2 cyan",
            "visualizer size 50",
            "visualizer pos 50 50",
            "visualizer opacity 80",
            "visualizer glow on",
            "visualizer reaction 1.5",
            "visualizer text \"Hello\"",
            "visualizer text \"Hi\" size 60",
            "visualizer show text",
            "visualizer show image",
            "visualizer hide text",
            "visualizer hide image",
            "visualizer order text-top",
            "visualizer order image-top"
        )
    )
    CommandCategory(
        "😀 Stickers", listOf(
            "sticker 🔥",
            "sticker ⚡",
            "sticker ✨",
            "sticker 💪",
            "sticker 😀",
            "sticker 🔥 position 15 22 scale 90",
            "sticker ⚡ animation popIn duration 0.6",
            "sticker ✨ position 12 82 animation overshootPop duration 0.7"
        )
    )

    CommandCategory(
        "🎚️ Adjustments", listOf(
            "brightness 20", "contrast 25", "saturation 30",
            "shadows 30", "highlights -15",
            "temperature 10", "tint -8",
            "vibrance 35", "clarity 22",
            "sharpen 28", "vignette 38",
            "reds 50", "blues -40", "lal 30", "hara -20"
        )
    )

    CommandCategory(
        "🔍 Filters", listOf(
            "grayscale", "sepia 80", "invert 100",
            "blur 5", "hue 90"
        )
    )

    CommandCategory(
        "✨ Effects — Motion", listOf(
            "shake, pulse, glitch, wobble",
            "heartbeat, strobe, flicker",
            "zoomPulse, zoomHard, zoomSoft",
            "bounce, throb, elastic"
        )
    )

    CommandCategory(
        "🎨 Effects — Color", listOf(
            "vintage, cinematic, warm, cool, bw",
            "dreamy, vivid, faded, dramatic, noir",
            "cyberpunk, vaporwave, synthwave",
            "moody, darkDrama, grunge, thermal",
            "hdr, contrastMax, gold, flashSoft"
        )
    )

    CommandCategory(
        "🎬 Effects — Overlay", listOf(
            "oRain, oSnow, oDust, oSparks",
            "oFog, oSmoke, oHaze, oMist",
            "oScanlines, oFilmGrain",
            "oLightLeak, oLensFlare",
            "oVignette, oBlackBars, oVhsLines"
        )
    )

    CommandCategory(
        "🔲 Transform", listOf(
            "scale 150", "rotation 45",
            "positionX 30", "positionY 70",
            "cropL 10", "cropR 10"
        )
    )

    CommandCategory(
        "🎬 Timeline", listOf(
            "[00:00 - 00:05] \"Welcome\" animation typewriter",
            "[00:05 - 00:08] brightness 130, saturation 140",
            "tighten track",
            "clear keyframes"
        )
    )

    CommandCategory(
        "⇄ Transitions — Per Clip (BEST)", listOf(
            "L1 transitions, C1 fade, C2 slide left",
            "L1 transitions, C1 fade, C2 push right, C3 zoom in",
            "L1 transitions, C1 fade, C2 slide left, C3 push right, C4 zoom in, C5 dissolve",
            "L1 transitions, C1 fade, C2 skip, C3 zoom in",
            "L2 transitions, C1 dissolve, C2 glitch"
        )
    )

    CommandCategory(
        "⇄ Transitions — Other", listOf(
            "fade in 0.5",
            "dissolve 0.8",
            "slide left 0.6",
            "transition all fade 0.5",
            "transition at 3 dissolve 0.8",
            "layer v1 transitions dissolve, slide left, zoom in"
        )
    )

    CommandCategory(
        "🖌️ Brush", listOf(
            "brush draw", "brush stop", "brush clear",
            "brush pen", "brush neon", "brush marker",
            "brush neon color cyan width 25",
            "brush gradient red to blue",
            "brush gradient #ff0066 to #00ffcc",
            "brush solid"
        )
    )

    CommandCategory(
        "🔊 Audio", listOf(
            "audio echo", "audio reverb", "audio robot",
            "audio underwater", "audio studio"
        )
    )

    CommandCategory(
        "🟢 Chroma", listOf(
            "green screen",
            "chroma #00ff00",
            "chroma #0000ff similarity 30"
        )
    )

    CommandCategory(
        "🌈 Color Wheel", listOf(
            "shadows red 50 40",
            "midtones blue 40 50",
            "highlights green 30 60",
            "hdr 120"
        )
    )

    CommandCategory(
        "⚙️ Special", listOf(
            "ratio 9:16", "ratio 16:9", "ratio 1:1",
            "speed 2x", "trim left", "split",
            "volume 0.5"
        )
    )
}

@Composable
private fun CommandCategory(title: String, commands: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF181818))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        commands.forEach { cmd ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F0F0F))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    cmd,
                    color = Color(0xFF60EFFF),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  GUIDE TAB
// ═══════════════════════════════════════════════════════════════
@Composable
private fun GuideContent() {
    GuideSection(
        "🎬 Getting Started", listOf(
            "1. Dashboard → + button → new project",
            "2. + Media → import video/image/audio",
            "3. Clip select karo → tools lagao",
            "4. Feature Shelf → tool tap karo",
            "5. Timeline pe clips drag/split/trim karo",
            "6. 💾 Export → Start Export"
        )
    )

    GuideSection(
        "✂️ Timeline Basics", listOf(
            "Drag clip → move horizontally (time) & vertically (track)",
            "Drag edges → trim start/end",
            "Tap track label (V1) → drag to swap track order",
            "👁 / 🔊 icons → hide/mute track",
            "Pinch → zoom timeline",
            "Multi-track drag → V4 se V3 pe drag karo to clip swap ho jayega"
        )
    )

    GuideSection(
        "📝 Text & Stickers", listOf(
            "Text panel → add text, font, color, gradient, shadow, glow",
            "Preview pe text drag/pinch → move/scale/rotate",
            "Stickers → 10 categories × 20 emojis",
            "Text + Sticker → keyframes + animations",
            "⚠️ AVOID 'bold' font weight — animations render issues ka reason",
            "Emphasis ke liye: stroke + glow + shadow + size use karo"
        )
    )

    GuideSection(
        "🌟 Text Effects — Stroke / Glow / Shadow", listOf(
            "Stroke → outline around letters",
            "Glow → soft neon blur",
            "Shadow → drop shadow with offset",
            "Gradient → 2-color fill with angle",
            "Sab combine ho sakte hain"
        )
    )

    GuideSection(
        "🎨 Filters & Effects", listOf(
            "Filters → 9 primitives (brightness, contrast, etc.)",
            "Effects → 150+ presets (motion, color, overlay)",
            "Adjustments → 24 sliders for pro grading",
            "Color Wheels → shadows/midtones/highlights + HDR",
            "Chroma Key → green screen removal",
            "8K Look = moody + hdr + contrast + saturation + vibrance + sharpen + vignette"
        )
    )

    GuideSection(
        "⇄ Transitions", listOf(
            "Per-clip: L1 transitions, C1 fade, C2 slide left, C3 zoom in",
            "Clips adjacent hone chahiye (koi gap nahi)",
            "C1 pe transition set hota hai but render nahi (no left neighbor)",
            "5 clips = 4 visible transitions",
            "Layer pattern: layer v1 transitions dissolve, slide left, zoom in",
            "Duration slider 0.2s - 3s"
        )
    )

    GuideSection(
        "🖌️ Brush & Mask", listOf(
            "Brush panel → Create layer → Start Drawing",
            "Preview pe finger se draw karo",
            "Brush: pen/marker/chalk/neon/glow/spray",
            "Gradient support (start/mid/end colors)",
            "Mask → Circle/Rect/Linear/Heart/Custom",
            "Custom → Pen tool → tap points → close on first point"
        )
    )
    GuideSection(
        "🎵 Visualizer", listOf(
            "1. Timeline pe AUDIO clip add karo",
            "2. Audio clip SELECT karo",
            "3. FeatureShelf → 🎵 Visualizer tap karo",
            "4. Beats auto-detect honge (2-5 sec)",
            "5. Visualizer timeline pe V-layer mein ban jayegi",
            "6. Preview pe drag/pinch → move/scale/rotate",
            "7. Circle Content: 🖼️ Image ya 📝 Text ON karo",
            "8. Text mode mein full TextPanel khulta hai (font/color/glow/shadow/gradient)",
            "9. Both ON → Order toggle (Image→Text / Text→Image)",
            "10. Visualizer layer ko V2/V3 pe drag karo → upar/neeche layering"
        )
    )
    GuideSection(
        "🔲 Transform & Keyframes", listOf(
            "Transform → Position/Scale/Rotation/Anchor",
            "◆ icon → add/remove keyframe at playhead",
            "Auto-keyframe: agar keyframes hain, values change karne pe auto-add",
            "📊 Easing Graphs → 14 presets",
            "📈 Full Keyframe Graph → drag points to edit"
        )
    )

    GuideSection(
        "⏩ Speed & Volume", listOf(
            "Speed panel → 0.25x se 4x",
            "Linked clips sync automatically",
            "Volume panel → per-clip volume + mute"
        )
    )

    GuideSection(
        "💾 Export", listOf(
            "Export button (top-right)",
            "🎚️ Custom Range Toggle: OFF = full timeline, ON = custom time",
            "Resolution: 480p/720p/1080p/2K/4K",
            "FPS: 24/25/30/60",
            "Bitrate: auto or manual (1-50 Mbps)",
            "Format: MP4 or MOV",
            "Multi-track base = V1 only (transitions V1 pe)",
            "Text/stickers sab tracks se overlay hote hain"
        )
    )

    GuideSection(
        "💡 Pro Tips", listOf(
            "Multi-select → ⏩ Select Forward / ⏪ Select Backward",
            "Bulk drag → saari selected clips saath move",
            "Magnet 🧲 → close gaps in track",
            "Split ✂️ → playhead pe clip divide",
            "◆ → keyframe at playhead",
            "Undo/Redo → ↶ ↷ buttons",
            "Code Mode → prompt-based editing",
            "Preview aur Export match — same position, same animations"
        )
    )
}

@Composable
private fun GuideSection(title: String, items: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF181818))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("•", color = Color(0xFF7C3AED), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    item,
                    color = Color(0xFFCCCCCC),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}