package com.example.data.local

import android.content.Context
import java.io.File

/**
 * FileUtils - Quản lý tệp tin và dọn dẹp bộ nhớ đệm (cache) của ứng dụng
 * Hỗ trợ dọn dẹp các tệp tạm video, audio lồng tiếng, và phụ đề sinh ra trong quá trình render.
 */
object FileUtils {

    /**
     * Dọn dẹp toàn bộ dữ liệu tạm thời trong thư mục cacheDir của ứng dụng
     */
    fun clearAppTempCache(context: Context) {
        try {
            val cacheDir = context.cacheDir
            deleteDir(cacheDir)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Đệ quy xóa thư mục và các tệp con
     */
    private fun deleteDir(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list()
            if (children != null) {
                for (child in children) {
                    val success = deleteDir(File(dir, child))
                    if (!success) return false
                }
            }
            return dir.delete()
        } else if (dir != null && dir.isFile) {
            return dir.delete()
        }
        return false
    }
}
