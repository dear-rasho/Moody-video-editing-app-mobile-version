package com.moody.moodyvideoeditor.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ═══════════════════════════════════════════════════════════════
//  SAVED TEMPLATE REPOSITORY
//  Persists user templates as JSON.
//  Reuses ProjectRepository's serialization pattern.
// ═══════════════════════════════════════════════════════════════

object SavedTemplateRepository {

    private const val DIR_TEMPLATES = "saved_templates"
    private const val FILE_INDEX = "index.json"

    // ═══════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════

    fun listTemplates(context: Context): List<SavedTemplateMeta> {
        val indexFile = File(dir(context), FILE_INDEX)
        if (!indexFile.exists()) return emptyList()
        return try {
            val json = JSONArray(indexFile.readText())
            (0 until json.length()).mapNotNull { i ->
                metaFromJson(json.optJSONObject(i) ?: return@mapNotNull null)
            }.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun save(
        context: Context,
        meta: SavedTemplateMeta,
        state: EditorState
    ): Boolean {
        if (state.clips.isEmpty()) return false
        try {
            val stateFile = File(dir(context), "${meta.id}.json")
            stateFile.writeText(stateToJson(state).toString())

            val index = listTemplates(context).toMutableList()
            index.removeAll { it.id == meta.id }
            // Don't persist editorState in index — it lives in the state file
            index.add(0, meta.copy(editorState = null))
            writeIndex(context, index)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun load(context: Context, templateId: String): EditorState? {
        val file = File(dir(context), "$templateId.json")
        if (!file.exists()) return null
        return try {
            stateFromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun delete(context: Context, templateId: String) {
        try {
            File(dir(context), "$templateId.json").delete()
            val index = listTemplates(context).toMutableList()
            index.removeAll { it.id == templateId }
            writeIndex(context, index)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun rename(context: Context, templateId: String, newName: String) {
        val index = listTemplates(context).toMutableList()
        val idx = index.indexOfFirst { it.id == templateId }
        if (idx >= 0) {
            index[idx] = index[idx].copy(
                name = newName,
                updatedAt = System.currentTimeMillis()
            )
            writeIndex(context, index)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  INTERNALS
    // ═══════════════════════════════════════════════════════════

    private fun dir(context: Context): File {
        val d = File(context.filesDir, DIR_TEMPLATES)
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun writeIndex(context: Context, list: List<SavedTemplateMeta>) {
        val arr = JSONArray()
        list.forEach { arr.put(metaToJson(it)) }
        File(dir(context), FILE_INDEX).writeText(arr.toString())
    }

    private fun metaToJson(m: SavedTemplateMeta): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("name", m.name)
        put("createdAt", m.createdAt)
        put("updatedAt", m.updatedAt)
        put("aspectRatio", m.aspectRatio)
        put("clipCount", m.clipCount)
        put("durationMs", m.durationMs)
        put("imageSlotCount", m.imageSlotCount)
        put("textSlotCount", m.textSlotCount)
    }

    private fun metaFromJson(o: JSONObject) = SavedTemplateMeta(
        id = o.getString("id"),
        name = o.optString("name", "Untitled Template"),
        createdAt = o.optLong("createdAt", 0L),
        updatedAt = o.optLong("updatedAt", 0L),
        aspectRatio = o.optString("aspectRatio", "16:9"),
        clipCount = o.optInt("clipCount", 0),
        durationMs = o.optLong("durationMs", 0L),
        imageSlotCount = o.optInt("imageSlotCount", 0),
        textSlotCount = o.optInt("textSlotCount", 0),
        editorState = null
    )

    // ═══════════════════════════════════════════════════════════
    //  STATE SERIALIZATION (reuses ProjectRepository format)
    //  In templates, image URIs are stored EMPTY (placeholder).
    //  Text content is preserved (user will change it).
    // ═══════════════════════════════════════════════════════════

    private fun stateToJson(s: EditorState): JSONObject = JSONObject().apply {
        val arr = JSONArray()
        s.clips.forEach { arr.put(clipToJson(it)) }
        put("clips", arr)
        put("visualLayerCount", s.visualLayerCount)
        put("audioLayerCount", s.audioLayerCount)
        put("aspectRatio", s.aspectRatio)
        put("rotation", s.rotation)
        put("aspectMode", s.aspectMode)
    }

    private fun stateFromJson(o: JSONObject): EditorState {
        val clips = mutableListOf<EditorClip>()
        val arr = o.optJSONArray("clips") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            clips.add(clipFromJson(obj))
        }
        return EditorState(
            clips = clips,
            currentPosMs = 0L,
            visualLayerCount = o.optInt("visualLayerCount", 3),
            audioLayerCount = o.optInt("audioLayerCount", 2),
            aspectRatio = o.optString("aspectRatio", "16:9"),
            rotation = o.optInt("rotation", 0),
            aspectMode = o.optInt("aspectMode", 0)
        )
    }

    private fun clipToJson(c: EditorClip): JSONObject = JSONObject().apply {
        put("id", c.id)
        // Placeholder: images lose their URI in templates
        val isImage = c.type.startsWith("image/")
        put("uri", if (isImage && !c.isTextClip && !c.isStickerClip) "" else c.uri.toString())
        put("name", c.name)
        put("type", c.type)
        put("sourceStartMs", c.sourceStartMs)
        put("sourceEndMs", c.sourceEndMs)
        put("timelineStartMs", c.timelineStartMs)
        put("speed", c.speed.toDouble())
        put("volume", c.volume.toDouble())
        put("scale", c.scale.toDouble())
        put("rotation", c.rotation.toDouble())
        put("offsetX", c.offsetX.toDouble())
        put("offsetY", c.offsetY.toDouble())
        put("cropL", c.cropL.toDouble())
        put("cropR", c.cropR.toDouble())
        put("cropT", c.cropT.toDouble())
        put("cropB", c.cropB.toDouble())
        put("trackIndex", c.trackIndex)
        put("isAudio", c.isAudio)
        put("sourceTotalMs", c.sourceTotalMs)
        put("linkedId", c.linkedId ?: JSONObject.NULL)
        put("linkGroupId", c.linkGroupId ?: JSONObject.NULL)
        put("isMuted", c.isMuted)
        put("blendMode", c.blendMode)
        put("blendOpacity", c.blendOpacity.toDouble())
        put("audioFx", c.audioFx)
        put("audioFxIntensity", c.audioFxIntensity.toDouble())
        put("soundFx", c.soundFx)
        put("soundFxIntensity", c.soundFxIntensity.toDouble())

        put("adjustments", adjustmentsToJson(c.adjustments))
        put("filters", filterToJson(c.filters))
        put("colorWheel", colorWheelToJson(c.colorWheel))
        put("overlay", overlayToJson(c.overlay))

        c.effectState?.let { put("effectState", effectStateToJson(it)) }
        c.textState?.let { put("textState", textStateToJson(it)) }
        c.stickerState?.let { put("stickerState", stickerStateToJson(it)) }

        c.freeze?.let { put("freeze", freezeToJson(it)) }
        c.transition?.let { put("transition", transitionToJson(it)) }
        c.ratio?.let { put("ratio", ratioToJson(it)) }
        put("mask", maskToJson(c.mask))
        put("brush", brushToJson(c.brush))
        put("matteStyle", matteStyleToJson(c.matteStyle))
        c.visualizer?.let { put("visualizer", visualizerToJson(it)) }

        val kfObj = JSONObject()
        c.keyframes.forEach { (prop, list) ->
            val kfArr = JSONArray()
            list.forEach { kf ->
                kfArr.put(JSONObject().apply {
                    put("time", kf.time.toDouble())
                    put("value", kf.value.toDouble())
                    put("ease", kf.ease)
                })
            }
            kfObj.put(prop, kfArr)
        }
        put("keyframes", kfObj)
    }

    private fun clipFromJson(o: JSONObject): EditorClip {
        return EditorClip(
            id = o.getString("id"),
            uri = Uri.parse(o.optString("uri", "")),
            name = o.optString("name", ""),
            type = o.optString("type", "video/mp4"),
            sourceStartMs = o.optLong("sourceStartMs", 0L),
            sourceEndMs = o.optLong("sourceEndMs", 3000L),
            timelineStartMs = o.optLong("timelineStartMs", 0L),
            speed = o.optDouble("speed", 1.0).toFloat(),
            volume = o.optDouble("volume", 1.0).toFloat(),
            scale = o.optDouble("scale", 1.0).toFloat(),
            rotation = o.optDouble("rotation", 0.0).toFloat(),
            offsetX = o.optDouble("offsetX", 0.0).toFloat(),
            offsetY = o.optDouble("offsetY", 0.0).toFloat(),
            cropL = o.optDouble("cropL", 0.0).toFloat(),
            cropR = o.optDouble("cropR", 0.0).toFloat(),
            cropT = o.optDouble("cropT", 0.0).toFloat(),
            cropB = o.optDouble("cropB", 0.0).toFloat(),
            trackIndex = o.optInt("trackIndex", 0),
            isAudio = o.optBoolean("isAudio", false),
            sourceTotalMs = o.optLong("sourceTotalMs", Long.MAX_VALUE),
            linkedId = if (o.isNull("linkedId")) null else o.optString("linkedId", null),
            linkGroupId = if (o.isNull("linkGroupId")) null
            else o.optString("linkGroupId", null),
            isMuted = o.optBoolean("isMuted", false),
            blendMode = o.optString("blendMode", "normal"),
            blendOpacity = o.optDouble("blendOpacity", 100.0).toFloat(),
            audioFx = o.optString("audioFx", "none"),
            audioFxIntensity = o.optDouble("audioFxIntensity", 100.0).toFloat(),
            soundFx = o.optString("soundFx", "none"),
            soundFxIntensity = o.optDouble("soundFxIntensity", 100.0).toFloat(),
            adjustments = adjustmentsFromJson(o.optJSONObject("adjustments")),
            filters = filterFromJson(o.optJSONObject("filters")),
            colorWheel = colorWheelFromJson(o.optJSONObject("colorWheel")),
            overlay = overlayFromJson(o.optJSONObject("overlay")),
            effectState = o.optJSONObject("effectState")?.let { effectStateFromJson(it) },
            textState = o.optJSONObject("textState")?.let { textStateFromJson(it) },
            stickerState = o.optJSONObject("stickerState")?.let { stickerStateFromJson(it) },

            freeze = o.optJSONObject("freeze")?.let { freezeFromJson(it) },
            transition = o.optJSONObject("transition")?.let { transitionFromJson(it) },
            ratio = o.optJSONObject("ratio")?.let { ratioFromJson(it) },
            mask = maskFromJson(o.optJSONObject("mask")),
            brush = brushFromJson(o.optJSONObject("brush")),
            matteStyle = matteStyleFromJson(o.optJSONObject("matteStyle")),
            visualizer = o.optJSONObject("visualizer")?.let { visualizerFromJson(it) },
            keyframes = keyframesFromJson(o.optJSONObject("keyframes"))
        )
    }

    // ─── Sub-serializers ───

    private fun adjustmentsToJson(a: AdjustmentData) = JSONObject().apply {
        put("brightness", a.brightness.toDouble()); put("contrast", a.contrast.toDouble())
        put("exposure", a.exposure.toDouble()); put("whites", a.whites.toDouble())
        put("blacks", a.blacks.toDouble()); put("shadows", a.shadows.toDouble())
        put("highlights", a.highlights.toDouble()); put("saturation", a.saturation.toDouble())
        put("vibrance", a.vibrance.toDouble()); put("clarity", a.clarity.toDouble())
        put("temperature", a.temperature.toDouble()); put("tint", a.tint.toDouble())
        put("noise", a.noise.toDouble()); put("sharpen", a.sharpen.toDouble())
        put("vignette", a.vignette.toDouble()); put("reds", a.reds.toDouble())
        put("oranges", a.oranges.toDouble()); put("yellows", a.yellows.toDouble())
        put("greens", a.greens.toDouble()); put("cyans", a.cyans.toDouble())
        put("blues", a.blues.toDouble()); put("purples", a.purples.toDouble())
        put("magentas", a.magentas.toDouble()); put("skinTones", a.skinTones.toDouble())
    }

    private fun adjustmentsFromJson(o: JSONObject?) = AdjustmentData(
        brightness = o?.optDouble("brightness", 0.0)?.toFloat() ?: 0f,
        contrast = o?.optDouble("contrast", 0.0)?.toFloat() ?: 0f,
        exposure = o?.optDouble("exposure", 0.0)?.toFloat() ?: 0f,
        whites = o?.optDouble("whites", 0.0)?.toFloat() ?: 0f,
        blacks = o?.optDouble("blacks", 0.0)?.toFloat() ?: 0f,
        shadows = o?.optDouble("shadows", 0.0)?.toFloat() ?: 0f,
        highlights = o?.optDouble("highlights", 0.0)?.toFloat() ?: 0f,
        saturation = o?.optDouble("saturation", 0.0)?.toFloat() ?: 0f,
        vibrance = o?.optDouble("vibrance", 0.0)?.toFloat() ?: 0f,
        clarity = o?.optDouble("clarity", 0.0)?.toFloat() ?: 0f,
        temperature = o?.optDouble("temperature", 0.0)?.toFloat() ?: 0f,
        tint = o?.optDouble("tint", 0.0)?.toFloat() ?: 0f,
        noise = o?.optDouble("noise", 0.0)?.toFloat() ?: 0f,
        sharpen = o?.optDouble("sharpen", 0.0)?.toFloat() ?: 0f,
        vignette = o?.optDouble("vignette", 0.0)?.toFloat() ?: 0f,
        reds = o?.optDouble("reds", 0.0)?.toFloat() ?: 0f,
        oranges = o?.optDouble("oranges", 0.0)?.toFloat() ?: 0f,
        yellows = o?.optDouble("yellows", 0.0)?.toFloat() ?: 0f,
        greens = o?.optDouble("greens", 0.0)?.toFloat() ?: 0f,
        cyans = o?.optDouble("cyans", 0.0)?.toFloat() ?: 0f,
        blues = o?.optDouble("blues", 0.0)?.toFloat() ?: 0f,
        purples = o?.optDouble("purples", 0.0)?.toFloat() ?: 0f,
        magentas = o?.optDouble("magentas", 0.0)?.toFloat() ?: 0f,
        skinTones = o?.optDouble("skinTones", 0.0)?.toFloat() ?: 0f
    )

    private fun filterToJson(f: FilterState) = JSONObject().apply {
        put("brightness", f.brightness.toDouble()); put("contrast", f.contrast.toDouble())
        put("saturation", f.saturation.toDouble()); put("hue", f.hue.toDouble())
        put("grayscale", f.grayscale.toDouble()); put("sepia", f.sepia.toDouble())
        put("invert", f.invert.toDouble()); put("blur", f.blur.toDouble())
        put("opacity", f.opacity.toDouble())
    }

    private fun filterFromJson(o: JSONObject?) = FilterState(
        brightness = o?.optDouble("brightness", 100.0)?.toFloat() ?: 100f,
        contrast = o?.optDouble("contrast", 100.0)?.toFloat() ?: 100f,
        saturation = o?.optDouble("saturation", 100.0)?.toFloat() ?: 100f,
        hue = o?.optDouble("hue", 0.0)?.toFloat() ?: 0f,
        grayscale = o?.optDouble("grayscale", 0.0)?.toFloat() ?: 0f,
        sepia = o?.optDouble("sepia", 0.0)?.toFloat() ?: 0f,
        invert = o?.optDouble("invert", 0.0)?.toFloat() ?: 0f,
        blur = o?.optDouble("blur", 0.0)?.toFloat() ?: 0f,
        opacity = o?.optDouble("opacity", 100.0)?.toFloat() ?: 100f
    )

    private fun toneToJson(t: ToneValue) = JSONObject().apply {
        put("hue", t.hue.toDouble()); put("saturation", t.saturation.toDouble())
        put("intensity", t.intensity.toDouble())
    }

    private fun toneFromJson(o: JSONObject?) = if (o == null) ToneValue.ZERO else ToneValue(
        o.optDouble("hue", 0.0).toFloat(),
        o.optDouble("saturation", 0.0).toFloat(),
        o.optDouble("intensity", 0.0).toFloat()
    )

    private fun colorWheelToJson(c: ColorWheelState) = JSONObject().apply {
        put("shadows", toneToJson(c.shadows)); put("midtones", toneToJson(c.midtones))
        put("highlights", toneToJson(c.highlights)); put("hdrWhite", c.hdrWhite.toDouble())
    }

    private fun colorWheelFromJson(o: JSONObject?) = ColorWheelState(
        shadows = toneFromJson(o?.optJSONObject("shadows")),
        midtones = toneFromJson(o?.optJSONObject("midtones")),
        highlights = toneFromJson(o?.optJSONObject("highlights")),
        hdrWhite = o?.optDouble("hdrWhite", 100.0)?.toFloat() ?: 100f
    )

    private fun overlayToJson(o: OverlayState) = JSONObject().apply {
        put("type", o.type); put("intensity", o.intensity.toDouble())
        put("color", o.color); put("color2", o.color2)
        put("size", o.size.toDouble()); put("opacity", o.opacity.toDouble())
        put("speed", o.speed.toDouble()); put("shape", o.shape)
        put("glowEnabled", o.glowEnabled); put("glowRadius", o.glowRadius.toDouble())
        put("glowColor", o.glowColor)
    }

    private fun overlayFromJson(o: JSONObject?) = OverlayState(
        type = o?.optString("type", "none") ?: "none",
        intensity = o?.optDouble("intensity", 100.0)?.toFloat() ?: 100f,
        color = o?.optLong("color", 0xFFFFFFFFL) ?: 0xFFFFFFFFL,
        color2 = o?.optLong("color2", 0xFFFFFFFFL) ?: 0xFFFFFFFFL,
        size = o?.optDouble("size", 1.0)?.toFloat() ?: 1f,
        opacity = o?.optDouble("opacity", 1.0)?.toFloat() ?: 1f,
        speed = o?.optDouble("speed", 1.0)?.toFloat() ?: 1f,
        shape = o?.optString("shape", "circle") ?: "circle",
        glowEnabled = o?.optBoolean("glowEnabled", false) ?: false,
        glowRadius = o?.optDouble("glowRadius", 1.8)?.toFloat() ?: 1.8f,
        glowColor = o?.optLong("glowColor", 0xFFFFFFFFL) ?: 0xFFFFFFFFL
    )

    private fun effectStateToJson(e: EffectState) = JSONObject().apply {
        put("kind", e.kind); put("presetKey", e.presetKey)
        put("masterIntensity", e.masterIntensity.toDouble())
    }

    private fun effectStateFromJson(o: JSONObject) = EffectState(
        kind = o.optString("kind", EffectState.KIND_EFFECT),
        presetKey = o.optString("presetKey", ""),
        masterIntensity = o.optDouble("masterIntensity", 100.0).toFloat()
    )

    private fun textStateToJson(t: TextState) = JSONObject().apply {
        put("content", t.content); put("fontFamily", t.fontFamily)
        put("fontSize", t.fontSize); put("fontWeight", t.fontWeight)
        put("fontStyle", t.fontStyle); put("color", t.color)
        put("alignment", t.alignment); put("letterSpacing", t.letterSpacing.toDouble())
        put("lineHeight", t.lineHeight.toDouble()); put("positionX", t.positionX.toDouble())
        put("positionY", t.positionY.toDouble()); put("scale", t.scale.toDouble())
        put("rotation", t.rotation.toDouble()); put("opacity", t.opacity.toDouble())
        put("animation", t.animation); put("animationDuration", t.animationDuration.toDouble())
        put("backgroundEnabled", t.backgroundEnabled); put("backgroundType", t.backgroundType)
        put("backgroundColor1", t.backgroundColor1); put("backgroundColor2", t.backgroundColor2)
        put("backgroundGradientEnabled", t.backgroundGradientEnabled)
        put("backgroundGradientAngle", t.backgroundGradientAngle.toDouble())
        put("backgroundOpacity", t.backgroundOpacity.toDouble())
        put("backgroundPaddingX", t.backgroundPaddingX.toDouble())
        put("backgroundPaddingY", t.backgroundPaddingY.toDouble())
        put("backgroundCornerRadius", t.backgroundCornerRadius.toDouble())
    }

    private fun textStateFromJson(o: JSONObject) = TextState(
        content = o.optString("content", ""),
        fontFamily = o.optString("fontFamily", "Arial"),
        fontSize = o.optInt("fontSize", 36),
        fontWeight = o.optString("fontWeight", "normal"),
        fontStyle = o.optString("fontStyle", "normal"),
        color = o.optLong("color", 0xFFFFFFFFL),
        alignment = o.optString("alignment", "center"),
        letterSpacing = o.optDouble("letterSpacing", 0.0).toFloat(),
        lineHeight = o.optDouble("lineHeight", 1.2).toFloat(),
        positionX = o.optDouble("positionX", 50.0).toFloat(),
        positionY = o.optDouble("positionY", 50.0).toFloat(),
        scale = o.optDouble("scale", 100.0).toFloat(),
        rotation = o.optDouble("rotation", 0.0).toFloat(),
        opacity = o.optDouble("opacity", 100.0).toFloat(),
        animation = o.optString("animation", "none"),
        animationDuration = o.optDouble("animationDuration", 0.6).toFloat(),
        backgroundEnabled = o.optBoolean("backgroundEnabled", false),
        backgroundType = o.optString("backgroundType", "rounded"),
        backgroundColor1 = o.optLong("backgroundColor1", 0xFF000000L),
        backgroundColor2 = o.optLong("backgroundColor2", 0xFF000000L),
        backgroundGradientEnabled = o.optBoolean("backgroundGradientEnabled", false),
        backgroundGradientAngle = o.optDouble("backgroundGradientAngle", 90.0).toFloat(),
        backgroundOpacity = o.optDouble("backgroundOpacity", 100.0).toFloat(),
        backgroundPaddingX = o.optDouble("backgroundPaddingX", 14.0).toFloat(),
        backgroundPaddingY = o.optDouble("backgroundPaddingY", 8.0).toFloat(),
        backgroundCornerRadius = o.optDouble("backgroundCornerRadius", 12.0).toFloat()
    )

    private fun stickerStateToJson(s: StickerState) = JSONObject().apply {
        put("emoji", s.emoji); put("x", s.x.toDouble()); put("y", s.y.toDouble())
        put("scale", s.scale.toDouble()); put("rotation", s.rotation.toDouble())
        put("animation", s.animation); put("animationDuration", s.animationDuration.toDouble())
        put("opacity", s.opacity.toDouble())
    }

    private fun stickerStateFromJson(o: JSONObject) = StickerState(
        emoji = o.optString("emoji", ""),
        x = o.optDouble("x", 50.0).toFloat(),
        y = o.optDouble("y", 50.0).toFloat(),
        scale = o.optDouble("scale", 100.0).toFloat(),
        rotation = o.optDouble("rotation", 0.0).toFloat(),
        animation = o.optString("animation", "none"),
        animationDuration = o.optDouble("animationDuration", 0.6).toFloat(),
        opacity = o.optDouble("opacity", 100.0).toFloat()
    )


    private fun freezeToJson(f: FreezeState) = JSONObject().apply {
        put("durationMs", f.durationMs); put("atTimeMs", f.atTimeMs)
    }

    private fun freezeFromJson(o: JSONObject) = FreezeState(
        durationMs = o.optLong("durationMs", 2000L),
        atTimeMs = o.optLong("atTimeMs", 0L)
    )

    private fun transitionToJson(t: TransitionState) = JSONObject().apply {
        put("key", t.key); put("durationMs", t.durationMs)
    }

    private fun transitionFromJson(o: JSONObject) = TransitionState(
        key = o.optString("key", "none"),
        durationMs = o.optLong("durationMs", 500L)
    )

    private fun ratioToJson(r: RatioState) = JSONObject().apply {
        put("key", r.key); put("w", r.w); put("h", r.h)
    }

    private fun ratioFromJson(o: JSONObject) = RatioState(
        key = o.optString("key", "16:9"),
        w = o.optInt("w", 16), h = o.optInt("h", 9)
    )

    private fun maskToJson(m: MaskState) = JSONObject().apply {
        put("type", m.type.name)
        put("positionX", m.positionX.toDouble()); put("positionY", m.positionY.toDouble())
        put("rotation", m.rotation.toDouble()); put("feather", m.feather.coerceIn(0f, 300f).toDouble())
        put("isInverted", m.isInverted); put("opacity", m.opacity.toDouble())
        put("radius", m.radius.toDouble()); put("width", m.width.toDouble())
        put("height", m.height.toDouble()); put("cornerRadius", m.cornerRadius.coerceAtLeast(0f).toDouble())
        put("linearPosition", m.linearPosition.toDouble()); put("scale", m.scale.toDouble())
        put("customClosed", m.customClosed); put("expansion", m.expansion.toDouble())
        put("strokeColor", m.strokeColor); put("overlayColor", m.overlayColor)

        val ptsArr = JSONArray()
        m.customPoints.forEach { p ->
            ptsArr.put(JSONObject().apply {
                put("x", p.x.toDouble()); put("y", p.y.toDouble())
                put("handleInX", p.handleInX.toDouble()); put("handleInY", p.handleInY.toDouble())
                put("handleOutX", p.handleOutX.toDouble()); put("handleOutY", p.handleOutY.toDouble())
                put("hasHandles", p.hasHandles)
            })
        }
        put("customPoints", ptsArr)
        put("keyframes", JSONArray())
    }

    private fun maskFromJson(o: JSONObject?): MaskState {
        if (o == null) return MaskState()
        val hasPositionSchema = o.has("positionX")
        val pts = mutableListOf<MaskPoint>()
        val ptsArr = o.optJSONArray("customPoints")
        if (ptsArr != null) {
            for (i in 0 until ptsArr.length()) {
                val p = ptsArr.optJSONObject(i) ?: continue
                pts.add(MaskPoint(
                    x = p.optDouble("x", 0.0).toFloat(),
                    y = p.optDouble("y", 0.0).toFloat(),
                    handleInX = p.optDouble("handleInX", 0.0).toFloat(),
                    handleInY = p.optDouble("handleInY", 0.0).toFloat(),
                    handleOutX = p.optDouble("handleOutX", 0.0).toFloat(),
                    handleOutY = p.optDouble("handleOutY", 0.0).toFloat(),
                    hasHandles = p.optBoolean("hasHandles", false)
                ))
            }
        }
        return MaskState(
            type = try { MaskType.valueOf(o.optString("type", "NONE")) }
            catch (e: Exception) { MaskType.NONE },
            positionX = o.optDouble("positionX", o.optDouble("centerX", 0.5)).toFloat(),
            positionY = if (hasPositionSchema) {
                o.optDouble("positionY", 0.5).toFloat()
            } else {
                o.optDouble("centerY", 0.5).toFloat()
            },
            rotation = o.optDouble("rotation", 0.0).toFloat(),
            feather = o.optDouble("feather", 0.0).toFloat().coerceIn(0f, 300f),
            isInverted = o.optBoolean("isInverted", false),
            opacity = o.optDouble("opacity", 100.0).toFloat(),
            radius = o.optDouble("radius", 0.3).toFloat(),
            width = o.optDouble("width", 0.5).toFloat(),
            height = o.optDouble("height", 0.5).toFloat(),
            cornerRadius = o.optDouble("cornerRadius", 0.0).toFloat().coerceAtLeast(0f),
            linearPosition = if (hasPositionSchema) {
                o.optDouble("linearPosition", 0.5).toFloat()
            } else {
                o.optDouble("positionY", 0.5).toFloat()
            },
            scale = o.optDouble("scale", 1.0).toFloat(),
            customPoints = pts,
            customClosed = o.optBoolean("customClosed", false),
            expansion = o.optDouble("expansion", 0.0).toFloat(),
            strokeColor = o.optLong("strokeColor", 0xFF22C55EL),
            overlayColor = o.optLong("overlayColor", 0xFF000000L)
        )
    }

    private fun brushToJson(b: BrushState) = JSONObject().apply {
        put("strokes", JSONArray())
    }

    private fun brushFromJson(o: JSONObject?): BrushState = BrushState()

    private fun matteStyleToJson(s: ColorMatteStyle) = JSONObject().apply {
        put("mode", s.mode.name); put("solidColor", s.solidColor)
        put("rampColor1", s.rampColor1); put("rampColor2", s.rampColor2)
        put("rampAngleDeg", s.rampAngleDeg.toDouble())
        put("topLeft", s.topLeft); put("topRight", s.topRight)
        put("bottomLeft", s.bottomLeft); put("bottomRight", s.bottomRight)
    }

    private fun matteStyleFromJson(o: JSONObject?): ColorMatteStyle {
        if (o == null) return ColorMatteStyle()
        return try {
            ColorMatteStyle(
                mode = ColorMatteMode.valueOf(o.optString("mode", "SOLID")),
                solidColor = o.optLong("solidColor", 0xFF000000L),
                rampColor1 = o.optLong("rampColor1", 0xFF000000L),
                rampColor2 = o.optLong("rampColor2", 0xFFFFFFFFL),
                rampAngleDeg = o.optDouble("rampAngleDeg", 90.0).toFloat(),
                topLeft = o.optLong("topLeft", 0xFFFF0000L),
                topRight = o.optLong("topRight", 0xFF00FF00L),
                bottomLeft = o.optLong("bottomLeft", 0xFF0000FFL),
                bottomRight = o.optLong("bottomRight", 0xFFFFCC00L)
            )
        } catch (_: Exception) { ColorMatteStyle() }
    }

    private fun visualizerToJson(v: VisualizerState) = JSONObject().apply {
        put("preset", v.preset.key)
        put("linkedAudioClipId", v.linkedAudioClipId ?: JSONObject.NULL)
        put("color1", v.color1); put("color2", v.color2)
        put("size", v.size.toDouble()); put("positionX", v.positionX.toDouble())
        put("positionY", v.positionY.toDouble()); put("opacity", v.opacity.toDouble())
    }

    private fun visualizerFromJson(o: JSONObject) = VisualizerState(
        preset = VisualizerPreset.fromKey(o.optString("preset", "neonGlowRing")),
        linkedAudioClipId = if (o.isNull("linkedAudioClipId")) null
        else o.optString("linkedAudioClipId", null),
        color1 = o.optLong("color1", 0xFFFFD166L),
        color2 = o.optLong("color2", 0xFFFFA500L),
        size = o.optDouble("size", 0.32).toFloat(),
        positionX = o.optDouble("positionX", 0.5).toFloat(),
        positionY = o.optDouble("positionY", 0.5).toFloat(),
        opacity = o.optDouble("opacity", 1.0).toFloat()
    )

    private fun keyframesFromJson(o: JSONObject?): Map<String, List<Keyframe>> {
        if (o == null) return emptyMap()
        val result = mutableMapOf<String, List<Keyframe>>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val prop = keys.next()
            val arr = o.optJSONArray(prop) ?: continue
            val list = mutableListOf<Keyframe>()
            for (i in 0 until arr.length()) {
                val kf = arr.optJSONObject(i) ?: continue
                list.add(Keyframe(
                    time = kf.optDouble("time", 0.0).toFloat(),
                    value = kf.optDouble("value", 0.0).toFloat(),
                    ease = kf.optString("ease", Keyframe.DEFAULT_EASE)
                ))
            }
            result[prop] = list
        }
        return result
    }
}