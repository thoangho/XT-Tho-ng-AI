package com.example.data.video

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * VideoExportEngine - Động cơ thực thi xử lý video FFmpeg 3 bước
 *
 * QUY TẮC AN TOÀN & ĐỘ TIN CẬY:
 * - BỎ HOÀN TOÀN GIỚI HẠN THỜI GIAN (TIMEOUT REMOVED):
 *   Không đặt bất kỳ giới hạn 300s hay 5 phút nào. Tiến trình FFmpeg được phép chạy liên tục
 *   cho đến khi xuất xong tệp hoàn chỉnh, tuyệt đối không tự động ngắt/hủy.
 */
class VideoExportEngine(private val context: Context) {

    companion object {
        private const val TAG = "VideoExportEngine"
    }

    /**
     * Quy trình xuất video 3 bước:
     * Bước 1: Gắn / thiêu kết phụ đề tiếng Việt vào video
     * Bước 2: Hòa trộn âm thanh lồng tiếng (TTS + nhạc nền)
     * Bước 3: Đóng gói luồng MP4 tốc độ cao (+faststart)
     */
    suspend fun executeThreeStepExport(
        inputVideo: File,
        subtitleFile: File,
        dubbedAudioFile: File,
        outputFile: File,
        onStepChange: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Bắt đầu quy trình xuất video 3 bước (Không giới hạn timeout)...")

        // BƯỚC 1: Chèn phụ đề vào Video
        onStepChange("Bước 1/3: Đang chèn phụ đề...")
        val step1Video = File(context.cacheDir, "step1_subtitled.mp4")
        val cmdStep1 = "-i \"${inputVideo.absolutePath}\" -vf subtitles=\"${subtitleFile.absolutePath}\" -c:a copy \"${step1Video.absolutePath}\" -y"
        if (!runFFmpegCommand(cmdStep1)) {
            Log.e(TAG, "Bước 1 thất bại")
            return@withContext false
        }

        // BƯỚC 2: Trộn âm thanh lồng tiếng
        onStepChange("Bước 2/3: Đang xử lý âm thanh lồng tiếng...")
        val step2Video = File(context.cacheDir, "step2_audio_merged.mp4")
        val cmdStep2 = "-i \"${step1Video.absolutePath}\" -i \"${dubbedAudioFile.absolutePath}\" -c:v copy -c:a aac -map 0:v:0 -map 1:a:0 \"${step2Video.absolutePath}\" -y"
        if (!runFFmpegCommand(cmdStep2)) {
            Log.e(TAG, "Bước 2 thất bại")
            step1Video.delete()
            return@withContext false
        }

        // BƯỚC 3: Hợp nhất và xuất tệp MP4 hoàn chỉnh
        onStepChange("Bước 3/3: Đang đóng gói video hoàn chỉnh...")
        val cmdStep3 = "-i \"${step2Video.absolutePath}\" -c copy -movflags +faststart \"${outputFile.absolutePath}\" -y"
        val success = runFFmpegCommand(cmdStep3)

        // Dọn dẹp tệp trung gian
        step1Video.delete()
        step2Video.delete()

        Log.d(TAG, "Hoàn tất quy trình xuất video 3 bước: Thành công = $success")
        return@withContext success
    }

    /**
     * Thực thi lệnh FFmpeg:
     * Tuyệt đối KHÔNG sử dụng timeout (no 300s / 5 minutes timeout), để tiến trình hoàn thành trọn vẹn.
     */
    private fun runFFmpegCommand(command: String): Boolean {
        Log.d(TAG, "Thực thi FFmpeg (Không timeout): $command")
        val session = FFmpegKit.execute(command)
        val success = ReturnCode.isSuccess(session.returnCode)
        Log.d(TAG, "FFmpeg kết thúc với mã trả về: ${session.returnCode}, thành công = $success")
        return success
    }
}
