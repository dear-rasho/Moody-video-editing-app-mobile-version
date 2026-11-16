package com.moody.moodyvideoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.EditorState
import com.moody.moodyvideoeditor.data.FilterState
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.MaskState
import com.moody.moodyvideoeditor.data.MaskType
import com.moody.moodyvideoeditor.data.ProjectRepository
import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.data.settings.SettingsRepository
import com.moody.moodyvideoeditor.utils.MaskEngine
import com.moody.moodyvideoeditor.utils.Media3CompositionBuilder
import com.moody.moodyvideoeditor.utils.VideoExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.cos

@RunWith(AndroidJUnit4::class)
class Media3ExportRuntimeTest {
    private lateinit var context: Context
    private lateinit var testDirectory: File
    private lateinit var redVideo: File
    private lateinit var blueVideo: File
    private lateinit var audioOnly: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testDirectory = File(context.cacheDir, "media3-export-runtime").apply {
            if (exists()) deleteRecursively()
            check(mkdirs()) { "Could not create runtime test directory" }
        }
        redVideo = createVideo("red-with-audio.mp4", "red", 4, includeAudio = true)
        blueVideo = createVideo("blue.mp4", "blue", 4, includeAudio = false)
        audioOnly = createAudio("external-tone.m4a", 4)
        runBlocking {
            SettingsRepository(context).setDebugMedia3Export(false)
        }
    }

    @Test
    fun fiveProgrammaticProjectsExportWithDeviceMedia() {
        val deviceVideo = copyStagedMedia("device-video.mp4")
        val deviceImage = copyStagedMedia("device-image.jpg")
        val clipDurationMs = 5_000L
        val reportFile = File(context.filesDir, "media3-generated-project-results.tsv")
        reportFile.writeText(
            "project\texpected_media3_route\tdebug_on_ms\tffmpeg_ms\tspeedup\t" +
                    "media3_dims\tffmpeg_dims\tmedia3_bytes\tffmpeg_bytes\n"
        )

        val baseVideo = videoClip(deviceVideo, clipDurationMs)
        val textClip = EditorClip(
            uri = Uri.EMPTY,
            name = "Test",
            type = "text/plain",
            sourceStartMs = 0L,
            sourceEndMs = clipDurationMs,
            timelineStartMs = 0L,
            trackIndex = 1,
            textState = TextState(content = "Test")
        )
        val imageClip = EditorClip(
            uri = Uri.fromFile(deviceImage),
            name = "device-image",
            type = "image/jpeg",
            sourceStartMs = 0L,
            sourceEndMs = clipDurationMs,
            timelineStartMs = 0L,
            scale = 0.25f,
            trackIndex = 1
        )
        val projects = listOf(
            GeneratedProject("Simple", EditorState(clips = listOf(baseVideo)), "Media3"),
            GeneratedProject(
                "Filter",
                EditorState(
                    clips = listOf(
                        baseVideo.copy(
                            filters = FilterState(
                                brightness = 110f,
                                contrast = 105f,
                                saturation = 115f
                            )
                        )
                    )
                ),
                "Media3"
            ),
            GeneratedProject(
                "Text overlay",
                EditorState(clips = listOf(baseVideo, textClip)),
                "FFmpeg fallback"
            ),
            GeneratedProject(
                "Mask",
                EditorState(
                    clips = listOf(
                        baseVideo.copy(
                            mask = MaskState(
                                type = MaskType.CIRCLE,
                                radius = 0.3f
                            )
                        )
                    )
                ),
                "Media3"
            ),
            GeneratedProject(
                "Multi-track",
                EditorState(clips = listOf(baseVideo, imageClip.copy(trackIndex = 1), textClip.copy(trackIndex = 2))),
                "FFmpeg fallback"
            )
        )

        projects.forEachIndexed { index, project ->
            val clips = project.state.clips
            val media3Output = export(
                clips = clips,
                debugMedia3 = true,
                name = "device-${index + 1}-media3",
                outputWidth = 1920,
                outputHeight = 1080,
                timeoutMs = 900_000L
            )
            val media3ElapsedMs = lastExportElapsedMs
            val media3Bytes: Long
            val ffmpegOutput: Uri
            val ffmpegElapsedMs: Long
            try {
                assertVideoOutput(media3Output, clipDurationMs, 1920, 1080)
                assertPlayableAndNotGreen(media3Output, clipDurationMs)
                media3Bytes = sizeInBytes(media3Output)
            } finally {
                deleteOutput(media3Output)
            }

            val ffmpegStartedAt = android.os.SystemClock.elapsedRealtime()
            ffmpegOutput = export(
                clips = clips,
                debugMedia3 = false,
                name = "device-${index + 1}-ffmpeg",
                outputWidth = 1920,
                outputHeight = 1080,
                timeoutMs = 900_000L
            )
            ffmpegElapsedMs = android.os.SystemClock.elapsedRealtime() - ffmpegStartedAt
            try {
                assertVideoOutput(ffmpegOutput, clipDurationMs, 1920, 1080)
                assertPlayableAndNotGreen(ffmpegOutput, clipDurationMs)
                val ffmpegBytes = sizeInBytes(ffmpegOutput)
                val speedup = if (project.expectedMedia3Route == "Media3") {
                    ffmpegElapsedMs / media3ElapsedMs.toDouble()
                } else {
                    Double.NaN
                }
                val resultLine = listOf(
                    project.name,
                    project.expectedMedia3Route,
                    media3ElapsedMs,
                    ffmpegElapsedMs,
                    speedup,
                    "1920x1080",
                    "1920x1080",
                    media3Bytes,
                    ffmpegBytes
                ).joinToString("\t")
                reportFile.appendText("$resultLine\n")
                Log.i(
                    TAG,
                    "DEVICE_PROJECT name=${project.name} expectedMedia3Route=" +
                            "${project.expectedMedia3Route} media3RouteElapsedMs=$media3ElapsedMs " +
                            "ffmpegElapsedMs=$ffmpegElapsedMs speedup=$speedup " +
                            "media3Dims=1920x1080 ffmpegDims=1920x1080 " +
                            "durationMs=$clipDurationMs"
                )
            } finally {
                deleteOutput(ffmpegOutput)
            }
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            SettingsRepository(context).setDebugMedia3Export(true)
        }
        testDirectory.deleteRecursively()
    }

    @Test
    fun basicSingleClipUsesDefaultFfmpegRoute() {
        val output = export(
            clips = listOf(videoClip(redVideo, durationMs = 4_000L)),
            debugMedia3 = false,
            name = "basic"
        )
        try {
            assertVideoOutput(output, expectedDurationMs = 4_000L)
            Log.i(TAG, "case=basic-single-clip passed")
        } finally {
            deleteOutput(output)
        }
    }

    @Test
    fun colorEffectsExportThroughMedia3Composition() {
        val clip = videoClip(redVideo, durationMs = 4_000L).copy(
            adjustments = AdjustmentData(brightness = 35f, saturation = -40f)
        )
        val output = export(listOf(clip), debugMedia3 = true, name = "color-effects")
        try {
            assertVideoOutput(output, expectedDurationMs = 4_000L)
            Log.i(TAG, "case=color-effects passed")
        } finally {
            deleteOutput(output)
        }
    }

    @Test
    fun media3CompositionPreservesUnalignedRequestedOutputHeight() {
        val output = export(
            clips = listOf(videoClip(redVideo, durationMs = 4_000L)),
            debugMedia3 = true,
            name = "unaligned-height",
            outputWidth = 1920,
            outputHeight = 1080
        )
        try {
            assertVideoOutput(
                output,
                expectedDurationMs = 4_000L,
                expectedWidth = 1920,
                expectedHeight = 1080
            )
            assertTrue("Dimension crop should preserve the embedded audio track", hasAudioTrack(output))
        } finally {
            deleteOutput(output)
        }
    }

    @Test
    fun staticTransformsRemainEligibleButRotationKeyframesAndTextFallBack() {
        val builder = Media3CompositionBuilder(context)
        val clip = videoClip(redVideo, durationMs = 4_000L)
        fun build(candidate: EditorClip) = builder.buildComposition(
            clips = listOf(candidate),
            textSequences = emptyList(),
            maskSequences = emptyList(),
            outputWidth = 320,
            outputHeight = 240,
            fps = 30
        )

        assertTrue(
            "Static scale, crop, offset, and opacity should remain Media3-eligible",
            build(
                clip.copy(
                    scale = 1.5f,
                    cropL = 0.1f,
                    offsetX = 0.1f,
                    layerOpacity = 80f
                )
            ) != null
        )
        assertTrue("Rotation should be rejected", build(clip.copy(rotation = 90f)) == null)
        assertTrue(
            "Animated transforms should be rejected",
            build(clip.copy(keyframes = mapOf("scale" to listOf(Keyframe(0f, 100f))))) == null
        )
        assertTrue("Text clips should be rejected", build(clip.copy(type = "text/plain")) == null)
    }

    @Test
    fun savedProjectsExportAt1080pAndReportRouteTiming() {
        val projects = ProjectRepository.listProjects(context).take(5)
        Log.i(TAG, "savedProjectSmokeCount=${projects.size}")
        assumeTrue("No saved projects are available for real-project smoke tests", projects.isNotEmpty())

        projects.forEachIndexed { index, project ->
            val clips = ProjectRepository.loadProject(context, project.id)?.clips
                ?: error("Could not load saved project ${index + 1}")
            check(clips.isNotEmpty()) { "Saved project ${index + 1} has no clips" }
            val durationMs = clips.maxOf(EditorClip::timelineEndMs)
            var ffmpegOutput: Uri? = null
            var media3Output: Uri? = null
            try {
                val ffmpegStartedAt = android.os.SystemClock.elapsedRealtime()
                ffmpegOutput = export(
                    clips = clips,
                    debugMedia3 = false,
                    name = "saved-${index + 1}-ffmpeg",
                    outputWidth = 1920,
                    outputHeight = 1080,
                    timeoutMs = 900_000L
                )
                val ffmpegElapsedMs = android.os.SystemClock.elapsedRealtime() - ffmpegStartedAt
                assertVideoOutput(
                    ffmpegOutput,
                    durationMs,
                    expectedWidth = 1920,
                    expectedHeight = 1080
                )

                val media3StartedAt = android.os.SystemClock.elapsedRealtime()
                media3Output = export(
                    clips = clips,
                    debugMedia3 = true,
                    name = "saved-${index + 1}-media3",
                    outputWidth = 1920,
                    outputHeight = 1080,
                    timeoutMs = 900_000L
                )
                val media3ElapsedMs = android.os.SystemClock.elapsedRealtime() - media3StartedAt
                assertVideoOutput(
                    media3Output,
                    durationMs,
                    expectedWidth = 1920,
                    expectedHeight = 1080
                )
                val frame = frameAt(media3Output, durationMs * 500L)
                try {
                    Log.i(
                        TAG,
                        "savedProject index=${index + 1} clipCount=${clips.size} " +
                                "durationMs=$durationMs ffmpegElapsedMs=$ffmpegElapsedMs " +
                                "media3RouteElapsedMs=$media3ElapsedMs " +
                                "relativeSpeedup=${ffmpegElapsedMs / media3ElapsedMs.toDouble()} " +
                                "frame=${frame.width}x${frame.height}"
                    )
                } finally {
                    frame.recycle()
                }
            } finally {
                ffmpegOutput?.let(::deleteOutput)
                media3Output?.let(::deleteOutput)
            }
        }
    }

    @Test
    fun higherVisualTrackCompositesAboveTrackZero() {
        val output = export(
            clips = listOf(
                videoClip(redVideo, durationMs = 4_000L, trackIndex = 0),
                videoClip(blueVideo, durationMs = 4_000L, trackIndex = 1)
            ),
            debugMedia3 = true,
            name = "multi-track"
        )
        try {
            assertVideoOutput(output, expectedDurationMs = 4_000L)
            val bitmap = frameAt(output, 2_000_000L)
            try {
                val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                assertTrue("Track 1 should be topmost; center pixel=$pixel", blue(pixel) > red(pixel))
            } finally {
                bitmap.recycle()
            }
            Log.i(TAG, "case=multi-track-z-order passed")
        } finally {
            deleteOutput(output)
        }
    }

    @Test
    fun maskEffectUsesCompositionTimelineForSecondClip() {
        val unmasked = videoClip(redVideo, durationMs = 1_000L, trackIndex = 0)
        val masked = videoClip(
            redVideo,
            durationMs = 2_000L,
            timelineStartMs = 1_000L,
            trackIndex = 0
        ).copy(mask = MaskState(type = MaskType.CIRCLE, radius = 0.28f))
        val maskBitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        try {
            maskBitmap.eraseColor(Color.WHITE)
            MaskEngine.drawMask(Canvas(maskBitmap), masked.mask, 320f, 240f)
            Log.i(
                TAG,
                "sourceMaskPixels outside=" +
                        Integer.toHexString(maskBitmap.getPixel(8, 8)) +
                        " inside=" +
                        Integer.toHexString(maskBitmap.getPixel(160, 120))
            )
        } finally {
            maskBitmap.recycle()
        }
        val output = export(listOf(unmasked, masked), debugMedia3 = true, name = "mask-timebase")
        try {
            assertVideoOutput(output, expectedDurationMs = 3_000L)
            val beforeMask = frameAt(output, 500_000L)
            val duringMask = frameAt(output, 1_500_000L)
            try {
                val beforeCorner = beforeMask.getPixel(8, 8)
                val duringCorner = duringMask.getPixel(8, 8)
                val duringCenter = duringMask.getPixel(
                    duringMask.width / 2,
                    duringMask.height / 2
                )
                Log.i(
                    TAG,
                    "maskPixels before=${Integer.toHexString(beforeCorner)} " +
                            "outside=${Integer.toHexString(duringCorner)} " +
                            "inside=${Integer.toHexString(duringCenter)}"
                )
                assertTrue("Mask must not affect the first clip", red(beforeCorner) > 100)
                assertTrue(
                    "Mask should clear pixels outside the circle; " +
                            "outside=${Integer.toHexString(duringCorner)}, " +
                            "inside=${Integer.toHexString(duringCenter)}",
                    red(duringCorner) < 100
                )
                assertTrue("Mask should retain pixels inside the circle", red(duringCenter) > 100)
            } finally {
                beforeMask.recycle()
                duringMask.recycle()
            }
            Log.i(TAG, "case=mask-timestamp passed")
        } finally {
            deleteOutput(output)
        }
    }

    @Test
    fun embeddedAndExternalAudioArePresentInCompositionOutput() {
        val audioClip = EditorClip(
            uri = Uri.fromFile(audioOnly),
            name = "external-tone",
            type = "audio/mp4",
            sourceStartMs = 0L,
            sourceEndMs = 4_000L,
            timelineStartMs = 0L,
            isAudio = true,
            trackIndex = 0
        )
        val output = export(
            clips = listOf(videoClip(redVideo, durationMs = 4_000L), audioClip),
            debugMedia3 = true,
            name = "audio-mix"
        )
        try {
            assertVideoOutput(output, expectedDurationMs = 4_000L)
            assertTrue("Export should contain a mixed audio track", hasAudioTrack(output))
            assertContainsBothAudioSources(output)
            Log.i(TAG, "case=audio-mix passed")
        } finally {
            deleteOutput(output)
        }
    }

    private fun export(
        clips: List<EditorClip>,
        debugMedia3: Boolean,
        name: String,
        outputWidth: Int = 320,
        outputHeight: Int = 240,
        timeoutMs: Long = 120_000L
    ): Uri = runBlocking {
        SettingsRepository(context).setDebugMedia3Export(debugMedia3)
        val result = CompletableDeferred<Uri>()
        val startedAtMs = android.os.SystemClock.elapsedRealtime()
        val exporter = VideoExporter(
            context = context,
            onProgress = { progress -> if (progress >= 1f) lastExportElapsedMs = android.os.SystemClock.elapsedRealtime() - startedAtMs },
            onSuccess = {
                lastExportElapsedMs = android.os.SystemClock.elapsedRealtime() - startedAtMs
                result.complete(it)
            },
            onError = { result.completeExceptionally(IllegalStateException(it)) },
            onCancelled = {
                result.cancel(CancellationException("Export cancelled"))
            }
        )
        exporter.export(
            clips = clips,
            fileName = "runtime-$name-${System.nanoTime()}",
            resolution = "360p",
            fps = 30,
            bitrateKbps = 1_500,
            format = "mp4",
            outputWidth = outputWidth,
            outputHeight = outputHeight
        )
        withTimeout(timeoutMs) { result.await() }
    }

    private data class GeneratedProject(
        val name: String,
        val state: EditorState,
        val expectedMedia3Route: String
    )

    private fun videoClip(
        file: File,
        durationMs: Long,
        timelineStartMs: Long = 0L,
        trackIndex: Int = 0
    ) = EditorClip(
        uri = Uri.fromFile(file),
        name = file.name,
        type = "video/mp4",
        sourceStartMs = 0L,
        sourceEndMs = durationMs,
        timelineStartMs = timelineStartMs,
        trackIndex = trackIndex
    )

    private fun copyStagedMedia(name: String): File {
        val staged = File(context.filesDir, "media3-inputs/$name")
        check(staged.isFile && staged.length() > 0L) {
            "Missing staged device test media: ${staged.name}"
        }
        return staged.copyTo(File(testDirectory, name), overwrite = true)
    }

    private fun assertPlayableAndNotGreen(uri: Uri, durationMs: Long) {
        val size = sizeInBytes(uri)
        assertTrue("Output must be non-empty", size > 0L)
        val frame = frameAt(uri, durationMs * 500L)
        try {
            val sampleColors = (0..4).flatMap { row ->
                (0..4).map { column ->
                    frame.getPixel(
                        (frame.width - 1) * column / 4,
                        (frame.height - 1) * row / 4
                    )
                }
            }
            val greenPixels = sampleColors.count { color ->
                red(color) < 15 && ((color shr 8) and 0xFF) > 240 && blue(color) < 15
            }
            assertTrue("Decoded output frame is nearly solid green", greenPixels < 23)
            assertTrue(frame.width > 0 && frame.height > 0)
        } finally {
            frame.recycle()
        }
    }

    private fun sizeInBytes(uri: Uri): Long =
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L

    private fun createVideo(
        fileName: String,
        color: String,
        durationSec: Int,
        includeAudio: Boolean
    ): File {
        val output = File(testDirectory, fileName)
        val arguments = mutableListOf(
            "-v", "error",
            "-f", "lavfi",
            "-i", "color=c=$color:s=320x240:r=30:d=$durationSec"
        )
        if (includeAudio) {
            arguments += listOf(
                "-f", "lavfi",
                "-i", "sine=frequency=440:sample_rate=44100:duration=$durationSec"
            )
        }
        arguments += listOf("-t", durationSec.toString(), "-c:v", "mpeg4", "-q:v", "4")
        if (includeAudio) arguments += listOf("-c:a", "aac", "-b:a", "96k")
        arguments += listOf("-y", output.absolutePath)
        val session = FFmpegKit.executeWithArguments(arguments.toTypedArray())
        check(ReturnCode.isSuccess(session.returnCode)) {
            "Could not create runtime test video: ${session.allLogsAsString}"
        }
        return output
    }

    private fun createAudio(fileName: String, durationSec: Int): File {
        val output = File(testDirectory, fileName)
        val session = FFmpegKit.executeWithArguments(
            arrayOf(
                "-v", "error",
                "-f", "lavfi",
                "-i", "sine=frequency=880:sample_rate=44100:duration=$durationSec",
                "-t", durationSec.toString(),
                "-c:a", "aac",
                "-b:a", "96k",
                "-y", output.absolutePath
            )
        )
        check(ReturnCode.isSuccess(session.returnCode)) {
            "Could not create runtime test audio: ${session.allLogsAsString}"
        }
        return output
    }

    private fun assertVideoOutput(
        uri: Uri,
        expectedDurationMs: Long,
        expectedWidth: Int = 320,
        expectedHeight: Int = 240
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
            assertTrue(
                "Unexpected video dimensions: ${width}x$height",
                width == expectedWidth && height == expectedHeight
            )
            assertTrue(
                "Unexpected output duration: $duration",
                duration != null && kotlin.math.abs(duration - expectedDurationMs) <= 500L
            )
        } finally {
            retriever.release()
        }
    }

    private fun frameAt(uri: Uri, timeUs: Long): Bitmap {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: error("No decoded frame at $timeUs")
        } finally {
            retriever.release()
        }
    }

    private fun hasAudioTrack(uri: Uri): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, emptyMap())
            (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            }
        } finally {
            extractor.release()
        }
    }

    private fun assertContainsBothAudioSources(uri: Uri) {
        val mixedFile = File(testDirectory, "mixed-output.mp4")
        context.contentResolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "Could not open mixed export" }.use { source ->
                mixedFile.outputStream().use { destination ->
                    source.copyTo(destination)
                }
            }
        }
        val pcmFile = File(testDirectory, "mixed-output.pcm")
        val session = FFmpegKit.executeWithArguments(
            arrayOf(
                "-v", "error",
                "-i", mixedFile.absolutePath,
                "-vn",
                "-t", "2",
                "-ac", "1",
                "-ar", "8000",
                "-f", "s16le",
                "-acodec", "pcm_s16le",
                "-y", pcmFile.absolutePath
            )
        )
        check(ReturnCode.isSuccess(session.returnCode)) {
            "Could not decode mixed audio: ${session.allLogsAsString}"
        }
        val bytes = pcmFile.readBytes()
        val samples = IntArray(bytes.size / 2) { index ->
            val low = bytes[index * 2].toInt() and 0xFF
            val high = bytes[index * 2 + 1].toInt()
            (low or (high shl 8)).toShort().toInt()
        }
        val tone440 = goertzelPower(samples, 8_000, 440)
        val tone880 = goertzelPower(samples, 8_000, 880)
        Log.i(TAG, "mixedAudioTonePower 440Hz=$tone440 880Hz=$tone880")
        assertTrue("Embedded 440 Hz audio missing from mix", tone440 > tone880 * 0.05)
        assertTrue("External 880 Hz audio missing from mix", tone880 > tone440 * 0.05)
    }

    private fun goertzelPower(samples: IntArray, sampleRate: Int, frequency: Int): Double {
        val coefficient = 2.0 * cos(2.0 * Math.PI * frequency / sampleRate)
        var previous = 0.0
        var previousPrevious = 0.0
        samples.forEach { sample ->
            val current = sample + coefficient * previous - previousPrevious
            previousPrevious = previous
            previous = current
        }
        return previous * previous + previousPrevious * previousPrevious -
                coefficient * previous * previousPrevious
    }

    private fun deleteOutput(uri: Uri) {
        if (uri.scheme == "file") {
            uri.path?.let { File(it).delete() }
        } else {
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun red(color: Int): Int = (color shr 16) and 0xFF
    private fun blue(color: Int): Int = color and 0xFF

    private companion object {
        const val TAG = "MEDIA3_RUNTIME_TEST"
    }

    @Volatile
    private var lastExportElapsedMs: Long = 0L
}
