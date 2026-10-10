package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VideoProject
import com.example.data.video.FFmpegOptions
import com.example.util.BatteryInfo
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

/**
 * Final Export Button UI Component:
 * - Triggers FFmpeg rendering process (Multi-threaded H.264 Ultrafast, Audio Ducking Mix, Subtitle Mask Burn-in)
 * - Displays live encoding progress & stage updates
 * - Handles user file downloads directly to device storage (/Downloads/DubStudio)
 * - Provides immediate Open & Share actions
 */
@Composable
fun FinalExportButton(
    project: VideoProject,
    isRendering: Boolean,
    renderProgress: Float,
    statusMessage: String,
    lastExportedVideo: File?,
    lastExportedSrt: File?,
    lastDownloadedFileName: String?,
    ffmpegOptions: FFmpegOptions,
    onUpdateFFmpegOptions: (FFmpegOptions) -> Unit,
    onTriggerFFmpegRender: (FFmpegOptions) -> Unit,
    onDownloadFile: (File, String) -> Unit,
    onOpenFile: (File, String) -> Unit,
    batteryInfo: BatteryInfo = BatteryInfo(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isSettingsExpanded by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "export_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("final_export_button_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isRendering) 2.dp else 1.2.dp,
            color = if (isRendering) StudioCyan else StudioCyan.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with FFmpeg specs & toggle settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(StudioCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, StudioCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = StudioCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Xuất Video Hoàn Chỉnh",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${ffmpegOptions.videoCodec} • ${ffmpegOptions.preset} • ${ffmpegOptions.threads} threads",
                            color = StudioCyan,
                            fontSize = 11.sp
                        )
                    }
                }

                // Settings toggle button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StudioSurfaceCardHover,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, StudioBorder),
                    modifier = Modifier.clickable { isSettingsExpanded = !isSettingsExpanded }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Cấu hình xuất video",
                            tint = StudioCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isSettingsExpanded) "Thu gọn" else "Cấu hình",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                        Icon(
                            imageVector = if (isSettingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Expandable Configurations
            AnimatedVisibility(
                visible = isSettingsExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Tham số tối ưu hóa kết xuất (Chống quá tải & Tăng tốc):",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )

                        // Preset Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Preset:", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(55.dp))
                            FilterChip(
                                selected = (ffmpegOptions.preset == "ultrafast"),
                                onClick = { onUpdateFFmpegOptions(ffmpegOptions.copy(preset = "ultrafast")) },
                                label = { Text("ultrafast (Siêu tốc)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = StudioCyan,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.testTag("preset_ultrafast_chip")
                            )
                            FilterChip(
                                selected = (ffmpegOptions.preset == "superfast"),
                                onClick = { onUpdateFFmpegOptions(ffmpegOptions.copy(preset = "superfast")) },
                                label = { Text("superfast", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = StudioCyan,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }

                        // Bitrate Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Bitrate:", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(55.dp))
                            FilterChip(
                                selected = (ffmpegOptions.bitrateKbps == 3000),
                                onClick = { onUpdateFFmpegOptions(ffmpegOptions.copy(bitrateKbps = 3000)) },
                                label = { Text("3.0 Mbps (Nhẹ)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = StudioCyan,
                                    selectedLabelColor = Color.Black
                                )
                            )
                            FilterChip(
                                selected = (ffmpegOptions.bitrateKbps == 5000),
                                onClick = { onUpdateFFmpegOptions(ffmpegOptions.copy(bitrateKbps = 5000)) },
                                label = { Text("5.0 Mbps (HD)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = StudioCyan,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }
            }

            // EXPORT INSTRUCTION NOTICE (Consolidated Single Export Button UX)
            // Low Battery Warning for Video Rendering
            if (batteryInfo.isLowBattery) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF2B1F0B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_battery_warning")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = StudioAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "⚠️ Cảnh báo mức pin (${batteryInfo.level}% - Không sạc)",
                                color = StudioAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Quá trình render video cần nhiều tài nguyên CPU/GPU. Vui lòng kết nối bộ sạc để tránh sập nguồn làm hỏng tệp video.",
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            if (!isRendering) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_instruction_notice"),
                    shape = RoundedCornerShape(12.dp),
                    color = StudioSurfaceCardHover,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioCyan.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = StudioGreen.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = StudioGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sẵn sàng xuất video hoàn chỉnh",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Nhấn nút 'Xuất video' ở góc trên cùng bên phải màn hình để xuất file MP4 trọn vẹn (Hình ảnh + Giọng lồng tiếng + Phụ đề).",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            } else {
                // LIVE RENDERING PROGRESS VIEW
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioCyan.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.5.dp,
                                    color = StudioCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = statusMessage.ifBlank { "Đang xử lý kết xuất video..." },
                                    color = StudioCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = "${(renderProgress * 100).toInt()}%",
                                color = StudioCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LinearProgressIndicator(
                            progress = { renderProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = StudioCyan,
                            trackColor = StudioBorder
                        )

                        Text(
                            text = "Audio Ducking BGM 10% • Subtitle Masking RGBA • Libx264 Ultrafast",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // USER DOWNLOAD & FILE ACTIONS (Appears when video is rendered)
            if (lastExportedVideo != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_actions_container"),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioGreen.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StudioGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Đã xuất xong: ${lastExportedVideo.name}",
                                    color = StudioGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        // Download Notification Badge if downloaded
                        if (lastDownloadedFileName == lastExportedVideo.name) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = StudioGreen.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, StudioGreen)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = StudioGreen,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Đã lưu vào thư mục Download của thiết bị",
                                        color = StudioGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Download & Open Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Primary Download to Device Button
                            Button(
                                onClick = { onDownloadFile(lastExportedVideo, "video/mp4") },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(46.dp)
                                    .testTag("download_video_to_device_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StudioGreen,
                                    contentColor = StudioBgDark
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Tải về máy",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tải Video MP4", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Open in System Player Button
                            OutlinedButton(
                                onClick = { onOpenFile(lastExportedVideo, "video/mp4") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("open_video_player_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioCyan),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StudioCyan)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Xem",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Xem Video", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Subtitle & Transcript Downloads Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (lastExportedSrt != null) {
                                OutlinedButton(
                                    onClick = { onDownloadFile(lastExportedSrt, "text/plain") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("download_srt_file_button"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioCyan),
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, StudioCyan.copy(alpha = 0.6f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Lưu .SRT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Share Action
                            OutlinedButton(
                                onClick = { shareFile(context, lastExportedVideo, "video/mp4") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_video_button"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioPurpleLight),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, StudioPurpleLight.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Chia sẻ", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
