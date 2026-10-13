package com.moody.moodyvideoeditor.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Saves/loads project state to local storage as JSON.
// No external dependencies — uses org.json (built into Android).
object ProjectRepository {

    private const val DIR_PROJECTS = "projects"
    private const val DIR_THUMBS = "thumbnails"
    private const val FILE_INDEX = "index.json"


    //  PUBLIC API


    fun listProjects(context: Context): List<ProjectMeta> {
        val indexFile = File(getProjectsDir(context), FILE_INDEX)
        if (!indexFile.exists()) return emptyList()
        return try {
            val json = JSONArray(indexFile.readText())
            (0 until json.length()).mapNotNull { i ->
                jsonObjectToMeta(json.optJSONObject(i) ?: return@mapNotNull null)
            }.filter { it.clipCount > 0 }
                .sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveProject(context: Context, meta: ProjectMeta, state: EditorState): Boolean {
        if (state.clips.isEmpty()) return false

        try {
            val stateFile = File(getProjectsDir(context), "${meta.id}.json")
            stateFile.writeText(editorStateToJson(state).toString())

            val index = listProjects(context).toMutableList()
            index.removeAll { it.id == meta.id }
            index.add(0, meta)
            writeIndex(context, index)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
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
            index[idx] = index[idx].copy(
                name = newName,
                updatedAt = System.currentTimeMillis()
            )
            writeIndex(context, index)
        }
    }

    fun getThumbFile(context: Context, projectId: String): File {
        val dir = getThumbsDir(context)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$projectId.jpg")
    }


    //  INTERNAL — DIRS/INDEX


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
        thumbnailPath = if (o.isNull("thumbnailPath")) null
        else o.optString("thumbnailPath", null),
        clipCount = o.optInt("clipCount", 0),
        durationMs = o.optLong("durationMs", 0L)
    )


    //  INTERNAL — STATE SERIALIZATION


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
            val obj = arr.optJSONObject(i)
            if (obj != null) clips.add(clipFromJson(obj))
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


    //  CLIP


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
        put("linkGroupId", c.linkGroupId ?: JSONObject.NULL)
        put("isMuted", c.isMuted)

        put("audioFx", c.audioFx)
        put("audioFxIntensity", c.audioFxIntensity.toDouble())
        put("soundFx", c.soundFx)
        put("soundFxIntensity", c.soundFxIntensity.toDouble())

        put("adjustments", adjustmentsToJson(c.adjustments))
        put("filters", filterToJson(c.filters))
        put("colorWheel", colorWheelToJson(c.colorWheel))
        put("overlay", overlayToJson(c.overlay))

        val effectState = c.effectState
        if (effectState != null) put("effectState", effectStateToJson(effectState))

        val textState = c.textState
        if (textState != null) put("textState", textStateToJson(textState))

        val stickerState = c.stickerState
        if (stickerState != null) put("stickerState", stickerStateToJson(stickerState))

        val chroma = c.chroma
        if (chroma != null) put("chroma", chromaToJson(chroma))

        val freeze = c.freeze
        if (freeze != null) put("freeze", freezeToJson(freeze))

        val transition = c.transition
        if (transition != null) put("transition", transitionToJson(transition))

        val ratio = c.ratio
        if (ratio != null) put("ratio", ratioToJson(ratio))

        put("mask", maskToJson(c.mask))
        put("brush", brushToJson(c.brush))
        put("matteStyle", matteStyleToJson(c.matteStyle))

        val visualizer = c.visualizer
        if (visualizer != null) put("visualizer", visualizerToJson(visualizer))


        // Keyframes
        val kfObj = JSONObject()
        c.keyframes.forEach { entry ->
            val prop = entry.key
            val list = entry.value
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
            linkedId = if (o.isNull("linkedId")) null
            else o.optString("linkedId", null),
            linkGroupId = if (o.isNull("linkGroupId")) null
            else o.optString("linkGroupId", null),
            isMuted = o.optBoolean("isMuted", false),

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
            matteStyle = matteStyleFromJson(o.optJSONObject("matteStyle")),
            visualizer = o.optJSONObject("visualizer")?.let { visualizerFromJson(it) },
            keyframes = keyframesFromJson(o.optJSONObject("keyframes"))
        )
    }



    private fun keyframesToJson(kfs: Map<String, List<Keyframe>>): JSONObject {
        val o = JSONObject()
        kfs.forEach { entry ->
            val prop = entry.key
            val list = entry.value
            val arr = JSONArray()
            list.forEach { kf ->
                arr.put(JSONObject().apply {
                    put("time", kf.time.toDouble())
                    put("value", kf.value.toDouble())
                    put("ease", kf.ease)
                })
            }
            o.put(prop, arr)
        }
        return o
    }


    //  ADJUSTMENTS


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


    //  FILTERS


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


    //  COLOR WHEEL


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


    //  OVERLAY


    private fun overlayToJson(o: OverlayState) = JSONObject().apply {
        put("type", o.type)
        put("intensity", o.intensity.toDouble())
        put("color", o.color)
        put("color2", o.color2)
        put("size", o.size.toDouble())
        put("opacity", o.opacity.toDouble())
        put("speed", o.speed.toDouble())
        put("shape", o.shape)
        put("glowEnabled", o.glowEnabled)
        put("glowRadius", o.glowRadius.toDouble())
        put("glowColor", o.glowColor)
    }

    private fun overlayFromJson(o: JSONObject?): OverlayState {
        if (o == null) return OverlayState()
        return OverlayState(
            type = o.optString("type", "none"),
            intensity = o.optDouble("intensity", 100.0).toFloat(),
            color = o.optLong("color", 0xFFFFFFFFL),
            color2 = o.optLong("color2", 0xFFFFFFFFL),
            size = o.optDouble("size", 1.0).toFloat(),
            opacity = o.optDouble("opacity", 1.0).toFloat(),
            speed = o.optDouble("speed", 1.0).toFloat(),
            shape = o.optString("shape", "circle"),
            glowEnabled = o.optBoolean("glowEnabled", false),
            glowRadius = o.optDouble("glowRadius", 1.8).toFloat(),
            glowColor = o.optLong("glowColor", 0xFFFFFFFFL)
        )
    }


    //  EFFECT STATE


    private fun effectStateToJson(e: EffectState) = JSONObject().apply {
        put("kind", e.kind)
        put("presetKey", e.presetKey ?: JSONObject.NULL)

        val motion = e.motion
        if (motion != null) {
            put("motion", JSONObject().apply {
                put("type", motion.type)
                put("intensity", motion.intensity.toDouble())
                put("speed", motion.speed.toDouble())
            })
        }

        // 🆕 Animated color
        val acCfg = e.animatedColor
        if (acCfg != null) {
            put("animatedColor", JSONObject().apply {
                put("type", acCfg.type)
                put("speed", acCfg.speed.toDouble())
                put("intensity", acCfg.intensity.toDouble())
            })
        }

// 🆕 Blur
        val blurCfg = e.blur
        if (blurCfg != null) {
            put("blur", JSONObject().apply {
                put("type", blurCfg.type)
                put("intensity", blurCfg.intensity.toDouble())
                put("animDuration", blurCfg.animDuration.toDouble())
                put("speed", blurCfg.speed.toDouble())
                put("angle", blurCfg.angle.toDouble())
                put("centerX", blurCfg.centerX.toDouble())
                put("centerY", blurCfg.centerY.toDouble())
                put("blockSize", blurCfg.blockSize.toDouble())
                put("secondary", blurCfg.secondary.toDouble())
            })
        }
        val overlay = e.overlay
        if (overlay != null) {
            put("overlayCfg", JSONObject().apply {
                put("type", overlay.type)
                put("intensity", overlay.intensity.toDouble())
                put("color", overlay.color)
                put("color2", overlay.color2)
                put("size", overlay.size.toDouble())
                put("opacity", overlay.opacity.toDouble())
                put("speed", overlay.speed.toDouble())
                put("shape", overlay.shape)
                put("glowEnabled", overlay.glowEnabled)
                put("glowRadius", overlay.glowRadius.toDouble())
                put("glowColor", overlay.glowColor)
            })
        }

        val edgeGlow = e.edgeGlow
        if (edgeGlow != null) {
            put("edgeGlow", JSONObject().apply {
                put("color", edgeGlow.color)
                put("color2", edgeGlow.color2)
                put("radius", edgeGlow.radius.toDouble())
                put("intensity", edgeGlow.intensity.toDouble())
                put("spread", edgeGlow.spread.toDouble())
                put("animation", edgeGlow.animation.name)
                put("speed", edgeGlow.speed.toDouble())
            })
        }

        // 🆕 Lightning
        val lightning = e.lightning
        if (lightning != null) {
            put("lightning", JSONObject().apply {
                put("intensity", lightning.intensity.toDouble())
                put("frequency", lightning.frequency.toDouble())
                put("color", lightning.color)
                put("durationMs", lightning.durationMs)
                put("doubleStrike", lightning.doubleStrike)
            })
        }
        // ✨💥 Particle
        val particle = e.particle
        if (particle != null) {
            put("particle", JSONObject().apply {
                put("style", particle.style.name)
                put("category", particle.category.name)
                put("count", particle.count)
                put("speed", particle.speed.toDouble())
                put("spread", particle.spread.toDouble())
                put("direction", particle.direction.toDouble())
                put("gravity", particle.gravity.toDouble())
                put("sizeMin", particle.sizeMin.toDouble())
                put("sizeMax", particle.sizeMax.toDouble())
                put("color1", particle.color1)
                put("color2", particle.color2)
                put("glow", particle.glow)
                put("fadeOut", particle.fadeOut)
                put("lifetimeMs", particle.lifetimeMs)
                put("burstDurationMs", particle.burstDurationMs)
                put("triggerEverySec", particle.triggerEverySec.toDouble())
                put("triggerX", particle.triggerX.toDouble())
                put("triggerY", particle.triggerY.toDouble())
                put("emojis", particle.emojis)
                put("shockwave", particle.shockwave)
                put("shockwaveThickness", particle.shockwaveThickness.toDouble())
                put("flashOnTrigger", particle.flashOnTrigger)
                put("masterIntensity", particle.masterIntensity.toDouble())
            })
        }
        // 🔥 Fire
        val fire = e.fire
        if (fire != null) {
            put("fire", JSONObject().apply {
                put("style", fire.style.name)
                put("intensity", fire.intensity.toDouble())
                put("flameHeight", fire.flameHeight.toDouble())
                put("waviness", fire.waviness.toDouble())
                put("density", fire.density)
                put("colorHot", fire.colorHot)
                put("colorMid", fire.colorMid)
                put("colorCool", fire.colorCool)
                put("particleCount", fire.particleCount)
                put("particleSpeed", fire.particleSpeed.toDouble())
                put("speed", fire.speed.toDouble())
                put("masterIntensity", fire.masterIntensity.toDouble())
            })
        }
        // 🌀 Dust
        val dust = e.dust
        if (dust != null) {
            put("dust", JSONObject().apply {
                put("style", dust.style.name)
                put("intensity", dust.intensity.toDouble())
                put("masterIntensity", dust.masterIntensity.toDouble())
                put("count", dust.count)
                put("speed", dust.speed.toDouble())
                put("spread", dust.spread.toDouble())
                put("direction", dust.direction.toDouble())
                put("gravity", dust.gravity.toDouble())
                put("sizeMin", dust.sizeMin.toDouble())
                put("sizeMax", dust.sizeMax.toDouble())
                put("color1", dust.color1)
                put("color2", dust.color2)
                put("glow", dust.glow)
                put("fadeOut", dust.fadeOut)
                put("softness", dust.softness.toDouble())
                put("lifetimeMs", dust.lifetimeMs)
                put("burstDurationMs", dust.burstDurationMs)
                put("triggerEverySec", dust.triggerEverySec.toDouble())
                put("triggerX", dust.triggerX.toDouble())
                put("triggerY", dust.triggerY.toDouble())
                put("windX", dust.windX.toDouble())
                put("floorHeight", dust.floorHeight.toDouble())
                put("opacity", dust.opacity.toDouble())
            })
        }
    }



    private fun effectStateFromJson(o: JSONObject): EffectState {
        val motionObj = o.optJSONObject("motion")
        val acObj = o.optJSONObject("animatedColor")
        val overlayObj = o.optJSONObject("overlayCfg")
        val edgeGlowObj = o.optJSONObject("edgeGlow")

        return EffectState(
            kind = o.optString("kind", EffectState.KIND_EFFECT),
            presetKey = if (o.isNull("presetKey")) ""
            else o.optString("presetKey", ""),

            motion = if (motionObj != null) {
                MotionConfig(
                    type = motionObj.optString("type", "shake"),
                    intensity = motionObj.optDouble("intensity", 100.0).toFloat(),
                    speed = motionObj.optDouble("speed", 1.0).toFloat()
                )
            } else null,
            blur = o.optJSONObject("blur")?.let { b ->
                com.moody.moodyvideoeditor.utils.BlurConfig(
                    type = b.optString("type", "blurIn"),
                    intensity = b.optDouble("intensity", 50.0).toFloat(),
                    animDuration = b.optDouble("animDuration", 2.0).toFloat(),
                    speed = b.optDouble("speed", 1.0).toFloat(),
                    angle = b.optDouble("angle", 0.0).toFloat(),
                    centerX = b.optDouble("centerX", 0.5).toFloat(),
                    centerY = b.optDouble("centerY", 0.5).toFloat(),
                    blockSize = b.optDouble("blockSize", 20.0).toFloat(),
                    secondary = b.optDouble("secondary", 50.0).toFloat()
                )
            },
            // 🆕 Animated color
            animatedColor = if (acObj != null) {
                com.moody.moodyvideoeditor.utils.AnimatedColorConfig(
                    type = acObj.optString("type", "hueCycle"),
                    speed = acObj.optDouble("speed", 1.0).toFloat(),
                    intensity = acObj.optDouble("intensity", 100.0).toFloat()
                )
            } else null,

            overlay = if (overlayObj != null) {
                OverlayConfig(
                    type = overlayObj.optString("type", "rain"),
                    intensity = overlayObj.optDouble("intensity", 100.0).toFloat(),
                    color = overlayObj.optLong("color", 0xFFFFFFFFL),
                    color2 = overlayObj.optLong("color2", 0xFFFFFFFFL),
                    size = overlayObj.optDouble("size", 1.0).toFloat(),
                    opacity = overlayObj.optDouble("opacity", 1.0).toFloat(),
                    speed = overlayObj.optDouble("speed", 1.0).toFloat(),
                    shape = overlayObj.optString("shape", "circle"),
                    glowEnabled = overlayObj.optBoolean("glowEnabled", false),
                    glowRadius = overlayObj.optDouble("glowRadius", 1.8).toFloat(),
                    glowColor = overlayObj.optLong("glowColor", 0xFFFFFFFFL)
                )
            } else null,

            edgeGlow = if (edgeGlowObj != null) {
                EdgeGlowConfig(
                    color = edgeGlowObj.optLong("color", 0xFFFFFFFFL),
                    color2 = edgeGlowObj.optLong("color2", 0xFF00E5FFL),
                    radius = edgeGlowObj.optDouble("radius", 20.0).toFloat(),
                    intensity = edgeGlowObj.optDouble("intensity", 85.0).toFloat(),
                    spread = edgeGlowObj.optDouble("spread", 6.0).toFloat(),
                    animation = try {
                        EdgeGlowAnimation
                            .valueOf(edgeGlowObj.optString("animation", "STATIC"))
                    } catch (_: Exception) {
                        EdgeGlowAnimation.STATIC
                    },
                    speed = edgeGlowObj.optDouble("speed", 1.0).toFloat()
                )
            } else null,

            lightning = o.optJSONObject("lightning")?.let { lc ->
                LightningConfig(
                    intensity = lc.optDouble("intensity", 80.0).toFloat(),
                    frequency = lc.optDouble("frequency", 1.0).toFloat(),
                    color = lc.optLong("color", 0xFFFFFFFFL),
                    durationMs = lc.optLong("durationMs", 80L),
                    doubleStrike = lc.optBoolean("doubleStrike", true)
                )
            },

            particle = o.optJSONObject("particle")?.let { pc ->
                ParticleConfig(
                    style = try {
                        ParticleStyle.valueOf(
                            pc.optString("style", "SPARK_BURST")
                        )
                    } catch (_: Exception) {
                        ParticleStyle.SPARK_BURST
                    },
                    category = try {
                        ParticleCategory.valueOf(
                            pc.optString("category", "PARTICLES")
                        )
                    } catch (_: Exception) {
                        ParticleCategory.PARTICLES
                    },
                    count = pc.optInt("count", 80),
                    speed = pc.optDouble("speed", 100.0).toFloat(),
                    spread = pc.optDouble("spread", 360.0).toFloat(),
                    direction = pc.optDouble("direction", -90.0).toFloat(),
                    gravity = pc.optDouble("gravity", 0.0).toFloat(),
                    sizeMin = pc.optDouble("sizeMin", 2.0).toFloat(),
                    sizeMax = pc.optDouble("sizeMax", 8.0).toFloat(),
                    color1 = pc.optLong("color1", 0xFFFFD166L),
                    color2 = pc.optLong("color2", 0xFFFF6B00L),
                    glow = pc.optBoolean("glow", true),
                    fadeOut = pc.optBoolean("fadeOut", true),
                    lifetimeMs = pc.optLong("lifetimeMs", 1200L),
                    burstDurationMs = pc.optLong("burstDurationMs", 500L),
                    triggerEverySec = pc.optDouble(
                        "triggerEverySec", 0.0
                    ).toFloat(),
                    triggerX = pc.optDouble("triggerX", 0.5).toFloat(),
                    triggerY = pc.optDouble("triggerY", 0.5).toFloat(),
                    emojis = pc.optString("emojis", ""),
                    shockwave = pc.optBoolean("shockwave", false),
                    shockwaveThickness = pc.optDouble(
                        "shockwaveThickness", 0.05
                    ).toFloat(),
                    flashOnTrigger = pc.optBoolean("flashOnTrigger", false),
                    masterIntensity = pc.optDouble(
                        "masterIntensity", 100.0
                    ).toFloat()
                )
            },

            fire = o.optJSONObject("fire")?.let { fc ->
                FireConfig(
                    style = try {
                        FireStyle.valueOf(fc.optString("style", "FIRE_BOTTOM"))
                    } catch (_: Exception) { FireStyle.FIRE_BOTTOM },
                    intensity = fc.optDouble("intensity", 100.0).toFloat(),
                    flameHeight = fc.optDouble("flameHeight", 0.35).toFloat(),
                    waviness = fc.optDouble("waviness", 0.5).toFloat(),
                    density = fc.optInt("density", 12),
                    colorHot = fc.optLong("colorHot", 0xFFFFEE88L),
                    colorMid = fc.optLong("colorMid", 0xFFFF6600L),
                    colorCool = fc.optLong("colorCool", 0xFFAA0000L),
                    particleCount = fc.optInt("particleCount", 60),
                    particleSpeed = fc.optDouble("particleSpeed", 80.0).toFloat(),
                    speed = fc.optDouble("speed", 1.0).toFloat(),
                    masterIntensity = fc.optDouble("masterIntensity", 100.0).toFloat()
                )
            },

            dust = o.optJSONObject("dust")?.let { dc ->
                DustConfig(
                    style = try {
                        DustStyle.valueOf(dc.optString("style", "DUST_MOTES"))
                    } catch (_: Exception) { DustStyle.DUST_MOTES },
                    intensity = dc.optDouble("intensity", 100.0).toFloat(),
                    masterIntensity = dc.optDouble("masterIntensity", 100.0).toFloat(),
                    count = dc.optInt("count", 100),
                    speed = dc.optDouble("speed", 40.0).toFloat(),
                    spread = dc.optDouble("spread", 360.0).toFloat(),
                    direction = dc.optDouble("direction", -90.0).toFloat(),
                    gravity = dc.optDouble("gravity", 0.0).toFloat(),
                    sizeMin = dc.optDouble("sizeMin", 2.0).toFloat(),
                    sizeMax = dc.optDouble("sizeMax", 10.0).toFloat(),
                    color1 = dc.optLong("color1", 0xFFCCAA88L),
                    color2 = dc.optLong("color2", 0xFF886644L),
                    glow = dc.optBoolean("glow", false),
                    fadeOut = dc.optBoolean("fadeOut", true),
                    softness = dc.optDouble("softness", 1.0).toFloat(),
                    lifetimeMs = dc.optLong("lifetimeMs", 2500L),
                    burstDurationMs = dc.optLong("burstDurationMs", 800L),
                    triggerEverySec = dc.optDouble("triggerEverySec", 0.0).toFloat(),
                    triggerX = dc.optDouble("triggerX", 0.5).toFloat(),
                    triggerY = dc.optDouble("triggerY", 0.5).toFloat(),
                    windX = dc.optDouble("windX", 30.0).toFloat(),
                    floorHeight = dc.optDouble("floorHeight", 0.25).toFloat(),
                    opacity = dc.optDouble("opacity", 1.0).toFloat()
                )
            }
        )
    }




    //  TEXT


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
        letterSpacing = if (o.has("letterSpacing"))
            o.optDouble("letterSpacing").toFloat() else null
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

        // 🆕 BACKGROUND
        put("backgroundEnabled", t.backgroundEnabled)
        put("backgroundType", t.backgroundType)
        put("backgroundColor1", t.backgroundColor1)
        put("backgroundColor2", t.backgroundColor2)
        put("backgroundGradientEnabled", t.backgroundGradientEnabled)
        put("backgroundGradientAngle", t.backgroundGradientAngle.toDouble())
        put("backgroundOpacity", t.backgroundOpacity.toDouble())
        put("backgroundPaddingX", t.backgroundPaddingX.toDouble())
        put("backgroundPaddingY", t.backgroundPaddingY.toDouble())
        put("backgroundCornerRadius", t.backgroundCornerRadius.toDouble())
        put("backgroundBrushSeed", t.backgroundBrushSeed)

        val segArr = JSONArray()
        t.segments.forEach { segArr.put(textSegmentToJson(it)) }
        put("segments", segArr)
    }

    private fun textStateFromJson(o: JSONObject): TextState {
        val segments = mutableListOf<TextSegment>()
        val segArr = o.optJSONArray("segments")
        if (segArr != null) {
            for (i in 0 until segArr.length()) {
                val segObj = segArr.optJSONObject(i)
                if (segObj != null) segments.add(textSegmentFromJson(segObj))
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
            templateId = if (o.isNull("templateId")) null
            else o.optString("templateId", null),

            // 🆕 BACKGROUND
            backgroundEnabled = o.optBoolean("backgroundEnabled", false),
            backgroundType = o.optString("backgroundType", "rounded"),
            backgroundColor1 = o.optLong("backgroundColor1", 0xFF000000L),
            backgroundColor2 = o.optLong("backgroundColor2", 0xFF000000L),
            backgroundGradientEnabled = o.optBoolean("backgroundGradientEnabled", false),
            backgroundGradientAngle = o.optDouble("backgroundGradientAngle", 90.0).toFloat(),
            backgroundOpacity = o.optDouble("backgroundOpacity", 100.0).toFloat(),
            backgroundPaddingX = o.optDouble("backgroundPaddingX", 14.0).toFloat(),
            backgroundPaddingY = o.optDouble("backgroundPaddingY", 8.0).toFloat(),
            backgroundCornerRadius = o.optDouble("backgroundCornerRadius", 12.0).toFloat(),
            backgroundBrushSeed = o.optInt("backgroundBrushSeed", 42),

            segments = segments
        )
    }


    //  STICKER


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


    //  CHROMA


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


    //  FREEZE


    private fun freezeToJson(f: FreezeState) = JSONObject().apply {
        put("durationMs", f.durationMs)
        put("atTimeMs", f.atTimeMs)
    }

    private fun freezeFromJson(o: JSONObject): FreezeState = FreezeState(
        durationMs = o.optLong("durationMs", 2000L),
        atTimeMs = o.optLong("atTimeMs", 0L)
    )


    //  TRANSITION


    private fun transitionToJson(t: TransitionState) = JSONObject().apply {
        put("key", t.key)
        put("durationMs", t.durationMs)
    }

    private fun transitionFromJson(o: JSONObject): TransitionState = TransitionState(
        key = o.optString("key", "none"),
        durationMs = o.optLong("durationMs", 500L)
    )


    //  RATIO


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


    //  MASK


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
                val p = ptsArr.optJSONObject(i)
                if (p != null) {
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
                val k = kfArr.optJSONObject(i)
                if (k != null) {
                    val kfPts = mutableListOf<MaskPoint>()
                    val kpArr = k.optJSONArray("customPoints")
                    if (kpArr != null) {
                        for (j in 0 until kpArr.length()) {
                            val p = kpArr.optJSONObject(j)
                            if (p != null) {
                                kfPts.add(
                                    MaskPoint(
                                        x = p.optDouble("x", 0.0).toFloat(),
                                        y = p.optDouble("y", 0.0).toFloat(),
                                        handleInX = p.optDouble("handleInX", 0.0)
                                            .toFloat(),
                                        handleInY = p.optDouble("handleInY", 0.0)
                                            .toFloat(),
                                        handleOutX = p.optDouble("handleOutX", 0.0)
                                            .toFloat(),
                                        handleOutY = p.optDouble("handleOutY", 0.0)
                                            .toFloat(),
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


    //  BRUSH


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
                val s = arr.optJSONObject(i)
                if (s != null) {
                    val pts = mutableListOf<BrushPoint>()
                    val pArr = s.optJSONArray("points")
                    if (pArr != null) {
                        for (j in 0 until pArr.length()) {
                            val p = pArr.optJSONObject(j)
                            if (p != null) {
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
                            id = s.optString(
                                "id",
                                java.util.UUID.randomUUID().toString()
                            ),
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


    //  COLOR MATTE STYLE


    private fun matteStyleToJson(s: ColorMatteStyle) = JSONObject().apply {
        put("mode", s.mode.name)
        put("solidColor", s.solidColor)
        put("rampColor1", s.rampColor1)
        put("rampColor2", s.rampColor2)
        put("rampAngleDeg", s.rampAngleDeg.toDouble())
        put("topLeft", s.topLeft)
        put("topRight", s.topRight)
        put("bottomLeft", s.bottomLeft)
        put("bottomRight", s.bottomRight)
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
        } catch (_: Exception) {
            ColorMatteStyle()
        }
    }


    //  KEYFRAMES


    private fun keyframesFromJson(o: JSONObject?): Map<String, List<Keyframe>> {
        if (o == null) return emptyMap()
        val result = mutableMapOf<String, List<Keyframe>>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val prop = keys.next()
            val arr = o.optJSONArray(prop) ?: continue
            val list = mutableListOf<Keyframe>()
            for (i in 0 until arr.length()) {
                val kf = arr.optJSONObject(i)
                if (kf != null) {
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


    //  VISUALIZER


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