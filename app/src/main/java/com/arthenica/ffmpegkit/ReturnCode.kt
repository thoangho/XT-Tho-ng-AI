package com.arthenica.ffmpegkit

/**
 * Mã trạng thái kết quả thực thi lệnh FFmpeg (ReturnCode)
 */
class ReturnCode(val value: Int) {
    val isValueSuccess: Boolean
        get() = value == 0

    val isValueCancel: Boolean
        get() = value == 255

    val isValueError: Boolean
        get() = value != 0 && value != 255

    companion object {
        val SUCCESS = ReturnCode(0)
        val CANCEL = ReturnCode(255)
        val ERROR = ReturnCode(1)

        fun isSuccess(returnCode: ReturnCode?): Boolean {
            return returnCode != null && returnCode.value == 0
        }

        fun isCancel(returnCode: ReturnCode?): Boolean {
            return returnCode != null && returnCode.value == 255
        }
    }
}
