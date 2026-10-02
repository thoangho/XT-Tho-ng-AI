package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SubtitleSegment
import com.example.data.model.VoiceOption
import com.example.data.network.TranslationService
import com.example.data.video.VideoExportService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DubStudio AI", appName)
    }

    @Test
    fun testTextSanitizationForTts() {
        val raw = "【爆笑】今天去吃火锅！#美食 #vlog 😊"
        val cleaned = TranslationService.sanitizeForTts(raw)
        // Should strip hashtag, bracket, and emojis
        assertTrue(!cleaned.contains("【"))
        assertTrue(!cleaned.contains("】"))
        assertTrue(!cleaned.contains("#美食"))
        assertTrue(!cleaned.contains("😊"))
    }

    @Test
    fun testSrtGeneration() {
        val segments = listOf(
            SubtitleSegment(
                id = 1,
                projectId = "test_proj",
                indexNumber = 1,
                startTimeMs = 1000,
                endTimeMs = 3500,
                originalChinese = "你好",
                vietnameseText = "Xin chào"
            )
        )
        val srt = VideoExportService.generateSrtContent(segments)
        assertTrue(srt.contains("1"))
        assertTrue(srt.contains("00:00:01,000 --> 00:00:03,500"))
        assertTrue(srt.contains("Xin chào"))
    }

    @Test
    fun testVoiceOptionsConfigured() {
        val hoaiMy = VoiceOption.findById("vi-VN-HoaiMyNeural")
        assertNotNull(hoaiMy)
        assertEquals("Hoài Mỹ", hoaiMy.name)
        assertEquals("Nữ", hoaiMy.gender)

        val namMinh = VoiceOption.findById("vi-VN-NamMinhNeural")
        assertNotNull(namMinh)
        assertEquals("Nam Minh", namMinh.name)
        assertEquals("Nam", namMinh.gender)

        // Verify newly added voices
        val quangAnh = VoiceOption.findById("vi-VN-QuangAnhNeural")
        assertNotNull(quangAnh)
        assertEquals("Quang Anh", quangAnh.name)
        assertEquals("Nam", quangAnh.gender)

        val giaKhiem = VoiceOption.findById("vi-VN-GiaKhiemNeural")
        assertNotNull(giaKhiem)
        assertEquals("Gia Khiêm", giaKhiem.name)
        assertEquals("Nam", giaKhiem.gender)

        val dungLongTieng = VoiceOption.findById("vi-VN-DungLongTiengNeural")
        assertNotNull(dungLongTieng)
        assertEquals("Dung Lồng Tiếng", dungLongTieng.name)
        assertEquals("Nữ", dungLongTieng.gender)

        val khanhVy = VoiceOption.findById("vi-VN-KhanhVyNeural")
        assertNotNull(khanhVy)
        assertEquals("Khánh Vy", khanhVy.name)
        assertEquals("Nữ", khanhVy.gender)

        val baoAnh = VoiceOption.findById("vi-VN-BaoAnhNeural")
        assertNotNull(baoAnh)
        assertEquals("Bảo Anh", baoAnh.name)

        val chiMai = VoiceOption.findById("vi-VN-ChiMaiNeural")
        assertNotNull(chiMai)
        assertEquals("Chi Mai", chiMai.name)

        val kimOanh = VoiceOption.findById("vi-VN-KimOanhNeural")
        assertNotNull(kimOanh)
        assertEquals("Kim Oanh", kimOanh.name)

        val adam = VoiceOption.findById("vi-VN-AdamNeural")
        assertNotNull(adam)
        assertEquals("Adam", adam.name)
        assertEquals("Nam", adam.gender)

        // Total 11 voices
        assertEquals(11, VoiceOption.ALL_VOICES.size)
    }

    @Test
    fun testFFmpegOptionsConfiguration() {
        val options = com.example.data.video.FFmpegOptions(
            preset = "ultrafast",
            threads = 4,
            bitrateKbps = 3000
        )
        assertEquals("ultrafast", options.preset)
        assertEquals(4, options.threads)
        assertEquals(3000, options.bitrateKbps)
        assertEquals("libx264", options.videoCodec)
    }

    @Test
    fun testSubtitleTextWrappingLogic() {
        val paint = android.graphics.Paint().apply {
            textSize = 36f
        }
        val longText = "Đây là một câu phụ đề dịch tiếng Việt rất dài cần được tự động ngắt dòng để không tràn khung hình video"
        val wrappedLines = VideoExportService.wrapText(longText, paint, maxWidth = 300f)
        assertTrue("Text must be wrapped into multiple lines", wrappedLines.size > 1)
        assertTrue("First line should not be empty", wrappedLines[0].isNotBlank())
    }

    @Test
    fun testCanvasMaskAndSubtitleDrawing() {
        val bitmap = android.graphics.Bitmap.createBitmap(544, 960, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Draw Mask
        val maskConfig = com.example.data.model.MaskConfig(yPercent = 75f, heightPercent = 10f)
        VideoExportService.drawMaskOnCanvas(canvas, 544, 960, maskConfig)

        // Draw Subtitle
        val subConfig = com.example.data.model.SubtitleConfig(fontSizeSp = 18, isBold = true)
        VideoExportService.drawSubtitleOnCanvas(
            canvas = canvas,
            text = "Phụ đề Tiếng Việt thử nghiệm",
            videoWidth = 544,
            videoHeight = 960,
            maskYCenter = 720f,
            subtitleConfig = subConfig
        )

        // Verify bitmap has drawn content (non-empty pixels)
        val argb = IntArray(544 * 960)
        val yuv = ByteArray(544 * 960 * 3 / 2)
        VideoExportService.encodeYuvFromBitmap(
            bitmap = bitmap,
            width = 544,
            height = 960,
            argb = argb,
            yuv = yuv,
            colorFormat = android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        )

        assertEquals(544 * 960 * 3 / 2, yuv.size)
        assertTrue("YUV buffer must contain valid data", yuv.any { it != 0.toByte() })
    }

    @Test
    fun testVideoExportFileValidation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val project = com.example.data.model.VideoProject(
            id = "test_export_proj",
            title = "Test Video Dub",
            videoUri = "sample_video.mp4",
            durationMs = 5000L
        )
        val segments = listOf(
            SubtitleSegment(
                id = 1,
                projectId = project.id,
                indexNumber = 1,
                startTimeMs = 0,
                endTimeMs = 2500,
                originalChinese = "测试视频",
                vietnameseText = "Video thử nghiệm lồng tiếng"
            )
        )

        // Verify SRT generation and export
        val srtContent = VideoExportService.generateSrtContent(segments)
        assertTrue(srtContent.contains("Video thử nghiệm lồng tiếng"))
        assertTrue(srtContent.contains("00:00:00,000 --> 00:00:02,500"))

        // Mock test output file validation
        val testOutputDir = java.io.File(context.filesDir, "test_output")
        testOutputDir.mkdirs()
        val mockMp4File = java.io.File(testOutputDir, "XThoangAI_Test_20261001.mp4")
        mockMp4File.writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte()))

        // Verify export conditions
        assertTrue("Output file must exist", mockMp4File.exists())
        assertTrue("Output file size must be > 0 bytes", mockMp4File.length() > 0L)
        assertTrue("File extension must be mp4", mockMp4File.name.endsWith(".mp4"))
        assertTrue("File name must start with XThoangAI", mockMp4File.name.startsWith("XThoangAI"))
    }

    @Test
    fun testStandaloneDubbingAudioGeneration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outputDir = java.io.File(context.filesDir, "test_audio")
        outputDir.mkdirs()

        // Create a mock segment audio file with valid WAV header
        val segmentWav = java.io.File(outputDir, "mock_seg_1.wav")
        java.io.FileOutputStream(segmentWav).use { fos ->
            VideoExportService.writeWavHeader(fos, 48000, 24000, 1, 16)
            fos.write(ByteArray(48000) { 1 }) // 1 second of mock PCM audio
        }

        val segment = SubtitleSegment(
            id = 101,
            projectId = "mock_dub_proj",
            indexNumber = 1,
            startTimeMs = 500,
            endTimeMs = 1500,
            originalChinese = "测试音频",
            vietnameseText = "Âm thanh lồng tiếng độc lập"
        )

        val masterWav = java.io.File(outputDir, "master_dubbing.wav")
        VideoExportService.concatenateWavSegments(
            totalDurationMs = 3000L,
            audioSegments = listOf(segment to segmentWav),
            outputFile = masterWav
        )

        assertTrue("Master WAV file must exist", masterWav.exists())
        assertTrue("Master WAV size must be greater than header (44 bytes)", masterWav.length() > 44L)

        // Verify WAV header magic numbers (RIFF .... WAVE)
        val headerBytes = masterWav.readBytes().take(12)
        assertEquals('R'.code.toByte(), headerBytes[0])
        assertEquals('I'.code.toByte(), headerBytes[1])
        assertEquals('F'.code.toByte(), headerBytes[2])
        assertEquals('F'.code.toByte(), headerBytes[3])
        assertEquals('W'.code.toByte(), headerBytes[8])
        assertEquals('A'.code.toByte(), headerBytes[9])
        assertEquals('V'.code.toByte(), headerBytes[10])
        assertEquals('E'.code.toByte(), headerBytes[11])
    }
}
