package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBgDark
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioPurpleLight
import com.example.ui.theme.StudioSurfaceCard

@Composable
fun ProcessingProgressDialog(
    currentStage: Int,
    stageTitle: String,
    progress: Float,
    logs: List<String>,
    onDismiss: () -> Unit,
    title: String = "Tiến trình Dịch Phụ Đề AI (Độc lập)",
    subtitle: String = "Bóc tách & Dịch thuật -> Tự động lưu .SRT/.TXT",
    totalStages: Int = 2,
    batteryInfo: com.example.util.BatteryInfo? = null
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Dialog(
        onDismissRequest = { /* Prevent accidental cancel during intensive render */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, StudioCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .testTag("processing_progress_dialog"),
            color = StudioBgDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Processing",
                            tint = StudioCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = subtitle,
                                color = StudioGreen,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (batteryInfo != null) {
                            BatteryIndicatorBadge(batteryInfo = batteryInfo)
                        }
                        if (progress >= 1.0f) {
                            IconButton(onClick = onDismiss) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Đóng", tint = Color.White)
                            }
                        }
                    }
                }

                // Low Battery Alert Row during long AI task
                if (batteryInfo?.isLowBattery == true) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2A150A),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, StudioAmber)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("⚡", fontSize = 12.sp)
                            Text(
                                text = "Pin thấp (${batteryInfo.level}%). Vui lòng cắm sạc để duy trì tiến trình không bị gián đoạn!",
                                color = StudioAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 2-Stages Stepper for Independent Subtitle Workflow
                if (totalStages == 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StageStepRow(
                            step = 1,
                            label = "[1/2] Bóc tách âm thanh lời thoại (Whisper AI)",
                            isCurrent = currentStage == 1,
                            isDone = currentStage > 1
                        )
                        StageStepRow(
                            step = 2,
                            label = "[2/2] Dịch ngữ cảnh 100% sang Tiếng Việt & Lưu .SRT",
                            isCurrent = currentStage == 2,
                            isDone = progress >= 1.0f || currentStage > 2
                        )
                    }
                } else {
                    // Multi-stage fallback
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StageStepRow(
                            step = 1,
                            label = "[1/3] Chuẩn bị phân đoạn phụ đề (.SRT)",
                            isCurrent = currentStage == 1,
                            isDone = currentStage > 1
                        )
                        StageStepRow(
                            step = 2,
                            label = "[2/3] Tổng hợp giọng đọc AI (Edge-TTS)",
                            isCurrent = currentStage == 2,
                            isDone = currentStage > 2
                        )
                        StageStepRow(
                            step = 3,
                            label = "[3/3] Gộp luồng âm thanh hoàn chỉnh (.WAV)",
                            isCurrent = currentStage == 3,
                            isDone = progress >= 1.0f
                        )
                    }
                }

                // Overall Progress Bar
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stageTitle,
                            color = StudioCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = StudioCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = StudioCyan,
                        trackColor = StudioBorder
                    )
                }

                // Terminal-Style System Logs (Garbage collection & memory monitoring)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Terminal",
                                tint = StudioAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "HỆ THỐNG GIÁM SÁT HIỆU NĂNG",
                                color = StudioAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(logs) { log ->
                                Text(
                                    text = log,
                                    color = if (log.contains("torch.cuda") || log.contains("Hoàn tất")) StudioGreen else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }

                // Completion Button
                if (progress >= 1.0f) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = StudioBgDark),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Mở bản thành phẩm", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StageStepRow(
    step: Int,
    label: String,
    isCurrent: Boolean,
    isDone: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = when {
                isDone -> StudioGreen
                isCurrent -> StudioCyan
                else -> StudioBorder
            },
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isDone) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Xong",
                        tint = StudioBgDark,
                        modifier = Modifier.size(12.dp)
                    )
                } else if (isCurrent) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = StudioBgDark,
                        modifier = Modifier.size(12.dp)
                    )
                } else {
                    Text(text = "$step", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                }
            }
        }

        Text(
            text = label,
            color = when {
                isDone -> StudioGreen
                isCurrent -> Color.White
                else -> Color.White.copy(alpha = 0.4f)
            },
            fontSize = 12.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
        )
    }
}
