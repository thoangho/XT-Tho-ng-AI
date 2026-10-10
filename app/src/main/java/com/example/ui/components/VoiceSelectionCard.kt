package com.example.ui.components

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DubbingConfig
import com.example.data.model.VoiceOption
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

@Composable
fun VoiceSelectionCard(
    dubbingConfig: DubbingConfig,
    availableVoices: List<VoiceOption>,
    isSubtitlesConfirmed: Boolean,
    approvedSegmentsCount: Int,
    isDubbingPlaying: Boolean,
    onStartDubbing: () -> Unit,
    onStopDubbing: () -> Unit,
    onGoToSubtitleTab: () -> Unit,
    onSelectVoice: (VoiceOption) -> Unit,
    onDubbingConfigChange: (DubbingConfig) -> Unit,
    onTestVoice: () -> Unit,
    isDubbingGenerating: Boolean = false,
    dubbingGenerationProgress: Float = 0f,
    dubbingProgressInt: Int = 0,
    dubbingStatusMessage: String = "",
    onGenerateDubbingFromSrt: () -> Unit = {},
    onImportSrt: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_selection_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Voice Dubbing",
                        tint = StudioCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Lồng tiếng AI Tiếng Việt",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tạo giọng nói AI tự nhiên dựa trên phụ đề đã duyệt",
                            color = StudioCyan,
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = onTestVoice,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioCyan.copy(alpha = 0.2f),
                        contentColor = StudioCyan
                    ),
                    modifier = Modifier.testTag("test_voice_sample_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Nghe thử",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nghe thử", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // TRẠNG THÁI KIỂM SOÁT PHỤ ĐỀ TRƯỚC KHI LỒNG TIẾNG (Mục tiêu 2)
            if (!isSubtitlesConfirmed || approvedSegmentsCount == 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = StudioAmber.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioAmber.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StudioAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Chưa có phụ đề tiếng Việt được duyệt",
                                color = StudioAmber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Tính năng này chỉ xử lý lồng tiếng dựa trên các dòng Text phụ đề tiếng Việt đã được người dùng xác nhận. Vui lòng chuyển sang Tab Phụ đề để dịch/chỉnh sửa và bấm 'Xác nhận/Duyệt' trước khi tạo giọng nói.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        Button(
                            onClick = onGoToSubtitleTab,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioAmber,
                                contentColor = StudioBgDark
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Chuyển sang Tab Phụ đề để duyệt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = StudioGreen.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioGreen.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StudioGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Đã chốt $approvedSegmentsCount câu phụ đề tiếng Việt",
                                    color = StudioGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Sẵn sàng để AI đọc lồng tiếng theo đúng dữ liệu đã xác nhận",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onGoToSubtitleTab,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, StudioBorder),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Sửa text", fontSize = 10.sp)
                        }
                    }
                }
            }

            // NÚT 'TẠO LỒNG TIẾNG / DỪNG ĐỌC LỒNG TIẾNG' (Lỗi 1)
            val isDubbingActive = isDubbingGenerating || isDubbingPlaying
            Button(
                onClick = {
                    if (isDubbingActive) {
                        onStopDubbing()
                    } else {
                        onStartDubbing()
                    }
                },
                enabled = approvedSegmentsCount > 0,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDubbingActive) StudioRed else StudioGreen,
                    contentColor = if (isDubbingActive) Color.White else StudioBgDark,
                    disabledContainerColor = StudioSurfaceCardHover,
                    disabledContentColor = Color.White.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("start_dubbing_button")
            ) {
                Icon(
                    imageVector = if (isDubbingActive) Icons.Default.Stop else Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isDubbingActive) "Dừng đọc lồng tiếng" else "Tạo Lồng Tiếng",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // BỔ SUNG THANH TIẾN TRÌNH & TEXTVIEW HIỂN THỊ TỶ LỆ % TIẾN ĐỘ ĐỌC REAL-TIME (Lỗi 1)
            if (isDubbingGenerating) {
                val displayPercent = if (dubbingProgressInt > 0) dubbingProgressInt else (dubbingGenerationProgress * 100).toInt().coerceIn(0, 100)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dubbing_progress_container"),
                    shape = RoundedCornerShape(12.dp),
                    color = StudioCyan.copy(alpha = 0.15f),
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
                            Text(
                                text = "Tiến độ lồng tiếng AI:",
                                color = StudioCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("dubbing_progress_label")
                            )
                            Text(
                                text = "$displayPercent%",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("dubbing_percentage_text")
                            )
                        }

                        LinearProgressIndicator(
                            progress = { (displayPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .testTag("dubbing_progress_bar"),
                            color = StudioCyan,
                            trackColor = Color(0xFF1E293B)
                        )

                        Text(
                            text = if (dubbingStatusMessage.isNotBlank()) dubbingStatusMessage else "Đang khởi tạo âm thanh...",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("dubbing_status_text")
                        )
                    }
                }
            }

            // HÀNG NÚT LUỒNG 2: TẠO LỒNG TIẾNG TỪ TỆP .SRT ĐỘC LẬP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGenerateDubbingFromSrt,
                    enabled = (isSubtitlesConfirmed && approvedSegmentsCount > 0) && !isDubbingGenerating,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioPurple,
                        contentColor = Color.White,
                        disabledContainerColor = StudioSurfaceCardHover,
                        disabledContentColor = Color.White.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(44.dp)
                        .testTag("generate_dubbing_from_srt_button")
                ) {
                    Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isDubbingGenerating) "Đang xuất WAV..." else "Tạo Lồng Tiếng Từ SRT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onImportSrt,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioCyan.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioCyan),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("import_external_srt_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nạp .SRT máy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // TIẾN TRÌNH TẠO FILE LỒNG TIẾNG AI ĐỘC LẬP (LUỒNG 2)
            if (isDubbingGenerating) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = StudioPurple.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioPurple.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tiến trình tạo tệp Lồng tiếng AI:",
                                color = StudioPurpleLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${(dubbingGenerationProgress * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { dubbingGenerationProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = StudioPurple,
                            trackColor = StudioSurfaceCardHover
                        )
                        Text(
                            text = dubbingStatusMessage,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            if (isDubbingPlaying) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = StudioCyan.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioCyan.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = StudioCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "AI đang đọc thoại & hòa âm hạ nền BGM tự động theo mốc thời gian...",
                            color = StudioCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Danh sách giọng đọc AI chuẩn Tiếng Việt
            var selectedFilter by remember { mutableStateOf("Tất cả") }
            val filterOptions = listOf(
                "Tất cả (${availableVoices.size})",
                "Nữ",
                "Nam",
                "Phim & Kịch",
                "Vlog & MC"
            )

            val filteredVoices = when {
                selectedFilter.startsWith("Tất cả") -> availableVoices
                selectedFilter == "Nữ" -> availableVoices.filter { it.gender == "Nữ" }
                selectedFilter == "Nam" -> availableVoices.filter { it.gender == "Nam" }
                selectedFilter == "Phim & Kịch" -> availableVoices.filter {
                    it.category.contains("Phim") || it.category.contains("Điện ảnh") || it.category.contains("Hoạt hình") || it.category.contains("Lồng tiếng")
                }
                selectedFilter == "Vlog & MC" -> availableVoices.filter {
                    it.category.contains("Trẻ trung") || it.category.contains("MC") || it.category.contains("Review") || it.category.contains("Công nghệ")
                }
                else -> availableVoices
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Chọn giọng đọc AI chuẩn Tiếng Việt (${filteredVoices.size}/${availableVoices.size}):",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filterOptions.forEach { filter ->
                        val isFilterSelected = (selectedFilter.startsWith("Tất cả") && filter.startsWith("Tất cả")) || selectedFilter == filter
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isFilterSelected) StudioCyan else StudioSurfaceCardHover,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isFilterSelected) StudioCyan else StudioBorder
                            ),
                            modifier = Modifier.clickable { selectedFilter = filter }
                        ) {
                            Text(
                                text = filter,
                                color = if (isFilterSelected) StudioBgDark else Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = if (isFilterSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredVoices.forEach { voice ->
                        val isSelected = dubbingConfig.voiceId == voice.id
                        VoiceOptionItem(
                            voice = voice,
                            isSelected = isSelected,
                            onClick = { onSelectVoice(voice) }
                        )
                    }
                }
            }

            // Hòa âm tự động (Audio Ducking Mixer)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "Audio Mixer",
                            tint = StudioAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Hòa âm & Tốc độ đọc (Audio Mixer)",
                            color = StudioAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Slider: Nhạc nền / Thoại gốc (10% default)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Âm lượng nhạc nền / tiếng gốc (BGM)",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${(dubbingConfig.originalAudioVolume * 100).toInt()}% (Hạ nền tự động)",
                                color = StudioCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = dubbingConfig.originalAudioVolume,
                            onValueChange = { onDubbingConfigChange(dubbingConfig.copy(originalAudioVolume = it)) },
                            valueRange = 0.0f..0.50f,
                            colors = SliderDefaults.colors(
                                thumbColor = StudioCyan,
                                activeTrackColor = StudioCyan
                            ),
                            modifier = Modifier.testTag("bgm_volume_slider")
                        )
                    }

                    // Slider: Âm lượng lồng tiếng AI (100% default)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Âm lượng lồng tiếng AI Tiếng Việt",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${(dubbingConfig.dubVoiceVolume * 100).toInt()}%",
                                color = StudioGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = dubbingConfig.dubVoiceVolume,
                            onValueChange = { onDubbingConfigChange(dubbingConfig.copy(dubVoiceVolume = it)) },
                            valueRange = 0.5f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = StudioGreen,
                                activeTrackColor = StudioGreen
                            ),
                            modifier = Modifier.testTag("tts_volume_slider")
                        )
                    }

                    // Speech Rate Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tốc độ: ${String.format("%.2f", dubbingConfig.speechRate)}x",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                            Slider(
                                value = dubbingConfig.speechRate,
                                onValueChange = { onDubbingConfigChange(dubbingConfig.copy(speechRate = it)) },
                                valueRange = 0.8f..1.4f,
                                colors = SliderDefaults.colors(thumbColor = StudioPurpleLight, activeTrackColor = StudioPurpleLight)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cao độ: ${String.format("%.2f", dubbingConfig.pitch)}x",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                            Slider(
                                value = dubbingConfig.pitch,
                                onValueChange = { onDubbingConfigChange(dubbingConfig.copy(pitch = it)) },
                                valueRange = 0.8f..1.3f,
                                colors = SliderDefaults.colors(thumbColor = StudioAmber, activeTrackColor = StudioAmber)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceOptionItem(
    voice: VoiceOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("voice_option_${voice.id}"),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFF1E293B) else StudioSurfaceCardHover,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) StudioCyan else StudioBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Surface(
                shape = CircleShape,
                color = if (isSelected) StudioCyan.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.4f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = voice.avatarEmoji, fontSize = 22.sp)
                }
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${voice.gender} - ${voice.name} (${voice.region})",
                        color = if (isSelected) StudioCyan else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isSelected) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = StudioCyan
                        ) {
                            Text(
                                text = "ĐANG CHỌN",
                                color = StudioBgDark,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = voice.description,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Tag thể loại
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) StudioCyan.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = voice.category,
                        color = if (isSelected) StudioCyan else Color.White.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Nút nghe thử / chỉ báo âm thanh
            Surface(
                shape = CircleShape,
                color = if (isSelected) StudioCyan else Color.White.copy(alpha = 0.08f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSelected) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PlayArrow,
                        contentDescription = "Chọn & Nghe thử giọng",
                        tint = if (isSelected) StudioBgDark else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
