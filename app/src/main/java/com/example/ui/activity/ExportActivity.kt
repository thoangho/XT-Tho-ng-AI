package com.example.ui.activity

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.data.model.SubtitleSegment
import com.example.data.model.VideoProject
import com.example.data.video.FFmpegOptions
import com.example.data.video.VideoExportService
import com.example.util.BatteryHelper
import com.example.util.BatteryInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * ExportActivity - Quản lý tiến trình xuất Render Video với Render Protection Mode
 *
 * 1. BỎ HOÀN TOÀN GIỚI HẠN THỜI GIAN 5 PHÚT (TIMEOUT REMOVAL):
 *    Tiến trình render FFmpeg chạy liên tục không bị ngắt/hủy bởi bất kỳ bộ đếm thời gian nào.
 *
 * 2. KHÓA TƯƠNG TÁC GIAO DIỆN VÀ GIỮ SÁNG MÀN HÌNH (TOUCH LOCK & OVERLAY):
 *    - Khi bắt đầu: Hiển thị Dialog/Overlay phủ kín toàn màn hình (isCancelable = false)
 *      + window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
 *      + window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
 *    - Khi kết thúc hoặc người dùng bấm Hủy:
 *      + Tắt Overlay, gỡ bỏ FLAG_NOT_TOUCHABLE và FLAG_KEEP_SCREEN_ON.
 */
class ExportActivity : ComponentActivity() {

    private var renderOverlayDialog: Dialog? = null
    private var exportJob: Job? = null
    private var isRendering: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_export)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelRenderProcess()
        disableRenderProtectionMode()
    }

    /**
     * Kiểm tra an toàn pin trước khi bắt đầu Render Video dài
     */
    fun checkBatterySafety(): BatteryInfo {
        return BatteryHelper.getBatteryInfo(this)
    }

    /**
     * Kích hoạt Chế độ Bảo vệ Render:
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
     * Gỡ bỏ Chế độ Bảo vệ Render:
     * 1. Tắt giữ sáng màn hình
     * 2. Mở lại khả năng tương tác chạm bình thường
     */
    fun disableRenderProtectionMode() {
        runOnUiThread {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
    }

    /**
     * Hiển thị Dialog / Overlay phủ kín toàn màn hình chặn mọi thao tác chạm
     */
    fun showRenderOverlay(
        initialMessage: String = "Đang bắt đầu xuất video...",
        onCancelClicked: (() -> Unit)? = null
    ) {
        runOnUiThread {
            if (renderOverlayDialog?.isShowing == true) {
                updateRenderOverlayProgress(0, initialMessage)
                return@runOnUiThread
            }

            val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCancelable(false)
                setCanceledOnTouchOutside(false)

                // Layout toàn màn hình
                setContentView(R.layout.activity_export)

                window?.apply {
                    setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundDrawable(ColorDrawable(Color.parseColor("#EE0A0E1A")))
                    addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                findViewById<TextView>(R.id.exportTitleText)?.text = "ĐANG XUẤT VIDEO (FFMPEG RENDER)"
                findViewById<TextView>(R.id.exportStatusText)?.text = initialMessage
            }

            renderOverlayDialog = dialog
            dialog.show()

            // Khóa chạm trên window cha và giữ màn hình sáng
            enableRenderProtectionMode()
        }
    }

    /**
     * Cập nhật tiến trình hiển thị trên Overlay Dialog
     */
    fun updateRenderOverlayProgress(progressPercent: Int, statusMessage: String) {
        runOnUiThread {
            val dialog = renderOverlayDialog ?: return@runOnUiThread
            if (!dialog.isShowing) return@runOnUiThread

            dialog.findViewById<ProgressBar>(R.id.exportProgressBar)?.apply {
                if (isIndeterminate && progressPercent > 0) {
                    isIndeterminate = false
                    max = 100
                }
                if (!isIndeterminate) {
                    progress = progressPercent
                }
            }

            dialog.findViewById<TextView>(R.id.exportStatusText)?.text =
                if (progressPercent > 0) "$statusMessage ($progressPercent%)" else statusMessage
        }
    }

    /**
     * Tắt Overlay Dialog và mở lại khóa tương tác giao diện
     */
    fun hideRenderOverlay() {
        runOnUiThread {
            try {
                if (renderOverlayDialog?.isShowing == true) {
                    renderOverlayDialog?.dismiss()
                }
            } catch (e: Exception) {
                // Ignore if activity destroyed
            } finally {
                renderOverlayDialog = null
                disableRenderProtectionMode()
            }
        }
    }

    /**
     * Bắt đầu tiến trình Render Video hoàn chỉnh:
     * - Bỏ hoàn toàn giới hạn thời gian (không timeout, chạy liên tục)
     * - Bật Overlay phủ màn hình & khóa chạm + giữ sáng màn hình
     */
    fun startExportProcess(
        project: VideoProject,
        segments: List<SubtitleSegment>,
        options: FFmpegOptions = FFmpegOptions(),
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isRendering) return
        isRendering = true

        showRenderOverlay("Khởi tạo tiến trình xuất video...") {
            cancelRenderProcess()
            onError("Người dùng đã hủy tiến trình xuất video.")
        }

        exportJob?.cancel()
        exportJob = lifecycleScope.launch {
            try {
                // Tiến trình render FFmpeg chạy liên tục không timeout
                val exportedFile = VideoExportService.renderVideoWithFFmpeg(
                    context = this@ExportActivity,
                    project = project,
                    segments = segments,
                    options = options
                ) { step, percentage, message ->
                    val pct = (percentage * 100).toInt()
                    updateRenderOverlayProgress(pct, message)
                }

                isRendering = false
                hideRenderOverlay()
                onSuccess(exportedFile)
            } catch (e: CancellationException) {
                isRendering = false
                hideRenderOverlay()
                onError("Đã hủy tiến trình xuất video.")
            } catch (e: Exception) {
                isRendering = false
                hideRenderOverlay()
                onError(e.localizedMessage ?: "Lỗi trong quá trình render video")
            }
        }
    }

    /**
     * Hủy tiến trình Render Video đang chạy, mở khóa giao diện
     */
    fun cancelRenderProcess() {
        isRendering = false
        exportJob?.cancel()
        exportJob = null
        hideRenderOverlay()
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, ExportActivity::class.java)
        }
    }
}
