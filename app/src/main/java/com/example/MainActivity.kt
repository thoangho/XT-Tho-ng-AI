package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.ui.screens.StudioMainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    private val studioViewModel: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Khởi tạo ban đầu: Chưa có video đầu vào -> Ẩn hoàn toàn 2 nút 'Dịch Phụ Đề' & 'Xuất video'
        updateActionButtonVisibility(hasVideo = false)

        setContent {
            MyApplicationTheme {
                StudioMainScreen(
                    viewModel = studioViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    /**
     * Quản lý trạng thái UI: Ẩn/Hiện 2 nút hành động (Dịch Phụ Đề, Xuất video)
     * - hasVideo = false: Ẩn hoàn toàn (tương đương GONE)
     * - hasVideo = true: Hiển thị lại (tương đương VISIBLE)
     */
    fun updateActionButtonVisibility(hasVideo: Boolean) {
        studioViewModel.updateActionButtonVisibility(hasVideo)
    }

    /**
     * Được kích hoạt sau khi chọn hoặc nhận kết quả video từ Picker / Storage
     */
    fun onVideoSelected(videoUri: android.net.Uri) {
        // Cập nhật trạng thái hiển thị 2 nút hành động khi đã có video
        updateActionButtonVisibility(hasVideo = true)
        studioViewModel.importCustomVideo(videoUri, this)
    }

    /**
     * Mở trực tiếp trang Facebook Admin:
     * - Ưu tiên mở bằng ứng dụng Facebook nếu đã cài (com.facebook.katana)
     * - Tự động fallback sang trình duyệt web nếu chưa cài Facebook
     */
    fun contactAdminFacebook() {
        val fbUrl = "https://www.facebook.com/share/19o6cDY1cf/"
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(fbUrl)).apply {
                setPackage("com.facebook.katana")
            }
            startActivity(intent)
        } catch (e: Exception) {
            val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(fbUrl))
            startActivity(webIntent)
        }
    }

    /**
     * Kích hoạt Chế độ Bảo vệ Render Video:
     * 1. Giữ màn hình luôn sáng (FLAG_KEEP_SCREEN_ON)
     * 2. Vô hiệu hóa thao tác chạm trên Activity (FLAG_NOT_TOUCHABLE)
     */
    fun enableRenderProtectionMode() {
        runOnUiThread {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
    }

    /**
     * Gỡ bỏ Chế độ Bảo vệ Render Video:
     * 1. Tắt giữ sáng màn hình
     * 2. Mở lại tương tác chạm bình thường
     */
    fun disableRenderProtectionMode() {
        runOnUiThread {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
    }
}
