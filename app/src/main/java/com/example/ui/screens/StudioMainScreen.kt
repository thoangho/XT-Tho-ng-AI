package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AspectRatio
import com.example.data.model.SampleVideoRepository
import com.example.ui.components.BatteryIndicatorBadge
import com.example.ui.components.LowBatteryWarningBanner
import com.example.ui.components.LowBatteryExportWarningDialog
import com.example.ui.components.ExportStudioCard
import com.example.ui.components.FFmpegRenderOverlayDialog
import com.example.ui.components.VoiceDubbingOverlayDialog
import com.example.ui.components.MaskControlsCard
import com.example.ui.components.ProcessingProgressDialog
import com.example.ui.components.SampleVideoPickerSheet
import com.example.ui.components.SubtitleEditorTable
import com.example.ui.components.VideoPlayerWithOverlay
import com.example.ui.components.VoiceSelectionCard
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBgDark
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioPurple
import com.example.ui.theme.StudioPurpleLight
import com.example.ui.theme.StudioSurfaceCard
import com.example.ui.viewmodel.StudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioMainScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSamplePicker by remember { mutableStateOf(false) }

    // System Video File Picker (GetContent contract: works universally on ALL Android versions & devices)
    val getContentVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importCustomVideo(uri, context)
        }
    }

    // Secondary Photo Picker fallback
    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importCustomVideo(uri, context)
        }
    }

    // Subtitle File Picker for .srt and .vtt files
    val getSubtitleFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importSubtitleFileFromUri(uri, context)
        }
    }

    LaunchedEffect(state.userNotice) {
        state.userNotice?.let { notice ->
            snackbarHostState.showSnackbar(notice)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = StudioBgDark,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StudioCyan.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StudioCyan.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "XT",
                                color = StudioCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Column(modifier = Modifier.wrapContentWidth()) {
                            Text(
                                text = "XT Thoáng AI",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "Dịch & Lồng tiếng AI",
                                color = StudioCyan,
                                fontSize = 9.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Badge Pin: Ràng buộc xếp song song bên phải Tên app, tuyệt đối không đè tên thương hiệu (Lỗi 2)
                        BatteryIndicatorBadge(
                            batteryInfo = state.batteryInfo,
                            onClick = { viewModel.refreshBatteryStatus() }
                        )
                    }
                },
                actions = {
                    // API Key Setting Icon Button
                    IconButton(
                        onClick = { viewModel.openApiKeyDialog() },
                        modifier = Modifier.size(32.dp).testTag("api_key_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Cài đặt API Key",
                            tint = if (state.userApiKey.isNotBlank()) StudioGreen else StudioAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Hiển thị 2 nút 'Dịch Phụ Đề' và 'Xuất video' CHỈ KHI đã nạp/chọn video thành công
                    // Nếu CHƯA có video (trạng thái mặc định khi vừa mở app hoặc sau khi xóa video): Ẩn hoàn toàn (visibility = GONE)
                    val hasVideo = state.hasVideoLoaded && state.activeProject != null

                    if (hasVideo) {
                        // Core Translation Button (Whisper + Gemini AI with error check)
                        Button(
                            onClick = {
                                if (state.activeProject == null) {
                                    viewModel.showNotice("Vui lòng chọn hoặc nạp video trước khi chạy dịch thuật!")
                                } else {
                                    viewModel.runSubtitleTranslationOnly()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioPurple,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .padding(end = 3.dp)
                                .testTag("top_run_pipeline_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Dịch Phụ Đề", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }

                        // SINGLE UNIFIED EXPORT BUTTON AT TOP-RIGHT CORNER (Module 4 Requirement)
                        Button(
                            onClick = {
                                if (state.activeProject == null) {
                                    viewModel.showNotice("Vui lòng chọn hoặc nạp video trước khi xuất video!")
                                } else {
                                    viewModel.startExportAndCleanup()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioGreen,
                                contentColor = StudioBgDark
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .padding(end = 4.dp)
                                .testTag("top_single_export_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Xuất video",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Xuất video", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudioBgDark
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Hiển thị Banner cảnh báo pin yếu để ngăn chặn lỗi khi render video dài
            if (!state.isBatteryBannerDismissed && state.batteryInfo.isLowBattery) {
                LowBatteryWarningBanner(
                    batteryInfo = state.batteryInfo,
                    onDismiss = { viewModel.dismissBatteryBanner() }
                )
            }

            val project = state.activeProject

            if (project != null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Project indicator banner with quick change/add action
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSamplePicker = true },
                            shape = RoundedCornerShape(12.dp),
                            color = StudioSurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (project.isSample) StudioBorder else StudioGreen.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = if (project.isSample) StudioCyan else StudioGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = project.title,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (project.isSample) "Video mẫu Douyin • Nhấn để đổi hoặc tải video của bạn" else "Video của bạn • Đã nạp thành công",
                                            color = if (project.isSample) StudioAmber else StudioGreen,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            try {
                                                getContentVideoLauncher.launch("video/*")
                                            } catch (e: Exception) {
                                                pickVisualMediaLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = StudioCyan,
                                            contentColor = StudioBgDark
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("Thêm video", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { showSamplePicker = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = androidx.compose.foundation.BorderStroke(0.8.dp, StudioBorder),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Đổi / Mẫu", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 1. Centerpiece Video Player with live mask bar & crisp subtitle overlay
                    item {
                        VideoPlayerWithOverlay(
                            project = project,
                            segments = state.segments,
                            currentTimeMs = state.currentPlaybackTimeMs,
                            isPlaying = state.isPlaying,
                            isSpeakingDubbing = state.isSpeakingPreview,
                            onPlayPauseToggle = { viewModel.togglePlayPause() },
                            onSeek = { viewModel.seekTo(it) },
                            onMaskYChange = { newY ->
                                viewModel.updateMaskConfig(project.maskConfig.copy(yPercent = newY))
                            }
                        )
                    }

                    // 2. Navigation Tabs (Masking, Subtitle Table, Voice Dubbing, Export)
                    item {
                        val tabs = listOf(
                            Triple("Che phụ đề", Icons.Default.Layers, 0),
                            Triple("Bảng phụ đề (${state.segments.size})", Icons.Default.TableChart, 1),
                            Triple("Lồng tiếng AI", Icons.Default.RecordVoiceOver, 2),
                            Triple("Xuất bản", Icons.Default.Movie, 3)
                        )

                        ScrollableTabRow(
                            selectedTabIndex = state.selectedTab,
                            containerColor = StudioBgDark,
                            contentColor = StudioCyan,
                            edgePadding = 0.dp,
                            indicator = { tabPositions ->
                                if (state.selectedTab < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[state.selectedTab]),
                                        color = StudioCyan
                                    )
                                }
                            },
                            divider = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(StudioBorder)
                                )
                            }
                        ) {
                            tabs.forEach { (title, icon, index) ->
                                val isSelected = state.selectedTab == index
                                Tab(
                                    selected = isSelected,
                                    onClick = { viewModel.selectTab(index) },
                                    modifier = Modifier.testTag("tab_$index"),
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = if (isSelected) StudioCyan else Color.White.copy(alpha = 0.5f)
                                            )
                                            Text(
                                                text = title,
                                                color = if (isSelected) StudioCyan else Color.White.copy(alpha = 0.6f),
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // 3. Active Tab Content
                    item {
                        when (state.selectedTab) {
                            0 -> {
                                // Tab 0: Masking & Subtitle Visual Controls
                                MaskControlsCard(
                                    project = project,
                                    onMaskConfigChange = { viewModel.updateMaskConfig(it) },
                                    onSubtitleConfigChange = { viewModel.updateSubtitleConfig(it) }
                                )
                            }
                            1 -> {
                                // Tab 1: Interactive Subtitle Editor Table (st.data_editor)
                                SubtitleEditorTable(
                                    segments = state.segments,
                                    currentTimeMs = state.currentPlaybackTimeMs,
                                    isRetranslatingAll = state.isRetranslatingAll,
                                    retranslateProgress = state.retranslateProgress,
                                    retranslateStatusMessage = state.retranslateStatusMessage,
                                    isSubtitlesConfirmed = state.isSubtitlesConfirmed,
                                    onConfirmSubtitles = { viewModel.confirmAndApproveAllSubtitles() },
                                    onGoToDubbingTab = { viewModel.selectTab(2) },
                                    onUpdateText = { id, text -> viewModel.updateSegmentText(id, text) },
                                    onUpdateChineseText = { id, zh -> viewModel.updateSegmentChineseText(id, zh) },
                                    onUpdateTiming = { id, start, end -> viewModel.updateSegmentTiming(id, start, end) },
                                    onNudgeTiming = { id, dStart, dEnd -> viewModel.nudgeSegmentTiming(id, dStart, dEnd) },
                                    onToggleApproval = { id -> viewModel.toggleSegmentApproval(id) },
                                    onApproveAll = { viewModel.confirmAndApproveAllSubtitles() },
                                    onRetranslateAll = { viewModel.runFullPipeline() },
                                    onAddShortSegment = { viewModel.addShortSegment() },
                                    onRetranslate = { seg -> viewModel.retranslateSegment(seg) },
                                    onPreviewVoice = { seg -> viewModel.previewVoiceForSegment(seg) },
                                    onDeleteSegment = { seg -> viewModel.deleteSegment(seg) },
                                    onMergeWithNext = { id -> viewModel.mergeSegmentWithNext(id) },
                                    onSplitSegment = { id -> viewModel.splitSegment(id) },
                                    onAddNewSegment = { viewModel.addNewSegment() },
                                    onInsertAtCurrentTime = { viewModel.insertSegmentAtCurrentTime() },
                                    onSeekTo = { timeMs -> viewModel.seekTo(timeMs) },
                                    onImportSubtitleFile = { getSubtitleFileLauncher.launch("*/*") }
                                )
                            }
                            2 -> {
                                // Tab 2: Voice Dubbing (Hoài Mỹ / Nam Minh) & Audio Ducking Mixer
                                val approvedCount = remember(state.segments) {
                                    state.segments.count { it.isApproved && it.vietnameseText.isNotBlank() }
                                }
                                VoiceSelectionCard(
                                    dubbingConfig = project.dubbingConfig,
                                    availableVoices = state.availableVoices,
                                    isSubtitlesConfirmed = state.isSubtitlesConfirmed,
                                    approvedSegmentsCount = approvedCount,
                                    isDubbingPlaying = state.isDubbingPlaying,
                                    onStartDubbing = { viewModel.startAiVoiceDubbing() },
                                    onStopDubbing = { viewModel.stopAiVoiceDubbing() },
                                    onGoToSubtitleTab = { viewModel.selectTab(1) },
                                    onSelectVoice = { viewModel.selectVoice(it) },
                                    onDubbingConfigChange = { viewModel.updateDubbingConfig(it) },
                                    onTestVoice = {
                                        viewModel.previewVoiceForSegment(
                                            state.segments.firstOrNull { it.isApproved && it.vietnameseText.isNotBlank() }
                                                ?: state.segments.firstOrNull() ?: return@VoiceSelectionCard
                                        )
                                    },
                                    isDubbingGenerating = state.isDubbingGenerating,
                                    dubbingGenerationProgress = state.dubbingGenerationProgress,
                                    dubbingStatusMessage = state.dubbingStatusMessage,
                                    onGenerateDubbingFromSrt = { viewModel.runDubbingGenerationOnly() },
                                    onImportSrt = { getSubtitleFileLauncher.launch("*/*") }
                                )
                            }
                            3 -> {
                                // Tab 3: Full Pipeline & Export (MP4, SRT, TXT)
                                ExportStudioCard(
                                    project = project,
                                    segments = state.segments,
                                    lastExportedVideo = state.lastExportedVideo,
                                    lastExportedSrt = state.lastExportedSrt,
                                    lastExportedTxt = state.lastExportedTxt,
                                    isRenderingFFmpeg = state.isRenderingFFmpeg,
                                    ffmpegRenderProgress = state.ffmpegRenderProgress,
                                    ffmpegStatusMessage = state.ffmpegStatusMessage,
                                    lastDownloadedFileName = state.lastDownloadedFileName,
                                    ffmpegOptions = state.ffmpegOptions,
                                    onUpdateFFmpegOptions = { viewModel.updateFFmpegOptions(it) },
                                    onTriggerFFmpegRender = { viewModel.startFFmpegRendering(it) },
                                    onDownloadFile = { file, mime -> viewModel.downloadFileToDevice(file, mime) },
                                    onOpenFile = { file, mime -> viewModel.openExportedFile(file, mime) },
                                    onRunFullPipeline = { viewModel.runFullPipeline() },
                                    onExportSrt = { viewModel.exportSrtOnly() },
                                    onExportTranscript = { viewModel.exportTranscriptOnly() },
                                    batteryInfo = state.batteryInfo
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            } else {
                // Trạng thái ban đầu (Initial State): Màn hình chờ yêu cầu thêm video (Ảnh 1)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = StudioSurfaceCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = StudioCyan.copy(alpha = 0.18f),
                                modifier = Modifier.size(68.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = StudioCyan,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Hệ thống XT Thoáng AI",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Hệ thống đã sẵn sàng cho phiên làm việc mới. Vui lòng thêm video từ thiết bị của bạn để bắt đầu.",
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 19.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Button(
                                onClick = { showSamplePicker = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StudioCyan,
                                    contentColor = StudioBgDark
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("welcome_add_video_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("+ Chọn hoặc Tải Video", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }

                            // Nút "💬 Liên hệ ADMIN": Mở trực tiếp trang Facebook (ưu tiên ứng dụng Facebook, fallback trình duyệt)
                            OutlinedButton(
                                onClick = {
                                    val fbUrl = "https://www.facebook.com/share/19o6cDY1cf/"
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fbUrl)).apply {
                                            setPackage("com.facebook.katana")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fbUrl))
                                        context.startActivity(webIntent)
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    contentColor = androidx.compose.ui.graphics.Color(0xFF00E5FF)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, androidx.compose.ui.graphics.Color(0xFF00E5FF)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("welcome_contact_admin_button")
                            ) {
                                Text(
                                    text = "💬 Liên hệ ADMIN",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color(0xFF00E5FF)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Module 1 & Global: Error Alert Dialog (Displays popup when translation or any process fails)
    if (state.errorAlertMessage != null) {
        val isApiKeyError = state.errorAlertTitle?.contains("API Key", ignoreCase = true) == true
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorAlert() },
            title = {
                Text(
                    text = state.errorAlertTitle ?: "Thông báo lỗi",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = state.errorAlertMessage ?: "",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearErrorAlert()
                        if (isApiKeyError) {
                            viewModel.openApiKeyDialog()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioCyan,
                        contentColor = StudioBgDark
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isApiKeyError) "Nhập API Key" else "Đóng", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = if (isApiKeyError) {
                {
                    TextButton(onClick = { viewModel.clearErrorAlert() }) {
                        Text("Hủy", color = Color.White.copy(alpha = 0.6f))
                    }
                }
            } else null,
            containerColor = StudioSurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Module 1: API Key Configuration Dialog
    if (state.showApiKeyDialog) {
        var tempApiKey by remember(state.userApiKey) { mutableStateOf(state.userApiKey) }
        AlertDialog(
            onDismissRequest = { viewModel.closeApiKeyDialog() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(20.dp))
                    Text("Cài đặt Gemini API Key", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Nhập một hoặc nhiều Google Gemini API Key (mỗi dòng một key hoặc phân cách bằng dấu phẩy) để kích hoạt cơ chế xoay vòng thông minh (Round-Robin). Nếu một key bị giới hạn 429 hoặc lỗi hạn ngạch, hệ thống sẽ tự động chuyển sang key tiếp theo mà không làm gián đoạn ứng dụng.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        placeholder = { Text("Dán 1 hoặc nhiều API Key (AIzaSy...)\nMỗi key 1 dòng hoặc cách nhau bằng dấu phẩy", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f)) },
                        singleLine = false,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = StudioCyan,
                            unfocusedBorderColor = StudioBorder
                        )
                    )

                    // Trạng thái kiểm tra kết nối
                    if (state.isTestingApiKey) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StudioBgDark.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = StudioCyan,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = state.apiKeyValidationMessage ?: "Đang kiểm tra kết nối với Google AI Studio...",
                                color = StudioCyan,
                                fontSize = 11.sp
                            )
                        }
                    } else if (state.apiKeyValidationMessage != null) {
                        val isOk = state.isApiKeyValid == true
                        val bg = if (isOk) StudioGreen.copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f)
                        val txtColor = if (isOk) StudioGreen else Color(0xFFFF8A80)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = bg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.apiKeyValidationMessage ?: "",
                                color = txtColor,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp),
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.testApiKey(tempApiKey) },
                            enabled = !state.isTestingApiKey && tempApiKey.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("test_api_key_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = StudioCyan)
                            Spacer(Modifier.width(4.dp))
                            Text("Test kết nối", fontSize = 11.sp, color = StudioCyan)
                        }

                        if (state.userApiKey.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    tempApiKey = ""
                                    viewModel.clearApiKey()
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Xoá Key", fontSize = 11.sp, color = Color(0xFFFF8A80))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveApiKey(tempApiKey) },
                    enabled = !state.isTestingApiKey,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioCyan,
                        contentColor = StudioBgDark
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("save_api_key_button")
                ) {
                    Text(if (state.isTestingApiKey) "Đang lưu..." else "Lưu & Kích hoạt", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeApiKeyDialog() }) {
                    Text("Đóng", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = StudioSurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Module 4: Export Success & Auto-Cleanup Confirmation Dialog
    if (state.exportSuccessMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearExportSuccess() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StudioGreen, modifier = Modifier.size(22.dp))
                    Text("Xuất video thành công!", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = state.exportSuccessMessage ?: "",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 19.sp
                    )
                    Text(
                        text = "Video hoàn chỉnh đã được lưu vào Bộ sưu tập và thư mục Download/XThoang_AI của máy. Toàn bộ phụ đề hiện tại và bộ nhớ đệm đã được tự động dọn sạch cho lần làm việc mới.",
                        color = StudioCyan,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearExportSuccess() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioGreen,
                        contentColor = StudioBgDark
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("OK, tiếp tục", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = StudioSurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Processing Progress Modal (Real-time 4 stages + logs)
    if (state.isProcessing) {
        ProcessingProgressDialog(
            currentStage = state.processingStage,
            stageTitle = state.processingStageTitle,
            progress = state.processingProgress,
            logs = state.processingLogs,
            onDismiss = { viewModel.dismissProcessingDialog() },
            batteryInfo = state.batteryInfo
        )
    }

    // Sample & custom video chooser modal
    if (showSamplePicker) {
        SampleVideoPickerSheet(
            activeSampleId = state.activeProject?.id,
            allProjects = state.allProjects,
            onSelectSample = { viewModel.loadSample(it) },
            onSelectProject = { viewModel.loadProject(it) },
            onPickCustomVideo = {
                try {
                    getContentVideoLauncher.launch("video/*")
                } catch (e: Exception) {
                    pickVisualMediaLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                }
            },
            onImportUrl = { url, title -> viewModel.importVideoFromUrl(url, title, context) },
            onCreateManualProject = { title, dur, isVert -> viewModel.createManualProject(title, dur, isVert) },
            onDeleteProject = { viewModel.deleteProject(it) },
            onDismiss = { showSamplePicker = false }
        )
    }

    // Tự động đóng Modal chọn video nếu danh sách dự án rỗng và không có video đang nạp (Lỗi 5)
    LaunchedEffect(state.allProjects.size, state.activeProject) {
        if (state.allProjects.isEmpty() && state.activeProject == null) {
            showSamplePicker = false
        }
    }

    // Modal Hộp thoại Cảnh báo Pin yếu trước khi bắt đầu Render Video (Bảo vệ render video dài)
    if (state.showLowBatteryExportWarningDialog) {
        LowBatteryExportWarningDialog(
            batteryInfo = state.batteryInfo,
            onConfirmExport = { viewModel.confirmExportDespiteLowBattery() },
            onDismiss = { viewModel.dismissLowBatteryDialog() }
        )
    }

    // Modal Overlay Khóa tương tác toàn màn hình & Giữ sáng màn hình khi Lồng tiếng AI (Lỗi 1)
    if (state.isDubbingGenerating) {
        VoiceDubbingOverlayDialog(
            progress = state.dubbingGenerationProgress,
            statusMessage = state.dubbingStatusMessage,
            onStopDubbing = { viewModel.stopAiVoiceDubbing() },
            batteryInfo = state.batteryInfo
        )
    }

    // Modal Overlay Khóa tương tác toàn màn hình & Giữ sáng màn hình khi Render Video
    if (state.isRenderingFFmpeg) {
        FFmpegRenderOverlayDialog(
            progress = state.ffmpegRenderProgress,
            statusMessage = state.ffmpegStatusMessage,
            onCancel = { viewModel.cancelFFmpegRendering() },
            batteryInfo = state.batteryInfo
        )
    }
}
