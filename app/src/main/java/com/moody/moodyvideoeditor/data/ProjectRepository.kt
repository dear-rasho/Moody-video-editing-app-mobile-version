package com.moody.moodyvideoeditor.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Saves/loads project state to local storage as JSON.
 * No external dependencies — uses org.json (built into Android).
 */
object ProjectRepository {

    private const val DIR_PROJECTS = "projects"
    private const val DIR_THUMBS = "thumbnails"
    private const val FILE_INDEX = "index.json"

    // ═══════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════

    fun listProjects(context: Context): List<ProjectMeta> {
        val indexFile = File(getProjectsDir(context), FILE_INDEX)
        if (!indexFile.exists()) return emptyList()
        return try {
            val json = JSONArray(indexFile.readText())
            (0 until json.length()).mapNotNull { i ->
                jsonObjectToMeta(json.optJSONObject(i) ?: return@mapNotNull null)
            }.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveProject(context: Context, meta: ProjectMeta, state: EditorState) {
        try {
            // Save state
            val stateFile = File(getProjectsDir(context), "${meta.id}.json")
            stateFile.writeText(editorStateToJson(state).toString())

            // Update index
            val index = listProjects(context).toMutableList()
            index.removeAll { it.id == meta.id }
            index.add(0, meta)
            writeIndex(context, index)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadProject(context: Context, projectId: String): EditorState? {
        val stateFile = File(getProjectsDir(context), "$projectId.json")
        if (!stateFile.exists()) return null
        return try {
            val json = JSONObject(stateFile.readText())
            editorStateFromJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteProject(context: Context, projectId: String) {
        try {
            File(getProjectsDir(context), "$projectId.json").delete()
            File(getThumbsDir(context), "$projectId.jpg").delete()
            val index = listProjects(context).toMutableList()
            index.removeAll { it.id == projectId }
            writeIndex(context, index)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun renameProject(context: Context, projectId: String, newName: String) {
        val index = listProjects(context).toMutableList()
        val idx = index.indexOfFirst { it.id == projectId }
        if (idx >= 0) {
            index[idx] = index[idx].copy(name = newName, updatedAt = System.currentTimeMillis())
            writeIndex(context, index)
        }
    }

    fun getThumbFile(context: Context, projectId: String): File {
        val dir = getThumbsDir(context)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$projectId.jpg")
    }

    // ═══════════════════════════════════════════════════════════
    //  INTERNAL — DIRS/INDEX
    // ═══════════════════════════════════════════════════════════

    private fun getProjectsDir(context: Context): File {
        val dir = File(context.filesDir, DIR_PROJECTS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getThumbsDir(context: Context): File {
        val dir = File(context.filesDir, DIR_THUMBS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun writeIndex(context: Context, list: List<ProjectMeta>) {
        val arr = JSONArray()
        list.forEach { arr.put(metaToJsonObject(it)) }
        File(getProjectsDir(context), FILE_INDEX).writeText(arr.toString())
    }

    private fun metaToJsonObject(m: ProjectMeta): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("name", m.name)
        put("createdAt", m.createdAt)
        put("updatedAt", m.updatedAt)
        put("thumbnailPath", m.thumbnailPath ?: JSONObject.NULL)
        put("clipCount", m.clipCount)
        put("durationMs", m.durationMs)
    }

    private fun jsonObjectToMeta(o: JSONObject): ProjectMeta = ProjectMeta(
        id = o.getString("id"),
        name = o.optString("name", "Untitled"),
        createdAt = o.optLong("createdAt", 0L),
        updatedAt = o.optLong("updatedAt", 0L),
        thumbnailPath = if (o.isNull("thumbnailPath")) null else o.optString("thumbnailPath", null),
        clipCount = o.optInt("clipCount", 0),
        durationMs = o.optLong("durationMs", 0L)
    )

    // ═══════════════════════════════════════════════════════════
    //  INTERNAL — STATE SERIALIZATION
    // ═══════════════════════════════════════════════════════════

    private fun editorStateToJson(s: EditorState): JSONObject = JSONObject().apply {
        val arr = JSONArray()
        s.clips.forEach { arr.put(clipToJson(it)) }
        put("clips", arr)
        put("visualLayerCount", s.visualLayerCount)
        put("audioLayerCount", s.audioLayerCount)
        put("aspectRatio", s.aspectRatio)
        put("rotation", s.rotation)
        put("aspectMode", s.aspectMode)
        put("volume", s.volume.toDouble())
        put("isMuted", s.isMuted)
        put("audioFx", s.audioFx)
        put("soundFx", s.soundFx)
        put("exportResolution", s.exportResolution)
        put("exportFps", s.exportFps)
        put("exportBitrateKbps", s.exportBitrateKbps)
        put("exportFormat", s.exportFormat)
        put("exportFolderUri", s.exportFolderUri ?: JSONObject.NULL)
        put("currentPosMs", s.currentPosMs)
        put("timelineZoom", s.timelineZoom.toDouble())
    }

    private fun editorStateFromJson(o: JSONObject): EditorState {
        val clips = mutableListOf<EditorClip>()
        val arr = o.optJSONArray("clips") ?: JSONArray()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { clips.add(clipFromJson(it)) }
        }
        return EditorState(
            clips = clips,
            currentPosMs = o.optLong("currentPosMs", 0L),
            visualLayerCount = o.optInt("visualLayerCount", 3),
            audioLayerCount = o.optInt("audioLayerCount", 2),
            aspectRatio = o.optString("aspectRatio", "16:9"),
            rotation = o.optInt("rotation", 0),
            aspectMode = o.optInt("aspectMode", 0),
            volume = o.optDouble("volume", 1.0).toFloat(),
            isMuted = o.optBoolean("isMuted", false),
            audioFx = o.optString("audioFx", "none"),
            soundFx = o.optString("soundFx", "none"),
            exportResolution = o.optString("exportResolution", "720p"),
            exportFps = o.optInt("exportFps", 30),
            exportBitrateKbps = o.optInt("exportBitrateKbps", 8000),
            exportFormat = o.optString("exportFormat", "mp4"),
            exportFolderUri = if (o.isNull("exportFolderUri")) null
            else o.optString("exportFolderUri", null),
            timelineZoom = o.optDouble("timelineZoom", 0.0).toFloat()
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  CLIP
    // ═══════════════════════════════════════════════════════════

    private fun clipToJson(c: EditorClip): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("uri", c.uri.toString())
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
        put("isMuted", c.isMuted)

        // 🆕 per-clip audio effects
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
        c.chroma?.let { put("chroma", chromaToJson(it)) }
        c.freeze?.let { put("freeze", freezeToJson(it)) }
        c.transition?.let { put("transition", transitionToJson(it)) }
        c.ratio?.let { put("ratio", ratioToJson(it)) }
        put("mask", maskToJson(c.mask))
        put("brush", brushToJson(c.brush))
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

    private fun clipFromJson(o: JSONObject): EditorClip = EditorClip(
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
        isMuted = o.optBoolean("isMuted", false),

        // 🆕 per-clip audio effects
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
        chroma = o.optJSONObject("chroma")?.let { chromaFromJson(it) },
        freeze = o.optJSONObject("freeze")?.let { freezeFromJson(it) },
        transition = o.optJSONObject("transition")?.let { transitionFromJson(it) },
        ratio = o.optJSONObject("ratio")?.let { ratioFromJson(it) },
        mask = maskFromJson(o.optJSONObject("mask")),
        brush = brushFromJson(o.optJSONObject("brush")),
        visualizer = o.optJSONObject("visualizer")?.let { visualizerFromJson(it) },
        keyframes = keyframesFromJson(o.optJSONObject("keyframes"))
    )

    // ═══════════════════════════════════════════════════════════
    //  ADJUSTMENTS
    // ═══════════════════════════════════════════════════════════
    private fun adjustmentsToJson(a: AdjustmentData) = JSONObject().apply {
        put("brightness", a.brightness.toDouble())
        put("contrast", a.contrast.toDouble())
        put("exposure", a.exposure.toDouble())
        put("whites", a.whites.toDouble())
        put("blacks", a.blacks.toDouble())
        put("shadows", a.shadows.toDouble())
        put("highlights", a.highlights.toDouble())
        put("saturation", a.saturation.toDouble())
        put("vibrance", a.vibrance.toDouble())
        put("clarity", a.clarity.toDouble())
        put("temperature", a.temperature.toDouble())
        put("tint", a.tint.toDouble())
        put("noise", a.noise.toDouble())
        put("sharpen", a.sharpen.toDouble())
        put("vignette", a.vignette.toDouble())
        put("reds", a.reds.toDouble())
        put("oranges", a.oranges.toDouble())
        put("yellows", a.yellows.toDouble())
        put("greens", a.greens.toDouble())
        put("cyans", a.cyans.toDouble())
        put("blues", a.blues.toDouble())
        put("purples", a.purples.toDouble())
        put("magentas", a.magentas.toDouble())
        put("skinTones", a.skinTones.toDouble())
    }

    private fun adjustmentsFromJson(o: JSONObject?): AdjustmentData {
        if (o == null) return AdjustmentData()
        return AdjustmentData(
            brightness = o.optDouble("brightness", 0.0).toFloat(),
            contrast = o.optDouble("contrast", 0.0).toFloat(),
            exposure = o.optDouble("exposure", 0.0).toFloat(),
            whites = o.optDouble("whites", 0.0).toFloat(),
            blacks = o.optDouble("blacks", 0.0).toFloat(),
            shadows = o.optDouble("shadows", 0.0).toFloat(),
            highlights = o.optDouble("highlights", 0.0).toFloat(),
            saturation = o.optDouble("saturation", 0.0).toFloat(),
            vibrance = o.optDouble("vibrance", 0.0).toFloat(),
            clarity = o.optDouble("clarity", 0.0).toFloat(),
            temperature = o.optDouble("temperature", 0.0).toFloat(),
            tint = o.optDouble("tint", 0.0).toFloat(),
            noise = o.optDouble("noise", 0.0).toFloat(),
            sharpen = o.optDouble("sharpen", 0.0).toFloat(),
            vignette = o.optDouble("vignette", 0.0).toFloat(),
            reds = o.optDouble("reds", 0.0).toFloat(),
            oranges = o.optDouble("oranges", 0.0).toFloat(),
            yellows = o.optDouble("yellows", 0.0).toFloat(),
            greens = o.optDouble("greens", 0.0).toFloat(),
            cyans = o.optDouble("cyans", 0.0).toFloat(),
            blues = o.optDouble("blues", 0.0).toFloat(),
            purples = o.optDouble("purples", 0.0).toFloat(),
            magentas = o.optDouble("magentas", 0.0).toFloat(),
            skinTones = o.optDouble("skinTones", 0.0).toFloat()
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  FILTERS
    // ═══════════════════════════════════════════════════════════
    private fun filterToJson(f: FilterState) = JSONObject().apply {
        put("brightness", f.brightness.toDouble())
        put("contrast", f.contrast.toDouble())
        put("saturation", f.saturation.toDouble())
        put("hue", f.hue.toDouble())
        put("grayscale", f.grayscale.toDouble())
        put("sepia", f.sepia.toDouble())
        put("invert", f.invert.toDouble())
        put("blur", f.blur.toDouble())
        put("opacity", f.opacity.toDouble())
    }

    private fun filterFromJson(o: JSONObject?): FilterState {
        if (o == null) return FilterState()
        return FilterState(
            brightness = o.optDouble("brightness", 100.0).toFloat(),
            contrast = o.optDouble("contrast", 100.0).toFloat(),
            saturation = o.optDouble("saturation", 100.0).toFloat(),
            hue = o.optDouble("hue", 0.0).toFloat(),
            grayscale = o.optDouble("grayscale", 0.0).toFloat(),
            sepia = o.optDouble("sepia", 0.0).toFloat(),
            invert = o.optDouble("invert", 0.0).toFloat(),
            blur = o.optDouble("blur", 0.0).toFloat(),
            opacity = o.optDouble("opacity", 100.0).toFloat()
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  COLOR WHEEL
    // ═══════════════════════════════════════════════════════════
    private fun toneToJson(t: ToneValue) = JSONObject().apply {
        put("hue", t.hue.toDouble())
        put("saturation", t.saturation.toDouble())
        put("intensity", t.intensity.toDouble())
    }

    private fun toneFromJson(o: JSONObject?): ToneValue {
        if (o == null) return ToneValue.ZERO
        return ToneValue(
            hue = o.optDouble("hue", 0.0).toFloat(),
            saturation = o.optDouble("saturation", 0.0).toFloat(),
            intensity = o.optDouble("intensity", 0.0).toFloat()
        )
    }

    private fun colorWheelToJson(c: ColorWheelState) = JSONObject().apply {
        put("shadows", toneToJson(c.shadows))
        put("midtones", toneToJson(c.midtones))
        put("highlights", toneToJson(c.highlights))
        put("hdrWhite", c.hdrWhite.toDouble())
    }

    private fun colorWheelFromJson(o: JSONObject?): ColorWheelState {
        if (o == null) return ColorWheelState()
        return ColorWheelState(
            shadows = toneFromJson(o.optJSONObject("shadows")),
            midtones = toneFromJson(o.optJSONObject("midtones")),
            highlights = toneFromJson(o.optJSONObject("highlights")),
            hdrWhite = o.optDouble("hdrWhite", 100.0).toFloat()
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  OVERLAY
    // ═══════════════════════════════════════════════════════════
    private fun overlayToJson(o: OverlayState) = JSONObject().apply {
        put("type", o.type)
        put("intensity", o.intensity.toDouble())
        put("color", o.color)
    }

    private fun overlayFromJson(o: JSONObject?): OverlayState {
        if (o == null) return OverlayState()
        return OverlayState(
            type = o.optString("type", "none"),
            intensity = o.optDouble("intensity", 100.0).toFloat(),
            color = o.optLong("color", 0xFFFFFFFFL)
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  EFFECT STATE
    // ═══════════════════════════════════════════════════════════
    private fun effectStateToJson(e: EffectState) = JSONObject().apply {
        put("kind", e.kind)
        put("presetKey", e.presetKey ?: JSONObject.NULL)
        e.motion?.let { m ->
            put("motion", JSONObject().apply {
                put("type", m.type)
                put("intensity", m.intensity.toDouble())
                put("speed", m.speed.toDouble())
            })
        }
        e.overlay?.let { ov ->
            put("overlayCfg", JSONObject().apply {
                put("type", ov.type)
                put("intensity", ov.intensity.toDouble())
                put("color", ov.color)
            })
        }
    }

    private fun effectStateFromJson(o: JSONObject): EffectState = EffectState(
        kind = o.optString("kind", EffectState.KIND_EFFECT),
        presetKey = if (o.isNull("presetKey")) null else o.optString("presetKey", null),
        motion = o.optJSONObject("motion")?.let {
            MotionConfig(
                type = it.optString("type", "shake"),
                intensity = it.optDouble("intensity", 100.0).toFloat(),
                speed = it.optDouble("speed", 1.0).toFloat()
            )
        },
        overlay = o.optJSONObject("overlayCfg")?.let {
            OverlayConfig(
                type = it.optString("type", "rain"),
                intensity = it.optDouble("intensity", 100.0).toFloat(),
                color = it.optLong("color", 0xFFFFFFFFL)
            )
        }
    )

    // ═══════════════════════════════════════════════════════════
    //  TEXT
    // ═══════════════════════════════════════════════════════════
    private fun textSegmentToJson(s: TextSegment) = JSONObject().apply {
        put("start", s.start)
        put("end", s.end)
        s.fontSize?.let { put("fontSize", it) }
        s.color?.let { put("color", it) }
        s.fontFamily?.let { put("fontFamily", it) }
        s.fontWeight?.let { put("fontWeight", it) }
        s.fontStyle?.let { put("fontStyle", it) }
        s.letterSpacing?.let { put("letterSpacing", it.toDouble()) }
    }

    private fun textSegmentFromJson(o: JSONObject) = TextSegment(
        start = o.optInt("start", 0),
        end = o.optInt("end", 0),
        fontSize = if (o.has("fontSize")) o.optInt("fontSize") else null,
        color = if (o.has("color")) o.optLong("color") else null,
        fontFamily = if (o.has("fontFamily")) o.optString("fontFamily") else null,
        fontWeight = if (o.has("fontWeight")) o.optString("fontWeight") else null,
        fontStyle = if (o.has("fontStyle")) o.optString("fontStyle") else null,
        letterSpacing = if (o.has("letterSpacing")) o.optDouble("letterSpacing").toFloat() else null
    )

    private fun textStateToJson(t: TextState) = JSONObject().apply {
        put("content", t.content)
        put("fontFamily", t.fontFamily)
        put("fontSize", t.fontSize)
        put("fontWeight", t.fontWeight)
        put("fontStyle", t.fontStyle)
        put("color", t.color)
        put("strokeEnabled", t.strokeEnabled)
        put("strokeWidth", t.strokeWidth.toDouble())
        put("strokeColor", t.strokeColor)
        put("glowEnabled", t.glowEnabled)
        put("glowColor", t.glowColor)
        put("glowRadius", t.glowRadius.toDouble())
        put("gradientEnabled", t.gradientEnabled)
        put("gradientColor1", t.gradientColor1)
        put("gradientColor2", t.gradientColor2)
        put("gradientAngle", t.gradientAngle.toDouble())
        put("shadowEnabled", t.shadowEnabled)
        put("shadowColor", t.shadowColor)
        put("shadowBlur", t.shadowBlur.toDouble())
        put("shadowOffsetX", t.shadowOffsetX.toDouble())
        put("shadowOffsetY", t.shadowOffsetY.toDouble())
        put("alignment", t.alignment)
        put("letterSpacing", t.letterSpacing.toDouble())
        put("lineHeight", t.lineHeight.toDouble())
        put("tracking", t.tracking.toDouble())
        put("positionX", t.positionX.toDouble())
        put("positionY", t.positionY.toDouble())
        put("anchorX", t.anchorX.toDouble())
        put("anchorY", t.anchorY.toDouble())
        put("maxWidth", t.maxWidth.toDouble())
        put("scale", t.scale.toDouble())
        put("rotation", t.rotation.toDouble())
        put("opacity", t.opacity.toDouble())
        put("animation", t.animation)
        put("animationDuration", t.animationDuration.toDouble())
        t.templateId?.let { put("templateId", it) }

        val segArr = JSONArray()
        t.segments.forEach { segArr.put(textSegmentToJson(it)) }
        put("segments", segArr)
    }

    private fun textStateFromJson(o: JSONObject): TextState {
        val segments = mutableListOf<TextSegment>()
        val segArr = o.optJSONArray("segments")
        if (segArr != null) {
            for (i in 0 until segArr.length()) {
                segArr.optJSONObject(i)?.let { segments.add(textSegmentFromJson(it)) }
            }
        }
        return TextState(
            content = o.optString("content", ""),
            fontFamily = o.optString("fontFamily", "Arial"),
            fontSize = o.optInt("fontSize", 36),
            fontWeight = o.optString("fontWeight", "normal"),
            fontStyle = o.optString("fontStyle", "normal"),
            color = o.optLong("color", 0xFFFFFFFFL),
            strokeEnabled = o.optBoolean("strokeEnabled", false),
            strokeWidth = o.optDouble("strokeWidth", 0.0).toFloat(),
            strokeColor = o.optLong("strokeColor", 0xFF000000L),
            glowEnabled = o.optBoolean("glowEnabled", false),
            glowColor = o.optLong("glowColor", 0xFF4DD0E1L),
            glowRadius = o.optDouble("glowRadius", 25.0).toFloat(),
            gradientEnabled = o.optBoolean("gradientEnabled", false),
            gradientColor1 = o.optLong("gradientColor1", 0xFFFF0066L),
            gradientColor2 = o.optLong("gradientColor2", 0xFF0066FFL),
            gradientAngle = o.optDouble("gradientAngle", 90.0).toFloat(),
            shadowEnabled = o.optBoolean("shadowEnabled", false),
            shadowColor = o.optLong("shadowColor", 0xFF000000L),
            shadowBlur = o.optDouble("shadowBlur", 8.0).toFloat(),
            shadowOffsetX = o.optDouble("shadowOffsetX", 2.0).toFloat(),
            shadowOffsetY = o.optDouble("shadowOffsetY", 2.0).toFloat(),
            alignment = o.optString("alignment", "center"),
            letterSpacing = o.optDouble("letterSpacing", 0.0).toFloat(),
            lineHeight = o.optDouble("lineHeight", 1.2).toFloat(),
            tracking = o.optDouble("tracking", 0.0).toFloat(),
            positionX = o.optDouble("positionX", 50.0).toFloat(),
            positionY = o.optDouble("positionY", 50.0).toFloat(),
            anchorX = o.optDouble("anchorX", 50.0).toFloat(),
            anchorY = o.optDouble("anchorY", 50.0).toFloat(),
            maxWidth = o.optDouble("maxWidth", 90.0).toFloat(),
            scale = o.optDouble("scale", 100.0).toFloat(),
            rotation = o.optDouble("rotation", 0.0).toFloat(),
            opacity = o.optDouble("opacity", 100.0).toFloat(),
            animation = o.optString("animation", "none"),
            animationDuration = o.optDouble("animationDuration", 0.6).toFloat(),
            templateId = if (o.isNull("templateId")) null else o.optString("templateId", null),
            segments = segments
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  STICKER
    // ═══════════════════════════════════════════════════════════
    private fun stickerStateToJson(s: StickerState) = JSONObject().apply {
        put("emoji", s.emoji)
        put("x", s.x.toDouble())
        put("y", s.y.toDouble())
        put("scale", s.scale.toDouble())
        put("rotation", s.rotation.toDouble())
    }

    private fun stickerStateFromJson(o: JSONObject): StickerState = StickerState(
        emoji = o.optString("emoji", ""),
        x = o.optDouble("x", 50.0).toFloat(),
        y = o.optDouble("y", 50.0).toFloat(),
        scale = o.optDouble("scale", 100.0).toFloat(),
        rotation = o.optDouble("rotation", 0.0).toFloat()
    )

    // ═══════════════════════════════════════════════════════════
    //  CHROMA
    // ═══════════════════════════════════════════════════════════
    private fun chromaToJson(c: ChromaState) = JSONObject().apply {
        put("keyColor", c.keyColor)
        put("similarity", c.similarity.toDouble())
        put("smoothness", c.smoothness.toDouble())
        put("spill", c.spill.toDouble())
        put("intensity", c.intensity.toDouble())
    }

    private fun chromaFromJson(o: JSONObject): ChromaState = ChromaState(
        keyColor = o.optLong("keyColor", 0xFF00FF00L),
        similarity = o.optDouble("similarity", 30.0).toFloat(),
        smoothness = o.optDouble("smoothness", 20.0).toFloat(),
        spill = o.optDouble("spill", 50.0).toFloat(),
        intensity = o.optDouble("intensity", 100.0).toFloat()
    )

    // ═══════════════════════════════════════════════════════════
    //  FREEZE
    // ═══════════════════════════════════════════════════════════
    private fun freezeToJson(f: FreezeState) = JSONObject().apply {
        put("durationMs", f.durationMs)
        put("atTimeMs", f.atTimeMs)
    }

    private fun freezeFromJson(o: JSONObject): FreezeState = FreezeState(
        durationMs = o.optLong("durationMs", 2000L),
        atTimeMs = o.optLong("atTimeMs", 0L)
    )

    // ═══════════════════════════════════════════════════════════
    //  TRANSITION
    // ═══════════════════════════════════════════════════════════
    private fun transitionToJson(t: TransitionState) = JSONObject().apply {
        put("key", t.key)
        put("durationMs", t.durationMs)
    }

    private fun transitionFromJson(o: JSONObject): TransitionState = TransitionState(
        key = o.optString("key", "none"),
        durationMs = o.optLong("durationMs", 500L)
    )

    // ═══════════════════════════════════════════════════════════
    //  RATIO
    // ═══════════════════════════════════════════════════════════
    private fun ratioToJson(r: RatioState) = JSONObject().apply {
        put("key", r.key)
        put("w", r.w)
        put("h", r.h)
    }

    private fun ratioFromJson(o: JSONObject): RatioState = RatioState(
        key = o.optString("key", "16:9"),
        w = o.optInt("w", 16),
        h = o.optInt("h", 9)
    )

    // ═══════════════════════════════════════════════════════════
    //  MASK
    // ═══════════════════════════════════════════════════════════
    private fun maskToJson(m: MaskState) = JSONObject().apply {
        put("type", m.type.name)
        put("centerX", m.centerX.toDouble())
        put("centerY", m.centerY.toDouble())
        put("rotation", m.rotation.toDouble())
        put("feather", m.feather.toDouble())
        put("isInverted", m.isInverted)
        put("opacity", m.opacity.toDouble())
        put("radius", m.radius.toDouble())
        put("width", m.width.toDouble())
        put("height", m.height.toDouble())
        put("cornerRadius", m.cornerRadius.toDouble())
        put("positionY", m.positionY.toDouble())
        put("scale", m.scale.toDouble())
        put("customClosed", m.customClosed)
        put("expansion", m.expansion.toDouble())
        put("strokeColor", m.strokeColor)
        put("overlayColor", m.overlayColor)

        val ptsArr = JSONArray()
        m.customPoints.forEach { p ->
            ptsArr.put(JSONObject().apply {
                put("x", p.x.toDouble())
                put("y", p.y.toDouble())
                put("handleInX", p.handleInX.toDouble())
                put("handleInY", p.handleInY.toDouble())
                put("handleOutX", p.handleOutX.toDouble())
                put("handleOutY", p.handleOutY.toDouble())
                put("hasHandles", p.hasHandles)
            })
        }
        put("customPoints", ptsArr)

        val kfArr = JSONArray()
        m.keyframes.forEach { kf ->
            kfArr.put(JSONObject().apply {
                put("timeMs", kf.timeMs)
                put("centerX", kf.centerX.toDouble())
                put("centerY", kf.centerY.toDouble())
                put("radius", kf.radius.toDouble())
                put("width", kf.width.toDouble())
                put("height", kf.height.toDouble())
                put("rotation", kf.rotation.toDouble())
                put("cornerRadius", kf.cornerRadius.toDouble())
                put("scale", kf.scale.toDouble())
                put("positionY", kf.positionY.toDouble())
                put("feather", kf.feather.toDouble())
                put("expansion", kf.expansion.toDouble())
                put("opacity", kf.opacity.toDouble())
                put("ease", kf.ease)

                val kfPts = JSONArray()
                kf.customPoints.forEach { p ->
                    kfPts.put(JSONObject().apply {
                        put("x", p.x.toDouble())
                        put("y", p.y.toDouble())
                        put("handleInX", p.handleInX.toDouble())
                        put("handleInY", p.handleInY.toDouble())
                        put("handleOutX", p.handleOutX.toDouble())
                        put("handleOutY", p.handleOutY.toDouble())
                        put("hasHandles", p.hasHandles)
                    })
                }
                put("customPoints", kfPts)
            })
        }
        put("keyframes", kfArr)
    }

    private fun maskFromJson(o: JSONObject?): MaskState {
        if (o == null) return MaskState()

        val pts = mutableListOf<MaskPoint>()
        val ptsArr = o.optJSONArray("customPoints")
        if (ptsArr != null) {
            for (i in 0 until ptsArr.length()) {
                ptsArr.optJSONObject(i)?.let { p ->
                    pts.add(
                        MaskPoint(
                            x = p.optDouble("x", 0.0).toFloat(),
                            y = p.optDouble("y", 0.0).toFloat(),
                            handleInX = p.optDouble("handleInX", 0.0).toFloat(),
                            handleInY = p.optDouble("handleInY", 0.0).toFloat(),
                            handleOutX = p.optDouble("handleOutX", 0.0).toFloat(),
                            handleOutY = p.optDouble("handleOutY", 0.0).toFloat(),
                            hasHandles = p.optBoolean("hasHandles", false)
                        )
                    )
                }
            }
        }

        val kfs = mutableListOf<MaskKeyframe>()
        val kfArr = o.optJSONArray("keyframes")
        if (kfArr != null) {
            for (i in 0 until kfArr.length()) {
                kfArr.optJSONObject(i)?.let { k ->
                    val kfPts = mutableListOf<MaskPoint>()
                    val kpArr = k.optJSONArray("customPoints")
                    if (kpArr != null) {
                        for (j in 0 until kpArr.length()) {
                            kpArr.optJSONObject(j)?.let { p ->
                                kfPts.add(
                                    MaskPoint(
                                        x = p.optDouble("x", 0.0).toFloat(),
                                        y = p.optDouble("y", 0.0).toFloat(),
                                        handleInX = p.optDouble("handleInX", 0.0).toFloat(),
                                        handleInY = p.optDouble("handleInY", 0.0).toFloat(),
                                        handleOutX = p.optDouble("handleOutX", 0.0).toFloat(),
                                        handleOutY = p.optDouble("handleOutY", 0.0).toFloat(),
                                        hasHandles = p.optBoolean("hasHandles", false)
                                    )
                                )
                            }
                        }
                    }
                    kfs.add(
                        MaskKeyframe(
                            timeMs = k.optLong("timeMs", 0L),
                            centerX = k.optDouble("centerX", 0.5).toFloat(),
                            centerY = k.optDouble("centerY", 0.5).toFloat(),
                            radius = k.optDouble("radius", 0.3).toFloat(),
                            width = k.optDouble("width", 0.5).toFloat(),
                            height = k.optDouble("height", 0.5).toFloat(),
                            rotation = k.optDouble("rotation", 0.0).toFloat(),
                            cornerRadius = k.optDouble("cornerRadius", 0.0).toFloat(),
                            scale = k.optDouble("scale", 1.0).toFloat(),
                            positionY = k.optDouble("positionY", 0.5).toFloat(),
                            feather = k.optDouble("feather", 0.0).toFloat(),
                            expansion = k.optDouble("expansion", 0.0).toFloat(),
                            opacity = k.optDouble("opacity", 100.0).toFloat(),
                            customPoints = kfPts,
                            ease = k.optString("ease", "easeInOut")
                        )
                    )
                }
            }
        }

        return MaskState(
            type = try {
                MaskType.valueOf(o.optString("type", "NONE"))
            } catch (e: Exception) {
                MaskType.NONE
            },
            centerX = o.optDouble("centerX", 0.5).toFloat(),
            centerY = o.optDouble("centerY", 0.5).toFloat(),
            rotation = o.optDouble("rotation", 0.0).toFloat(),
            feather = o.optDouble("feather", 0.0).toFloat(),
            isInverted = o.optBoolean("isInverted", false),
            opacity = o.optDouble("opacity", 100.0).toFloat(),
            radius = o.optDouble("radius", 0.3).toFloat(),
            width = o.optDouble("width", 0.5).toFloat(),
            height = o.optDouble("height", 0.5).toFloat(),
            cornerRadius = o.optDouble("cornerRadius", 0.0).toFloat(),
            positionY = o.optDouble("positionY", 0.5).toFloat(),
            scale = o.optDouble("scale", 1.0).toFloat(),
            customPoints = pts,
            customClosed = o.optBoolean("customClosed", false),
            expansion = o.optDouble("expansion", 0.0).toFloat(),
            strokeColor = o.optLong("strokeColor", 0xFF22C55EL),
            overlayColor = o.optLong("overlayColor", 0xFF000000L),
            keyframes = kfs
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  BRUSH
    // ═══════════════════════════════════════════════════════════
    private fun brushToJson(b: BrushState) = JSONObject().apply {
        val arr = JSONArray()
        b.strokes.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("type", s.type.name)
                put("color", s.color)
                put("width", s.width.toDouble())
                put("opacity", s.opacity.toDouble())
                put("startMs", s.startMs)
                put("endMs", s.endMs)

                val ptsArr = JSONArray()
                s.points.forEach { p ->
                    ptsArr.put(JSONObject().apply {
                        put("x", p.x.toDouble())
                        put("y", p.y.toDouble())
                    })
                }
                put("points", ptsArr)

                put("gradient", JSONObject().apply {
                    put("enabled", s.gradient.enabled)
                    put("color1", s.gradient.color1)
                    put("color2", s.gradient.color2)
                    put("color3", s.gradient.color3)
                    put("hasMid", s.gradient.hasMid)
                    put("mode", s.gradient.mode)
                })
            })
        }
        put("strokes", arr)
    }

    private fun brushFromJson(o: JSONObject?): BrushState {
        if (o == null) return BrushState()
        val strokes = mutableListOf<BrushStroke>()
        val arr = o.optJSONArray("strokes")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { s ->
                    val pts = mutableListOf<BrushPoint>()
                    val pArr = s.optJSONArray("points")
                    if (pArr != null) {
                        for (j in 0 until pArr.length()) {
                            pArr.optJSONObject(j)?.let { p ->
                                pts.add(
                                    BrushPoint(
                                        x = p.optDouble("x", 0.0).toFloat(),
                                        y = p.optDouble("y", 0.0).toFloat()
                                    )
                                )
                            }
                        }
                    }

                    val gradObj = s.optJSONObject("gradient")
                    val grad = if (gradObj != null) {
                        BrushGradient(
                            enabled = gradObj.optBoolean("enabled", false),
                            color1 = gradObj.optLong("color1", 0xFFFF0000L),
                            color2 = gradObj.optLong("color2", 0xFF00FF00L),
                            color3 = gradObj.optLong("color3", 0x00000000L),
                            hasMid = gradObj.optBoolean("hasMid", false),
                            mode = gradObj.optString("mode", "linear")
                        )
                    } else BrushGradient()

                    val type = try {
                        BrushType.valueOf(s.optString("type", "PEN"))
                    } catch (e: Exception) {
                        BrushType.PEN
                    }

                    strokes.add(
                        BrushStroke(
                            id = s.optString("id", java.util.UUID.randomUUID().toString()),
                            type = type,
                            color = s.optLong("color", 0xFFFF0000L),
                            width = s.optDouble("width", 20.0).toFloat(),
                            opacity = s.optDouble("opacity", 1.0).toFloat(),
                            points = pts,
                            startMs = s.optLong("startMs", 0L),
                            endMs = s.optLong("endMs", Long.MAX_VALUE),
                            gradient = grad
                        )
                    )
                }
            }
        }
        return BrushState(strokes = strokes)
    }

    // ═══════════════════════════════════════════════════════════
    //  KEYFRAMES
    // ═══════════════════════════════════════════════════════════
    private fun keyframesFromJson(o: JSONObject?): Map<String, List<Keyframe>> {
        if (o == null) return emptyMap()
        val result = mutableMapOf<String, List<Keyframe>>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val prop = keys.next()
            val arr = o.optJSONArray(prop) ?: continue
            val list = mutableListOf<Keyframe>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { kf ->
                    list.add(
                        Keyframe(
                            time = kf.optDouble("time", 0.0).toFloat(),
                            value = kf.optDouble("value", 0.0).toFloat(),
                            ease = kf.optString("ease", Keyframe.DEFAULT_EASE)
                        )
                    )
                }
            }
            result[prop] = list
        }
        return result
    }

    // ═══════════════════════════════════════════════════════════
    //  🆕 VISUALIZER
    // ═══════════════════════════════════════════════════════════
    private fun visualizerToJson(v: VisualizerState): JSONObject =
        JSONObject().apply {
            put("preset", v.preset.key)
            put("linkedAudioClipId", v.linkedAudioClipId ?: JSONObject.NULL)

            put("color1", v.color1)
            put("color2", v.color2)

            put("sensitivity", v.sensitivity.toDouble())
            put("smoothing", v.smoothing.toDouble())

            put("size", v.size.toDouble())
            put("positionX", v.positionX.toDouble())
            put("positionY", v.positionY.toDouble())
            put("rotation", v.rotation.toDouble())
            put("opacity", v.opacity.toDouble())
            put("glow", v.glow)

            put("imageUri", v.imageUri ?: JSONObject.NULL)
            put("showImage", v.showImage)
            put("imageScale", v.imageScale.toDouble())
            put("imageOpacity", v.imageOpacity.toDouble())

            put("imageIdleRotation", v.imageIdleRotation)
            put("imageIdleSpeed", v.imageIdleSpeed.toDouble())
            put("imagePulseAmount", v.imagePulseAmount.toDouble())
            put("imageBassOnly", v.imageBassOnly)

            put("showText", v.showText)
            put("textContent", v.textContent)
            put("textState", textStateToJson(v.textState))
            put("textOnTopOfImage", v.textOnTopOfImage)

            put("bassRingBoost", v.bassRingBoost.toDouble())
            put("midBarBoost", v.midBarBoost.toDouble())
            put("trebleSpikeBoost", v.trebleSpikeBoost.toDouble())

            put("beatReaction", v.beatReaction.toDouble())
            put("beatPulseDurationMs", v.beatPulseDurationMs)
            put("useBeatSync", v.useBeatSync)

            put("lerpFactor", v.lerpFactor.toDouble())

            val bt = JSONArray()
            v.beatTimesMs.forEach { bt.put(it) }
            put("beatTimesMs", bt)

            val bs = JSONArray()
            v.beatStrengths.forEach { bs.put(it.toDouble()) }
            put("beatStrengths", bs)
        }

    private fun visualizerFromJson(o: JSONObject): VisualizerState {
        val preset = VisualizerPreset
            .fromKey(o.optString("preset", "neonGlowRing"))

        val beatTimes = mutableListOf<Long>()
        val btArr = o.optJSONArray("beatTimesMs")
        if (btArr != null) {
            for (i in 0 until btArr.length()) {
                beatTimes.add(btArr.optLong(i, 0L))
            }
        }

        val beatStrengths = mutableListOf<Float>()
        val bsArr = o.optJSONArray("beatStrengths")
        if (bsArr != null) {
            for (i in 0 until bsArr.length()) {
                beatStrengths.add(bsArr.optDouble(i, 0.5).toFloat())
            }
        }

        return VisualizerState(
            preset = preset,
            linkedAudioClipId = if (o.isNull("linkedAudioClipId")) null
            else o.optString("linkedAudioClipId", null),

            color1 = o.optLong("color1", 0xFFFFD166L),
            color2 = o.optLong("color2", 0xFFFFA500L),

            sensitivity = o.optDouble("sensitivity", 1.5).toFloat(),
            smoothing = o.optDouble("smoothing", 0.65).toFloat(),

            size = o.optDouble("size", 0.32).toFloat(),
            positionX = o.optDouble("positionX", 0.5).toFloat(),
            positionY = o.optDouble("positionY", 0.5).toFloat(),
            rotation = o.optDouble("rotation", 0.0).toFloat(),
            opacity = o.optDouble("opacity", 1.0).toFloat(),
            glow = o.optBoolean("glow", true),

            imageUri = if (o.isNull("imageUri")) null
            else o.optString("imageUri", null),
            showImage = o.optBoolean("showImage", false),
            imageScale = o.optDouble("imageScale", 0.55).toFloat(),
            imageOpacity = o.optDouble("imageOpacity", 1.0).toFloat(),

            imageIdleRotation = o.optBoolean("imageIdleRotation", true),
            imageIdleSpeed = o.optDouble("imageIdleSpeed", 0.5).toFloat(),
            imagePulseAmount = o.optDouble("imagePulseAmount", 0.15).toFloat(),
            imageBassOnly = o.optBoolean("imageBassOnly", true),

            showText = o.optBoolean("showText", false),
            textContent = o.optString("textContent", "🎵"),
            textState = o.optJSONObject("textState")?.let { textStateFromJson(it) }
                ?: TextState(),
            textOnTopOfImage = o.optBoolean("textOnTopOfImage", true),

            bassRingBoost = o.optDouble("bassRingBoost", 1.0).toFloat(),
            midBarBoost = o.optDouble("midBarBoost", 1.0).toFloat(),
            trebleSpikeBoost = o.optDouble("trebleSpikeBoost", 1.0).toFloat(),

            beatTimesMs = beatTimes,
            beatStrengths = beatStrengths,
            beatReaction = o.optDouble("beatReaction", 1.0).toFloat(),
            beatPulseDurationMs = o.optLong("beatPulseDurationMs", 260L),
            useBeatSync = o.optBoolean("useBeatSync", true),

            lerpFactor = o.optDouble("lerpFactor", 0.20).toFloat()
        )
    }
}