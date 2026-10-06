package com.arthenica.ffmpegkit

/**
 * Phiên làm việc thực thi lệnh FFmpeg (FFmpegSession)
 */
class FFmpegSession(
    val command: String = "",
    val returnCode: ReturnCode = ReturnCode.SUCCESS,
    val output: String = "",
    val failStackTrace: String? = null
) {
    val duration: Long = 0L
    val state: String = "COMPLETED"
}
