package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AspectRatio
import com.example.data.model.MaskConfig
import com.example.data.model.SubtitleConfig
import com.example.data.model.VideoProject
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioPurpleLight
import com.example.ui.theme.StudioSurfaceCard

@Composable
fun MaskControlsCard(
    project: VideoProject,
    onMaskConfigChange: (MaskConfig) -> Unit,
    onSubtitleConfigChange: (SubtitleConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val mask = project.maskConfig
    val sub = project.subtitleConfig

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mask_controls_card"),
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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Masking",
                        tint = StudioCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "1. Che phụ đề gốc (Subtitle Masking)",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = StudioCyan.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Y: ${mask.yPercent.toInt()}%",
                        color = StudioCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Aspect Ratio & Preset Height Buttons
            Text(
                text = "Vị trí đặt dải che chuẩn tỉ lệ khung hình:",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Preset TikTok 9:16 (75%)
                FilterChip(
                    selected = (mask.yPercent == 75f),
                    onClick = { onMaskConfigChange(mask.copy(yPercent = 75f)) },
                    label = { Text("📱 TikTok 9:16 (75%)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StudioCyan,
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.weight(1f).testTag("preset_tiktok_button")
                )

                // Preset YouTube 16:9 (82%)
                FilterChip(
                    selected = (mask.yPercent == 82f),
                    onClick = { onMaskConfigChange(mask.copy(yPercent = 82f)) },
                    label = { Text("🖥️ YouTube 16:9 (82%)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StudioCyan,
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.weight(1f).testTag("preset_youtube_button")
                )
            }

            // Slider: Tọa độ Y
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tọa độ dọc Y (Độ cao che)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${mask.yPercent.toInt()}%",
                        color = StudioCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = mask.yPercent,
                    onValueChange = { onMaskConfigChange(mask.copy(yPercent = it)) },
                    valueRange = 50f..95f,
                    colors = SliderDefaults.colors(
                        thumbColor = StudioCyan,
                        activeTrackColor = StudioCyan,
                        inactiveTrackColor = StudioBorder
                    ),
                    modifier = Modifier.testTag("mask_y_slider")
                )
            }

            // Slider: Độ mờ đục (Opacity)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Độ đục nền che (Opacity)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(mask.opacity * 100).toInt()}% (Đen đục)",
                        color = StudioPurpleLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = mask.opacity,
                    onValueChange = { onMaskConfigChange(mask.copy(opacity = it)) },
                    valueRange = 0.2f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = StudioPurpleLight,
                        activeTrackColor = StudioPurpleLight,
                        inactiveTrackColor = StudioBorder
                    ),
                    modifier = Modifier.testTag("mask_opacity_slider")
                )
            }

            // Slider: Chiều cao & Chiều rộng khối che
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Width
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Độ rộng: ${mask.widthPercent.toInt()}%",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Slider(
                        value = mask.widthPercent,
                        onValueChange = { onMaskConfigChange(mask.copy(widthPercent = it)) },
                        valueRange = 60f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = StudioCyan,
                            activeTrackColor = StudioCyan
                        )
                    )
                }
                // Height
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Độ cao dải: ${mask.heightPercent.toInt()}%",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Slider(
                        value = mask.heightPercent,
                        onValueChange = { onMaskConfigChange(mask.copy(heightPercent = it)) },
                        valueRange = 5f..18f,
                        colors = SliderDefaults.colors(
                            thumbColor = StudioCyan,
                            activeTrackColor = StudioCyan
                        )
                    )
                }
            }

            // Cỡ chữ phụ đề Tiếng Việt
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Cỡ chữ phụ đề Tiếng Việt",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${sub.fontSizeSp} sp",
                        color = StudioAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = sub.fontSizeSp.toFloat(),
                    onValueChange = { onSubtitleConfigChange(sub.copy(fontSizeSp = it.toInt())) },
                    valueRange = 12f..24f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        thumbColor = StudioAmber,
                        activeTrackColor = StudioAmber,
                        inactiveTrackColor = StudioBorder
                    ),
                    modifier = Modifier.testTag("sub_font_size_slider")
                )
            }
        }
    }
}
