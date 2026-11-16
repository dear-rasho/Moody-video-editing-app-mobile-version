package com.moody.moodyvideoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.ColorWheelState
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.EditorState
import com.moody.moodyvideoeditor.data.FilterState
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.MaskState
import com.moody.moodyvideoeditor.data.MaskType
import com.moody.moodyvideoeditor.data.ProjectMeta
import com.moody.moodyvideoeditor.data.ProjectRepository
import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.data.ToneValue
import com.moody.moodyvideoeditor.data.settings.SettingsRepository
import com.moody.moodyvideoeditor.utils.Media3CompositionBuilder
import com.moody.moodyvideoeditor.utils.MaskSequenceData
import com.moody.moodyvideoeditor.utils.NativeVideoExporter
import com.moody.moodyvideoeditor.utils.TextBitmapRenderer
import com.moody.moodyvideoeditor.utils.TextOverlaySequence
import com.moody.moodyvideoeditor.utils.TransformGlEffect
import com.moody.moodyvideoeditor.utils.VideoExporter
import com.moody.moodyvideoeditor.utils.WatermarkHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
class Media3ExportParityTest {
    private lateinit var context: Context
    private lateinit var testDirectory: File
    private lateinit var artifactDirectory: File
    private lateinit var landscapeVideo: File
    private lateinit var portraitVideo: File
    private lateinit var blueVideo: File
    private lateinit var colorPatchVideo: File
    private val savedProjectIds = mutableListOf<String>()
    private val textSequences = mutableListOf<TextOverlaySequence>()
    private var watermarkCacheField: java.lang.reflect.Field? = null
    private var originalWatermarkPath: String? = null
    private var requestedBitrateKbps = BITRATE_KBPS
    private var explicitBt709Source = false

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val parityMode = InstrumentationRegistry.getArguments()
            .getString("parityMode")
            .orEmpty()
        requestedBitrateKbps = if (parityMode == "near-lossless") {
            NEAR_LOSSLESS_BITRATE_KBPS
        } else {
            BITRATE_KBPS
        }
        explicitBt709Source = parityMode == "bt709"
        val runId = System.currentTimeMillis()
        testDirectory = File(context.cacheDir, "media3-parity-$runId").apply {
            check(mkdirs()) { "Could not create parity test directory" }
        }
        artifactDirectory = File(
            context.getExternalFilesDir("media3-parity"),
            "run-$runId"
        ).apply {
            check(mkdirs()) { "Could not create parity artifact directory" }
        }
        disableFfmpegWatermarkForParity()
        landscapeVideo = createVideo(
            "pattern-landscape.mp4",
            "testsrc2=size=${WIDTH}x${HEIGHT}:rate=$FPS:duration=${DURATION_MS / 1000}"
        )
        portraitVideo = createVideo(
            "pattern-portrait.mp4",
            "testsrc2=size=720x1280:rate=$FPS:duration=${DURATION_MS / 1000}"
        )
        blueVideo = createVideo(
            "blue-overlay.mp4",
            "color=c=blue:s=${WIDTH}x${HEIGHT}:r=$FPS:d=${DURATION_MS / 1000}"
        )
        colorPatchVideo = createVideo(
            "colorspace-patches.mp4",
            colorPatchSource(),
            includeBt709Metadata = false
        )
        runBlocking {
            SettingsRepository(context).setDebugMedia3Export(false)
        }
        Log.i(
            TAG,
            "configuration mode=${parityMode.ifBlank { "default" }} " +
                    "requestedBitrateKbps=$requestedBitrateKbps " +
                    "explicitBt709Source=$explicitBt709Source " +
                    "artifactDirectory=${artifactDirectory.absolutePath}"
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            SettingsRepository(context).setDebugMedia3Export(true)
        }
        watermarkCacheField?.set(WatermarkHelper, originalWatermarkPath)
        textSequences.mapNotNull { it.workingDirectory }.distinct().forEach {
            it.deleteRecursively()
        }
        savedProjectIds.forEach { ProjectRepository.deleteProject(context, it) }
        testDirectory.deleteRecursively()
    }

    @Test
    fun filterBrightnessContrastSaturationParity() = runParityCase("filter") { clips ->
        clips += videoClip(landscapeVideo).copy(
            filters = FilterState(
                brightness = 122f,
                contrast = 118f,
                saturation = 132f
            )
        )
    }

    @Test
    fun adjustmentNoiseSharpenVignetteParity() = runParityCase("adjustment") { clips ->
        clips += videoClip(landscapeVideo).copy(
            adjustments = AdjustmentData(
                noise = 12f,
                sharpen = 28f,
                vignette = 35f
            )
        )
    }

    @Test
    fun colorWheelParity() = runParityCase("color-wheel") { clips ->
        clips += videoClip(landscapeVideo).copy(
            colorWheel = ColorWheelState(
                shadows = ToneValue(hue = 220f, saturation = 34f, intensity = 24f),
                midtones = ToneValue(hue = 28f, saturation = 22f, intensity = 18f),
                highlights = ToneValue(hue = 48f, saturation = 26f, intensity = 20f)
            )
        )
    }

    @Test
    fun baselineNoEffectsParity() = runParityCase("baseline") { clips ->
        clips += videoClip(landscapeVideo)
    }

    @Test
    fun portraitFitPadParity() = runParityCase(
        caseName = "fit-pad",
        validRegion = ::inPortraitFitRegion
    ) { clips ->
        clips += videoClip(portraitVideo)
    }

    @Test
    fun transformNoOpParity() = runParityCase("transform-noop") { clips ->
        clips += videoClip(landscapeVideo)
    }

    @Test
    fun transformScaleOnlyParity() {
        runDirectTransformParity(
            caseName = "transform-scale",
            scale = 1.5f,
            validRegion = { x, y -> inCenterFraction(x, y, 2f / 3f) }
        )
    }

    @Test
    fun transformRotationOnlyParity() {
        runDirectTransformParity(
            caseName = "transform-rotation",
            rotationDeg = 90f
        )
    }

    @Test
    fun transformCropOnlyParity() {
        runDirectTransformParity(
            caseName = "transform-crop",
            cropL = 0.1f,
            validRegion = { x, y -> inCenterFraction(x, y, 0.8f) }
        )
    }

    @Test
    fun transformOffsetOnlyParity() {
        runDirectTransformParity(
            caseName = "transform-offset",
            offsetX = 0.1f
        )
    }

    @Test
    fun textOverlayParity() = runParityCase("text-overlay") { clips ->
        clips += videoClip(landscapeVideo)
        clips += textClip(
            trackIndex = 1,
            state = TextState(
                content = "MEDIA3 PARITY",
                fontSize = 34,
                fontWeight = "bold",
                color = 0xFFFFD54FL,
                strokeEnabled = true,
                strokeWidth = 2f,
                positionX = 50f,
                positionY = 52f
            )
        )
    }

    @Test
    fun maskParity() = runParityCase("mask") { clips ->
        clips += videoClip(landscapeVideo).copy(
            mask = MaskState(type = MaskType.CIRCLE, radius = 0.34f)
        )
    }

    @Test
    fun multiTrackColorAndTextParity() = runParityCase("multi-track-color-text") { clips ->
        clips += videoClip(landscapeVideo, trackIndex = 0)
        clips += videoClip(blueVideo, trackIndex = 1).copy(
            colorWheel = ColorWheelState(
                midtones = ToneValue(hue = 205f, saturation = 22f, intensity = 10f)
            )
        )
        clips += textClip(
            trackIndex = 2,
            state = TextState(
                content = "COLOR + TEXT",
                fontSize = 30,
                fontWeight = "bold",
                positionX = 50f,
                positionY = 48f
            )
        )
    }

    @Test
    fun keyframedScaleOnlyParity() = runBlocking {
        val baselineMetrics = relativeBaselineMetrics()
        val keyframes = mapOf(
            "scale" to listOf(Keyframe(0f, 100f), Keyframe(3f, 200f))
        )
        val clip = videoClip(landscapeVideo).copy(keyframes = keyframes)
        val ffmpegOutput = exportWithVideoExporter(
            clips = listOf(clip),
            name = "keyframe-scale-ffmpeg",
            debugMedia3 = false
        )
        var media3Output: Uri? = null
        try {
            media3Output = exportWithDirectTransform(
                source = landscapeVideo,
                name = "keyframe-scale-media3",
                keyframes = keyframes
            )
            listOf(0L, 1_000L, 2_000L, 3_000L).forEach { timeMs ->
                val metrics = compareFrames(
                    referenceUri = ffmpegOutput,
                    candidateUri = media3Output,
                    caseName = "keyframe-scale-${timeMs}ms",
                    durationMs = DURATION_MS,
                    sampleTimesMs = listOf(timeMs),
                    validRegion = { x, y -> inCenterFraction(x, y, 2f / 3f) }
                )
                val deltaDb = metrics.psnrDb - baselineMetrics.psnrDb
                saveMetrics(
                    caseName = "keyframe-scale-${timeMs}ms",
                    metrics = metrics,
                    baseline = baselineMetrics,
                    deltaDb = deltaDb,
                    frameCount = 1
                )
                Log.i(
                    TAG,
                    "KEYFRAME_SAMPLE timeMs=$timeMs localTimeSec=${timeMs / 1000.0} " +
                            "psnrDb=${metrics.psnrDb} ssim=${metrics.ssim} " +
                            "baselinePsnrDb=${baselineMetrics.psnrDb} deltaDb=$deltaDb " +
                            "validPixels=${metrics.validPixels}"
                )
            }
        } finally {
            deleteOutput(ffmpegOutput)
            media3Output?.let(::deleteOutput)
        }
    }

    @Test
    fun colorspaceDiagnostic() {
        runBlocking {
            val clips = listOf(videoClip(colorPatchVideo))
            var ffmpegOutput: Uri? = null
            var media3Output: Uri? = null
            try {
                ffmpegOutput = exportWithVideoExporter(
                    clips = clips,
                    name = "colorspace-ffmpeg",
                    debugMedia3 = false
                )
                media3Output = exportWithMedia3Composition(
                    clips = clips,
                    textClips = emptyList(),
                    name = "colorspace-media3"
                )
                assertVideoOutput(ffmpegOutput, DURATION_MS)
                assertVideoOutput(
                    media3Output,
                    DURATION_MS,
                    allowEncoderAlignmentPadding = true
                )

                val comparison = compareFrames(
                    referenceUri = ffmpegOutput,
                    candidateUri = media3Output,
                    caseName = "colorspace-diagnostic",
                    durationMs = DURATION_MS,
                    sampleTimesMs = listOf(1_000L)
                )
                File(artifactDirectory, "colorspace-diagnostic-metrics.txt").writeText(
                    "psnr_db=${comparison.psnrDb}\nssim=${comparison.ssim}\n"
                )
                Log.i(
                    TAG,
                    "COLORSPACE_FRAME_METRICS psnrDb=${comparison.psnrDb} " +
                            "ssim=${comparison.ssim}"
                )
                val ffmpegFrame = frameAt(ffmpegOutput, 1_000L)
                val media3Frame = frameAt(media3Output, 1_000L)
                try {
                    val channelMae = calculateChannelMae(ffmpegFrame, media3Frame)
                    val ffmpegYuv = decodeYuvFrame(ffmpegOutput, "colorspace-ffmpeg")
                    val media3Yuv = decodeYuvFrame(media3Output, "colorspace-media3")
                    val planePsnr = calculateYuvPlanePsnr(ffmpegYuv, media3Yuv)

                    val colorSamples = COLOR_PATCHES.map { patch ->
                        val x =
                            patch.index * WIDTH / COLOR_PATCHES.size +
                                    WIDTH / COLOR_PATCHES.size / 2
                        val y = HEIGHT / 2
                        ColorSample(
                            name = patch.name,
                            ffmpegRgb = ffmpegFrame.getPixel(x, y),
                            media3Rgb = media3Frame.getPixel(x, y)
                        )
                    }
                    val patchRows = colorSamples.map { sample ->
                        "${sample.name},${rgbHex(sample.ffmpegRgb)},${rgbHex(sample.media3Rgb)}," +
                                sample.channelDeltas().joinToString(",")
                    }
                    val diagnosis = classifyColorDifference(channelMae, colorSamples)
                    val baselineAssessment = when {
                        comparison.psnrDb > 35.0 -> "above-35db"
                        comparison.psnrDb < 30.0 -> "below-30db"
                        else -> "30-to-35db"
                    }
                    val report = buildString {
                        appendLine("aggregate_rgb_psnr_db=${comparison.psnrDb}")
                        appendLine("baseline_assessment=$baselineAssessment")
                        appendLine("rgb_mae_r=${channelMae.red}")
                        appendLine("rgb_mae_g=${channelMae.green}")
                        appendLine("rgb_mae_b=${channelMae.blue}")
                        appendLine("y_psnr_db=${planePsnr.y}")
                        appendLine("u_psnr_db=${planePsnr.u}")
                        appendLine("v_psnr_db=${planePsnr.v}")
                        appendLine("ffmpeg_output_bytes=${outputSize(ffmpegOutput)}")
                        appendLine("media3_output_bytes=${outputSize(media3Output)}")
                        appendLine("ffmpeg_color_tags=${colorTagSummary(ffmpegOutput, "ffmpeg")}")
                        appendLine("media3_color_tags=${colorTagSummary(media3Output, "media3")}")
                        appendLine("pattern,ffmpeg_rgb,media3_rgb,delta_r,delta_g,delta_b")
                        patchRows.forEach(::appendLine)
                        appendLine("heuristic=$diagnosis")
                    }
                    File(artifactDirectory, "colorspace-diagnostic.txt").writeText(report)
                    Log.i(TAG, "COLORSPACE_DIAGNOSTIC\n$report")
                    Log.i(
                        TAG,
                        "COLORSPACE_RESULT psnrDb=${comparison.psnrDb} " +
                                "mae=${channelMae.red},${channelMae.green},${channelMae.blue} " +
                                "yuvPsnr=${planePsnr.y},${planePsnr.u},${planePsnr.v} " +
                                "baseline=$baselineAssessment heuristic=$diagnosis"
                    )
                } finally {
                    ffmpegFrame.recycle()
                    media3Frame.recycle()
                }
            } finally {
                ffmpegOutput?.let(::deleteOutput)
                media3Output?.let(::deleteOutput)
            }
        }
    }

    private fun runParityCase(
        caseName: String,
        validRegion: ((Int, Int) -> Boolean)? = null,
        makeClips: (MutableList<EditorClip>) -> Unit
    ) = runBlocking {
        val baselineMetrics = relativeBaselineMetrics()
        val clips = mutableListOf<EditorClip>()
        makeClips(clips)
        val projectFile = saveProjectFile(caseName, clips)
        val projectState = ProjectRepository.editorStateFromJson(
            JSONObject(projectFile.readText())
        )
        assertTrue("Project file did not preserve its clips", projectState.clips.size == clips.size)

        val ffmpegOutput = exportWithVideoExporter(
            clips = projectState.clips,
            name = "$caseName-ffmpeg",
            debugMedia3 = false
        )
        var media3Output: Uri? = null
        try {
            val textClips = projectState.clips.filter(EditorClip::isTextClip)
            val usesProductionFallback = textClips.isNotEmpty() ||
                    projectState.clips.any { it.mask.isActive }
            media3Output = if (usesProductionFallback) {
                exportWithVideoExporter(
                    clips = projectState.clips,
                    name = "$caseName-media3",
                    debugMedia3 = true
                )
            } else {
                exportWithMedia3Composition(
                    clips = projectState.clips,
                    textClips = textClips,
                    name = "$caseName-media3"
                )
            }

            val durationMs = projectState.clips.maxOf(EditorClip::timelineEndMs)
            assertVideoOutput(ffmpegOutput, durationMs)
            assertVideoOutput(
                media3Output,
                durationMs,
                allowEncoderAlignmentPadding = true
            )
            if (caseName == "baseline") {
                captureBaselineDiagnostics(ffmpegOutput, media3Output)
            }
            val metrics = compareFrames(
                referenceUri = ffmpegOutput,
                candidateUri = media3Output,
                caseName = caseName,
                durationMs = durationMs,
                validRegion = validRegion
            )
            val deltaDb = metrics.psnrDb - baselineMetrics.psnrDb
            val status = when {
                deltaDb >= RELATIVE_PASS_DELTA_DB -> "PASS"
                deltaDb < RELATIVE_BUG_DELTA_DB -> "BUG"
                else -> "BORDERLINE"
            }
            saveMetrics(caseName, metrics, baselineMetrics, deltaDb)
            Log.i(
                TAG,
                "RESULT case=$caseName route=${if (usesProductionFallback) {
                    "production-debug-route-with-fallback"
                } else {
                    "composition-builder-direct"
                }} psnrDb=${metrics.psnrDb} ssim=${metrics.ssim} " +
                        "baselinePsnrDb=${baselineMetrics.psnrDb} deltaDb=$deltaDb " +
                        "status=$status"
            )
            assertTrue(
                "$caseName is more than ${-RELATIVE_BUG_DELTA_DB} dB below baseline: " +
                        "PSNR=${metrics.psnrDb} dB baseline=${baselineMetrics.psnrDb} dB " +
                        "(delta=$deltaDb dB, SSIM=${metrics.ssim})",
                deltaDb >= RELATIVE_BUG_DELTA_DB
            )
        } finally {
            deleteOutput(ffmpegOutput)
            media3Output?.let(::deleteOutput)
        }
    }

    private suspend fun relativeBaselineMetrics(): Metrics {
        relativeBaseline?.let { return it }
        val baselineClips = listOf(videoClip(landscapeVideo))
        var ffmpegOutput: Uri? = null
        var media3Output: Uri? = null
        try {
            ffmpegOutput = exportWithVideoExporter(
                clips = baselineClips,
                name = "relative-baseline-ffmpeg",
                debugMedia3 = false
            )
            media3Output = exportWithMedia3Composition(
                clips = baselineClips,
                textClips = emptyList(),
                name = "relative-baseline-media3"
            )
            val metrics = compareFrames(
                referenceUri = ffmpegOutput,
                candidateUri = media3Output,
                caseName = "relative-baseline",
                durationMs = DURATION_MS
            )
            relativeBaseline = metrics
            saveMetrics("relative-baseline", metrics, metrics, 0.0)
            Log.i(
                TAG,
                "BASELINE psnrDb=${metrics.psnrDb} ssim=${metrics.ssim} source=untagged"
            )
            return metrics
        } finally {
            ffmpegOutput?.let(::deleteOutput)
            media3Output?.let(::deleteOutput)
        }
    }

    private suspend fun exportWithMedia3Composition(
        clips: List<EditorClip>,
        textClips: List<EditorClip>,
        name: String
    ): Uri {
        SettingsRepository(context).setDebugMedia3Export(true)
        val durationMs = clips.maxOf(EditorClip::timelineEndMs)
        val sequences = TextBitmapRenderer.renderOverlaysByTrack(
            context = context,
            textClips = textClips,
            W = WIDTH,
            H = HEIGHT,
            fps = FPS,
            totalDurationMs = durationMs
        )
        textSequences += sequences
        val textClipWindows = textClips.associateBy(EditorClip::trackIndex)
        sequences.forEach { sequence ->
            val textClip = textClipWindows[sequence.trackIndex]
                ?: error("No text clip for overlay track ${sequence.trackIndex}")
            val clipStartSec = textClip.timelineStartMs / 1000.0
            val clipEndSec = textClip.timelineEndMs / 1000.0
            check(sequence.startSec >= clipStartSec && sequence.endSec <= clipEndSec) {
                "Text sequence ${sequence.startSec}..${sequence.endSec}s is outside " +
                        "track ${sequence.trackIndex} clip window $clipStartSec..$clipEndSec"
            }
            check(sequence.startSec < sequence.endSec) {
                "Text sequence has an empty time range: ${sequence.startSec}..${sequence.endSec}"
            }
        }
        val maskSequences = emptyList<MaskSequenceData>()

        check(
            Media3CompositionBuilder(context).buildComposition(
                clips = clips,
                textSequences = sequences,
                maskSequences = maskSequences,
                outputWidth = WIDTH,
                outputHeight = HEIGHT,
                fps = FPS
            ) != null
        ) { "Media3 composition rejected the $name project" }

        val outputFile = File(testDirectory, "$name.mp4")
        val completed = NativeVideoExporter(context) {}.exportWithComposition(
            clips = clips,
            textSequences = sequences,
            maskSequences = maskSequences,
            outputFile = outputFile,
            width = WIDTH,
            height = HEIGHT,
            fps = FPS,
            bitrateKbps = requestedBitrateKbps
        )
        check(completed) { "Media3 did not export the $name project" }
        Log.i(
            TAG,
            "case=$name route=composition-builder-direct " +
                    "textSequences=${sequences.size} " +
                    "textWindows=${sequences.joinToString { "${it.startSec}..${it.endSec}" }} " +
                    "maskSequences=${maskSequences.size}"
        )
        return Uri.fromFile(outputFile)
    }

    private fun runDirectTransformParity(
        caseName: String,
        scale: Float = 1f,
        rotationDeg: Float = 0f,
        offsetX: Float = 0f,
        cropL: Float = 0f,
        keyframes: Map<String, List<Keyframe>> = emptyMap(),
        validRegion: (Int, Int) -> Boolean = { _, _ -> true }
    ) = runBlocking {
        val baselineMetrics = relativeBaselineMetrics()
        val clip = videoClip(landscapeVideo)
        check(clip.timelineStartMs == 0L) { "Transform parity clips must start at zero" }
        val ffmpegOutput = exportWithVideoExporter(
            clips = listOf(
                clip.copy(
                    scale = scale,
                    rotation = rotationDeg,
                    offsetX = offsetX,
                    cropL = cropL,
                    keyframes = keyframes
                )
            ),
            name = "$caseName-ffmpeg",
            debugMedia3 = false
        )
        var media3Output: Uri? = null
        try {
            media3Output = exportWithDirectTransform(
                source = landscapeVideo,
                name = "$caseName-media3",
                scale = scale,
                rotationDeg = rotationDeg,
                offsetX = offsetX,
                cropL = cropL,
                keyframes = keyframes
            )
            assertVideoOutput(ffmpegOutput, DURATION_MS)
            assertVideoOutput(
                media3Output,
                DURATION_MS,
                allowEncoderAlignmentPadding = true
            )
            val centerValidRegion = when {
                scale != 1f -> { x: Int, y: Int -> inCenterFraction(x, y, 2f / 3f) }
                cropL > 0f -> { x: Int, y: Int -> inCenterFraction(x, y, 0.8f) }
                else -> validRegion
            }
            val metrics = compareFrames(
                referenceUri = ffmpegOutput,
                candidateUri = media3Output,
                caseName = caseName,
                durationMs = DURATION_MS,
                validRegion = centerValidRegion,
                expectedTransform = ExpectedTransform(
                    scale = scale,
                    rotationDeg = rotationDeg,
                    offsetX = offsetX,
                    cropL = cropL
                )
            )
            val deltaDb = metrics.psnrDb - baselineMetrics.psnrDb
            saveMetrics(caseName, metrics, baselineMetrics, deltaDb)
            Log.i(
                TAG,
                "TRANSFORM_RESULT case=$caseName " +
                        "route=ffmpeg-vs-direct-media3-effect psnrDb=${metrics.psnrDb} " +
                        "ssim=${metrics.ssim} baselinePsnrDb=${baselineMetrics.psnrDb} " +
                        "deltaDb=$deltaDb " +
                        "validPixels=${metrics.validPixels}"
            )
        } finally {
            deleteOutput(ffmpegOutput)
            media3Output?.let(::deleteOutput)
        }
    }

    private suspend fun exportWithDirectTransform(
        source: File,
        name: String,
        scale: Float = 1f,
        rotationDeg: Float = 0f,
        offsetX: Float = 0f,
        cropL: Float = 0f,
        keyframes: Map<String, List<Keyframe>> = emptyMap()
    ): Uri {
        val outputFile = File(testDirectory, "$name.mp4")
        val effect = TransformGlEffect(
            targetWidth = WIDTH,
            targetHeight = HEIGHT,
            scale = scale,
            scaleX = 1f,
            scaleY = 1f,
            rotationDeg = rotationDeg,
            offsetX = offsetX,
            offsetY = 0f,
            cropL = cropL,
            cropR = 0f,
            cropT = 0f,
            cropB = 0f,
            layerOpacity = 100f,
            keyframes = keyframes,
            clipStartSec = 0.0,
            timestampOffsetSec = 0.0,
            fps = FPS
        )
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(source))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0L)
                    .setEndPositionMs(DURATION_MS)
                    .build()
            )
            .build()
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setFrameRate(FPS)
            .setRemoveAudio(true)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(
                        effect,
                        Presentation.createForWidthAndHeight(
                            WIDTH,
                            HEIGHT,
                            Presentation.LAYOUT_SCALE_TO_FIT
                        )
                    )
                )
            )
            .build()

        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(context)
                            .setEnableFallback(true)
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder()
                                    .setBitrate(requestedBitrateKbps * 1000)
                                    .build()
                            )
                            .build()
                    )
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(
                                composition: androidx.media3.transformer.Composition,
                                exportResult: androidx.media3.transformer.ExportResult
                            ) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }

                            override fun onError(
                                composition: androidx.media3.transformer.Composition,
                                exportResult: androidx.media3.transformer.ExportResult,
                                exportException: androidx.media3.transformer.ExportException
                            ) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(exportException)
                                }
                            }
                        }
                    )
                    .build()
                continuation.invokeOnCancellation { transformer.cancel() }
                try {
                    transformer.start(editedMediaItem, outputFile.absolutePath)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        }
        return Uri.fromFile(outputFile)
    }

    private fun disableFfmpegWatermarkForParity() {
        val transparentWatermark = File(testDirectory, "transparent-watermark.png")
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
        }
        try {
            FileOutputStream(transparentWatermark).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not create transparent parity watermark"
                }
            }
        } finally {
            bitmap.recycle()
        }

        val field = WatermarkHelper::class.java.getDeclaredField("cachedPath").apply {
            isAccessible = true
        }
        originalWatermarkPath = field.get(WatermarkHelper) as? String
        watermarkCacheField = field
        field.set(WatermarkHelper, transparentWatermark.absolutePath)
    }

    private fun captureBaselineDiagnostics(ffmpegUri: Uri, media3Uri: Uri) {
        val source = File(artifactDirectory, "baseline-source.mp4")
        landscapeVideo.copyTo(source, overwrite = true)
        val sourceProbe = FFprobeKit.executeWithArguments(
            arrayOf(
                "-v", "error",
                "-show_streams",
                "-show_format",
                "-of", "json",
                source.absolutePath
            )
        )
        check(ReturnCode.isSuccess(sourceProbe.returnCode)) {
            "ffprobe failed for baseline source: ${sourceProbe.allLogsAsString}"
        }
        File(artifactDirectory, "baseline-source.ffprobe.json")
            .writeText(sourceProbe.allLogsAsString)

        listOf("ffmpeg" to ffmpegUri, "media3" to media3Uri).forEach { (name, uri) ->
            val output = File(artifactDirectory, "baseline-$name.mp4")
            openInput(uri).use { input ->
                FileOutputStream(output).use { destination -> input.copyTo(destination) }
            }

            val probe = FFprobeKit.executeWithArguments(
                arrayOf(
                    "-v", "error",
                    "-show_streams",
                    "-show_format",
                    "-of", "json",
                    output.absolutePath
                )
            )
            check(ReturnCode.isSuccess(probe.returnCode)) {
                "ffprobe failed for $name baseline: ${probe.allLogsAsString}"
            }
            File(artifactDirectory, "baseline-$name.ffprobe.json")
                .writeText(probe.allLogsAsString)

            val extractor = MediaExtractor()
            val trackFormats = try {
                extractor.setDataSource(output.absolutePath)
                (0 until extractor.trackCount)
                    .joinToString("\n") { index ->
                        "track[$index]=${extractor.getTrackFormat(index)}"
                    }
            } finally {
                extractor.release()
            }
            File(artifactDirectory, "baseline-$name.mediaformat.txt")
                .writeText(trackFormats)

            val frame = File(artifactDirectory, "baseline-$name-frame.png")
            val frameSession = FFmpegKit.executeWithArguments(
                arrayOf(
                    "-v", "error",
                    "-i", output.absolutePath,
                    "-map", "0:v:0",
                    "-frames:v", "1",
                    "-vsync", "0",
                    "-y", frame.absolutePath
                )
            )
            check(ReturnCode.isSuccess(frameSession.returnCode)) {
                "Could not extract first frame from $name baseline: " +
                        frameSession.allLogsAsString
            }

            val rawFrame = File(artifactDirectory, "baseline-$name-frame.yuv")
            val rawSession = FFmpegKit.executeWithArguments(
                arrayOf(
                    "-v", "error",
                    "-i", output.absolutePath,
                    "-map", "0:v:0",
                    "-frames:v", "1",
                    "-pix_fmt", "yuv420p",
                    "-f", "rawvideo",
                    "-y", rawFrame.absolutePath
                )
            )
            check(ReturnCode.isSuccess(rawSession.returnCode)) {
                "Could not extract YUV frame from $name baseline: " +
                        rawSession.allLogsAsString
            }
        }
    }

    private fun openInput(uri: Uri) =
        if (uri.scheme == "file") {
            File(uri.path ?: error("Missing output path for $uri")).inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
                ?: error("Could not open exported output $uri")
        }

    private fun exportWithVideoExporter(
        clips: List<EditorClip>,
        name: String,
        debugMedia3: Boolean
    ): Uri = runBlocking {
        SettingsRepository(context).setDebugMedia3Export(debugMedia3)
        val result = CompletableDeferred<Uri>()
        val exporter = VideoExporter(
            context = context,
            onProgress = {},
            onSuccess = { result.complete(it) },
            onError = { result.completeExceptionally(IllegalStateException(it)) },
            onCancelled = {
                result.cancel(CancellationException("Parity export cancelled"))
            }
        )
        exporter.export(
            clips = clips,
            fileName = "parity-$name-${System.nanoTime()}",
            resolution = "360p",
            fps = FPS,
            bitrateKbps = requestedBitrateKbps,
            format = "mp4",
            outputWidth = WIDTH,
            outputHeight = HEIGHT
        )
        withTimeout(EXPORT_TIMEOUT_MS) { result.await() }
    }

    private fun saveProjectFile(name: String, clips: List<EditorClip>): File {
        val now = System.currentTimeMillis()
        val projectId = "media3-parity-$name-$now"
        val durationMs = clips.maxOf(EditorClip::timelineEndMs)
        val saved = ProjectRepository.saveProject(
            context,
            ProjectMeta(
                id = projectId,
                name = "Media3 parity $name",
                createdAt = now,
                updatedAt = now,
                thumbnailPath = null,
                clipCount = clips.size,
                durationMs = durationMs
            ),
            EditorState(clips = clips)
        )
        check(saved) { "Could not save parity project file for $name" }
        savedProjectIds += projectId
        val projectFile = File(context.filesDir, "projects/$projectId.json")
        check(projectFile.isFile) { "Saved project file is missing: ${projectFile.name}" }
        projectFile.copyTo(File(artifactDirectory, "$name.project.json"), overwrite = true)
        return projectFile
    }

    private fun videoClip(file: File, trackIndex: Int = 0) = EditorClip(
        uri = Uri.fromFile(file),
        name = file.name,
        type = "video/mp4",
        sourceStartMs = 0L,
        sourceEndMs = DURATION_MS,
        timelineStartMs = 0L,
        trackIndex = trackIndex
    )

    private fun colorPatchSource(): String {
        val patchWidth = WIDTH / COLOR_PATCHES.size
        val patchDrawBoxes = COLOR_PATCHES.joinToString(",") { patch ->
            "drawbox=x=${patch.index * patchWidth}:y=0:w=$patchWidth:h=$HEIGHT:" +
                    "color=${patch.ffmpegColor}:t=fill"
        }
        return "color=c=black:s=${WIDTH}x${HEIGHT}:r=$FPS:d=${DURATION_MS / 1000}," +
                patchDrawBoxes
    }

    private fun textClip(trackIndex: Int, state: TextState) = EditorClip(
        uri = Uri.EMPTY,
        name = "parity-text",
        type = "text/plain",
        sourceStartMs = 0L,
        sourceEndMs = DURATION_MS,
        timelineStartMs = 0L,
        trackIndex = trackIndex,
        textState = state
    )

    private fun createVideo(
        name: String,
        source: String,
        includeBt709Metadata: Boolean = explicitBt709Source
    ): File {
        val output = File(testDirectory, name)
        val session = FFmpegKit.executeWithArguments(
            arrayOf(
                "-v", "error",
                "-f", "lavfi",
                "-i", source,
                "-t", (DURATION_MS / 1000).toString(),
                "-vf",
                "select=eq(n\\,0),tpad=stop_mode=clone:" +
                        "stop_duration=${DURATION_MS / 1000},setpts=N/($FPS*TB)",
                "-an",
                "-c:v", "mpeg4",
                "-q:v", "1",
                "-pix_fmt", "yuv420p",
            ).toMutableList().apply {
                if (includeBt709Metadata) {
                    addAll(
                        listOf(
                            "-color_range", "tv",
                            "-colorspace", "bt709",
                            "-color_primaries", "bt709",
                            "-color_trc", "bt709"
                        )
                    )
                }
                addAll(listOf("-y", output.absolutePath))
            }.toTypedArray()
        )
        check(ReturnCode.isSuccess(session.returnCode)) {
            "Could not create $name: ${session.allLogsAsString}"
        }
        return output
    }

    private fun frameAt(uri: Uri, timeMs: Long): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(
                timeMs * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: error("No frame available at ${timeMs}ms for $uri")
            return cropToSize(frame, WIDTH, HEIGHT).also {
                if (it !== frame) frame.recycle()
            }
        } finally {
            retriever.release()
        }
    }

    private fun cropToSize(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        require(width > 0 && height > 0)
        require(bitmap.width >= width && bitmap.height >= height) {
            "Cannot crop ${bitmap.width}x${bitmap.height} to ${width}x$height"
        }
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    private fun calculateChannelMae(first: Bitmap, second: Bitmap): ChannelMae {
        check(first.width == second.width && first.height == second.height)
        val firstPixels = IntArray(first.width * first.height)
        val secondPixels = IntArray(second.width * second.height)
        first.getPixels(firstPixels, 0, first.width, 0, 0, first.width, first.height)
        second.getPixels(secondPixels, 0, second.width, 0, 0, second.width, second.height)
        var redError = 0L
        var greenError = 0L
        var blueError = 0L
        for (index in firstPixels.indices) {
            redError += abs(Color.red(firstPixels[index]) - Color.red(secondPixels[index]))
            greenError += abs(Color.green(firstPixels[index]) - Color.green(secondPixels[index]))
            blueError += abs(Color.blue(firstPixels[index]) - Color.blue(secondPixels[index]))
        }
        val count = firstPixels.size.toDouble()
        return ChannelMae(
            red = redError / count,
            green = greenError / count,
            blue = blueError / count
        )
    }

    private fun decodeYuvFrame(uri: Uri, name: String): ByteArray {
        val input = File(testDirectory, "$name-input.mp4")
        openInput(uri).use { stream ->
            FileOutputStream(input).use { output -> stream.copyTo(output) }
        }
        val output = File(testDirectory, "$name-frame.yuv")
        val session = FFmpegKit.executeWithArguments(
            arrayOf(
                "-v", "error",
                "-i", input.absolutePath,
                "-ss", "1",
                "-map", "0:v:0",
                "-vf", "crop=$WIDTH:$HEIGHT:0:0",
                "-frames:v", "1",
                "-pix_fmt", "yuv420p",
                "-f", "rawvideo",
                "-y", output.absolutePath
            )
        )
        check(ReturnCode.isSuccess(session.returnCode)) {
            "Could not decode YUV frame from $uri: ${session.allLogsAsString}"
        }
        val expectedBytes = WIDTH * HEIGHT * 3 / 2
        val bytes = output.readBytes()
        check(bytes.size == expectedBytes) {
            "Unexpected YUV frame size ${bytes.size}; expected $expectedBytes; " +
                    "returnCode=${session.returnCode}; logs=${session.allLogsAsString}"
        }
        return bytes
    }

    private fun calculateYuvPlanePsnr(first: ByteArray, second: ByteArray): YuvPlanePsnr {
        val ySize = WIDTH * HEIGHT
        val chromaSize = ySize / 4
        check(first.size == ySize + chromaSize * 2 && second.size == first.size)
        return YuvPlanePsnr(
            y = planePsnr(first, second, 0, ySize),
            u = planePsnr(first, second, ySize, chromaSize),
            v = planePsnr(first, second, ySize + chromaSize, chromaSize)
        )
    }

    private fun planePsnr(
        first: ByteArray,
        second: ByteArray,
        offset: Int,
        count: Int
    ): Double {
        var squaredError = 0.0
        for (index in offset until offset + count) {
            val difference = (first[index].toInt() and 0xFF) -
                    (second[index].toInt() and 0xFF)
            squaredError += difference * difference
        }
        val mse = squaredError / count
        return if (mse == 0.0) Double.POSITIVE_INFINITY
        else 10.0 * log10(255.0 * 255.0 / mse)
    }

    private fun classifyColorDifference(
        channelMae: ChannelMae,
        samples: List<ColorSample>
    ): String {
        val neutralColors = samples.filter { it.name in setOf("gray", "white", "black") }
        val saturatedColors = samples.filter { it.name in setOf("red", "green", "blue") }
        val neutralMeanDelta = neutralColors
            .flatMap { sample -> sample.channelDeltas() }
            .map(::abs)
            .average()
        val saturatedMeanDelta = saturatedColors
            .flatMap { sample -> sample.channelDeltas() }
            .map(::abs)
            .average()
        val channelMaeMean = (channelMae.red + channelMae.green + channelMae.blue) / 3.0
        return when {
            neutralMeanDelta < 3.0 && saturatedMeanDelta > neutralMeanDelta * 2.0 ->
                "matrix-like: neutrals are closer than saturated patches; verify metadata and YUV planes"
            samples.flatMap { it.channelDeltas() }.map(::abs).average() > 8.0 &&
                    channelMaeMean > 8.0 ->
                "range-or-transfer-like: broad patch and channel offsets; verify range tags before changing conversion"
            else ->
                "small-or-encoder-like: this comparison does not isolate a matrix or range defect"
        }
    }

    private fun ColorSample.channelDeltas(): List<Int> = listOf(
        Color.red(media3Rgb) - Color.red(ffmpegRgb),
        Color.green(media3Rgb) - Color.green(ffmpegRgb),
        Color.blue(media3Rgb) - Color.blue(ffmpegRgb)
    )

    private fun outputSize(uri: Uri): Long = if (uri.scheme == "file") {
        File(uri.path ?: error("Missing output path for $uri")).length()
    } else {
        openInput(uri).use { stream ->
            var size = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                size += read
            }
            size
        }
    }

    private fun colorTagSummary(uri: Uri, name: String): String {
        val input = File(testDirectory, "$name-color-tags.mp4")
        openInput(uri).use { stream ->
            FileOutputStream(input).use { output -> stream.copyTo(output) }
        }
        val probe = FFprobeKit.executeWithArguments(
            arrayOf(
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries",
                "stream=color_range,color_space,color_transfer,color_primaries",
                "-of", "json",
                input.absolutePath
            )
        )
        check(ReturnCode.isSuccess(probe.returnCode)) {
            "Could not read color tags for $name output: ${probe.allLogsAsString}"
        }
        val probeOutput = probe.output
            ?: error("No color-tag output for $name: ${probe.allLogsAsString}")
        val jsonOutput = probeOutput.lineSequence()
            .filterNot { line ->
                val trimmed = line.trimStart()
                trimmed.startsWith("[") && " @ " in trimmed
            }
            .joinToString("\n")
        val jsonStart = jsonOutput.indexOf('{')
        val jsonEnd = jsonOutput.lastIndexOf('}')
        check(jsonStart >= 0 && jsonEnd > jsonStart) {
            "Could not parse color tags for $name: $jsonOutput"
        }
        return JSONObject(jsonOutput.substring(jsonStart, jsonEnd + 1))
            .optJSONArray("streams")
            ?.optJSONObject(0)
            ?.let { stream ->
                listOf("color_range", "color_space", "color_transfer", "color_primaries")
                    .joinToString(",") { key -> "$key=${stream.optString(key, "unknown")}" }
            }
            ?: "unavailable"
    }

    private fun inCenterFraction(x: Int, y: Int, fraction: Float): Boolean {
        val insetX = (1f - fraction) * WIDTH / 2f
        val insetY = (1f - fraction) * HEIGHT / 2f
        return x + 0.5f >= insetX && x + 0.5f < WIDTH - insetX &&
                y + 0.5f >= insetY && y + 0.5f < HEIGHT - insetY
    }

    private fun inPortraitFitRegion(x: Int, y: Int): Boolean {
        val fitScale = min(WIDTH / 720f, HEIGHT / 1280f)
        val fittedWidth = 720f * fitScale
        val fittedHeight = 1280f * fitScale
        val left = (WIDTH - fittedWidth) / 2f
        val top = (HEIGHT - fittedHeight) / 2f
        return x + 0.5f >= left && x + 0.5f < left + fittedWidth &&
                y + 0.5f >= top && y + 0.5f < top + fittedHeight
    }

    private fun rgbHex(color: Int): String =
        String.format(
            java.util.Locale.US,
            "#%02X%02X%02X",
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )

    private fun assertVideoOutput(
        uri: Uri,
        expectedDurationMs: Long,
        allowEncoderAlignmentPadding: Boolean = false
    ) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val width = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toInt()
            val height = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toInt()
            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLong()
            val maxAlignedHeight = ((HEIGHT + 15) / 16) * 16
            val heightMatches = height == HEIGHT ||
                    (allowEncoderAlignmentPadding && height != null &&
                            height in HEIGHT..maxAlignedHeight)
            Log.i(
                TAG,
                "OUTPUT_DIMENSIONS width=$width height=$height " +
                        "encoderPaddingAllowed=$allowEncoderAlignmentPadding"
            )
            assertTrue(
                "Unexpected output dimensions ${width}x$height",
                width == WIDTH && heightMatches
            )
            assertTrue(
                "Unexpected output duration $duration (expected $expectedDurationMs)",
                duration != null && abs(duration - expectedDurationMs) <= 500L
            )
        } finally {
            retriever.release()
        }
    }

    private fun compareFrames(
        referenceUri: Uri,
        candidateUri: Uri,
        caseName: String,
        durationMs: Long,
        sampleTimesMs: List<Long>? = null,
        validRegion: ((Int, Int) -> Boolean)? = null,
        expectedTransform: ExpectedTransform? = null
    ): Metrics {
        val reference = MediaMetadataRetriever()
        val candidate = MediaMetadataRetriever()
        val comparisonTimesMs = sampleTimesMs ?: listOf(
            250L,
            durationMs / 3,
            durationMs * 2 / 3,
            (durationMs - 250L).coerceAtLeast(250L)
        ).distinct()
        val sheet = Bitmap.createBitmap(
            COMPARISON_PANEL_WIDTH * 3,
            comparisonTimesMs.size * (COMPARISON_PANEL_HEIGHT + LABEL_HEIGHT),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(sheet)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16f
        }
        var totalSquaredError = 0.0
        var totalSamples = 0L
        var ssimTotal = 0.0
        var totalValidPixels = 0L

        try {
            reference.setDataSource(context, referenceUri)
            candidate.setDataSource(context, candidateUri)
            comparisonTimesMs.forEachIndexed { frameIndex, timeMs ->
                val referenceFrame = reference.getFrameAtTime(
                    timeMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: error("No FFmpeg frame at ${timeMs}ms for $caseName")
                val candidateFrame = candidate.getFrameAtTime(
                    timeMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: error("No Media3 frame at ${timeMs}ms for $caseName")
                val croppedCandidateFrame = cropToSize(candidateFrame, WIDTH, HEIGHT)
                try {
                    check(referenceFrame.width == WIDTH && referenceFrame.height == HEIGHT) {
                        "Unexpected FFmpeg frame dimensions " +
                                "${referenceFrame.width}x${referenceFrame.height} in $caseName"
                    }
                    val referencePixels = IntArray(WIDTH * HEIGHT)
                    val candidatePixels = IntArray(WIDTH * HEIGHT)
                    referenceFrame.getPixels(
                        referencePixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT
                    )
                    croppedCandidateFrame.getPixels(
                        candidatePixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT
                    )
                    val validPixels = BooleanArray(referencePixels.size)
                    for (index in referencePixels.indices) {
                        val x = index % WIDTH
                        val y = index / WIDTH
                        val first = referencePixels[index]
                        val second = candidatePixels[index]
                        val alphaValid = Color.alpha(first) / 255f >= ALPHA_VALID_THRESHOLD &&
                                Color.alpha(second) / 255f >= ALPHA_VALID_THRESHOLD
                        val regionValid = validRegion?.invoke(x, y) ?: true
                        val transformValid =
                            expectedTransform?.isValidAt(x, y, WIDTH, HEIGHT) ?: true
                        if (!alphaValid || !regionValid || !transformValid) continue
                        validPixels[index] = true
                        val redDiff = ((first shr 16) and 0xFF) - ((second shr 16) and 0xFF)
                        val greenDiff = ((first shr 8) and 0xFF) - ((second shr 8) and 0xFF)
                        val blueDiff = (first and 0xFF) - (second and 0xFF)
                        totalSquaredError +=
                                redDiff * redDiff + greenDiff * greenDiff + blueDiff * blueDiff
                        totalSamples += 3
                        totalValidPixels++
                    }
                    check(totalValidPixels > 0) { "No valid comparison pixels for $caseName" }
                    ssimTotal += calculateLumaSsim(
                        referencePixels,
                        candidatePixels,
                        WIDTH,
                        HEIGHT,
                        validPixels
                    )
                    drawComparisonRow(
                        canvas,
                        labelPaint,
                        referenceFrame,
                        croppedCandidateFrame,
                        frameIndex,
                        timeMs
                    )
                } finally {
                    referenceFrame.recycle()
                    candidateFrame.recycle()
                    if (croppedCandidateFrame !== candidateFrame) {
                        croppedCandidateFrame.recycle()
                    }
                }
            }
        } finally {
            reference.release()
            candidate.release()
        }

        val mse = totalSquaredError / totalSamples
        val psnr = if (mse == 0.0) {
            Double.POSITIVE_INFINITY
        } else {
            10.0 * log10(255.0 * 255.0 / mse)
        }
        val ssim = ssimTotal / comparisonTimesMs.size
        val metrics = Metrics(psnrDb = psnr, ssim = ssim, validPixels = totalValidPixels)
        saveComparisonSheet(caseName, sheet)
        sheet.recycle()
        return metrics
    }

    private fun drawComparisonRow(
        canvas: Canvas,
        labelPaint: Paint,
        referenceFrame: Bitmap,
        candidateFrame: Bitmap,
        rowIndex: Int,
        timeMs: Long
    ) {
        val rowTop = rowIndex * (COMPARISON_PANEL_HEIGHT + LABEL_HEIGHT)
        val imageTop = rowTop + LABEL_HEIGHT
        val referenceThumb = Bitmap.createScaledBitmap(
            referenceFrame,
            COMPARISON_PANEL_WIDTH,
            COMPARISON_PANEL_HEIGHT,
            true
        )
        val candidateThumb = Bitmap.createScaledBitmap(
            candidateFrame,
            COMPARISON_PANEL_WIDTH,
            COMPARISON_PANEL_HEIGHT,
            true
        )
        val referenceThumbPixels = IntArray(COMPARISON_PANEL_WIDTH * COMPARISON_PANEL_HEIGHT)
        val candidateThumbPixels = IntArray(referenceThumbPixels.size)
        referenceThumb.getPixels(
            referenceThumbPixels,
            0,
            COMPARISON_PANEL_WIDTH,
            0,
            0,
            COMPARISON_PANEL_WIDTH,
            COMPARISON_PANEL_HEIGHT
        )
        candidateThumb.getPixels(
            candidateThumbPixels,
            0,
            COMPARISON_PANEL_WIDTH,
            0,
            0,
            COMPARISON_PANEL_WIDTH,
            COMPARISON_PANEL_HEIGHT
        )
        val differencePixels = IntArray(referenceThumbPixels.size) { index ->
            val reference = referenceThumbPixels[index]
            val candidate = candidateThumbPixels[index]
            Color.rgb(
                (abs(((reference shr 16) and 0xFF) - ((candidate shr 16) and 0xFF)) * 4)
                    .coerceAtMost(255),
                (abs(((reference shr 8) and 0xFF) - ((candidate shr 8) and 0xFF)) * 4)
                    .coerceAtMost(255),
                (abs((reference and 0xFF) - (candidate and 0xFF)) * 4).coerceAtMost(255)
            )
        }
        val difference = Bitmap.createBitmap(
            differencePixels,
            COMPARISON_PANEL_WIDTH,
            COMPARISON_PANEL_HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val rowBackground = Paint().apply { color = Color.BLACK }
        canvas.drawRect(
            0f,
            rowTop.toFloat(),
            (COMPARISON_PANEL_WIDTH * 3).toFloat(),
            (rowTop + COMPARISON_PANEL_HEIGHT + LABEL_HEIGHT).toFloat(),
            rowBackground
        )
        canvas.drawText("FFmpeg  ${timeMs}ms", 4f, rowTop + 17f, labelPaint)
        canvas.drawText("Media3", COMPARISON_PANEL_WIDTH + 4f, rowTop + 17f, labelPaint)
        canvas.drawText(
            "Diff x4",
            COMPARISON_PANEL_WIDTH * 2f + 4f,
            rowTop + 17f,
            labelPaint
        )
        canvas.drawBitmap(referenceThumb, 0f, imageTop.toFloat(), null)
        canvas.drawBitmap(
            candidateThumb,
            COMPARISON_PANEL_WIDTH.toFloat(),
            imageTop.toFloat(),
            null
        )
        canvas.drawBitmap(
            difference,
            COMPARISON_PANEL_WIDTH * 2f,
            imageTop.toFloat(),
            null
        )
        referenceThumb.recycle()
        candidateThumb.recycle()
        difference.recycle()
    }

    private fun calculateLumaSsim(
        firstPixels: IntArray,
        secondPixels: IntArray,
        width: Int,
        height: Int,
        validPixels: BooleanArray? = null
    ): Double {
        val c1 = (0.01 * 255.0) * (0.01 * 255.0)
        val c2 = (0.03 * 255.0) * (0.03 * 255.0)
        var totalSsim = 0.0
        var windowCount = 0
        val windowSize = 8

        for (top in 0 until height step windowSize) {
            for (left in 0 until width step windowSize) {
                val right = min(left + windowSize, width)
                val bottom = min(top + windowSize, height)
                var sumFirst = 0.0
                var sumSecond = 0.0
                var sumFirstSquared = 0.0
                var sumSecondSquared = 0.0
                var sumProduct = 0.0
                var validCount = 0

                for (y in top until bottom) {
                    for (x in left until right) {
                        val index = y * width + x
                        if (validPixels != null && !validPixels[index]) continue
                        val first = luma(firstPixels[index])
                        val second = luma(secondPixels[index])
                        sumFirst += first
                        sumSecond += second
                        sumFirstSquared += first * first
                        sumSecondSquared += second * second
                        sumProduct += first * second
                        validCount++
                    }
                }
                if (validCount == 0) continue

                val meanFirst = sumFirst / validCount
                val meanSecond = sumSecond / validCount
                val varianceFirst =
                    max(sumFirstSquared / validCount - meanFirst * meanFirst, 0.0)
                val varianceSecond =
                    max(sumSecondSquared / validCount - meanSecond * meanSecond, 0.0)
                val covariance = sumProduct / validCount - meanFirst * meanSecond
                totalSsim += (
                        (2.0 * meanFirst * meanSecond + c1) *
                                (2.0 * covariance + c2)
                        ) / (
                        (meanFirst * meanFirst + meanSecond * meanSecond + c1) *
                                (varianceFirst + varianceSecond + c2)
                        )
                windowCount++
            }
        }
        return if (windowCount == 0) 0.0 else totalSsim / windowCount
    }

    private fun luma(color: Int): Double =
        0.299 * ((color shr 16) and 0xFF) +
                0.587 * ((color shr 8) and 0xFF) +
                0.114 * (color and 0xFF)

    private fun saveMetrics(
        caseName: String,
        metrics: Metrics,
        baseline: Metrics,
        deltaDb: Double,
        frameCount: Int = 4
    ) {
        val csv = File(artifactDirectory, "metrics.csv")
        if (!csv.exists()) {
            csv.writeText("case,psnr_db,ssim,baseline_psnr_db,delta_db,frames,status\n")
        }
        val status = when {
            deltaDb >= RELATIVE_PASS_DELTA_DB -> "PASS"
            deltaDb < RELATIVE_BUG_DELTA_DB -> "BUG"
            else -> "BORDERLINE"
        }
        csv.appendText(
            "$caseName,${metrics.psnrDb},${metrics.ssim},${baseline.psnrDb}," +
                    "$deltaDb,$frameCount,$status\n"
        )
    }

    private fun saveComparisonSheet(caseName: String, sheet: Bitmap) {
        val output = File(artifactDirectory, "$caseName-comparison.png")
        FileOutputStream(output).use { stream ->
            check(sheet.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Could not save comparison sheet for $caseName"
            }
        }
    }

    private fun deleteOutput(uri: Uri) {
        if (uri.scheme == "file") {
            uri.path?.let { File(it).delete() }
        } else {
            context.contentResolver.delete(uri, null, null)
        }
    }

    private data class Metrics(
        val psnrDb: Double,
        val ssim: Double,
        val validPixels: Long = 0L
    )

    private data class ChannelMae(
        val red: Double,
        val green: Double,
        val blue: Double
    )

    private data class YuvPlanePsnr(
        val y: Double,
        val u: Double,
        val v: Double
    )

    private data class ColorPatch(
        val name: String,
        val index: Int,
        val ffmpegColor: String
    )

    private data class ColorSample(
        val name: String,
        val ffmpegRgb: Int,
        val media3Rgb: Int
    )

    private data class ExpectedTransform(
        val scale: Float = 1f,
        val rotationDeg: Float = 0f,
        val offsetX: Float = 0f,
        val cropL: Float = 0f
    ) {
        fun isValidAt(x: Int, y: Int, width: Int, height: Int): Boolean {
            val canvasX = x + 0.5f
            val canvasY = y + 0.5f
            if (canvasX < cropL * width || scale <= 0f) return false

            val centerX = width / 2f
            val centerY = height / 2f
            val radians = Math.toRadians(-rotationDeg.toDouble())
            val cosine = kotlin.math.cos(radians).toFloat()
            val sine = kotlin.math.sin(radians).toFloat()
            val centeredX = canvasX - centerX
            val centeredY = canvasY - centerY
            val unrotatedX = centeredX * cosine - centeredY * sine + centerX
            val unrotatedY = centeredX * sine + centeredY * cosine + centerY
            val sourceX = (unrotatedX - offsetX * width - centerX) / scale + centerX
            val sourceY = (unrotatedY - centerY) / scale + centerY
            return sourceX >= 0f && sourceX < width &&
                    sourceY >= 0f && sourceY < height
        }
    }

    private companion object {
        private var relativeBaseline: Metrics? = null
        private val COLOR_PATCHES = listOf(
            ColorPatch("red", 0, "0xFF0000"),
            ColorPatch("green", 1, "0x00FF00"),
            ColorPatch("blue", 2, "0x0000FF"),
            ColorPatch("gray", 3, "0x808080"),
            ColorPatch("white", 4, "white"),
            ColorPatch("black", 5, "black")
        )

        @BeforeClass
        @JvmStatic
        fun resetRelativeBaseline() {
            relativeBaseline = null
        }

        const val TAG = "MEDIA3_PARITY"
        const val WIDTH = 1920
        const val HEIGHT = 1080
        const val FPS = 30
        const val DURATION_MS = 4_000L
        const val BITRATE_KBPS = 4_000
        const val NEAR_LOSSLESS_BITRATE_KBPS = 100_000
        const val ALPHA_VALID_THRESHOLD = 0.9f
        const val RELATIVE_PASS_DELTA_DB = -2.0
        const val RELATIVE_BUG_DELTA_DB = -5.0
        const val EXPORT_TIMEOUT_MS = 300_000L
        const val LABEL_HEIGHT = 22
        const val COMPARISON_PANEL_WIDTH = 640
        const val COMPARISON_PANEL_HEIGHT = 360
    }
}
