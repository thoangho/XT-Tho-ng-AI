package com.example.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.example.R

/**
 * ExportActivity - Quản lý tiến trình xuất Render Video với Render Protection Mode
 * Đảm bảo màn hình luôn sáng và chống chạm vô ý trong suốt quá trình xử lý video nặng.
 */
class ExportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_export)
    }

    /**
     * Kích hoạt khi bắt đầu Render Video:
     * 1. Giữ màn hình luôn sáng (FLAG_KEEP_SCREEN_ON)
     * 2. Vô hiệu hóa tương tác chạm trên toàn màn hình (FLAG_NOT_TOUCHABLE)
     */
    fun enableRenderProtectionMode() {
        runOnUiThread {
            // 1. Giữ màn hình luôn sáng
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // 2. Vô hiệu hóa tương tác chạm trên toàn màn hình
            window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
        }
    }

    /**
     * Tắt bảo vệ khi Render hoàn thành hoặc bị lỗi:
     * 1. Tắt giữ sáng màn hình
     * 2. Mở lại khả năng tương tác chạm
     */
    fun disableRenderProtectionMode() {
        runOnUiThread {
            // Tắt giữ sáng màn hình
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Mở lại khả năng tương tác chạm
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, ExportActivity::class.java)
        }
    }
}

