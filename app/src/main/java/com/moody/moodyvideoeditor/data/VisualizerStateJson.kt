package com.moody.moodyvideoeditor.data

import org.json.JSONArray
import org.json.JSONObject

fun VisualizerState.toJson(): JSONObject = JSONObject().apply {
    put("preset", preset.key)
    putOptional("linkedAudioClipId", linkedAudioClipId)
    put("color1", color1)
    put("color2", color2)
    put("sensitivity", sensitivity.toDouble())
    put("smoothing", smoothing.toDouble())
    put("size", size.toDouble())
    put("positionX", positionX.toDouble())
    put("positionY", positionY.toDouble())
    put("rotation", rotation.toDouble())
    put("opacity", opacity.toDouble())
    put("glow", glow)
    put("glowIntensity", glowIntensity.toDouble())
    putOptional("imageUri", imageUri)
    put("showImage", showImage)
    put("imageScale", imageScale.toDouble())
    put("imageOpacity", imageOpacity.toDouble())
    putOptional("backgroundImageUri", backgroundImageUri)
    put("showBackgroundImage", showBackgroundImage)
    put("imageIdleRotation", imageIdleRotation)
    put("imageIdleSpeed", imageIdleSpeed.toDouble())
    put("imagePulseAmount", imagePulseAmount.toDouble())
    put("imageBassOnly", imageBassOnly)
    put("showText", showText)
    put("textContent", textContent)
    put("textState", textState.toJson())
    put("textOnTopOfImage", textOnTopOfImage)
    put("bassRingBoost", bassRingBoost.toDouble())
    put("midBarBoost", midBarBoost.toDouble())
    put("trebleSpikeBoost", trebleSpikeBoost.toDouble())
    put("beatTimesMs", JSONArray().apply { beatTimesMs.forEach(::put) })
    put("beatStrengths", JSONArray().apply {
        beatStrengths.forEach { put(it.toDouble()) }
    })
    put("beatReaction", beatReaction.toDouble())
    put("beatPulseDurationMs", beatPulseDurationMs)
    put("useBeatSync", useBeatSync)
    put("lerpFactor", lerpFactor.toDouble())
    put("startFrequencyHz", startFrequencyHz.toDouble())
    put("endFrequencyHz", endFrequencyHz.toDouble())
    put("bands", bands)
    put("maxHeight", maxHeight.toDouble())
    put("audioWindowMs", audioWindowMs)
    put("audioOffsetMs", audioOffsetMs)
    put("displayStyle", displayStyle)
    put("sideMode", sideMode)
    put("lineWidth", lineWidth.toDouble())
    put("particleSize", particleSize.toDouble())
}

fun VisualizerState.Companion.fromJson(json: JSONObject): VisualizerState {
    val default = VisualizerState()
    val presetKey = json.optString("preset", default.preset.key)
    val beatTimes = json.optJSONArray("beatTimesMs").toLongList()
    val beatStrengths = json.optJSONArray("beatStrengths").toFloatList()
    return VisualizerState(
        preset = VisualizerPreset.values().firstOrNull {
            it.key == presetKey || it.name == presetKey
        } ?: VisualizerPreset.fromKey(presetKey),
        linkedAudioClipId = json.optNullableString("linkedAudioClipId"),
        color1 = json.optLong("color1", default.color1),
        color2 = json.optLong("color2", default.color2),
        sensitivity = json.optDouble("sensitivity", default.sensitivity.toDouble()).toFloat(),
        smoothing = json.optDouble("smoothing", default.smoothing.toDouble()).toFloat(),
        size = json.optDouble("size", default.size.toDouble()).toFloat(),
        positionX = json.optDouble("positionX", default.positionX.toDouble()).toFloat(),
        positionY = json.optDouble("positionY", default.positionY.toDouble()).toFloat(),
        rotation = json.optDouble("rotation", default.rotation.toDouble()).toFloat(),
        opacity = json.optDouble("opacity", default.opacity.toDouble()).toFloat(),
        glow = json.optBoolean("glow", default.glow),
        glowIntensity = json.optDouble(
            "glowIntensity", default.glowIntensity.toDouble()
        ).toFloat().coerceIn(0f, 5f),
        imageUri = json.optNullableString("imageUri"),
        showImage = json.optBoolean("showImage", default.showImage),
        imageScale = json.optDouble("imageScale", default.imageScale.toDouble()).toFloat(),
        imageOpacity = json.optDouble("imageOpacity", default.imageOpacity.toDouble()).toFloat(),
        backgroundImageUri = json.optNullableString("backgroundImageUri"),
        showBackgroundImage = json.optBoolean(
            "showBackgroundImage", default.showBackgroundImage
        ),
        imageIdleRotation = json.optBoolean("imageIdleRotation", default.imageIdleRotation),
        imageIdleSpeed = json.optDouble(
            "imageIdleSpeed", default.imageIdleSpeed.toDouble()
        ).toFloat(),
        imagePulseAmount = json.optDouble(
            "imagePulseAmount", default.imagePulseAmount.toDouble()
        ).toFloat(),
        imageBassOnly = json.optBoolean("imageBassOnly", default.imageBassOnly),
        showText = json.optBoolean("showText", default.showText),
        textContent = json.optString("textContent", default.textContent),
        textState = json.optJSONObject("textState")?.let(TextState::fromJson)
            ?: default.textState,
        textOnTopOfImage = json.optBoolean("textOnTopOfImage", default.textOnTopOfImage),
        bassRingBoost = json.optDouble(
            "bassRingBoost", default.bassRingBoost.toDouble()
        ).toFloat(),
        midBarBoost = json.optDouble("midBarBoost", default.midBarBoost.toDouble()).toFloat(),
        trebleSpikeBoost = json.optDouble(
            "trebleSpikeBoost", default.trebleSpikeBoost.toDouble()
        ).toFloat(),
        beatTimesMs = beatTimes,
        beatStrengths = beatStrengths,
        beatReaction = json.optDouble("beatReaction", default.beatReaction.toDouble()).toFloat(),
        beatPulseDurationMs = json.optLong(
            "beatPulseDurationMs", default.beatPulseDurationMs
        ),
        useBeatSync = json.optBoolean("useBeatSync", default.useBeatSync),
        lerpFactor = json.optDouble("lerpFactor", default.lerpFactor.toDouble()).toFloat(),
        startFrequencyHz = json.optDouble(
            "startFrequencyHz", default.startFrequencyHz.toDouble()
        ).toFloat(),
        endFrequencyHz = json.optDouble(
            "endFrequencyHz", default.endFrequencyHz.toDouble()
        ).toFloat(),
        bands = json.optInt("bands", default.bands),
        maxHeight = json.optDouble("maxHeight", default.maxHeight.toDouble()).toFloat(),
        audioWindowMs = json.optInt("audioWindowMs", default.audioWindowMs),
        audioOffsetMs = json.optInt("audioOffsetMs", default.audioOffsetMs),
        displayStyle = json.optString("displayStyle", default.displayStyle),
        sideMode = json.optString("sideMode", default.sideMode),
        lineWidth = json.optDouble("lineWidth", default.lineWidth.toDouble()).toFloat(),
        particleSize = json.optDouble("particleSize", default.particleSize.toDouble()).toFloat()
    )
}

private fun TextState.toJson() = JSONObject().apply {
    put("content", content)
    put("fontFamily", fontFamily)
    put("fontSize", fontSize)
    put("fontWeight", fontWeight)
    put("fontStyle", fontStyle)
    put("color", color)
    put("strokeEnabled", strokeEnabled)
    put("strokeWidth", strokeWidth.toDouble())
    put("strokeColor", strokeColor)
    put("glowEnabled", glowEnabled)
    put("glowColor", glowColor)
    put("glowRadius", glowRadius.toDouble())
    put("gradientEnabled", gradientEnabled)
    put("gradientColor1", gradientColor1)
    put("gradientColor2", gradientColor2)
    put("gradientAngle", gradientAngle.toDouble())
    put("gradientStartX", gradientStartX.toDouble())
    put("gradientStartY", gradientStartY.toDouble())
    put("gradientEndX", gradientEndX.toDouble())
    put("gradientEndY", gradientEndY.toDouble())
    put("shadowEnabled", shadowEnabled)
    put("shadowColor", shadowColor)
    put("shadowBlur", shadowBlur.toDouble())
    put("shadowOffsetX", shadowOffsetX.toDouble())
    put("shadowOffsetY", shadowOffsetY.toDouble())
    put("alignment", alignment)
    put("letterSpacing", letterSpacing.toDouble())
    put("lineHeight", lineHeight.toDouble())
    put("tracking", tracking.toDouble())
    put("positionX", positionX.toDouble())
    put("positionY", positionY.toDouble())
    put("anchorX", anchorX.toDouble())
    put("anchorY", anchorY.toDouble())
    put("maxWidth", maxWidth.toDouble())
    put("scale", scale.toDouble())
    put("rotation", rotation.toDouble())
    put("opacity", opacity.toDouble())
    put("animation", animation)
    put("animationDuration", animationDuration.toDouble())
    putOptional("captionPresetId", captionPresetId)
    put("captionHighlightColor", captionHighlightColor)
    put("captionPresetAnimation", captionPresetAnimation)
    put("captionPresetAnimationDuration", captionPresetAnimationDuration.toDouble())
    putOptional("templateId", templateId)
    put("backgroundEnabled", backgroundEnabled)
    put("backgroundType", backgroundType)
    put("backgroundColor1", backgroundColor1)
    put("backgroundColor2", backgroundColor2)
    put("backgroundGradientEnabled", backgroundGradientEnabled)
    put("backgroundGradientAngle", backgroundGradientAngle.toDouble())
    put("backgroundOpacity", backgroundOpacity.toDouble())
    put("backgroundPaddingX", backgroundPaddingX.toDouble())
    put("backgroundPaddingY", backgroundPaddingY.toDouble())
    put("backgroundCornerRadius", backgroundCornerRadius.toDouble())
    put("backgroundBrushSeed", backgroundBrushSeed)
    putOptional("textureUri", textureUri)
    putOptional("textureMimeType", textureMimeType)
    put("textureScale", textureScale.toDouble())
    put("textureOffsetX", textureOffsetX.toDouble())
    put("textureOffsetY", textureOffsetY.toDouble())
    put("textureRotation", textureRotation.toDouble())
    put("segments", JSONArray().apply {
        segments.forEach { segment ->
            put(JSONObject().apply {
                put("start", segment.start)
                put("end", segment.end)
                segment.fontSize?.let { put("fontSize", it) }
                segment.color?.let { put("color", it) }
                segment.fontFamily?.let { put("fontFamily", it) }
                segment.fontWeight?.let { put("fontWeight", it) }
                segment.fontStyle?.let { put("fontStyle", it) }
                segment.letterSpacing?.let { put("letterSpacing", it.toDouble()) }
            })
        }
    })
}

private fun TextState.Companion.fromJson(json: JSONObject): TextState {
    val default = TextState()
    val segments = buildList {
        val array = json.optJSONArray("segments") ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                TextSegment(
                    start = item.optInt("start"),
                    end = item.optInt("end"),
                    fontSize = item.optNullableInt("fontSize"),
                    color = item.optNullableLong("color"),
                    fontFamily = item.optNullableString("fontFamily"),
                    fontWeight = item.optNullableString("fontWeight"),
                    fontStyle = item.optNullableString("fontStyle"),
                    letterSpacing = item.optNullableDouble("letterSpacing")?.toFloat()
                )
            )
        }
    }
    return TextState(
        content = json.optString("content", default.content),
        fontFamily = json.optString("fontFamily", default.fontFamily),
        fontSize = json.optInt("fontSize", default.fontSize),
        fontWeight = json.optString("fontWeight", default.fontWeight),
        fontStyle = json.optString("fontStyle", default.fontStyle),
        color = json.optLong("color", default.color),
        strokeEnabled = json.optBoolean("strokeEnabled", default.strokeEnabled),
        strokeWidth = json.float("strokeWidth", default.strokeWidth),
        strokeColor = json.optLong("strokeColor", default.strokeColor),
        glowEnabled = json.optBoolean("glowEnabled", default.glowEnabled),
        glowColor = json.optLong("glowColor", default.glowColor),
        glowRadius = json.float("glowRadius", default.glowRadius),
        gradientEnabled = json.optBoolean("gradientEnabled", default.gradientEnabled),
        gradientColor1 = json.optLong("gradientColor1", default.gradientColor1),
        gradientColor2 = json.optLong("gradientColor2", default.gradientColor2),
        gradientAngle = json.float("gradientAngle", default.gradientAngle),
        gradientStartX = json.float("gradientStartX", default.gradientStartX),
        gradientStartY = json.float("gradientStartY", default.gradientStartY),
        gradientEndX = json.float("gradientEndX", default.gradientEndX),
        gradientEndY = json.float("gradientEndY", default.gradientEndY),
        shadowEnabled = json.optBoolean("shadowEnabled", default.shadowEnabled),
        shadowColor = json.optLong("shadowColor", default.shadowColor),
        shadowBlur = json.float("shadowBlur", default.shadowBlur),
        shadowOffsetX = json.float("shadowOffsetX", default.shadowOffsetX),
        shadowOffsetY = json.float("shadowOffsetY", default.shadowOffsetY),
        alignment = json.optString("alignment", default.alignment),
        letterSpacing = json.float("letterSpacing", default.letterSpacing),
        lineHeight = json.float("lineHeight", default.lineHeight),
        tracking = json.float("tracking", default.tracking),
        positionX = json.float("positionX", default.positionX),
        positionY = json.float("positionY", default.positionY),
        anchorX = json.float("anchorX", default.anchorX),
        anchorY = json.float("anchorY", default.anchorY),
        maxWidth = json.float("maxWidth", default.maxWidth),
        scale = json.float("scale", default.scale),
        rotation = json.float("rotation", default.rotation),
        opacity = json.float("opacity", default.opacity),
        animation = json.optString("animation", default.animation),
        animationDuration = json.float("animationDuration", default.animationDuration),
        captionPresetId = json.optNullableString("captionPresetId"),
        captionHighlightColor = json.optLong(
            "captionHighlightColor", default.captionHighlightColor
        ),
        captionPresetAnimation = json.optString(
            "captionPresetAnimation", default.captionPresetAnimation
        ),
        captionPresetAnimationDuration = json.float(
            "captionPresetAnimationDuration", default.captionPresetAnimationDuration
        ),
        templateId = json.optNullableString("templateId"),
        backgroundEnabled = json.optBoolean("backgroundEnabled", default.backgroundEnabled),
        backgroundType = json.optString("backgroundType", default.backgroundType),
        backgroundColor1 = json.optLong("backgroundColor1", default.backgroundColor1),
        backgroundColor2 = json.optLong("backgroundColor2", default.backgroundColor2),
        backgroundGradientEnabled = json.optBoolean(
            "backgroundGradientEnabled", default.backgroundGradientEnabled
        ),
        backgroundGradientAngle = json.float(
            "backgroundGradientAngle", default.backgroundGradientAngle
        ),
        backgroundOpacity = json.float("backgroundOpacity", default.backgroundOpacity),
        backgroundPaddingX = json.float("backgroundPaddingX", default.backgroundPaddingX),
        backgroundPaddingY = json.float("backgroundPaddingY", default.backgroundPaddingY),
        backgroundCornerRadius = json.float(
            "backgroundCornerRadius", default.backgroundCornerRadius
        ),
        backgroundBrushSeed = json.optInt("backgroundBrushSeed", default.backgroundBrushSeed),
        textureUri = json.optNullableString("textureUri"),
        textureMimeType = json.optNullableString("textureMimeType"),
        textureScale = json.float("textureScale", default.textureScale),
        textureOffsetX = json.float("textureOffsetX", default.textureOffsetX),
        textureOffsetY = json.float("textureOffsetY", default.textureOffsetY),
        textureRotation = json.float("textureRotation", default.textureRotation),
        segments = segments
    )
}

private fun JSONObject.putOptional(key: String, value: String?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key)

private fun JSONObject.optNullableInt(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)

private fun JSONObject.optNullableLong(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key)

private fun JSONObject.float(key: String, default: Float): Float =
    optDouble(key, default.toDouble()).toFloat()

private fun JSONArray?.toLongList(): List<Long> = buildList {
    if (this@toLongList == null) return@buildList
    for (index in 0 until length()) add(optLong(index))
}

private fun JSONArray?.toFloatList(): List<Float> = buildList {
    if (this@toFloatList == null) return@buildList
    for (index in 0 until length()) add(optDouble(index).toFloat())
}
