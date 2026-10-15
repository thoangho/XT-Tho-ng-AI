package com.aistudio.dubstudio.vmqxrt.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * PermissionManager - Lớp quản lý và kiểm tra quyền hệ thống tập trung
 * Hỗ trợ phân định chính xác theo từng phiên bản Android (Tiramisu API 33+ vs Android 12 trở xuống)
 */
object PermissionManager {

    /**
     * Danh sách quyền lưu trữ/truy cập file media theo phiên bản Android
     */
    fun getRequiredStoragePermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            }
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
            else -> {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }
    }

    /**
     * Kiểm tra ứng dụng đã được cấp đủ quyền đọc media/storage hay chưa
     */
    fun hasStoragePermission(context: Context): Boolean {
        val permissions = getRequiredStoragePermissions()
        return permissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Kiểm tra quyền gửi thông báo (Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Kiểm tra toàn bộ quyền cần thiết của ứng dụng
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return hasStoragePermission(context) && hasNotificationPermission(context)
    }
}
