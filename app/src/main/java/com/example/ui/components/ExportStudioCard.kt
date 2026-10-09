package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.SubtitleSegment
import com.example.data.model.VideoProject
import com.example.data.video.FFmpegOptions
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBgDark
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioPurple
import com.example.ui.theme.StudioPurpleLight
import com.example.ui.theme.StudioSurfaceCard
import com.example.ui.theme.StudioSurfaceCardHover
import java.io.File

@Composable
fun ExportStudioCard(
    project: VideoProject,
    segments: List<SubtitleSegment>,
    lastExportedVideo: File?,
    lastExportedSrt: File?,
    lastExportedTxt: File?,
    isRenderingFFmpeg: Boolean,
    ffmpegRenderProgress: Float,
    ffmpegStatusMessage: String,
    lastDownloadedFileName: String?,
    ffmpegOptions: FFmpegOptions,
    onUpdateFFmpegOptions: (FFmpegOptions) -> Unit,
    onTriggerFFmpegRender: (FFmpegOptions) -> Unit,
    onDownloadFile: (File, String) -> Unit,
    onOpenFile: (File, String) -> Unit,
    onRunFullPipeline: () -> Unit,
    onExportSrt: () -> Unit,
    onExportTranscript: () -> Unit,
    batteryInfo: com.example.util.BatteryInfo = com.example.util.BatteryInfo(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. DEDICATED FINAL EXPORT BUTTON COMPONENT (Triggers FFmpeg process & handles downloads)
        FinalExportButton(
            project = project,
            isRendering = isRenderingFFmpeg,
            renderProgress = ffmpegRenderProgress,
            statusMessage = ffmpegStatusMessage,
            lastExportedVideo = lastExportedVideo,
            lastExportedSrt = lastExportedSrt,
            lastDownloadedFileName = lastDownloadedFileName,
            ffmpegOptions = ffmpegOptions,
            onUpdateFFmpegOptions = onUpdateFFmpegOptions,
            onTriggerFFmpegRender = onTriggerFFmpegRender,
            onDownloadFile = onDownloadFile,
            onOpenFile = onOpenFile,
            batteryInfo = batteryInfo
        )

        // 2. ADDITIONAL UTILITIES & FULL PIPELINE CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("export_studio_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = StudioSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = "Xuất video",
                        tint = StudioCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Quy trình Tự động hóa Toàn diện (4 Bước)",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Instructions: Unified top bar action
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioSurfaceCardHover,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = StudioCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Toàn bộ quy trình Dịch thuật & Xuất video đã được quy hoạch đồng nhất tại thanh công cụ trên cùng.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Export Actions Grid
                Text(
                    text = "Tải file đầu ra độc lập:",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Export .SRT Button
                    OutlinedButton(
                        onClick = onExportSrt,
                        modifier = Modifier.weight(1f).testTag("export_srt_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioCyan.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = "SRT",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tải .SRT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Export Transcript .TXT Button
                    OutlinedButton(
                        onClick = onExportTranscript,
                        modifier = Modifier.weight(1f).testTag("export_txt_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioPurpleLight),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioPurpleLight.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "TXT",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tải Kịch bản", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // System Protection Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = "Protection",
                        tint = StudioGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "FFmpeg đa luồng tự động dọn RAM & xóa sạch tempfile sau mỗi lần xuất.",
                        color = StudioGreen.copy(alpha = 0.9f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ExportFileItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    fileName: String,
    fileType: String,
    onShare: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = StudioSurfaceCardHover
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = StudioCyan,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = fileName,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = fileType,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }

            Button(
                onClick = onShare,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan.copy(alpha = 0.2f), contentColor = StudioCyan),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "Chia sẻ", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Gửi / Mở", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun shareFile(context: Context, file: File, mimeType: String) {
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TEXT, "File xuất từ DubStudio AI: ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Chia sẻ ${file.name}"))
    } catch (_: Exception) {
    }
}
