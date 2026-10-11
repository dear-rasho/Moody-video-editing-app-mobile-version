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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.moody.moodyvideoeditor.utils.PromptChunks
import kotlinx.coroutines.delay

@Composable
fun HelpGuideScreen(onBack: () -> Unit) {
    var activeTab by remember { mutableStateOf("prompt") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {

        // ═══════════════════════════════════════════════════════════
        //  TOP BAR
        // ═══════════════════════════════════════════════════════════
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

        // ═══════════════════════════════════════════════════════════
        //  TAB CHIPS
        // ═══════════════════════════════════════════════════════════
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

        // ═══════════════════════════════════════════════════════════
        //  CONTENT
        // ═══════════════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (activeTab) {
                "prompt" -> PromptContent()
                "commands" -> CommandsContent()
                "guide" -> GuideContent()
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TAB CHIP
// ═══════════════════════════════════════════════════════════════

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
private fun PromptContent() {
    val context = LocalContext.current

    var fullCopied by remember { mutableStateOf(false) }
    var copiedChunkId by remember { mutableStateOf<String?>(null) }
    var expandedChunkId by remember { mutableStateOf<String?>(null) }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as ClipboardManager

    fun copyToClipboard(label: String, text: String, onDone: () -> Unit) {
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            onDone()
        } catch (_: Exception) {
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // ─── HOW TO USE ───
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
                "1. Copy a chunk below (or the FULL prompt)\n" +
                        "2. Paste into ChatGPT / Claude / Gemini\n" +
                        "3. Tell AI what edit you want\n" +
                        "4. Copy AI's commands\n" +
                        "5. Paste in Code Mode → Apply",
                color = Color(0xFFCCCCCC),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Text(
                "💡 Tip: For simple edits, copy only the relevant chunk " +
                        "to save AI credit.",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }

        // ─── FULL PROMPT BUTTON ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (fullCopied) Color(0xFF22C55E)
                    else Color(0xFF7C3AED)
                )
                .pointerInput(fullCopied) {
                    detectTapGestures {
                        copyToClipboard(
                            "Moody Full Prompt",
                            PromptChunks.fullPrompt()
                        ) {
                            fullCopied = true
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (fullCopied) "✅ Copied Full Prompt!"
                    else "📋 Copy FULL Prompt (all features)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${PromptChunks.fullPrompt().length} chars",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 9.sp
                )
            }
        }

        // ─── CHUNK LIST HEADER ───
        Text(
            "📦 Feature Chunks — copy only what you need",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )

        // ─── CHUNK ROWS ───
        PromptChunks.ALL_CHUNKS.forEach { chunk ->
            val isCopied = copiedChunkId == chunk.id
            val isExpanded = expandedChunkId == chunk.id

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF181818))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(chunk.icon, fontSize = 18.sp)

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            chunk.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            chunk.subtitle,
                            color = Color(0xFF888888),
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }

                    // Expand toggle
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2A2A2A))
                            .pointerInput(chunk.id) {
                                detectTapGestures {
                                    expandedChunkId =
                                        if (isExpanded) null else chunk.id
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isExpanded) "▲" else "▼",
                            color = Color(0xFFAAAAAA),
                            fontSize = 10.sp
                        )
                    }

                    // Copy button
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isCopied) Color(0xFF22C55E)
                                else Color(0xFF7C3AED).copy(alpha = 0.25f)
                            )
                            .pointerInput(chunk.id) {
                                detectTapGestures {
                                    copyToClipboard(
                                        "Moody Prompt: ${chunk.title}",
                                        chunk.content
                                    ) {
                                        copiedChunkId = chunk.id
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isCopied) "✓ Copied" else "📋 Copy",
                            color = if (isCopied) Color.White
                            else Color(0xFFA78BFA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Expanded content
                if (isExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(
                                RoundedCornerShape(
                                    bottomStart = 10.dp,
                                    bottomEnd = 10.dp
                                )
                            )
                            .background(Color(0xFF0F0F0F))
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    ) {
                        Text(
                            chunk.content,
                            color = Color(0xFFAAAAAA),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }

        // ─── Reset copy indicators ───
        LaunchedEffect(copiedChunkId) {
            if (copiedChunkId != null) {
                delay(2000)
                copiedChunkId = null
            }
        }
        LaunchedEffect(fullCopied) {
            if (fullCopied) {
                delay(2000)
                fullCopied = false
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  COMMANDS TAB
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CommandsContent() {

    CommandCategory(
        "📝 Text — Create", listOf(
            "text \"Hello\" size 48 color #ff0066",
            "text \"Hi\" size 72 animation typewriter",
            "text \"Title\" font \"Cinzel\" size 96 italic",
            "text \"HELLO\" glow cyan 40 stroke 3 black",
            "text \"WORD\" gradient #ff0066 #00ffcc 45",
            "text \"WRAPPED\" maxwidth 70 textscale 120",
            "text \"FAST\" animduration 1.5 animation fadeIn"
        )
    )

    CommandCategory(
        "✏️ Text — Edit / Delete / Bulk", listOf(
            "edittext \"new content\"",
            "settext \"new content\"",
            "delete text",
            "remove text",
            "duplicate text",
            "maxwidth 80",
            "textscale 150",
            "animduration 1.5",
            "textlayer 3",
            "alltext font \"Arizonia\"",
            "alltext color #ff0066",
            "alltext size 60",
            "alltext opacity 80",
            "alltext align center",
            "alltext animation popIn"
        )
    )

    CommandCategory(
        "😀 Stickers", listOf(
            "sticker 🔥",
            "sticker ⚡",
            "sticker ✨",
            "sticker 🔥 position 15 22 scale 90",
            "sticker ⚡ animation popIn duration 0.6",
            "deletesticker",
            "duplicate sticker"
        )
    )

    CommandCategory(
        "🎞️ Text Animations (89)", listOf(
            "animation fadeIn",
            "animation typewriter",
            "animation popIn",
            "animation bounceIn",
            "animation slideLeft",
            "animation characterRise",
            "animation wordReveal",
            "animation blurryReveal",
            "animation overshootPop",
            "animation stompBounce",
            "animation neonGlow",
            "animation glitch"
        )
    )

    CommandCategory(
        "🎚️ Adjustments", listOf(
            "brightness 20", "contrast 25", "saturation 30",
            "shadows 30", "highlights -15",
            "temperature 10", "tint -8",
            "vibrance 35", "clarity 22",
            "sharpen 28", "vignette 38",
            "reds 50", "blues -40",
            "lal 30", "hara -20"
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
            "shake", "pulse", "glitch", "wobble",
            "heartbeat", "strobe", "flicker",
            "zoomPulse", "bounce", "elastic"
        )
    )

    CommandCategory(
        "🎨 Effects — Color", listOf(
            "vintage", "cinematic", "warm", "cool", "bw",
            "dreamy", "vivid", "faded", "dramatic", "noir",
            "cyberpunk", "vaporwave", "synthwave",
            "moody", "grunge", "thermal", "hdr", "gold"
        )
    )

    CommandCategory(
        "🎬 Effects — Overlay", listOf(
            "oRain", "oSnow", "oDust", "oSparks",
            "oFog", "oSmoke", "oHaze", "oMist",
            "oScanlines", "oFilmGrain", "oStaticTV",
            "oLightLeak", "oLensFlare", "oBloom",
            "oVignette", "oBlackBars", "oVhsLines", "oGlitchBars"
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
            "[00:00 - 00:05] text \"Title\" animation typewriter",
            "[00:02 - 00:08] sticker 🔥 position 85 18",
            "tighten track",
            "clear keyframes",
            "graph"
        )
    )

    CommandCategory(
        "⇄ Transitions — Per Clip (BEST)", listOf(
            "L1 transitions, C1 fade, C2 slide left",
            "L1 transitions, C1 fade, C2 push right, C3 zoom in",
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
            "brush solid"
        )
    )

    CommandCategory(
        "🔊 Audio", listOf(
            "audio echo", "audio reverb", "audio robot",
            "audio underwater", "audio studio",
            "sound bell", "sound pop 120"
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
        "🎵 Visualizer", listOf(
            "visualizer add",
            "visualizer preset circularSpectrum",
            "visualizer preset vortex",
            "visualizer color1 #ff0066",
            "visualizer color2 cyan",
            "visualizer size 50",
            "visualizer glow on",
            "visualizer text \"Hello\"",
            "visualizer show text"
        )
    )

    CommandCategory(
        "⚙️ Special", listOf(
            "ratio 9:16", "ratio 16:9", "ratio 1:1",
            "speed 2x", "trim left", "split",
            "volume 0.5", "mute",
            "color matte #0066FF opacity 75 duration 6",
            "keyframe x 50"
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
            "Fonts: 117 available — Arabic, Hindi, Script, Display, Modern",
            "Emphasis ke liye: stroke + glow + shadow + size use karo"
        )
    )

    GuideSection(
        "🌟 Text Effects — Stroke / Glow / Shadow", listOf(
            "Stroke → outline around letters",
            "Glow → soft neon blur (radius up to 500)",
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
            "Edge Glow → 16 animated glowing borders",
            "Light Leak → 10 ambient light presets"
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
            "8. Text mode mein full TextPanel khulta hai",
            "9. Both ON → Order toggle (Image→Text / Text→Image)",
            "10. Visualizer layer ko V2/V3 pe drag karo"
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
            "Multi-track base = V1 only",
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
            "Fonts: 117 available (Arabic, Hindi, Script, Display, Modern)",
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
                Text(
                    "•",
                    color = Color(0xFF7C3AED),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
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