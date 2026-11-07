package com.moody.moodyvideoeditor.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

fun editorFeatureIcon(key: String): ImageVector = when (key) {
    "spotlight" -> Icons.Filled.Highlight
    "layer_shadow" -> Icons.Filled.Layers
    "matte" -> Icons.Filled.GridView
    "track_matte" -> Icons.Filled.Layers
    "chromakey" -> Icons.Filled.Colorize
    "trim" -> Icons.Filled.ContentCut
    "speed" -> Icons.Filled.Speed
    "text" -> Icons.Filled.TextFields
    "blend" -> Icons.Filled.Layers
    "opacity" -> Icons.Filled.Opacity
    "captions" -> Icons.Filled.Subtitles
    "manual_captions" -> Icons.Filled.EditNote
    "animations" -> Icons.Filled.Movie
    "keyframe_easing" -> Icons.AutoMirrored.Filled.ShowChart
    "filters" -> Icons.Filled.FilterAlt
    "effects" -> Icons.Filled.AutoAwesome
    "adjustments" -> Icons.Filled.Tune
    "wheel" -> Icons.Filled.Palette
    "overlays" -> Icons.Filled.Layers
    "transitions" -> Icons.Filled.SwapHoriz
    "mask" -> Icons.Filled.CropFree
    "transform" -> Icons.Filled.Transform
    "crop" -> Icons.Filled.Crop
    "volume" -> Icons.AutoMirrored.Filled.VolumeUp
    "audiofx" -> Icons.Filled.GraphicEq
    "beats" -> Icons.Filled.MusicNote
    "visualizer" -> Icons.Filled.Equalizer
    "freeze" -> Icons.Filled.AcUnit
    "ratio" -> Icons.Filled.AspectRatio
    "delete" -> Icons.Filled.Delete
    "save_template" -> Icons.Filled.BookmarkAdd
    "replace_media" -> Icons.Filled.PermMedia
    "code" -> Icons.Filled.Code
    "export" -> Icons.Filled.FileUpload
    else -> Icons.Filled.Movie
}

fun editorPanelIconKey(title: String): String {
    val normalized = title.lowercase()
    return when {
        "track matte" in normalized -> "track_matte"
        "color matte" in normalized || "matte" in normalized -> "matte"
        "aspect ratio" in normalized -> "ratio"
        "chroma" in normalized -> "chromakey"
        "spotlight" in normalized -> "spotlight"
        "shadow" in normalized -> "layer_shadow"
        "trim" in normalized -> "trim"
        "speed" in normalized -> "speed"
        "text animation" in normalized || "animation" in normalized -> "animations"
        "keyframe" in normalized -> "keyframe_easing"
        "filter" in normalized -> "filters"
        "effect" in normalized -> "effects"
        "adjustment" in normalized -> "adjustments"
        "color wheel" in normalized -> "wheel"
        "blend" in normalized -> "blend"
        "opacity" in normalized -> "opacity"
        "overlay" in normalized -> "overlays"
        "transition" in normalized -> "transitions"
        "mask" in normalized -> "mask"
        "transform" in normalized -> "transform"
        "crop" in normalized -> "crop"
        "volume" in normalized -> "volume"
        "audio" in normalized || "sound fx" in normalized -> "audiofx"
        "beats" in normalized -> "beats"
        "visualizer" in normalized -> "visualizer"
        "freeze" in normalized -> "freeze"
        "caption" in normalized -> "captions"
        "export" in normalized -> "export"
        "code" in normalized -> "code"
        else -> "text"
    }
}
