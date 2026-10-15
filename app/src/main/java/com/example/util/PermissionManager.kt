package com.example.util

import android.content.Context
import com.aistudio.dubstudio.vmqxrt.util.PermissionManager as ImplPermissionManager

/**
 * Lớp chuyển tiếp PermissionManager trong package com.example.util
 */
object PermissionManager {
    fun getRequiredStoragePermissions(): Array<String> =
        ImplPermissionManager.getRequiredStoragePermissions()

    fun hasStoragePermission(context: Context): Boolean =
        ImplPermissionManager.hasStoragePermission(context)

    fun hasNotificationPermission(context: Context): Boolean =
        ImplPermissionManager.hasNotificationPermission(context)

    fun hasAllRequiredPermissions(context: Context): Boolean =
        ImplPermissionManager.hasAllRequiredPermissions(context)
}
