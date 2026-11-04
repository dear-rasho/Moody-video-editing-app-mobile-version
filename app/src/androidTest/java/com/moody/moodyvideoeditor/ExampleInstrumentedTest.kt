package com.moody.moodyvideoeditor

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.utils.FFmpegExecutor
import com.moody.moodyvideoeditor.utils.KeyframeStore
import java.io.File

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.moody.moodyvideoeditor", appContext.packageName)
    }

    @Test
    fun ffmpegExportsKeyframedScaleForLinearAndBounceEasing() {
        listOf("linear", "bounceOut").forEach { ease ->
            val keyframes = mapOf(
                "scale" to listOf(
                    Keyframe(0f, 100f, ease),
                    Keyframe(0.5f, 60f, ease),
                    Keyframe(1f, 120f)
                )
            )
            val expression = KeyframeStore.escapeFfmpegExpression(
                KeyframeStore.ffmpegExpression(keyframes, "scale", 100f)
            )
            val graph =
                "[0:v]scale=w='iw*($expression/100)':h='ih*($expression/100)':eval=frame[out]"
            val session = FFmpegKit.executeWithArguments(
                arrayOf(
                    "-v", "error",
                    "-f", "lavfi", "-i", "color=c=black:s=854x480:r=30:d=1",
                    "-filter_complex_threads", "1",
                    "-filter_complex", graph,
                    "-map", "[out]",
                    "-frames:v", "1",
                    "-f", "null", "-"
                )
            )

            assertTrue(
                "$ease scale export failed: ${session.allLogsAsString}",
                ReturnCode.isSuccess(session.returnCode)
            )
        }
    }

    @Test
    fun perspectiveDestinationCoordinatesMatchPreviewZoomDirection() {
        val cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        listOf(
            Triple("zoom", -50, 150) to (900..Int.MAX_VALUE),
            Triple("shrink", 40, 60) to (1..100)
        ).forEach { (case, expectedWhitePixels) ->
            val (name, first, last) = case
            val outputFile = File(cacheDir, "perspective-destination-$name-test.png")
            val graph =
                "[0:v]drawbox=x=40:y=40:w=20:h=20:color=white:t=fill:replace=1," +
                        "perspective=x0=$first:y0=$first:x1=$last:y1=$first:" +
                        "x2=$first:y2=$last:x3=$last:y3=$last:" +
                        "sense=destination[out]"
            val session = FFmpegKit.executeWithArguments(
                arrayOf(
                    "-v", "error",
                    "-f", "lavfi", "-i", "color=c=black@0:s=100x100:r=1:d=1,format=rgba",
                    "-filter_complex", graph,
                    "-map", "[out]",
                    "-frames:v", "1",
                    "-y", outputFile.absolutePath
                )
            )
            try {
                assertTrue(
                    "$name perspective export failed: ${session.allLogsAsString}",
                    ReturnCode.isSuccess(session.returnCode)
                )
                val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                    ?: error("$name perspective output image was not created")
                try {
                    var whitePixels = 0
                    for (y in 0 until bitmap.height) {
                        for (x in 0 until bitmap.width) {
                            val color = bitmap.getPixel(x, y)
                            if (android.graphics.Color.red(color) > 200 &&
                                android.graphics.Color.green(color) > 200 &&
                                android.graphics.Color.blue(color) > 200
                            ) {
                                whitePixels++
                            }
                        }
                    }
                    assertTrue(
                        "$name perspective should render within $expectedWhitePixels white pixels, got $whitePixels",
                        whitePixels in expectedWhitePixels
                    )
                    assertTrue(
                        "Perspective should keep outside pixels transparent",
                        android.graphics.Color.alpha(bitmap.getPixel(0, 0)) < 10
                    )
                } finally {
                    bitmap.recycle()
                }
            } finally {
                outputFile.delete()
            }
        }
    }

    @Test
    fun perspectiveScaleExpressionHandlesZeroAndSmallAnimatedScale() {
        val keyframes = mapOf(
            "scale" to listOf(Keyframe(0f, 0f), Keyframe(1f, 20f))
        )
        val scale = KeyframeStore.ffmpegExpression(keyframes, "scale", 100f, "on/30")
        val safeScale = KeyframeStore.escapeFfmpegExpression(
            "max(0.001,clip(($scale)/100,0,5))"
        )
        val alphaScale = KeyframeStore.ffmpegExpression(keyframes, "scale", 100f, "T")
        val scaleVisibility = KeyframeStore.escapeFfmpegExpression(
            "if(gt(($alphaScale),0)*gt(($alphaScale),0),1,0)"
        )
        val graph =
            "[0:v]perspective=x0='50+(0-50)*$safeScale':y0='50+(0-50)*$safeScale':" +
                    "x1='50+(100-50)*$safeScale':y1='50+(0-50)*$safeScale':" +
                    "x2='50+(0-50)*$safeScale':y2='50+(100-50)*$safeScale':" +
                    "x3='50+(100-50)*$safeScale':y3='50+(100-50)*$safeScale':" +
                    "eval=frame:sense=destination[transformed];" +
                    "[transformed]geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':" +
                    "a='alpha(X,Y)*$scaleVisibility'[out]"
        val session = FFmpegKit.executeWithArguments(
            arrayOf(
                "-v", "error",
                "-f", "lavfi", "-i", "color=c=black:s=100x100:r=30:d=1,format=rgba",
                "-filter_complex", graph,
                "-map", "[out]",
                "-frames:v", "16",
                "-f", "null", "-"
            )
        )
        assertTrue(
            "Zero/small-scale perspective expression failed: ${session.allLogsAsString}",
            ReturnCode.isSuccess(session.returnCode)
        )
    }

    @Test
    fun perspectivePortraitShrinkKeepsTheImageWithinExpectedBounds() {
        val outputFile = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "perspective-portrait-shrink-test.png"
        )
        val graph =
            "[0:v]drawbox=x=40:y=70:w=20:h=40:color=white:t=fill:replace=1," +
                    "perspective=x0=40:y0=72:x1=60:y1=72:" +
                    "x2=40:y2=108:x3=60:y3=108:sense=destination[out]"
        val session = FFmpegKit.executeWithArguments(
            arrayOf(
                "-v", "error",
                "-f", "lavfi", "-i", "color=c=black@0:s=100x180:r=1:d=1,format=rgba",
                "-filter_complex", graph,
                "-map", "[out]",
                "-frames:v", "1",
                "-y", outputFile.absolutePath
            )
        )
        try {
            assertTrue(
                "Portrait shrink export failed: ${session.allLogsAsString}",
                ReturnCode.isSuccess(session.returnCode)
            )
            val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                ?: error("Portrait shrink output image was not created")
            try {
                var minX = bitmap.width
                var minY = bitmap.height
                var maxX = -1
                var maxY = -1
                for (y in 0 until bitmap.height) {
                    for (x in 0 until bitmap.width) {
                        val color = bitmap.getPixel(x, y)
                        if (android.graphics.Color.red(color) > 200 &&
                            android.graphics.Color.green(color) > 200 &&
                            android.graphics.Color.blue(color) > 200
                        ) {
                            minX = minOf(minX, x)
                            minY = minOf(minY, y)
                            maxX = maxOf(maxX, x)
                            maxY = maxOf(maxY, y)
                        }
                    }
                }
                assertTrue("The transformed portrait patch should remain visible", maxX >= minX)
                assertTrue("The transformed portrait patch should remain visible", maxY >= minY)
                assertTrue("Horizontal bounds should reflect 20% scale", maxX - minX + 1 <= 6)
                assertTrue("Vertical bounds should reflect 20% scale", maxY - minY + 1 <= 10)
            } finally {
                bitmap.recycle()
            }
        } finally {
            outputFile.delete()
        }
    }

    @Test
    fun exportedPortraitLayerHonorsStaticAndAnimatedScale() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clip = EditorClip(
            uri = android.net.Uri.parse("file:///portrait.png"),
            name = "portrait",
            type = "image/png",
            sourceStartMs = 0L,
            sourceEndMs = 1000L,
            timelineStartMs = 0L,
            scale = 0.5f
        )
        val executor = FFmpegExecutor(context, {}, {}, {})
        val filterMethod = FFmpegExecutor::class.java.getDeclaredMethod(
            "buildVisualTransformFilters",
            EditorClip::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val cases = listOf(
            "static" to clip,
            "animated" to clip.copy(
                scale = 1f,
                keyframes = mapOf(
                    "scale" to listOf(
                        Keyframe(0f, 50f, "easeInOut"),
                        Keyframe(1f, 60f)
                    )
                )
            ),
            "animated-position-anchor" to clip.copy(
                scale = 1f,
                keyframes = mapOf(
                    "scale" to listOf(
                        Keyframe(0f, 50f, "easeInOut"),
                        Keyframe(1f, 60f)
                    ),
                    "x" to listOf(Keyframe(0f, 55f), Keyframe(1f, 55f)),
                    "y" to listOf(Keyframe(0f, 45f), Keyframe(1f, 45f)),
                    "anchorX" to listOf(Keyframe(0f, 65f), Keyframe(1f, 65f)),
                    "anchorY" to listOf(Keyframe(0f, 35f), Keyframe(1f, 35f))
                )
            )
        )
        cases.forEach { (name, testClip) ->
            @Suppress("UNCHECKED_CAST")
            val filters = filterMethod.invoke(
                executor, testClip, 100, 180, false, false, 30
            ) as List<String>
            val outputFile = File(context.cacheDir, "exported-portrait-$name-scale-test.png")
            val graph = "[0:v]${filters.joinToString(",")}[out]"
            val session = FFmpegKit.executeWithArguments(
                arrayOf(
                    "-v", "error",
                    "-f", "lavfi",
                    "-i", "color=c=black:s=100x180:r=30:d=1,format=rgba," +
                            "drawbox=x=40:y=70:w=20:h=40:color=white:t=fill:replace=1",
                    "-filter_complex", graph,
                    "-map", "[out]",
                    "-frames:v", "1",
                    "-y", outputFile.absolutePath
                )
            )
            try {
                assertTrue(
                    "$name portrait export failed: ${session.allLogsAsString}\n$graph",
                    ReturnCode.isSuccess(session.returnCode)
                )
                val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                    ?: error("$name portrait export image was not created")
                try {
                    var minX = bitmap.width
                    var minY = bitmap.height
                    var maxX = -1
                    var maxY = -1
                    var minAlphaX = bitmap.width
                    var minAlphaY = bitmap.height
                    var maxAlphaX = -1
                    var maxAlphaY = -1
                    for (y in 0 until bitmap.height) {
                        for (x in 0 until bitmap.width) {
                            val color = bitmap.getPixel(x, y)
                            if (android.graphics.Color.alpha(color) > 10) {
                                minAlphaX = minOf(minAlphaX, x)
                                minAlphaY = minOf(minAlphaY, y)
                                maxAlphaX = maxOf(maxAlphaX, x)
                                maxAlphaY = maxOf(maxAlphaY, y)
                            }
                            if (android.graphics.Color.red(color) > 200 &&
                                android.graphics.Color.green(color) > 200 &&
                                android.graphics.Color.blue(color) > 200
                            ) {
                                minX = minOf(minX, x)
                                minY = minOf(minY, y)
                                maxX = maxOf(maxX, x)
                                maxY = maxOf(maxY, y)
                            }
                        }
                    }
                    assertTrue("$name scaled portrait should remain visible", maxX >= minX)
                    assertTrue("$name scaled portrait should remain visible", maxY >= minY)
                    assertTrue("$name export should apply 50% horizontal scale", maxX - minX + 1 <= 12)
                    assertTrue("$name export should apply 50% vertical scale", maxY - minY + 1 <= 22)
                    assertTrue(
                        "$name transformed layer should not stretch into the empty canvas above/below",
                        maxAlphaY - minAlphaY + 1 <= 92
                    )
                    assertTrue(
                        "$name transformed layer should not fill the canvas width",
                        maxAlphaX - minAlphaX + 1 <= 52
                    )
                } finally {
                    bitmap.recycle()
                }
            } finally {
                outputFile.delete()
            }
        }
    }

    @Test
    fun ffmpegExportsKeyframedTransformInEditorStyleCompositeGraph() {
        val keyframes = mapOf(
            "scale" to listOf(Keyframe(0f, 66f), Keyframe(0.5f, 20f), Keyframe(1f, 78f)),
            "scaleX" to listOf(
                Keyframe(0f, 80f, "bounceOut"),
                Keyframe(0.5f, 35f),
                Keyframe(1f, 90f)
            ),
            "scaleY" to listOf(
                Keyframe(0f, 75f),
                Keyframe(0.5f, 45f),
                Keyframe(1f, 85f)
            ),
            "x" to listOf(Keyframe(0f, 50f), Keyframe(1f, 50f)),
            "y" to listOf(Keyframe(0f, 50f), Keyframe(1f, 50f)),
            "anchorX" to listOf(Keyframe(0f, 25f), Keyframe(1f, 75f)),
            "anchorY" to listOf(Keyframe(0f, 65f), Keyframe(1f, 30f)),
            "rotation" to listOf(
                Keyframe(0f, 0f),
                Keyframe(0.5f, 25f, "easeInOut"),
                Keyframe(1f, 0f)
            )
        )
        fun expr(prop: String, base: Float, time: String = "t") =
            KeyframeStore.escapeFfmpegExpression(
                KeyframeStore.ffmpegExpression(keyframes, prop, base, time)
            )
        val x = expr("x", 50f, "(t-0.0)")
        val y = expr("y", 50f, "(t-0.0)")
        fun perspective(
            scaleX: String,
            scaleY: String,
            includePosition: Boolean
        ): String {
            val scaleXExpression = KeyframeStore.escapeFfmpegExpression("($scaleX/100)")
            val scaleYExpression = KeyframeStore.escapeFfmpegExpression("($scaleY/100)")
            val angleExpression = KeyframeStore.escapeFfmpegExpression(
                "(${expr("rotation", 0f, "on/30")}*PI/180)"
            )
            val anchorX = KeyframeStore.escapeFfmpegExpression(
                "(${expr("anchorX", 50f, "on/30")}/100)"
            )
            val anchorY = KeyframeStore.escapeFfmpegExpression(
                "(${expr("anchorY", 50f, "on/30")}/100)"
            )
            val offsetX = if (includePosition) {
                "((${expr("x", 50f, "on/30")}-50)*854/100)"
            } else "0"
            val offsetY = if (includePosition) {
                "((${expr("y", 50f, "on/30")}-50)*480/100)"
            } else "0"
            val px = "(854*$anchorX)"
            val py = "(480*$anchorY)"
            val cos = "cos($angleExpression)"
            val sin = "sin($angleExpression)"
            fun transformX(x: Int, y: Int) =
                "($px+($x-$px)*$scaleXExpression*$cos-" +
                        "($y-$py)*$scaleYExpression*$sin+$offsetX)"
            fun transformY(x: Int, y: Int) =
                "($py+($x-$px)*$scaleXExpression*$sin+" +
                        "($y-$py)*$scaleYExpression*$cos+$offsetY)"
            return "perspective=" +
                    "x0='${transformX(0, 0)}':y0='${transformY(0, 0)}':" +
                    "x1='${transformX(854, 0)}':y1='${transformY(854, 0)}':" +
                    "x2='${transformX(0, 480)}':y2='${transformY(0, 480)}':" +
                    "x3='${transformX(854, 480)}':y3='${transformY(854, 480)}':" +
                    "eval=frame:sense=destination"
        }
        fun fixedCanvasTransform(perspective: String) = listOf(
            "setpts=PTS-STARTPTS",
            "format=rgba",
            "fps=30",
            "scale=854:480:force_original_aspect_ratio=decrease",
            "pad=854:480:(ow-iw)/2:(oh-ih)/2:color=black@0",
            perspective
        ).joinToString(",")
        val fixedCanvasTransform = fixedCanvasTransform(
            perspective(
                expr("scaleX", 100f, "on/30"),
                expr("scaleY", 100f, "on/30"),
                includePosition = false
            )
        )
        val uniformScaleTransform = fixedCanvasTransform(
            perspective(
                expr("scale", 100f, "on/30"),
                expr("scale", 100f, "on/30"),
                includePosition = true
            )
        )
        val stages = listOf(
            "fixed-canvas-perspective-and-rotate" to "[0:v]$fixedCanvasTransform[out]",
            "uniform-scale-perspective-and-rotate" to "[0:v]$uniformScaleTransform[out]",
            "fixed-canvas-perspective-overlay" to listOf(
                "[1:v]setpts=PTS-STARTPTS,format=rgba[canvas0]",
                "[0:v]$fixedCanvasTransform,fps=30,trim=duration=1," +
                        "setpts=PTS-STARTPTS,format=rgba[layer]",
                "[canvas0][layer]overlay=" +
                        "x='(($x-50)*854/100)':y='(($y-50)*480/100)':" +
                        "eval=frame:eof_action=pass:repeatlast=0:" +
                        "enable='between(t,0,1)'[out]"
            ).joinToString(";")
        )

        stages.forEach { (stage, graph) ->
            Log.i("KeyframeGraphTest", "Running $stage filtergraph")
            val session = FFmpegKit.executeWithArguments(
                arrayOf(
                    "-v", "error",
                    "-f", "lavfi", "-t", "1", "-i", "color=c=black:s=854x480:r=30",
                    "-f", "lavfi", "-t", "1", "-i", "color=c=black:s=854x480:r=30",
                    "-filter_complex_threads", "1",
                    "-filter_complex", graph,
                    "-map", "[out]",
                    "-frames:v", "30",
                    "-f", "null", "-"
                )
            )

            assertTrue(
                "$stage keyframed export failed: ${session.allLogsAsString}",
                ReturnCode.isSuccess(session.returnCode)
            )
        }
    }
}