package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Data class đại diện cho thông tin pin và trạng thái sạc của thiết bị.
 */
data class BatteryInfo(
    val level: Int = 100,             // Phần trăm pin từ 0 đến 100
    val isCharging: Boolean = false,  // true nếu đang cắm sạc (AC/USB/Không dây)
    val isLowBattery: Boolean = level <= 20 && !isCharging, // Cảnh báo pin yếu (<= 20% và không sạc)
    val isCriticallyLow: Boolean = level <= 10 && !isCharging // Mức pin nguy hiểm (<= 10% và không sạc)
) {
    val statusText: String
        get() = if (isCharging) "$level% ⚡ (Đang sạc)" else "$level%"

    val warningMessage: String
        get() = when {
            isCriticallyLow -> "Mức pin cực thấp ($level%)! Quá trình render video rất nặng và có nguy cơ cao khiến thiết bị sập nguồn làm hỏng video. Vui lòng cắm sạc ngay!"
            isLowBattery -> "Pin thiết bị chỉ còn $level% và không cắm sạc. Render video tiêu tốn nhiều tài nguyên xử lý, bạn nên kết nối bộ sạc để tránh bị ngắt quãng giữa chừng."
            else -> ""
        }
}

/**
 * Tiện ích theo dõi mức pin và cảnh báo pin yếu trong các tác vụ dài (Render video, AI Dubbing).
 */
object BatteryHelper {

    /**
     * Lấy tức thời thông tin pin thông qua Sticky Broadcast Intent.ACTION_BATTERY_CHANGED.
     */
    fun getBatteryInfo(context: Context): BatteryInfo {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)

            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

            val batteryPct = if (level >= 0 && scale > 0) {
                ((level.toFloat() / scale.toFloat()) * 100).toInt()
            } else {
                100
            }

            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL ||
                    plugged != 0

            BatteryInfo(
                level = batteryPct.coerceIn(0, 100),
                isCharging = isCharging
            )
        } catch (e: Exception) {
            BatteryInfo(level = 100, isCharging = true)
        }
    }

    /**
     * Luồng Flow quan sát sự thay đổi trạng thái pin và nguồn sạc theo thời gian thực.
     */
    fun observeBattery(context: Context): Flow<BatteryInfo> = callbackFlow {
        // Gửi ngay trạng thái ban đầu
        trySend(getBatteryInfo(context))

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                trySend(getBatteryInfo(ctx))
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }

        try {
            context.registerReceiver(receiver, filter)
        } catch (e: Exception) {
            // Trường hợp không thể đăng ký broadcast
        }

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Bỏ qua lỗi nếu đã unregister
            }
        }
    }
}
