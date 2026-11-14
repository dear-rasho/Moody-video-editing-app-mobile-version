package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.utils.VideoEncoderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VideoEncoderPolicyTest {

    @Test
    fun parsesVideoEncodersFromFfmpegEncoderTable() {
        val encoders = VideoEncoderPolicy.parseFfmpegVideoEncoders(
            """
                V..... = Video
                V....D h264_mediacodec MediaCodec H.264 encoder
                V....D hevc_mediacodec MediaCodec HEVC encoder
                A..... aac AAC encoder
            """.trimIndent()
        )

        assertEquals(setOf("h264_mediacodec", "hevc_mediacodec"), encoders)
    }

    @Test
    fun replacingFailedEncoderPreservesOutputSettings() {
        val original = listOf(
            "-c:v", "h264_mediacodec",
            "-bitrate_mode", "vbr",
            "-g", "60",
            "-b:v", "8000k",
            "-pix_fmt", "yuv420p",
            "output.mp4"
        )
        val replacement = listOf("-c:v", "hevc_mediacodec", "-tag:v", "hvc1")

        val result = VideoEncoderPolicy.replaceCodecArguments(original, replacement)

        assertNotNull(result)
        assertEquals(
            listOf(
                "-c:v", "hevc_mediacodec", "-tag:v", "hvc1",
                "-b:v", "8000k", "-pix_fmt", "yuv420p", "output.mp4"
            ),
            result
        )
    }
}
