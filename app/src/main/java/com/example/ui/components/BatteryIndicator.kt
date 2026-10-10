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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBgDark
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioSurfaceCard
import com.example.util.BatteryInfo

/**
 * Huy hiệu hiển thị trạng thái và % pin nhỏ gọn ở thanh điều hướng / TopAppBar.
 */
@Composable
fun BatteryIndicatorBadge(
    batteryInfo: BatteryInfo,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val (badgeColor, badgeBg, icon) = when {
        batteryInfo.isCharging -> Triple(
            StudioGreen,
            StudioGreen.copy(alpha = 0.15f),
            Icons.Default.BatteryChargingFull
        )
        batteryInfo.isCriticallyLow -> Triple(
            Color(0xFFFF334B),
            Color(0xFFFF334B).copy(alpha = 0.2f),
            Icons.Default.BatteryAlert
        )
        batteryInfo.isLowBattery -> Triple(
            StudioAmber,
            StudioAmber.copy(alpha = 0.2f),
            Icons.Default.Battery0Bar
        )
        batteryInfo.level > 70 -> Triple(
            Color.White.copy(alpha = 0.9f),
            Color.White.copy(alpha = 0.08f),
            Icons.Default.BatteryFull
        )
        batteryInfo.level > 40 -> Triple(
            Color.White.copy(alpha = 0.85f),
            Color.White.copy(alpha = 0.08f),
            Icons.Default.Battery5Bar
        )
        else -> Triple(
            Color.White.copy(alpha = 0.75f),
            Color.White.copy(alpha = 0.08f),
            Icons.Default.Battery3Bar
        )
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .testTag("battery_indicator_badge"),
        color = badgeBg,
        border = androidx.compose.foundation.BorderStroke(
            width = if (batteryInfo.isLowBattery) 1.dp else 0.5.dp,
            color = if (batteryInfo.isLowBattery) badgeColor else StudioBorder
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Mức pin: ${batteryInfo.level}%",
                tint = badgeColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (batteryInfo.isCharging) "${batteryInfo.level}% ⚡" else "${batteryInfo.level}%",
                color = badgeColor,
                fontSize = 11.sp,
                fontWeight = if (batteryInfo.isLowBattery) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/**
 * Banner cảnh báo nổi bật khi pin yếu (<= 20% và không sạc) trên màn hình chính.
 */
@Composable
fun LowBatteryWarningBanner(
    batteryInfo: BatteryInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = batteryInfo.isLowBattery,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val isCrit = batteryInfo.isCriticallyLow
        val primaryColor = if (isCrit) Color(0xFFFF334B) else StudioAmber
        val containerBg = if (isCrit) Color(0xFF2A0D14) else Color(0xFF2B200A)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("low_battery_warning_banner"),
            color = containerBg,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.8f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = primaryColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isCrit) Icons.Default.Warning else Icons.Default.WarningAmber,
                            contentDescription = "Cảnh báo pin",
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCrit) "Cảnh báo pin cực yếu (${batteryInfo.level}%)" else "Khuyến nghị sạc pin (${batteryInfo.level}%)",
                        color = primaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Thiết bị không cắm sạc. Render video và lồng tiếng AI đòi hỏi CPU cao, hãy kết nối bộ sạc để tránh bị tắt nguồn gây lỗi file!",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng cảnh báo",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Hộp thoại cảnh báo bảo vệ an toàn pin trước khi bắt đầu Render Video dài.
 */
@Composable
fun LowBatteryExportWarningDialog(
    batteryInfo: BatteryInfo,
    onConfirmExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val isCrit = batteryInfo.isCriticallyLow
    val alertColor = if (isCrit) Color(0xFFFF334B) else StudioAmber

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryAlert,
                    contentDescription = null,
                    tint = alertColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Cảnh báo pin yếu (${batteryInfo.level}%)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = alertColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, alertColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Power,
                            contentDescription = null,
                            tint = alertColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Trạng thái: ${batteryInfo.level}% • Đang dùng pin (Không cắm sạc)",
                            color = alertColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = "Quá trình xuất video (kết xuất đồ họa đa luồng, làm mờ sub cũ, hòa âm lồng tiếng) là một tác vụ rất nặng và tiêu thụ nhiều năng lượng.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Text(
                    text = "Nếu thiết bị hết pin và tắt nguồn đột ngột trong khi render, tệp video đầu ra sẽ bị hỏng hoàn toàn và bạn phải xuất lại từ đầu. Vui lòng kết nối sạc để đảm bảo an toàn tuyệt đối.",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmExport,
                colors = ButtonDefaults.buttonColors(
                    containerColor = alertColor,
                    contentColor = StudioBgDark
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Vẫn tiếp tục xuất", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
            ) {
                Text("Để tôi cắm sạc", color = Color.White.copy(alpha = 0.85f))
            }
        },
        containerColor = Color(0xFF141926),
        shape = RoundedCornerShape(16.dp)
    )
}
