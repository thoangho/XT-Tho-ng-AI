package com.example.data.video

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VideoExportEngine(private val context: Context) {

    suspend fun executeThreeStepExport(
        inputVideo: File,
        subtitleFile: File,
        dubbedAudioFile: File,
        outputFile: File,
        onStepChange: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {

        // BƯỚC 1: Chèn phụ đề vào Video
        onStepChange("Bước 1/3: Đang chèn phụ đề...")
        val step1Video = File(context.cacheDir, "step1_subtitled.mp4")
        val cmdStep1 = "-i \"${inputVideo.absolutePath}\" -vf subtitles=\"${subtitleFile.absolutePath}\" -c:a copy \"${step1Video.absolutePath}\" -y"
        if (!runFFmpegCommand(cmdStep1)) return@withContext false

        // BƯỚC 2: Trộn âm thanh lồng tiếng
        onStepChange("Bước 2/3: Đang xử lý âm thanh lồng tiếng...")
        val step2Video = File(context.cacheDir, "step2_audio_merged.mp4")
        val cmdStep2 = "-i \"${step1Video.absolutePath}\" -i \"${dubbedAudioFile.absolutePath}\" -c:v copy -c:a aac -map 0:v:0 -map 1:a:0 \"${step2Video.absolutePath}\" -y"
        if (!runFFmpegCommand(cmdStep2)) return@withContext false

        // BƯỚC 3: Hợp nhất và xuất tệp MP4 hoàn chỉnh
        onStepChange("Bước 3/3: Đang đóng gói video hoàn chỉnh...")
        val cmdStep3 = "-i \"${step2Video.absolutePath}\" -c copy -movflags +faststart \"${outputFile.absolutePath}\" -y"
        val success = runFFmpegCommand(cmdStep3)

        // Dọn dẹp tệp trung gian
        step1Video.delete()
        step2Video.delete()

        return@withContext success
    }

    // Thực thi FFmpeg Không đặt Timeout
    private fun runFFmpegCommand(command: String): Boolean {
        val session = FFmpegKit.execute(command) // Không dùng timeout
        return ReturnCode.isSuccess(session.returnCode)
    }
}
