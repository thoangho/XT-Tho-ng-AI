package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.AspectRatio
import com.example.data.model.SubtitleSegment
import com.example.data.model.VideoProject
import com.example.data.video.VideoFrameExtractor
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBgDark
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioPurple
import java.io.File
import kotlin.math.roundToInt

@Composable
fun VideoPlayerWithOverlay(
    project: VideoProject,
    segments: List<SubtitleSegment>,
    currentTimeMs: Long,
    isPlaying: Boolean,
    isSpeakingDubbing: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onMaskYChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Current active segment at this timestamp
    val activeSegment = remember(segments, currentTimeMs) {
        segments.find { currentTimeMs in it.startTimeMs..it.endTimeMs }
    }

    val isVertical = project.aspectRatio == AspectRatio.VERTICAL_9_16
    val ratioValue = if (isVertical) 9f / 16f else 16f / 9f

    // Verify local video file on device storage
    val localVideoFile = remember(project.videoUri) {
        val path = when {
            project.videoUri.startsWith("file://") -> project.videoUri.removePrefix("file://")
            project.videoUri.startsWith("/") -> project.videoUri
            else -> null
        }
        path?.let { File(it) }?.takeIf { it.exists() && it.length() > 1024 }
    }

    // Real video frame bitmap extraction for crisp poster / scrubber preview
    var videoBitmap by remember(localVideoFile?.absolutePath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(localVideoFile?.absolutePath, (currentTimeMs / 400L)) {
        localVideoFile?.let { file ->
            val bm = VideoFrameExtractor.extractFrame(file.absolutePath, currentTimeMs)
            if (bm != null) {
                videoBitmap = bm
            }
        }
    }

    // Audio volume ducking calculation
    val effectiveAudioVolume = if (isSpeakingDubbing) {
        (project.dubbingConfig.originalAudioVolume * 0.1f).coerceIn(0f, 1f)
    } else {
        project.dubbingConfig.originalAudioVolume.coerceIn(0f, 1f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioBgDark)
            .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Player Container Box
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isVertical) 350.dp else 230.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = maxWidth
            val containerHeight = maxHeight

            // Centered Video Screen Frame strictly adhering to aspect ratio
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(ratioValue)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                if (localVideoFile != null) {
                    // 1. Crisp Real Video Poster / Frame Image (Always visible, prevents black screen)
                    if (videoBitmap != null) {
                        Image(
                            bitmap = videoBitmap!!.asImageBitmap(),
                            contentDescription = "Khung hình video đã thêm",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // 2. Hardware TextureView Video Player for smooth real-time playback
                    AndroidView(
                        factory = { ctx ->
                            TextureVideoView(ctx).apply {
                                setVideoPath(localVideoFile.absolutePath)
                                updatePlayback(isPlaying, currentTimeMs, effectiveAudioVolume)
                            }
                        },
                        update = { view ->
                            view.updatePlayback(isPlaying, currentTimeMs, effectiveAudioVolume)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // 3. Realistic Douyin / TikTok Simulated Video Canvas with Rich Visuals
                    DouyinRealisticVideoCanvas(
                        projectTitle = project.title,
                        isPlaying = isPlaying,
                        currentTimeMs = currentTimeMs,
                        activeChineseText = activeSegment?.originalChinese,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top Status Badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, StudioCyan.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = if (isVertical) "📱 9:16 DỌC" else "🖥️ 16:9 NGANG",
                            color = StudioCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (isSpeakingDubbing) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = StudioPurple.copy(alpha = 0.9f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Audio Ducking",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Lồng tiếng AI (BGM -90%)",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Subtitle Mask Box & Vietnamese Subtitle Overlay
                val maskConfig = project.maskConfig
                val maskTopOffset = (maskConfig.yPercent / 100f) * containerHeight.value
                val maskWidth = containerWidth * (maskConfig.widthPercent / 100f)
                val maskHeight = containerHeight * (maskConfig.heightPercent / 100f)

                // 1. SUBTITLE MASK BAR (Che phụ đề Tiếng Trung cũ)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, (maskTopOffset * density).roundToInt()) }
                        .width(maskWidth)
                        .height(maskHeight)
                        .clip(RoundedCornerShape(maskConfig.cornerRadiusDp.dp))
                        .background(
                            Color.Black.copy(alpha = maskConfig.opacity)
                        )
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaPercent = (dragAmount.y / (containerHeight.value * density)) * 100f
                                val newY = (maskConfig.yPercent + deltaPercent).coerceIn(20f, 95f)
                                onMaskYChange(newY)
                            }
                        }
                        .border(
                            width = 1.dp,
                            color = StudioCyan.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(maskConfig.cornerRadiusDp.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Visual handle hint on mask bar
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 2.dp)
                            .size(width = 28.dp, height = 3.dp)
                            .clip(CircleShape)
                            .background(StudioCyan.copy(alpha = 0.7f))
                    )
                }

                // 2. CRISP VIETNAMESE SUBTITLE OVERLAY (Chèn phụ đề Tiếng Việt sắc nét)
                if (activeSegment != null && activeSegment.vietnameseText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset { IntOffset(0, (maskTopOffset * density).roundToInt()) }
                            .width(maskWidth)
                            .height(maskHeight)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = activeSegment.vietnameseText,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = project.subtitleConfig.fontSizeSp.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                shadow = Shadow(
                                    color = Color.Black,
                                    offset = Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            ),
                            maxLines = project.subtitleConfig.maxLines,
                            modifier = Modifier.testTag("rendered_vietnamese_subtitle")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Playback Scrubber & Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Jump Back -5s
            IconButton(
                onClick = { onSeek((currentTimeMs - 5000L).coerceAtLeast(0L)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Lùi 5 giây",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Play/Pause button
            IconButton(
                onClick = onPlayPauseToggle,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(StudioCyan)
                    .testTag("play_pause_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                    tint = StudioBgDark,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Jump Forward +5s
            IconButton(
                onClick = { onSeek((currentTimeMs + 5000L).coerceAtMost(project.durationMs)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Tới 5 giây",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Current Time
            Text(
                text = SubtitleSegment.formatTimestamp(currentTimeMs),
                color = StudioCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            // Scrubber Slider
            val duration = project.durationMs.coerceAtLeast(1000L).toFloat()
            Slider(
                value = currentTimeMs.toFloat().coerceIn(0f, duration),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(
                    thumbColor = StudioCyan,
                    activeTrackColor = StudioCyan,
                    inactiveTrackColor = StudioBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("video_scrubber_slider")
            )

            // Total Duration
            Text(
                text = SubtitleSegment.formatTimestamp(project.durationMs),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * High-fidelity Douyin / TikTok simulated video canvas with rich, vibrant graphics,
 * authentic Douyin UI widgets, and original hardcoded Chinese subtitle display.
 */
@Composable
fun DouyinRealisticVideoCanvas(
    projectTitle: String,
    isPlaying: Boolean,
    currentTimeMs: Long,
    activeChineseText: String?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "douyin_motion")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "disc_spin"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Modern Studio Gradient Background
            val baseColors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0A0F1D))

            drawRect(
                brush = Brush.verticalGradient(baseColors),
                size = size
            )

            // Studio Art Centerpiece
            drawCircle(
                color = StudioCyan.copy(alpha = 0.15f * pulse),
                radius = w * 0.35f,
                center = Offset(w * 0.5f, h * 0.45f)
            )
            drawRoundRect(
                brush = Brush.linearGradient(listOf(StudioCyan.copy(alpha = 0.3f), StudioPurple.copy(alpha = 0.3f))),
                topLeft = Offset(w * 0.22f, h * 0.30f),
                size = Size(w * 0.56f, h * 0.30f),
                cornerRadius = CornerRadius(16f, 16f)
            )

            // Right Rail: Douyin Engagement Icons (Hearts, Comments, Share)
            val rightX = w * 0.90f
            drawCircle(Color(0xFFFF1744).copy(alpha = 0.9f), radius = 12f, center = Offset(rightX, h * 0.50f))
            drawCircle(Color.White.copy(alpha = 0.85f), radius = 11f, center = Offset(rightX, h * 0.58f))
            drawCircle(Color(0xFFFFD600).copy(alpha = 0.85f), radius = 11f, center = Offset(rightX, h * 0.66f))
            drawCircle(Color.White.copy(alpha = 0.85f), radius = 11f, center = Offset(rightX, h * 0.74f))

            // Bottom Right Spinning Vinyl Disc
            drawCircle(Color(0xFF111827), radius = 16f, center = Offset(rightX, h * 0.84f))
            drawCircle(Color(0xFFFF1744), radius = 6f, center = Offset(rightX, h * 0.84f))
        }

        // Authentic Douyin Top-Left Live Banner & Account
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(StudioPurple)
                    .border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "抖", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(text = "@Douyin_Creator", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(text = "🔥 Đang phát video mẫu", color = StudioAmber, fontSize = 9.sp)
            }
        }

        // Hardcoded Original Douyin Chinese Subtitle (Displayed right at bottom where mask covers)
        if (!activeChineseText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
            ) {
                Text(
                    text = activeChineseText,
                    style = TextStyle(
                        color = Color(0xFFFFEB3B), // Classic Douyin bright yellow
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        shadow = Shadow(
                            color = Color.Black,
                            offset = Offset(2f, 2f),
                            blurRadius = 4f
                        )
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
