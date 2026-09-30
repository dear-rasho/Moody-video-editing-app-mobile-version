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
    // 🆕 Default tab = AI Prompt
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

        // 🆕 TAB ORDER: AI Prompt → Commands → Guide
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
//  AI PROMPT TAB (DEFAULT)
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
            "Text 'HELLO' big, animation popIn, glow cyan",
            "Slow motion 0.5x with cool blue filter",
            "Zoom in 1.5x over 3 seconds",
            "Add sticker 🔥 at top-right",
            "60-second motivational reel about stress and action",
            "Retro VHS vibe with scanlines and vintage"
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
//  MEGA AI PROMPT
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

═══════════════════════════════════════════════════════════════
  COMMAND REFERENCE — ALL SUPPORTED FEATURES
═══════════════════════════════════════════════════════════════

### 📝 TEXT
Syntax:
  text "Hello" [options]
  [seg "bold" font Impact size 72] [seg " light" font Arial size 36]   (multi-segment)

Options (chain with spaces):
  size NUMBER            → font size (8-300)
  color #hex | COLORNAME → text fill color
  font NAME              → font family (see FONT CATEGORIES below)
  bold | italic          → style
  align left|center|right
  anchor top-left | top-center | top-right | center-left | center |
         center-right | bottom-left | bottom-center | bottom-right
  position X Y           → 0-100 percent
  animation NAME         → see ANIMATIONS list below
  opacity 0-100

  ▸ TEXT OUTLINE (stroke):
    stroke WIDTH COLOR
    stroke 3 #000000
    stroke 5 red
    stroke 8 white
    nostroke                → remove outline

  ▸ TEXT GLOW (neon):
    glow COLOR RADIUS
    glow #ff0066 40         → pink glow radius 40
    glow cyan 30
    glow red 25
    glow white 20
    noglow                  → remove glow

  ▸ TEXT SHADOW:
    shadow COLOR BLUR X Y
    shadow black 8 2 2
    shadow #000000 15 0 4
    shadow black 20 5 5
    noshadow                → remove shadow

  ▸ TEXT GRADIENT (color ramp):
    gradient COLOR1 COLOR2 [ANGLE]
    gradient red blue               → 2-color gradient
    gradient red to blue            → "to" is optional
    gradient #ff0066 #00ffcc 90     → with angle (0-360)
    solid                           → turn off gradient

  ▸ TYPOGRAPHY:
    tracking NUMBER         → letter spacing (-10 to +30, negative = tighter)
    lineheight NUMBER       → line spacing multiplier (1.0-3.0)
    rotation DEG            → rotate text (-180 to +180)

FONT CATEGORIES (mention category, editor auto-picks best font):
  font handwriting   → Comic Sans, Brush Script, Dancing Script, Pacifico, Caveat
  font system        → Arial, Helvetica, Roboto, Segoe UI, Inter
  font serif         → Times New Roman, Georgia, Garamond, Playfair, Baskerville
  font mono          → Courier New, Consolas, Monaco, JetBrains Mono, Fira Code
  font display       → Impact, Bebas Neue, Oswald, Anton, Bungee, Audiowide
  font elegant       → Playfair, Cormorant, Cinzel, Bodoni, Abril Fatface
  font modern        → Poppins, Montserrat, Raleway, DM Sans, Manrope, Sora
  font titles        → Bebas Neue, Cinzel, Alfa Slab One, Abril Fatface
  font music         → Bebas Neue, Anton, Permanent Marker, Rock Salt
  font playful       → Comic Sans, Baloo 2, Fredoka, Chewy, Luckiest Guy
  font retro         → Lobster, Righteous, Bungee Shade, Monoton, Ultra
  font educational   → Open Sans, Lato, Source Sans 3, Noto Sans
  font cinematic     → Cinzel, Playfair, Cormorant, Prata, Spectra, Lora
  font minimal       → Inter, Roboto, Open Sans, Work Sans, DM Sans

Or exact font name: font "Playfair Display" size 48

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
Rotations: flip3DX, flip3DY, rotate3D, yAxisFlip, xAxisFlip, vortexSpin,
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

  Use: brightness 120, contrast 110, saturation 150

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

### ✨ EFFECTS (100+ presets — single word)
Motion:   shake, tremor, quake, earthquake, hit, impact, jolt, rumble, vibration,
          jitter, chaos, bounce, punch, kick, throb, beat, spring, elastic,
          headbang, pulse, heartbeat, breath, pump, thump, drum,
          zoomPulse, zoomHard, zoomSoft, push, pull, rush, slam,
          wobble, swing, sway, rock, spin, roll, whirl,
          glitch, noise, digital, rgbSplit, pixel, stutter, tear, vhs, staticFx,
          signalLoss, flicker, strobe, flashFast, tv, lightning, blink, spark
Color:    warm, cool, vintage, cinematic, bw, dreamy, vivid, faded, dramatic,
          noir, tealOrange, hollywood, blockbuster, filmLook, drama, epic,
          thriller, bleach, bleachBypass, sepiaMem, sepiaDeep, retro8mm,
          kodak, polaroid, oldFilm, antique, monochrome, graySoft, grayHard,
          inkwell, filmNoir, cyberpunk, vaporwave, synthwave, plasma,
          electric, techno, neonCity, retrowave, gold, sunrise, sunset,
          goldenHour, amber, ember, copper, autumn, moonlight, midnight,
          ice, frost, ocean, sky, deepBlue, moody, darkDrama, grunge,
          gritty, somber, infrared, matrix, thermal, xray, duotone,
          spectrum, hyperSat, softFocus, pastel, creamy, haze, bloom,
          ethereal, hdr, punchy, dynamic, vividHard, contrastMax, sepia,
          sepiaWarm, brownTone, coffee, flashWhite, flashSoft, lightBurst,
          overexpose
Overlay:  oRain, oSnow, oDust, oSparks, oEmbers, oStars, oBokeh, oFireFlies,
          oFog, oSmoke, oHaze, oMist, oNoise, oFilmGrain, oBlackNoise,
          oWhiteNoise, oScanlines, oStaticTV, oLightLeak, oLensFlare,
          oBloom, oSunburst, oGodRays, oFlicker, oStrobe, oPulseFx,
          oBlink, oBlueLake, oWarmWash, oCoolWash, oTealWash, oRoseWash,
          oVignette, oBlackBars, oVhsLines, oGlitchBars

### 🔲 TRANSFORM
  scale VALUE              (10-500, default 100)
  rotation VALUE           (-360 to +360 degrees)
  positionX VALUE          (0-100 percent)
  positionY VALUE          (0-100 percent)
  cropL VALUE              (0-45 percent)
  cropR VALUE
  cropT VALUE
  cropB VALUE
  anchor KEY               (top-left, center, bottom-right, etc.)

### 🎞️ KEYFRAME ANIMATIONS
Syntax:
  PROP START to END over DURATIONs [easing]

Props: zoom, scale, rotate, position, x, y

Examples:
  zoom 100 to 200 over 3s
  zoom 100 to 200 over 3s easeOut
  rotate 0 to 360 over 5s linear
  position 50 50 to 90 50 over 1s easeInOut
  scale 100 to 150 over 2s easeOutBack

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

### ⇄ TRANSITIONS (30+ types)
Single (needs left neighbor):
  fade in 0.5
  dissolve 0.8
  fade black 0.5
  fade white 0.5
  blur 0.5
  slide left 0.6
  slide right 0.6
  slide up 0.6
  slide down 0.6
  push left 0.5
  wipe right 0.5
  zoom in 0.5
  circleIn 0.5
  clockWipe 0.7
  spinCW 0.5
  swirl 0.6
  rgbSplit 0.4
  glitch 0.5
  flashWhite 0.3

All junctions:
  transition all fade 0.5
  transition all dissolve 0.6

At specific time:
  transition at 3 fade 0.5
  transition at 6 dissolve 0.8
  transition at 9 zoom in 0.5

Layer pattern:
  layer v1 transitions dissolve, slide left, zoom in
  layer v1 transitions dissolve, null, zoom in    (null = skip)
  layer v1 transitions dissolve, slide, zoom loop

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

### 😀 STICKERS
  sticker EMOJI
  sticker 😀
  sticker 🔥
  sticker ⭐

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
  graph on / graph off  → toggle keyframe graph
  clear keyframes       → remove all keyframes
  ratio 16:9 / 9:16 / 1:1 / 4:5 / 3:4 / 21:9

═══════════════════════════════════════════════════════════════
  TIMESTAMPED MULTI-LAYER BLOCKS
═══════════════════════════════════════════════════════════════
For time-ranged edits, use MM:SS format:

[00:00 - 00:05] "Welcome" animation typewriter, position center
[00:05 - 00:08] brightness 130, saturation 140
[00:08 - 00:12] sticker 🔥, motion shake
[00:12 - 00:15] audio echo, speed 1.5x

Each block applies at that time range on the timeline.

═══════════════════════════════════════════════════════════════
  COMPLETE EXAMPLES
═══════════════════════════════════════════════════════════════

### Example 1 — Cinematic Intro
User: "Make a 5-second cinematic intro with fade-in title"

Your output:
[00:00 - 00:05] text "YOUR STORY" font cinematic size 72 bold color white align center anchor center position 50 50 animation fadeIn, glow white 20
[00:00 - 00:05] cinematic, brightness 98, contrast 115, saturation 90, shadows 20, highlights -10
[00:00 - 00:05] vignette 40
[00:00 - 00:05] fade in 1.0

### Example 2 — Neon TikTok Text
User: "Add neon text with glow"

Your output:
[00:00 - 00:05] text "TURN UP" font display size 96 bold color white align center anchor center position 50 50 animation popScale glow #ff0066 50 stroke 4 #ff0066 shadow black 15 0 4 tracking -4
[00:00 - 00:05] contrast 120, saturation 140, vibrance 60
[00:00 - 00:05] sticker 🔥

### Example 3 — Retro Gradient Title
User: "80s retro style title with gradient"

Your output:
[00:00 - 00:05] text "MOODY" font retro size 140 bold color white align center anchor center animation popIn gradient #ff0066 #ffcc00 45 glow cyan 50 stroke 6 black shadow black 20 6 6
[00:00 - 00:05] retro8mm, contrast 108, saturation 118
[00:00 - 00:05] oScanlines, oVhsLines

### Example 4 — Lyrics Video
User: "Show lyrics with typewriter effect"

Your output:
[00:00 - 00:03] text "First line" font handwriting size 42 color white align center anchor center position 50 40 animation typewriter
[00:03 - 00:06] text "Second line" font handwriting size 42 color white align center anchor center position 50 40 animation typewriter
[00:06 - 00:09] text "Third line" font handwriting size 42 color white align center anchor center position 50 40 animation typewriter
[00:00 - 00:10] vivid, brightness 105, saturation 130, audio warm

### Example 5 — Chrome/Metal Text
User: "Metallic chrome text"

Your output:
[00:00 - 00:05] text "CHROME" font display size 130 bold color #e0e0e0 align center anchor center gradient #ffffff #808080 90 stroke 3 black shadow black 10 2 2 glow white 15
[00:00 - 00:05] contrast 120, saturation 30

### Example 6 — Neon Sign Style
User: "Neon sign text"

Your output:
[00:00 - 00:05] text "OPEN" font display size 130 bold color white align center anchor center glow #ff00ff 60 stroke 4 #ff00ff, darkDrama

### Example 7 — Brush Drawing
User: "I want to draw with neon on video"

Your output:
brush draw, brush neon color cyan width 25, brush gradient cyan to magenta

═══════════════════════════════════════════════════════════════
  🎬 REAL USER EXAMPLE — MOTIVATIONAL REEL (Copy this style)
═══════════════════════════════════════════════════════════════

User Request: "Make a 60-second motivational reel about stress and action. Cinematic style with big bold texts and clean animations."

Your Output (EXACTLY this format):

ratio 9:16[00:00 - 00:05] text "You're not" font minimal size 42 color white align center anchor center position 50 32 animation fadeUp tracking -1[00:01 - 00:05] text "Stressed" font display size 115 bold color white align center anchor center position 50 50 animation popScale shadow black 15 0 4 tracking -4[00:02 - 00:05] text "From" font cinematic size 70 italic color white align center anchor center position 50 68 animation cinematicBlur[00:03 - 00:05] text "doing too much" font modern size 48 color white align center anchor center position 50 82 animation characterRise tracking -2, darkDrama, vignette 40[00:05 - 00:10] clear keyframes, text "You're stressed" font minimal size 42 color white align center anchor center position 50 35 animation slideDown tracking -1[00:06 - 00:10] text "BECAUSE" font display size 110 bold color #ff0066 align center anchor center position 50 53 animation overshootPop tracking -3 glow #ff0066 10[00:07 - 00:10] text "you are doing" font modern size 48 color white align center anchor center position 50 70 animation characterRise tracking -1[00:10 - 00:16] text "TOO LITTLE" font display size 120 bold color red align center anchor center position 50 42 animation sliceGlitch tracking -4 shadow black 20 0 0[00:12 - 00:16] text "of what makes you" font minimal size 45 color white align center anchor center position 50 62 animation blurryReveal tracking -1[00:14 - 00:16] text "TRULY ALIVE" font cinematic size 75 italic color yellow align center anchor center position 50 78 animation smokeDissolve, contrastMax[00:16 - 00:23] text "Stop chasing" font minimal size 45 color white align center anchor center position 50 35 animation fadeUp tracking -1[00:18 - 00:23] text "COMFORT" font display size 110 bold color white align center anchor center position 50 52 animation stompBounce tracking -3 shadow black 15 3 3[00:20 - 00:23] text "comfort kills growth" font mono size 36 color #00ffcc align center anchor center position 50 68 animation typewriter tracking 0[00:23 - 00:30] text "Look at" font minimal size 45 color white align center anchor center position 50 30 animation characterRise tracking -1[00:25 - 00:30] text "YOUR LIFE" font display size 115 bold color white align center anchor center position 50 48 animation popIn tracking -4 shadow black 15 0 0[00:26 - 00:30] text "Are you genuinely" font cinematic size 60 italic color white align center anchor center position 50 64 animation gentleTilt[00:27 - 00:30] text "SATISFIED?" font modern size 75 bold color cyan align center anchor center position 50 80 animation neonGlow tracking -2, contrast 30[00:30 - 00:38] clear keyframes, text "Or are you" font minimal size 42 color white align center anchor center position 50 32 animation fadeDown tracking -1[00:32 - 00:38] text "HIDING" font display size 120 bold color red align center anchor center position 50 50 animation glitch tracking -4 shadow black 20 0 0[00:34 - 00:38] text "behind safe choices?" font modern size 46 color white align center anchor center position 50 68 animation wordReveal tracking -2, noise 15[00:38 - 00:46] text "and" font handwriting size 55 color yellow align center anchor center position 50 32 animation popIn rotation -10[00:40 - 00:46] text "Excited" font display size 130 bold color white align center anchor center position 50 54 animation stompBounce tracking -4 shadow black 20 0 5[00:42 - 00:46] text "in the morning" font modern size 55 color white align center anchor center position 50 76 animation characterRise tracking -2, gold, flashSoft[00:46 - 00:53] text "That excitement" font minimal size 42 color white align center anchor center position 50 35 animation blurryReveal tracking -1[00:48 - 00:53] text "REQUIRES" font display size 105 bold color white align center anchor center position 50 52 animation overshootPop tracking -3 shadow black 15 2 2[00:50 - 00:53] text "massive action" font cinematic size 70 italic color #ff0066 align center anchor center position 50 70 animation trailFade[00:53 - 01:00] text "Wake up." font minimal size 45 color white align center anchor center position 50 30 animation slideUp tracking -1[00:55 - 01:00] text "START NOW." font display size 125 bold color white align center anchor center position 50 50 animation popScale tracking -4 shadow black 25 0 0[00:57 - 01:00] text "Build your legacy." font modern size 50 bold color cyan align center anchor center position 50 70 animation typewriter tracking -2, zoom 100 to 120 over 7s linear, flashWhite, sticker 🔥

═══════════════════════════════════════════════════════════════
  ⚡ PATTERNS TO NOTICE
═══════════════════════════════════════════════════════════════

1. ratio 9:16 first — vertical reel format
2. Time blocks — multiple texts overlap
3. Font variety — display for big words, minimal for small, cinematic for italic
4. Animation variety — fadeUp, popScale, characterRise, glitch, smokeDissolve
5. Color strategy — white base, red/pink/cyan/yellow for emphasis
6. Tracking negative — tight spacing for big text (-3, -4)
7. Position layering — Y values (32, 50, 68, 82) stack vertically
8. Effects at end — darkDrama, vignette, contrast, gold, flashSoft, noise
9. clear keyframes — resets between sections
10. zoom + sticker at end — finishing flourish

Style recipe for similar request:
  - Big bold display font for KEY words
  - Smaller minimal font for connecting phrases
  - Cinematic italic for emotional emphasis
  - Time-block each word 2-3 seconds
  - Stack vertically (30, 50, 70 Y positions)
  - Bold colors for emphasis (#ff0066, red, cyan, yellow)
  - Add effect at end (contrast, vignette, gold)

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
            "text \"Title\" font cinematic size 96 bold",
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
        "🎚️ Adjustments", listOf(
            "brightness 120", "contrast 110", "saturation 150",
            "shadows 30", "highlights -15",
            "temperature 30", "tint -20",
            "vibrance 50", "clarity 30",
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
            "tealOrange, hollywood, sepiaDeep",
            "moody, grunge, thermal, xray",
            "darkDrama, contrastMax, gold, flashSoft"
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
        "🎞️ Keyframes", listOf(
            "zoom 100 to 200 over 3s",
            "position 50 50 to 90 50 over 1s",
            "rotate 0 to 360 over 5s",
            "zoom 100 to 150 over 2s easeOutBack"
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
        "⇄ Transitions", listOf(
            "fade in 0.5", "dissolve 0.8", "slide left 0.6",
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
        "😀 Stickers", listOf(
            "sticker 😀", "sticker 🔥", "sticker ⭐",
            "sticker ❤️", "sticker 🎬"
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
//  GUIDE TAB (LAST)
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
            "Pinch → zoom timeline"
        )
    )

    GuideSection(
        "📝 Text & Stickers", listOf(
            "Text panel → add text, font, color, gradient, shadow, glow",
            "Preview pe text drag/pinch → move/scale/rotate",
            "Double-tap text → edit content",
            "Stickers → 10 categories × 20 emojis",
            "Text + Sticker → keyframes + animations"
        )
    )

    GuideSection(
        "🌟 Text Effects — Stroke / Glow / Shadow", listOf(
            "Stroke → outline around letters (width + color)",
            "Glow → soft neon blur (color + radius 2-80)",
            "Shadow → drop shadow (color + blur + offset X/Y)",
            "Gradient → 2-color fill (start color + end color + angle)",
            "All can be combined — stroke + glow + shadow + gradient"
        )
    )

    GuideSection(
        "🎨 Filters & Effects", listOf(
            "Filters → 9 primitives (brightness, contrast, etc.)",
            "Effects → 100+ presets (motion, color, overlay)",
            "Adjustments → 24 sliders for pro grading",
            "Color Wheels → shadows/midtones/highlights",
            "Chroma Key → green screen removal"
        )
    )

    GuideSection(
        "🖌️ Brush & Mask", listOf(
            "Brush panel → Create layer → Start Drawing",
            "Preview pe finger se draw karo",
            "Brush pen/marker/chalk/neon/glow/spray",
            "Gradient support (start/mid/end colors)",
            "Mask → Circle/Rect/Linear/Heart/Custom",
            "Custom → Pen tool → tap points → close on first point"
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
            "Speed panel → 0.25x se 4x (presets + slider)",
            "Linked clips sync automatically",
            "Volume panel → per-clip volume + mute"
        )
    )

    GuideSection(
        "🎯 Transitions", listOf(
            "Transitions → 30+ types",
            "Duration slider (0.2s se 3s)",
            "Clip ke start pe lagti hai (needs left neighbor)",
            "Layer transitions → pattern for whole track"
        )
    )

    GuideSection(
        "💾 Export", listOf(
            "Export button (top-right)",
            "Resolution: 480p/720p/1080p/2K/4K",
            "FPS: 24/25/30/60",
            "Bitrate: auto or manual (1-50 Mbps)",
            "Format: MP4 or MOV",
            "Save location: default ya custom folder"
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
            "Code Mode → prompt-based editing"
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