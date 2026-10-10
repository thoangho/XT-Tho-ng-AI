package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SubtitleSegment
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBgDark
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioPurple
import com.example.ui.theme.StudioPurpleLight
import com.example.ui.theme.StudioRed
import com.example.ui.theme.StudioSurfaceCard
import com.example.ui.theme.StudioSurfaceCardHover

import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info

/**
 * Interactive Subtitle Management Interface
 * Maps Chinese text timestamps to Vietnamese translation fields, allowing for manual correction before rendering.
 */
@Composable
fun SubtitleEditorTable(
    segments: List<SubtitleSegment>,
    currentTimeMs: Long,
    isRetranslatingAll: Boolean = false,
    retranslateProgress: Float = 0f,
    retranslateStatusMessage: String = "",
    isSubtitlesConfirmed: Boolean = false,
    onConfirmSubtitles: () -> Unit = {},
    onGoToDubbingTab: () -> Unit = {},
    onUpdateText: (Long, String) -> Unit,
    onUpdateChineseText: (Long, String) -> Unit = { _, _ -> },
    onUpdateTiming: (Long, Long, Long) -> Unit = { _, _, _ -> },
    onNudgeTiming: (Long, Long, Long) -> Unit = { _, _, _ -> },
    onToggleApproval: (Long) -> Unit = {},
    onApproveAll: () -> Unit = {},
    onRetranslateAll: () -> Unit = {},
    onAddShortSegment: () -> Unit = {},
    onRetranslate: (SubtitleSegment) -> Unit,
    onPreviewVoice: (SubtitleSegment) -> Unit,
    onDeleteSegment: (SubtitleSegment) -> Unit,
    onMergeWithNext: (Long) -> Unit = {},
    onSplitSegment: (Long) -> Unit = {},
    onAddNewSegment: () -> Unit,
    onInsertAtCurrentTime: () -> Unit = {},
    onSeekTo: (Long) -> Unit,
    onImportSubtitleFile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, UNAPPROVED, EDITED, SPEED_WARNING

    val filteredSegments = remember(segments, searchQuery, selectedFilter) {
        segments.filter { seg ->
            val matchesQuery = searchQuery.isBlank() ||
                    seg.originalChinese.contains(searchQuery, ignoreCase = true) ||
                    seg.vietnameseText.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "UNAPPROVED" -> !seg.isApproved
                "EDITED" -> seg.isEdited
                "SPEED_WARNING" -> {
                    val durationSec = seg.durationMs / 1000f
                    val wordCount = seg.vietnameseText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    durationSec > 0 && (wordCount / durationSec) > 3.8f
                }
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    val approvedCount = remember(segments) { segments.count { it.isApproved } }
    val warningCount = remember(segments) {
        segments.count { seg ->
            val durationSec = seg.durationMs / 1000f
            val wordCount = seg.vietnameseText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
            durationSec > 0 && (wordCount / durationSec) > 3.8f
        }
    }

    if (segments.isEmpty()) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("subtitle_management_empty_container"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = StudioSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = StudioCyan.copy(alpha = 0.15f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            tint = StudioCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Trang quản lý phụ đề",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Vui lòng chọn 1 trong 2 phương thức dưới đây để tạo phụ đề:",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                // Cung cấp 2 tính năng rõ rệt theo Nhiệm vụ 2
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // TÍNH NĂNG 1: TỰ ĐỘNG DỊCH PHỤ ĐỀ BẰNG AI
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = StudioPurple.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioPurple.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "1. Tự động dịch bằng AI",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Whisper AI bóc tách tiếng gốc & Gemini dịch chuẩn ngữ cảnh",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            }
                            Button(
                                onClick = onRetranslateAll,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StudioPurple,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.testTag("empty_state_translate_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Dịch AI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // TÍNH NĂNG 2: TẢI LÊN FILE PHỤ ĐỀ CÓ SẴN (.SRT / .VTT)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = StudioCyan.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioCyan.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "2. Tải lên file phụ đề (.srt / .vtt)",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tự động co giãn thời gian vừa khít 100% video",
                                    color = StudioCyan,
                                    fontSize = 11.sp
                                )
                            }
                            Button(
                                onClick = onImportSubtitleFile,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StudioCyan,
                                    contentColor = StudioBgDark
                                ),
                                modifier = Modifier.testTag("empty_state_import_subtitle_button")
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tải file", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("subtitle_management_container"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // HAI TÍNH NĂNG TẠO PHỤ ĐỀ TRÊN CÙNG
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRetranslateAll,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioPurple.copy(alpha = 0.85f),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Dịch lại bằng AI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onImportSubtitleFile,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioCyan,
                    contentColor = StudioBgDark
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tải file phụ đề", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // KHỐI "PHỤ ĐỀ ĐÃ ĐƯỢC XÁC NHẬN" (LỖI 3)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSubtitlesConfirmed) StudioGreen.copy(alpha = 0.12f) else StudioSurfaceCard
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isSubtitlesConfirmed) StudioGreen else StudioBorder
            )
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            imageVector = if (isSubtitlesConfirmed) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isSubtitlesConfirmed) StudioGreen else StudioAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isSubtitlesConfirmed) "Phụ đề đã được xác nhận" else "Xác nhận & Duyệt phụ đề",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSubtitlesConfirmed) StudioGreen.copy(alpha = 0.2f) else StudioAmber.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "$approvedCount/${segments.size} đã duyệt",
                            color = if (isSubtitlesConfirmed) StudioGreen else StudioAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (!isSubtitlesConfirmed) {
                    Button(
                        onClick = onConfirmSubtitles,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioGreen,
                            contentColor = StudioBgDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("confirm_and_approve_subtitles_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Xác nhận & Duyệt phụ đề",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    // Khi đã xác nhận: Xóa bỏ nút Cập nhật và toàn bộ dòng chú thích phụ rườm rà.
                    // Chỉ giữ lại 01 nút duy nhất: "Sang Lồng tiếng ->" để chuyển Tab.
                    Button(
                        onClick = onGoToDubbingTab,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioCyan,
                            contentColor = StudioBgDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("go_to_dubbing_tab_button")
                    ) {
                        Text("Sang Lồng tiếng ->", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }

        // KHỐI "BẢNG KHỚP NỐI & BIÊN TẬP PHỤ ĐỀ" (LỖI 3: Giảm chiều cao khung Header xuống tối thiểu, thu nhỏ textSize, ẩn mô tả phụ)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = StudioSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header & Title siêu gọn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = StudioCyan.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, StudioCyan.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                tint = StudioCyan,
                                modifier = Modifier
                                    .padding(3.dp)
                                    .size(13.dp)
                            )
                        }

                        Text(
                            text = "Bảng Khớp Nối & Biên Tập Phụ Đề",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // Đã ẩn hoàn toàn dòng mô tả phụ ("Map mốc thời gian...") để tối đa hóa diện tích hiển thị danh sách câu thoại
                    }

                    // Approved Counter Badge nhỏ gọn
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (approvedCount == segments.size) StudioGreen.copy(alpha = 0.2f) else StudioAmber.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            0.6.dp,
                            if (approvedCount == segments.size) StudioGreen else StudioAmber
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (approvedCount == segments.size) StudioGreen else StudioAmber,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "$approvedCount/${segments.size} đã duyệt",
                                color = if (approvedCount == segments.size) StudioGreen else StudioAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Search Bar & Filter Row nhỏ gọn
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("subtitle_search_input"),
                    placeholder = {
                        Text(
                            text = "Tìm kiếm câu thoại (Trung hoặc Việt)...",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = StudioCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StudioCyan,
                        unfocusedBorderColor = StudioBorder,
                        focusedContainerColor = Color(0xFF0C121F),
                        unfocusedContainerColor = Color(0xFF0C121F),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp)
                )

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = (selectedFilter == "ALL"),
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("Tất cả (${segments.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StudioCyan,
                            selectedLabelColor = StudioBgDark
                        ),
                        modifier = Modifier.testTag("filter_all_chip")
                    )

                    FilterChip(
                        selected = (selectedFilter == "UNAPPROVED"),
                        onClick = { selectedFilter = "UNAPPROVED" },
                        label = { Text("Chưa duyệt (${segments.size - approvedCount})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StudioAmber,
                            selectedLabelColor = StudioBgDark
                        ),
                        modifier = Modifier.testTag("filter_unapproved_chip")
                    )

                    if (warningCount > 0) {
                        FilterChip(
                            selected = (selectedFilter == "SPEED_WARNING"),
                            onClick = { selectedFilter = "SPEED_WARNING" },
                            label = { Text("⚠️ Cảnh báo ($warningCount)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StudioRed,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("filter_warning_chip")
                        )
                    }
                }

                // Primary Retranslation Action Card: Multi-pass AI with Short-segment Preservation
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onRetranslateAll,
                        enabled = !isRetranslatingAll,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("retranslate_all_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioCyan,
                            contentColor = StudioBgDark
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRetranslatingAll) "Đang dịch chuẩn 3 lượt ($retranslateStatusMessage)" else "Dịch Chuẩn Đa Tầng (3 lượt AI - Quét trọn câu ngắn)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isRetranslatingAll) {
                        LinearProgressIndicator(
                            progress = { retranslateProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = StudioCyan,
                            trackColor = StudioBorder
                        )
                        Text(
                            text = retranslateStatusMessage,
                            color = StudioCyan,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }

                // Batch Operations & Quick Insertion Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Approve All Button
                    OutlinedButton(
                        onClick = onApproveAll,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("approve_all_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioGreen),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioGreen.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Duyệt hết", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Add Short Segment Button
                    OutlinedButton(
                        onClick = onAddShortSegment,
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("add_short_segment_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioAmber),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioAmber.copy(alpha = 0.7f)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("+ Câu ngắn", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Insert at current video playback position
                    OutlinedButton(
                        onClick = onInsertAtCurrentTime,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("insert_at_playback_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioCyan.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Chèn mốc", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // MAPPED SUBTITLE SEGMENTS LIST
        if (filteredSegments.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = StudioSurfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Không tìm thấy đoạn phụ đề nào phù hợp",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            filteredSegments.forEachIndexed { index, seg ->
                val isActive = currentTimeMs in seg.startTimeMs..seg.endTimeMs
                InteractiveSubtitleMappingCard(
                    segment = seg,
                    index = index + 1,
                    isActive = isActive,
                    onUpdateText = { onUpdateText(seg.id, it) },
                    onUpdateChineseText = { onUpdateChineseText(seg.id, it) },
                    onUpdateTiming = { start, end -> onUpdateTiming(seg.id, start, end) },
                    onNudgeTiming = { dStart, dEnd -> onNudgeTiming(seg.id, dStart, dEnd) },
                    onToggleApproval = { onToggleApproval(seg.id) },
                    onRetranslate = { onRetranslate(seg) },
                    onPreviewVoice = { onPreviewVoice(seg) },
                    onDelete = { onDeleteSegment(seg) },
                    onMergeWithNext = { onMergeWithNext(seg.id) },
                    onSplit = { onSplitSegment(seg.id) },
                    onSeek = { onSeekTo(seg.startTimeMs) }
                )
            }
        }

        // Add Segment Button
        Button(
            onClick = onAddNewSegment,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("add_segment_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = StudioSurfaceCardHover,
                contentColor = StudioCyan
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Thêm đoạn",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Thêm câu thoại mới vào cuối video",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Individual Interactive Subtitle Card:
 * Mappings:
 * - Start / End timestamps with duration badge
 * - Original Chinese text (Whisper AI)
 * - Mapped Vietnamese translation field for manual editing
 * - Character count / reading speed safety check
 * - Interactive timestamp fine-tuning
 */
@Composable
fun InteractiveSubtitleMappingCard(
    segment: SubtitleSegment,
    index: Int,
    isActive: Boolean,
    onUpdateText: (String) -> Unit,
    onUpdateChineseText: (String) -> Unit = {},
    onUpdateTiming: (Long, Long) -> Unit,
    onNudgeTiming: (Long, Long) -> Unit,
    onToggleApproval: () -> Unit,
    onRetranslate: () -> Unit,
    onPreviewVoice: () -> Unit,
    onDelete: () -> Unit,
    onMergeWithNext: () -> Unit,
    onSplit: () -> Unit,
    onSeek: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var isTimingExpanded by remember { mutableStateOf(false) }
    var isEditingChinese by remember { mutableStateOf(false) }
    var localVietnameseText by remember(segment.vietnameseText) { mutableStateOf(segment.vietnameseText) }
    var localChineseText by remember(segment.originalChinese) { mutableStateOf(segment.originalChinese) }

    // Reading speed safety calculation
    val durationSec = segment.durationMs / 1000f
    val wordCount = remember(localVietnameseText) {
        localVietnameseText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
    }
    val wordsPerSecond = if (durationSec > 0) wordCount / durationSec else 0f
    val isSpeedWarning = wordsPerSecond > 3.8f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("interactive_subtitle_card_$index"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF19253E) else StudioSurfaceCard
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isActive) 1.8.dp else 1.dp,
            color = if (isActive) StudioCyan else StudioBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ROW 1: Index, Timestamp Mapping Range, Duration, Approval Toggle & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Index & Interactive Timestamp Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Index badge
                    Surface(
                        shape = CircleShape,
                        color = if (isActive) StudioCyan else StudioBorder,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$index",
                                color = if (isActive) StudioBgDark else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Clickable Timestamp Range (Click seeks video player)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isActive) StudioCyan.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            if (isActive) StudioCyan else StudioBorder
                        ),
                        modifier = Modifier.clickable { onSeek() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Seek",
                                tint = StudioCyan,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${segment.startTimeFormatted} → ${segment.endTimeFormatted}",
                                color = StudioCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Duration Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = StudioPurpleLight.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "${String.format("%.1f", durationSec)}s",
                            color = StudioPurpleLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // Short segment badge
                    if (segment.durationMs <= 1600L) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = StudioCyan.copy(alpha = 0.25f),
                            border = androidx.compose.foundation.BorderStroke(0.6.dp, StudioCyan)
                        ) {
                            Text(
                                text = "⚡ Câu ngắn",
                                color = StudioCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Edit indicator
                    if (segment.isEdited) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = StudioAmber.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Đã sửa",
                                color = StudioAmber,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Row action buttons & Approval Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Approval Toggle Chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (segment.isApproved) StudioGreen.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(
                            0.8.dp,
                            if (segment.isApproved) StudioGreen else StudioBorder
                        ),
                        modifier = Modifier
                            .clickable { onToggleApproval() }
                            .testTag("approval_toggle_$index")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = if (segment.isApproved) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = null,
                                tint = if (segment.isApproved) StudioGreen else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = if (segment.isApproved) "Đã duyệt" else "Chờ duyệt",
                                color = if (segment.isApproved) StudioGreen else Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Timing Adjuster Toggle
                    IconButton(
                        onClick = { isTimingExpanded = !isTimingExpanded },
                        modifier = Modifier.size(30.dp).testTag("timing_toggle_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Căn chỉnh thời gian",
                            tint = if (isTimingExpanded) StudioCyan else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Listen voice preview
                    IconButton(
                        onClick = onPreviewVoice,
                        modifier = Modifier.size(30.dp).testTag("preview_voice_$index")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Nghe thử",
                            tint = StudioCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Re-translate
                    IconButton(
                        onClick = onRetranslate,
                        modifier = Modifier.size(30.dp).testTag("retranslate_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Dịch lại",
                            tint = StudioPurpleLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Delete
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp).testTag("delete_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Xóa",
                            tint = StudioRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // EXPANDABLE TIMING FINE-TUNER (Nudge +/- 100ms or 500ms)
            AnimatedVisibility(
                visible = isTimingExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioCyan.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⏱️ Tinh chỉnh khớp nối thời gian xuất hiện (Micro-Timing):",
                            color = StudioCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Start Time Nudger
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Bắt đầu: ${segment.startTimeFormatted}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                NudgeButton(label = "-0.5s") { onNudgeTiming(-500L, 0L) }
                                NudgeButton(label = "-0.1s") { onNudgeTiming(-100L, 0L) }
                                NudgeButton(label = "+0.1s") { onNudgeTiming(100L, 0L) }
                                NudgeButton(label = "+0.5s") { onNudgeTiming(500L, 0L) }
                            }
                        }

                        // End Time Nudger
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Kết thúc: ${segment.endTimeFormatted}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                NudgeButton(label = "-0.5s") { onNudgeTiming(0L, -500L) }
                                NudgeButton(label = "-0.1s") { onNudgeTiming(0L, -100L) }
                                NudgeButton(label = "+0.1s") { onNudgeTiming(0L, 100L) }
                                NudgeButton(label = "+0.5s") { onNudgeTiming(0L, 500L) }
                            }
                        }

                        // Split & Merge Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onSplit,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioCyan),
                                border = androidx.compose.foundation.BorderStroke(0.6.dp, StudioCyan),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tách đôi câu", fontSize = 10.sp)
                            }

                            OutlinedButton(
                                onClick = onMergeWithNext,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioPurpleLight),
                                border = androidx.compose.foundation.BorderStroke(0.6.dp, StudioPurpleLight),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gộp câu kế", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // ROW 2: MAPPED SOURCE - ORIGINAL CHINESE TEXT (EDITABLE)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(0.6.dp, if (isEditingChinese) StudioCyan else StudioBorder)
            ) {
                if (isEditingChinese) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = localChineseText,
                            onValueChange = {
                                localChineseText = it
                                onUpdateChineseText(it)
                            },
                            label = { Text("🇨🇳 Chỉnh sửa câu thoại tiếng Trung gốc", color = Color(0xFFFFEB3B), fontSize = 11.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chinese_input_$index"),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFFFEB3B), fontSize = 13.sp),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { isEditingChinese = false },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Lưu câu tiếng Trung", tint = StudioGreen, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = onRetranslate,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = "Dịch câu này", tint = StudioCyan, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "🇨🇳 Tiếng Trung gốc:",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "(${localChineseText.length} ký tự)",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (localChineseText.isNotBlank()) localChineseText else "(Chưa có câu thoại tiếng Trung)",
                                color = if (localChineseText.isNotBlank()) Color(0xFFFFEB3B) else Color.White.copy(alpha = 0.4f), // Classic yellow Douyin subtitle color
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Edit Chinese button
                            IconButton(
                                onClick = { isEditingChinese = true },
                                modifier = Modifier.size(28.dp).testTag("edit_chinese_$index")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Sửa tiếng Trung",
                                    tint = StudioCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Copy Chinese text button
                            IconButton(
                                onClick = { clipboardManager.setText(AnnotatedString(localChineseText)) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Sao chép tiếng Trung",
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ROW 3: MAPPED TARGET - EDITABLE VIETNAMESE TRANSLATION
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = localVietnameseText,
                    onValueChange = {
                        localVietnameseText = it
                        onUpdateText(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vietnamese_input_$index"),
                    label = {
                        Text(
                            text = "🇻🇳 Lời thoại Tiếng Việt (Chỉnh sửa trực tiếp trước khi render)",
                            color = StudioCyan,
                            fontSize = 11.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StudioCyan,
                        unfocusedBorderColor = if (isSpeedWarning) StudioAmber else StudioBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF0C1322),
                        unfocusedContainerColor = Color(0xFF0C1322)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
                    singleLine = false,
                    maxLines = 3
                )

                // Live Reading Speed & Warning Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSpeedWarning) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Cảnh báo",
                                tint = StudioAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Lời thoại dài (~${String.format("%.1f", wordsPerSecond)} từ/giây). Khuyên dùng câu ngắn hơn.",
                                color = StudioAmber,
                                fontSize = 10.sp
                            )
                        }
                    } else {
                        Text(
                            text = "Tốc độ đọc tối ưu: ~${String.format("%.1f", wordsPerSecond)} từ/giây",
                            color = StudioGreen,
                            fontSize = 10.sp
                        )
                    }

                    Text(
                        text = "$wordCount từ • ${localVietnameseText.length} ký tự",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun NudgeButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = StudioSurfaceCardHover,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, StudioBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = StudioCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
