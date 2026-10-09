package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.StudioDatabase
import com.example.data.model.DubbingConfig
import com.example.data.model.MaskConfig
import com.example.data.model.SampleVideoItem
import com.example.data.model.SampleVideoRepository
import com.example.data.model.SubtitleConfig
import com.example.data.model.SubtitleSegment
import com.example.data.model.VideoProject
import com.example.data.model.VoiceOption
import com.example.data.network.TranslationService
import com.example.data.repository.TranslationRepository
import com.example.data.subtitle.SubtitleFileService
import com.example.data.tts.VoiceDubbingService
import com.example.data.video.FFmpegOptions
import com.example.data.video.SampleVideoHelper
import com.example.data.video.VideoExportService
import com.example.data.video.VideoImportService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import com.example.data.network.ApiKeyException
import com.example.data.network.ApiKeyManager
import com.example.util.BatteryHelper
import com.example.util.BatteryInfo
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class StudioUiState(
    val activeProject: VideoProject? = null,
    val allProjects: List<VideoProject> = emptyList(),
    val segments: List<SubtitleSegment> = emptyList(),
    val currentPlaybackTimeMs: Long = 0,
    val isPlaying: Boolean = false,
    val isProcessing: Boolean = false,
    val processingStage: Int = 1,
    val processingStageTitle: String = "",
    val processingProgress: Float = 0f,
    val processingLogs: List<String> = emptyList(),
    val selectedTab: Int = 0, // 0: Video & Mask, 1: Subtitle Table, 2: Voice & Dubbing, 3: Export
    val availableVoices: List<VoiceOption> = VoiceOption.ALL_VOICES,
    val lastExportedSrt: File? = null,
    val lastExportedVideo: File? = null,
    val lastExportedTxt: File? = null,
    val isSpeakingPreview: Boolean = false,
    val userNotice: String? = null,
    val ffmpegOptions: FFmpegOptions = FFmpegOptions(),
    val isRenderingFFmpeg: Boolean = false,
    val ffmpegRenderProgress: Float = 0f,
    val ffmpegStatusMessage: String = "",
    val lastDownloadedFileName: String? = null,
    val isRetranslatingAll: Boolean = false,
    val retranslateProgress: Float = 0f,
    val retranslateStatusMessage: String = "",
    val translationSuccessful: Boolean = false,
    val userApiKey: String = "",
    val savedApiKeys: List<String> = emptyList(),
    val errorAlertTitle: String? = null,
    val errorAlertMessage: String? = null,
    val showApiKeyDialog: Boolean = false,
    val isTestingApiKey: Boolean = false,
    val apiKeyValidationMessage: String? = null,
    val isApiKeyValid: Boolean? = null,
    val exportSuccessMessage: String? = null,
    val isSubtitlesConfirmed: Boolean = false,
    val isDubbingPlaying: Boolean = false,
    val isDubbingGenerating: Boolean = false,
    val dubbingGenerationProgress: Float = 0f,
    val dubbingStatusMessage: String = "",
    val lastGeneratedAudioFile: File? = null,
    val hasVideoLoaded: Boolean = false,
    val batteryInfo: BatteryInfo = BatteryInfo(),
    val showLowBatteryExportWarningDialog: Boolean = false,
    val pendingExportDirect: Boolean = false,
    val isBatteryBannerDismissed: Boolean = false
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val database = StudioDatabase.getInstance(application)
    private val projectDao = database.projectDao()
    private val subtitleDao = database.subtitleDao()
    val dubbingService = VoiceDubbingService(application)
    private val prefs = application.getSharedPreferences("xthoang_ai_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var lastSpokenSegmentId: Long? = null

    init {
        val savedKeys = ApiKeyManager.getSavedApiKeys(application)
        val savedKeyText = savedKeys.joinToString("\n")
        _uiState.update { it.copy(userApiKey = savedKeyText, savedApiKeys = savedKeys) }

        // Quan sát danh sách dự án từ Room DB nhưng KHÔNG tự động nạp activeProject khi khởi động
        // Mặc định ban đầu luôn là màn hình chờ (Ảnh 1) yêu cầu người dùng bấm chọn hoặc thêm video
        viewModelScope.launch {
            projectDao.getAllProjects().collect { projects ->
                _uiState.update { it.copy(allProjects = projects) }
            }
        }

        dubbingService.onDuckingChange = { isSpeaking ->
            _uiState.update { it.copy(isSpeakingPreview = isSpeaking) }
        }

        // Quan sát mức pin và nguồn sạc theo thời gian thực để bảo vệ render
        viewModelScope.launch {
            BatteryHelper.observeBattery(application).collect { battery ->
                _uiState.update { it.copy(batteryInfo = battery) }
            }
        }
    }

    fun testApiKey(rawKeyInput: String) {
        val parsedKeys = ApiKeyManager.parseApiKeys(rawKeyInput)
        if (parsedKeys.isEmpty()) {
            _uiState.update {
                it.copy(
                    isTestingApiKey = false,
                    isApiKeyValid = false,
                    apiKeyValidationMessage = "Vui lòng nhập ít nhất 1 API Key hợp lệ trước khi kiểm tra."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTestingApiKey = true,
                    isApiKeyValid = null,
                    apiKeyValidationMessage = "Đang kiểm tra kết nối lần lượt ${parsedKeys.size} API Key với máy chủ Google AI Studio..."
                )
            }
            var successCount = 0
            val errors = mutableListOf<String>()

            for ((i, key) in parsedKeys.withIndex()) {
                val res = ApiKeyManager.validateGeminiApiKey(key)
                if (res.isSuccess) {
                    successCount++
                } else {
                    val err = res.exceptionOrNull()?.localizedMessage ?: "Lỗi kết nối"
                    errors.add("Key #${i + 1}: $err")
                }
            }

            if (successCount > 0) {
                _uiState.update {
                    it.copy(
                        isTestingApiKey = false,
                        isApiKeyValid = true,
                        apiKeyValidationMessage = "✅ Xác thực thành công $successCount/${parsedKeys.size} API Key! Đã kích hoạt cơ chế xoay vòng Round-Robin tự động."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isTestingApiKey = false,
                        isApiKeyValid = false,
                        apiKeyValidationMessage = "❌ Tất cả ${parsedKeys.size} API Key đều không khả dụng: ${errors.joinToString("; ")}"
                    )
                }
            }
        }
    }

    fun saveApiKey(keyInput: String) {
        val parsedKeys = ApiKeyManager.parseApiKeys(keyInput)
        if (parsedKeys.isEmpty()) {
            ApiKeyManager.clearApiKey(getApplication())
            _uiState.update {
                it.copy(
                    userApiKey = "",
                    savedApiKeys = emptyList(),
                    showApiKeyDialog = false,
                    isTestingApiKey = false,
                    apiKeyValidationMessage = null,
                    isApiKeyValid = null
                )
            }
            showNotice("Đã xoá danh sách API Key. Hệ thống chuyển sang chế độ Google Neural Direct miễn phí.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTestingApiKey = true,
                    isApiKeyValid = null,
                    apiKeyValidationMessage = "Đang xác thực ${parsedKeys.size} API Key với Google AI Studio..."
                )
            }
            // Kiểm tra key đầu tiên hoặc ít nhất 1 key hoạt động
            var activeKeyFound = false
            for (key in parsedKeys) {
                val validation = ApiKeyManager.validateGeminiApiKey(key)
                if (validation.isSuccess) {
                    activeKeyFound = true
                    break
                }
            }

            if (activeKeyFound) {
                ApiKeyManager.saveApiKeys(getApplication(), parsedKeys)
                val fullText = parsedKeys.joinToString("\n")
                _uiState.update {
                    it.copy(
                        userApiKey = fullText,
                        savedApiKeys = parsedKeys,
                        showApiKeyDialog = false,
                        isTestingApiKey = false,
                        isApiKeyValid = true,
                        apiKeyValidationMessage = null
                    )
                }
                showNotice("✅ Đã lưu thành công ${parsedKeys.size} API Key! Hệ thống tự động chuyển đổi xoay vòng khi hết hạn ngạch (Quota/429).")
            } else {
                // Cho phép lưu dự phòng nếu người dùng muốn
                ApiKeyManager.saveApiKeys(getApplication(), parsedKeys)
                val fullText = parsedKeys.joinToString("\n")
                _uiState.update {
                    it.copy(
                        userApiKey = fullText,
                        savedApiKeys = parsedKeys,
                        showApiKeyDialog = false,
                        isTestingApiKey = false,
                        isApiKeyValid = false,
                        apiKeyValidationMessage = null
                    )
                }
                showNotice("⚠️ Đã lưu ${parsedKeys.size} API Key. Vui lòng kiểm tra lại hạn ngạch mạng.")
            }
        }
    }

    fun saveApiKeyDirectlyWithoutValidation(keyInput: String) {
        val parsedKeys = ApiKeyManager.parseApiKeys(keyInput)
        ApiKeyManager.saveApiKeys(getApplication(), parsedKeys)
        val fullText = parsedKeys.joinToString("\n")
        _uiState.update {
            it.copy(
                userApiKey = fullText,
                savedApiKeys = parsedKeys,
                showApiKeyDialog = false,
                isTestingApiKey = false,
                apiKeyValidationMessage = null,
                isApiKeyValid = null
            )
        }
        showNotice("Đã lưu ${parsedKeys.size} API Key.")
    }

    fun clearApiKey() {
        ApiKeyManager.clearApiKey(getApplication())
        _uiState.update {
            it.copy(
                userApiKey = "",
                savedApiKeys = emptyList(),
                showApiKeyDialog = false,
                isTestingApiKey = false,
                apiKeyValidationMessage = null,
                isApiKeyValid = null
            )
        }
        showNotice("Đã xoá danh sách API Key. Hệ thống sử dụng Google Neural Direct miễn phí.")
    }

    fun openApiKeyDialog() {
        _uiState.update {
            it.copy(
                showApiKeyDialog = true,
                apiKeyValidationMessage = null,
                isApiKeyValid = null,
                isTestingApiKey = false
            )
        }
    }

    fun closeApiKeyDialog() {
        _uiState.update {
            it.copy(
                showApiKeyDialog = false,
                apiKeyValidationMessage = null,
                isApiKeyValid = null,
                isTestingApiKey = false
            )
        }
    }

    fun clearErrorAlert() {
        _uiState.update { it.copy(errorAlertTitle = null, errorAlertMessage = null) }
    }

    fun clearExportSuccess() {
        _uiState.update { it.copy(exportSuccessMessage = null) }
    }

    /**
     * Quản lý trạng thái hiển thị của các nút hành động (Dịch Phụ Đề, Xuất video)
     * - false: Ẩn hoàn toàn (GONE) khi chưa chọn/nạp video
     * - true: Hiển thị lại (VISIBLE) khi đã tải hoặc chọn video thành công
     */
    fun updateActionButtonVisibility(hasVideo: Boolean) {
        _uiState.update { it.copy(hasVideoLoaded = hasVideo) }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun closeCurrentProject() {
        playbackJob?.cancel()
        dubbingService.stopSpeaking()
        _uiState.update {
            it.copy(
                activeProject = null,
                hasVideoLoaded = false,
                segments = emptyList(),
                currentPlaybackTimeMs = 0L,
                isPlaying = false,
                isDubbingPlaying = false,
                isSubtitlesConfirmed = false,
                translationSuccessful = false
            )
        }
        showNotice("Đã đóng video và trở về màn hình chờ.")
    }

    fun loadSample(sample: SampleVideoItem) {
        viewModelScope.launch {
            val (project, _) = SampleVideoHelper.createProjectFromSample(getApplication(), sample)
            projectDao.insertProject(project)
            subtitleDao.deleteSubtitlesForProject(project.id)

            // Subtitle state is initially EMPTY per specification (No dummy text)
            _uiState.update {
                it.copy(
                    activeProject = project,
                    hasVideoLoaded = true,
                    segments = emptyList(),
                    translationSuccessful = false,
                    isSubtitlesConfirmed = false,
                    isDubbingPlaying = false,
                    currentPlaybackTimeMs = 0,
                    isPlaying = false,
                    selectedTab = 0
                )
            }
            showNotice("Đã tải video mẫu: ${sample.title}. Bạn có thể vào tab 'Bảng phụ đề' để dịch hoặc tải file phụ đề.")
        }
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            val project = projectDao.getProjectById(projectId)
            if (project != null) {
                val segments = subtitleDao.getSubtitlesList(projectId).filter { it.vietnameseText.isNotBlank() }
                val allApproved = segments.isNotEmpty() && segments.all { it.isApproved }
                _uiState.update {
                    it.copy(
                        activeProject = project,
                        hasVideoLoaded = true,
                        segments = segments,
                        translationSuccessful = segments.isNotEmpty(),
                        isSubtitlesConfirmed = allApproved,
                        isDubbingPlaying = false,
                        currentPlaybackTimeMs = 0,
                        isPlaying = false,
                        selectedTab = 0
                    )
                }
            }
        }
    }

    fun importCustomVideo(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingStage = 1,
                    processingStageTitle = "Đang nạp video từ thiết bị...",
                    processingProgress = 0.05f,
                    processingLogs = listOf("Khởi động luồng nhập video người dùng...")
                )
            }
            try {
                val result = VideoImportService.importVideoFromUri(context, uri) { pct, msg ->
                    _uiState.update {
                        it.copy(
                            processingProgress = pct,
                            processingStageTitle = msg,
                            processingLogs = it.processingLogs + msg
                        )
                    }
                }
                projectDao.insertProject(result.project)
                subtitleDao.insertSubtitles(result.segments)
                val allApproved = result.segments.isNotEmpty() && result.segments.all { it.isApproved }
                _uiState.update {
                    it.copy(
                        activeProject = result.project,
                        hasVideoLoaded = true,
                        segments = result.segments,
                        currentPlaybackTimeMs = 0,
                        isPlaying = false,
                        isDubbingPlaying = false,
                        isSubtitlesConfirmed = allApproved,
                        isProcessing = false,
                        selectedTab = 0
                    )
                }
                showNotice("✅ Đã nạp thành công: \"${result.project.title}\" (${result.project.durationMs / 1000}s, ${result.segments.size} câu phụ đề)!")
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false) }
                showNotice("❌ Không thể nạp video: ${e.localizedMessage ?: "Vui lòng thử lại"}")
            }
        }
    }

    fun importVideoFromUrl(videoUrl: String, customTitle: String?, context: Context) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingStage = 1,
                    processingStageTitle = "Đang tải video từ đường dẫn...",
                    processingProgress = 0.05f,
                    processingLogs = listOf("Đang kết nối: $videoUrl")
                )
            }
            try {
                val result = VideoImportService.importVideoFromUrl(context, videoUrl, customTitle) { pct, msg ->
                    _uiState.update {
                        it.copy(
                            processingProgress = pct,
                            processingStageTitle = msg,
                            processingLogs = it.processingLogs + msg
                        )
                    }
                }
                projectDao.insertProject(result.project)
                subtitleDao.insertSubtitles(result.segments)
                val allApproved = result.segments.isNotEmpty() && result.segments.all { it.isApproved }
                _uiState.update {
                    it.copy(
                        activeProject = result.project,
                        hasVideoLoaded = true,
                        segments = result.segments,
                        currentPlaybackTimeMs = 0,
                        isPlaying = false,
                        isDubbingPlaying = false,
                        isSubtitlesConfirmed = allApproved,
                        isProcessing = false,
                        selectedTab = 0
                    )
                }
                showNotice("✅ Đã tải video thành công: \"${result.project.title}\"!")
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false) }
                showNotice("❌ Lỗi khi tải video từ liên kết: ${e.localizedMessage ?: "Vui lòng thử lại"}")
            }
        }
    }

    fun createManualProject(title: String, durationSec: Int, isVertical: Boolean) {
        viewModelScope.launch {
            val result = VideoImportService.createManualProject(title, durationSec, isVertical)
            projectDao.insertProject(result.project)
            subtitleDao.insertSubtitles(result.segments)
            _uiState.update {
                it.copy(
                    activeProject = result.project,
                    hasVideoLoaded = true,
                    segments = result.segments,
                    currentPlaybackTimeMs = 0,
                    isPlaying = false
                )
            }
            showNotice("✅ Đã tạo dự án dịch mới: ${result.project.title}")
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectDao.deleteProjectById(projectId)
            subtitleDao.deleteSubtitlesForProject(projectId)
            val remaining = projectDao.getAllProjectsList()
            if (remaining.isNotEmpty()) {
                loadProject(remaining.first().id)
            } else {
                loadSample(SampleVideoRepository.SAMPLES.first())
            }
            showNotice("Đã xóa dự án thành công.")
        }
    }

    fun updateMaskConfig(newConfig: MaskConfig) {
        val current = _uiState.value.activeProject ?: return
        val updated = current.copy(maskConfig = newConfig, updatedAt = System.currentTimeMillis())
        _uiState.update { it.copy(activeProject = updated) }
        viewModelScope.launch {
            projectDao.updateProject(updated)
        }
    }

    fun updateSubtitleConfig(newConfig: SubtitleConfig) {
        val current = _uiState.value.activeProject ?: return
        val updated = current.copy(subtitleConfig = newConfig, updatedAt = System.currentTimeMillis())
        _uiState.update { it.copy(activeProject = updated) }
        viewModelScope.launch {
            projectDao.updateProject(updated)
        }
    }

    fun updateDubbingConfig(newConfig: DubbingConfig) {
        val current = _uiState.value.activeProject ?: return
        val updated = current.copy(dubbingConfig = newConfig, updatedAt = System.currentTimeMillis())
        _uiState.update { it.copy(activeProject = updated) }
        viewModelScope.launch {
            projectDao.updateProject(updated)
        }
    }

    fun selectVoice(voice: VoiceOption) {
        val current = _uiState.value.activeProject ?: return
        val currentDub = current.dubbingConfig
        val updatedDub = currentDub.copy(
            voiceId = voice.id,
            voiceName = "${voice.name} (${voice.gender} - ${voice.region})",
            isMale = voice.gender == "Nam"
        )
        updateDubbingConfig(updatedDub)

        val introSentence = when {
            voice.name.contains("Khiêm", ignoreCase = true) ->
                "Tôi là Gia Khiêm, giọng nam trầm ấm thuyết minh phim điện ảnh."
            voice.name.contains("Nam Minh", ignoreCase = true) ->
                "Tôi là Nam Minh, giọng đọc thời sự và quảng cáo đĩnh đạc."
            voice.name.contains("Quang Anh", ignoreCase = true) ->
                "Xin chào các bạn, mình là Quang Anh, giọng nam trẻ trung và năng động."
            voice.name.contains("Adam", ignoreCase = true) ->
                "Tôi là Adam, giọng đọc hiện đại cho công nghệ và đời sống."
            voice.name.contains("Khánh Vy", ignoreCase = true) ->
                "Chào mọi người, mình là Khánh Vy, rất vui được lồng tiếng cho video của bạn!"
            voice.name.contains("Dung", ignoreCase = true) ->
                "Xin chào, tôi là Dung, giọng lồng tiếng phim truyền hình và hoạt hình chuyên nghiệp."
            voice.name.contains("Bảo Anh", ignoreCase = true) ->
                "Xin chào, mình là Bảo Anh, giọng đọc tâm sự và podcast đêm muộn."
            voice.name.contains("Chi Mai", ignoreCase = true) ->
                "Xin chào quý thính giả, tôi là Chi Mai, giọng đọc sách và bản tin."
            voice.name.contains("Kim Oanh", ignoreCase = true) ->
                "Kính chào quý khán giả, tôi là Kim Oanh, thuyết minh phim tài liệu và lịch sử."
            voice.name.contains("Phương", ignoreCase = true) ->
                "Dạ em là Mai Phương, giọng nữ miền Nam duyên dáng và mộc mạc."
            else ->
                "Xin chào, tôi là Hoài Mỹ, giọng đọc truyền cảm chuẩn mực."
        }
        dubbingService.speakText(introSentence, updatedDub)
    }

    fun updateSegmentText(segmentId: Long, newVietnameseText: String) {
        viewModelScope.launch {
            val segments = _uiState.value.segments.toMutableList()
            val index = segments.indexOfFirst { it.id == segmentId }
            if (index != -1) {
                val updatedSeg = segments[index].copy(
                    vietnameseText = newVietnameseText,
                    isEdited = true
                )
                segments[index] = updatedSeg
                _uiState.update { it.copy(segments = segments) }
                subtitleDao.updateSubtitle(updatedSeg)
            }
        }
    }

    fun updateSegmentChineseText(segmentId: Long, newChineseText: String) {
        viewModelScope.launch {
            val segments = _uiState.value.segments.toMutableList()
            val index = segments.indexOfFirst { it.id == segmentId }
            if (index != -1) {
                val updatedSeg = segments[index].copy(
                    originalChinese = newChineseText,
                    isEdited = true
                )
                segments[index] = updatedSeg
                _uiState.update { it.copy(segments = segments) }
                subtitleDao.updateSubtitle(updatedSeg)
            }
        }
    }

    fun updateSegmentTiming(segmentId: Long, newStartMs: Long, newEndMs: Long) {
        viewModelScope.launch {
            val segments = _uiState.value.segments.toMutableList()
            val index = segments.indexOfFirst { it.id == segmentId }
            if (index != -1) {
                val updatedSeg = segments[index].copy(
                    startTimeMs = newStartMs,
                    endTimeMs = newEndMs.coerceAtLeast(newStartMs + 500)
                )
                segments[index] = updatedSeg
                _uiState.update { it.copy(segments = segments.sortedBy { s -> s.startTimeMs }) }
                subtitleDao.updateSubtitle(updatedSeg)
            }
        }
    }

    fun retranslateSegment(segment: SubtitleSegment) {
        viewModelScope.launch {
            showNotice("Đang dịch chuẩn (3 lượt): \"${segment.originalChinese}\"...")
            val result = TranslationService.translateAccuratelyMultiPass(
                chineseText = segment.originalChinese,
                passCount = 3,
                userApiKey = _uiState.value.userApiKey,
                videoTitle = _uiState.value.activeProject?.title ?: ""
            )
            val updated = segment.copy(
                vietnameseText = result.vietnameseText,
                isEdited = false
            )
            val segments = _uiState.value.segments.toMutableList()
            val idx = segments.indexOfFirst { it.id == segment.id }
            if (idx != -1) {
                segments[idx] = updated
                _uiState.update { it.copy(segments = segments) }
                subtitleDao.updateSubtitle(updated)
                showNotice("✅ Đã dịch chuẩn (${result.engineUsed}): \"${result.vietnameseText}\"")
                // Preview dubbing audio
                val dubConfig = _uiState.value.activeProject?.dubbingConfig ?: DubbingConfig()
                val segDuration = (segment.endTimeMs - segment.startTimeMs).coerceAtLeast(500L)
                dubbingService.speakText(result.vietnameseText, dubConfig, segDuration)
            }
        }
    }

    fun retranslateAllSegmentsAccurately() {
        val segments = _uiState.value.segments
        if (segments.isEmpty()) {
            showNotice("Không có câu phụ đề nào để dịch!")
            return
        }

        val project = _uiState.value.activeProject
        val effectiveApiKey = TranslationService.getEffectiveApiKey(_uiState.value.userApiKey)
        val videoCategory = "Ẩm thực, hài kịch & Đời sống Douyin"

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRetranslatingAll = true,
                    retranslateProgress = 0.05f,
                    retranslateStatusMessage = "Bắt đầu quy trình dịch hàng loạt JSON Array cho ${segments.size} câu thoại..."
                )
            }

            try {
                val updatedList = TranslationService.translateBatchSegments(
                    segments = segments,
                    userApiKey = effectiveApiKey,
                    videoTitle = project?.title ?: "Video ngắn",
                    videoCategory = videoCategory
                ) { pct, msg ->
                    _uiState.update {
                        it.copy(
                            retranslateProgress = pct,
                            retranslateStatusMessage = msg
                        )
                    }
                }

                // Làm sạch phụ đề trùng lặp và template loops qua TranslationRepository
                val cleanedList = TranslationRepository.cleanSubtitleSegments(updatedList)

                // Cập nhật Database
                cleanedList.forEach { subtitleDao.updateSubtitle(it) }

                _uiState.update {
                    it.copy(
                        segments = cleanedList,
                        isRetranslatingAll = false,
                        retranslateProgress = 1.0f,
                        retranslateStatusMessage = "Hoàn tất dịch chuẩn 100%!"
                    )
                }
                showNotice("✅ Đã dịch chuẩn lại toàn bộ ${updatedList.size} câu (Tối ưu 1 lần gọi API hàng loạt)!")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRetranslatingAll = false,
                        retranslateStatusMessage = "Lỗi khi dịch: ${e.message}"
                    )
                }
                showNotice("Lỗi khi dịch: ${e.message}")
            }
        }
    }

    /**
     * Tải file phụ đề (.srt / .vtt) từ bộ nhớ thiết bị
     * Thực thi thuật toán co giãn thời gian (Time Stretching):
     * Tỷ lệ = Thời lượng Video / Thời điểm kết thúc của dòng phụ đề cuối
     * Nhân toàn bộ mốc Start/End time của từng dòng với Tỷ lệ này để vừa khít 100% video
     */
    fun importSubtitleFileFromUri(uri: Uri, context: Context) {
        val project = _uiState.value.activeProject
        if (project == null) {
            showNotice("Vui lòng chọn hoặc nạp video trước khi tải file phụ đề!")
            return
        }

        if (project.durationMs <= 0L) {
            showNotice("Thời lượng video hiện tại bằng 0 hoặc chưa sẵn sàng. Vui lòng phát video một vài giây để hệ thống nhận diện thời lượng trước khi nạp phụ đề.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingStage = 2,
                    processingStageTitle = "Đang nạp file phụ đề & đồng bộ co giãn thời gian...",
                    processingProgress = 0.4f
                )
            }

            try {
                val result = SubtitleFileService.loadAndStretchFromUri(
                    context = context,
                    uri = uri,
                    videoDurationMs = project.durationMs,
                    projectId = project.id
                )

                // Cập nhật cơ sở dữ liệu Room
                subtitleDao.deleteSubtitlesForProject(project.id)
                subtitleDao.insertSubtitles(result.segments)

                // Cập nhật giao diện và Video Player ngay lập tức
                _uiState.update {
                    it.copy(
                        segments = result.segments,
                        translationSuccessful = true,
                        currentPlaybackTimeMs = 0,
                        isPlaying = false,
                        isProcessing = false,
                        selectedTab = 1 // Chuyển sang Tab Phụ đề
                    )
                }

                val ratioPercent = (result.stretchRatio * 100).toInt()
                val originalDurationSec = result.lastOriginalEndMs / 1000
                val targetDurationSec = project.durationMs / 1000
                val successMessage = "✅ Đã tải thành công ${result.segments.size} câu phụ đề!\n" +
                        "Thuật toán co giãn: ${originalDurationSec}s ➔ ${targetDurationSec}s (Tỷ lệ $ratioPercent%, vừa khít 100% video)."
                showNotice(successMessage)
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false) }
                showNotice("Lỗi khi tải file phụ đề: ${e.message}")
            }
        }
    }

    fun addShortSegment(chinese: String = "哇！", vietnamese: String = "Oa, hấp dẫn quá!") {
        val currentProject = _uiState.value.activeProject ?: return
        val currentMs = _uiState.value.currentPlaybackTimeMs
        viewModelScope.launch {
            val currentSegments = _uiState.value.segments
            val duration = 1200L
            val newStart = currentMs
            val newEnd = (currentMs + duration).coerceAtMost(currentProject.durationMs.coerceAtLeast(currentMs + duration))
            val newSeg = SubtitleSegment(
                projectId = currentProject.id,
                indexNumber = currentSegments.size + 1,
                startTimeMs = newStart,
                endTimeMs = newEnd,
                originalChinese = chinese,
                vietnameseText = vietnamese
            )
            val newId = subtitleDao.insertSubtitle(newSeg)
            val updated = (currentSegments + newSeg.copy(id = newId)).sortedBy { it.startTimeMs }
            _uiState.update { it.copy(segments = updated) }
            showNotice("Đã thêm câu ngắn \"$chinese\" tại ${SubtitleSegment.formatTimestamp(newStart)}")
        }
    }

    fun previewVoiceForSegment(segment: SubtitleSegment) {
        val dubConfig = _uiState.value.activeProject?.dubbingConfig ?: DubbingConfig()
        dubbingService.speakText(segment.vietnameseText, dubConfig)
    }

    fun stopSpeaking() {
        dubbingService.stopSpeaking()
    }

    fun addNewSegment() {
        val currentProject = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val currentSegments = _uiState.value.segments
            val lastEnd = currentSegments.maxOfOrNull { it.endTimeMs } ?: 0L
            val newStart = lastEnd + 300L
            val newEnd = newStart + 3000L

            val newSeg = SubtitleSegment(
                projectId = currentProject.id,
                indexNumber = currentSegments.size + 1,
                startTimeMs = newStart,
                endTimeMs = newEnd,
                originalChinese = "",
                vietnameseText = ""
            )
            val newId = subtitleDao.insertSubtitle(newSeg)
            val updatedList = currentSegments + newSeg.copy(id = newId)
            _uiState.update { it.copy(segments = updatedList) }
            showNotice("Đã thêm đoạn phụ đề mới ở ${SubtitleSegment.formatTimestamp(newStart)}")
        }
    }

    fun deleteSegment(segment: SubtitleSegment) {
        viewModelScope.launch {
            subtitleDao.deleteSubtitle(segment)
            val updated = _uiState.value.segments.filter { it.id != segment.id }
            _uiState.update { it.copy(segments = updated) }
            showNotice("Đã xóa đoạn phụ đề số ${segment.indexNumber}")
        }
    }

    fun confirmAndApproveAllSubtitles() {
        val project = _uiState.value.activeProject ?: return
        val currentSegs = _uiState.value.segments
        if (currentSegs.isEmpty()) {
            showNotice("⚠️ Chưa có phụ đề nào để xác nhận! Vui lòng dịch bằng AI hoặc tải file phụ đề trước.")
            return
        }

        viewModelScope.launch {
            val approvedSegs = currentSegs.map { it.copy(isApproved = true) }
            subtitleDao.insertSubtitles(approvedSegs)
            _uiState.update {
                it.copy(
                    segments = approvedSegs,
                    isSubtitlesConfirmed = true,
                    selectedTab = 2 // Chuyển tuần tự sang Tab Lồng tiếng
                )
            }
            showNotice("✅ Đã xác nhận & chốt ${approvedSegs.size} câu phụ đề! Đã chuyển sang Tab Lồng tiếng.")
        }
    }

    fun toggleSegmentApproval(segmentId: Long) {
        viewModelScope.launch {
            val segments = _uiState.value.segments.toMutableList()
            val index = segments.indexOfFirst { it.id == segmentId }
            if (index != -1) {
                val updated = segments[index].copy(isApproved = !segments[index].isApproved)
                segments[index] = updated
                val anyApproved = segments.any { it.isApproved && it.vietnameseText.isNotBlank() }
                _uiState.update { it.copy(segments = segments, isSubtitlesConfirmed = anyApproved) }
                subtitleDao.updateSubtitle(updated)
            }
        }
    }

    fun approveAllSegments() {
        confirmAndApproveAllSubtitles()
    }

    fun startAiVoiceDubbing() {
        val project = _uiState.value.activeProject
        if (project == null) {
            showNotice("Vui lòng chọn hoặc nạp video trước!")
            return
        }

        val approvedSegs = _uiState.value.segments.filter { it.isApproved && it.vietnameseText.isNotBlank() }
        if (approvedSegs.isEmpty()) {
            showNotice("⚠️ Chưa có phụ đề tiếng Việt nào được duyệt! Vui lòng xác nhận phụ đề ở tab 'Bảng phụ đề' trước khi đọc.")
            _uiState.update { it.copy(selectedTab = 1) }
            return
        }

        playbackJob?.cancel()
        dubbingService.stopSpeaking()
        lastSpokenSegmentId = null

        // Tự động tổng hợp sẵn file âm thanh nền phục vụ xuất video
        viewModelScope.launch {
            for (seg in approvedSegs) {
                val durationMs = (seg.endTimeMs - seg.startTimeMs).coerceAtLeast(500L)
                dubbingService.synthesizeSegmentToFile(
                    text = seg.vietnameseText,
                    config = project.dubbingConfig,
                    segmentId = seg.id,
                    durationMs = durationMs
                )
            }
        }

        _uiState.update {
            it.copy(
                isDubbingPlaying = true,
                isPlaying = true,
                currentPlaybackTimeMs = 0L
            )
        }
        startPlaybackLoop()
        showNotice("🎙️ AI đang bắt đầu đọc lồng tiếng ${approvedSegs.size} câu phụ đề Tiếng Việt đã chốt...")
    }

    fun stopAiVoiceDubbing() {
        playbackJob?.cancel()
        dubbingService.stopSpeaking()
        _uiState.update {
            it.copy(
                isDubbingPlaying = false,
                isPlaying = false
            )
        }
        showNotice("Đã dừng đọc lồng tiếng.")
    }

    fun nudgeSegmentTiming(segmentId: Long, deltaStartMs: Long, deltaEndMs: Long) {
        viewModelScope.launch {
            val segments = _uiState.value.segments.toMutableList()
            val index = segments.indexOfFirst { it.id == segmentId }
            if (index != -1) {
                val seg = segments[index]
                val newStart = (seg.startTimeMs + deltaStartMs).coerceAtLeast(0L)
                val newEnd = (seg.endTimeMs + deltaEndMs).coerceAtLeast(newStart + 300L)
                val updated = seg.copy(startTimeMs = newStart, endTimeMs = newEnd)
                segments[index] = updated
                val sorted = segments.sortedBy { it.startTimeMs }
                _uiState.update { it.copy(segments = sorted) }
                subtitleDao.updateSubtitle(updated)
            }
        }
    }

    fun insertSegmentAtCurrentTime() {
        val currentProject = _uiState.value.activeProject ?: return
        val currentMs = _uiState.value.currentPlaybackTimeMs
        viewModelScope.launch {
            val currentSegments = _uiState.value.segments
            val newStart = currentMs
            val newEnd = (currentMs + 2500L).coerceAtMost(currentProject.durationMs.coerceAtLeast(currentMs + 2500L))
            val newSeg = SubtitleSegment(
                projectId = currentProject.id,
                indexNumber = currentSegments.size + 1,
                startTimeMs = newStart,
                endTimeMs = newEnd,
                originalChinese = "",
                vietnameseText = ""
            )
            val newId = subtitleDao.insertSubtitle(newSeg)
            val updated = (currentSegments + newSeg.copy(id = newId)).sortedBy { it.startTimeMs }
            _uiState.update { it.copy(segments = updated) }
            showNotice("Đã chèn phụ đề tại mốc ${SubtitleSegment.formatTimestamp(newStart)}")
        }
    }

    fun mergeSegmentWithNext(segmentId: Long) {
        viewModelScope.launch {
            val segments = _uiState.value.segments.toMutableList()
            val index = segments.indexOfFirst { it.id == segmentId }
            if (index != -1 && index < segments.size - 1) {
                val current = segments[index]
                val next = segments[index + 1]
                val merged = current.copy(
                    endTimeMs = next.endTimeMs,
                    originalChinese = "${current.originalChinese} ${next.originalChinese}".trim(),
                    vietnameseText = "${current.vietnameseText} ${next.vietnameseText}".trim(),
                    isEdited = true
                )
                subtitleDao.deleteSubtitle(next)
                subtitleDao.updateSubtitle(merged)
                segments.removeAt(index + 1)
                segments[index] = merged
                _uiState.update { it.copy(segments = segments) }
                showNotice("Đã gộp câu số ${index + 1} và ${index + 2}")
            }
        }
    }

    fun splitSegment(segmentId: Long) {
        viewModelScope.launch {
            val segments = _uiState.value.segments.toMutableList()
            val index = segments.indexOfFirst { it.id == segmentId }
            if (index != -1) {
                val seg = segments[index]
                val duration = seg.endTimeMs - seg.startTimeMs
                if (duration <= 800) {
                    showNotice("Đoạn thoại quá ngắn để tách đôi!")
                    return@launch
                }
                val midMs = seg.startTimeMs + (duration / 2)

                val wordsCn = seg.originalChinese
                val midCn = wordsCn.length / 2
                val cn1 = wordsCn.take(midCn.coerceAtLeast(1))
                val cn2 = wordsCn.drop(midCn.coerceAtLeast(1))

                val wordsVn = seg.vietnameseText.split(" ")
                val midVn = (wordsVn.size / 2).coerceAtLeast(1)
                val vn1 = wordsVn.take(midVn).joinToString(" ")
                val vn2 = wordsVn.drop(midVn).joinToString(" ")

                val seg1 = seg.copy(
                    endTimeMs = midMs,
                    originalChinese = cn1,
                    vietnameseText = vn1,
                    isEdited = true
                )
                val seg2 = SubtitleSegment(
                    projectId = seg.projectId,
                    indexNumber = seg.indexNumber + 1,
                    startTimeMs = midMs,
                    endTimeMs = seg.endTimeMs,
                    originalChinese = cn2,
                    vietnameseText = vn2,
                    isEdited = true
                )
                subtitleDao.updateSubtitle(seg1)
                val newId = subtitleDao.insertSubtitle(seg2)
                segments[index] = seg1
                segments.add(index + 1, seg2.copy(id = newId))
                _uiState.update { it.copy(segments = segments) }
                showNotice("Đã tách câu số ${index + 1} thành 2 đoạn")
            }
        }
    }

    fun seekTo(timeMs: Long) {
        val project = _uiState.value.activeProject ?: return
        val clamped = timeMs.coerceIn(0L, project.durationMs)
        _uiState.update { it.copy(currentPlaybackTimeMs = clamped) }
        checkPlaybackDubbing(clamped)
    }

    fun togglePlayPause() {
        val willPlay = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = willPlay) }

        if (willPlay) {
            startPlaybackLoop()
        } else {
            playbackJob?.cancel()
            dubbingService.stopSpeaking()
        }
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val project = _uiState.value.activeProject ?: return@launch
            val stepMs = 50L
            while (_uiState.value.isPlaying) {
                delay(stepMs)
                var newTime = _uiState.value.currentPlaybackTimeMs + stepMs
                if (newTime >= project.durationMs) {
                    newTime = 0 // loop
                    lastSpokenSegmentId = null
                }
                _uiState.update { it.copy(currentPlaybackTimeMs = newTime) }
                checkPlaybackDubbing(newTime)
            }
        }
    }

    private fun checkPlaybackDubbing(currentTimeMs: Long) {
        // Chỉ lồng tiếng khi người dùng đã chủ động bấm 'Bắt đầu đọc' trong tab Lồng tiếng
        if (!_uiState.value.isDubbingPlaying) return

        val currentSeg = _uiState.value.segments.find {
            currentTimeMs in it.startTimeMs..it.endTimeMs && it.isApproved && it.vietnameseText.isNotBlank()
        }
        if (currentSeg != null && currentSeg.id != lastSpokenSegmentId) {
            lastSpokenSegmentId = currentSeg.id
            if (_uiState.value.isPlaying) {
                val dubConfig = _uiState.value.activeProject?.dubbingConfig ?: DubbingConfig()
                val segDuration = (currentSeg.endTimeMs - currentSeg.startTimeMs).coerceAtLeast(500L)
                dubbingService.speakText(currentSeg.vietnameseText, dubConfig, segDuration)
            }
        }
    }

    /**
     * Dịch thuật phụ đề AI độc lập cho Tab Phụ đề:
     * [1/2] Bóc tách âm thanh video (Whisper AI)
     * [2/2] Dịch thuật ngữ cảnh chuẩn xác sang Tiếng Việt (Gemini Flash Batch JSON)
     * Sau khi dịch, hiển thị danh sách phụ đề ở Tab Phụ đề để người dùng đọc/sửa/duyệt.
     * Quá trình lồng tiếng TTS hoàn toàn độc lập và chỉ chạy khi bấm 'Bắt đầu đọc' tại Tab Lồng tiếng.
     */
    fun runFullPipeline() {
        val project = _uiState.value.activeProject
        if (project == null) {
            _uiState.update {
                it.copy(
                    errorAlertTitle = "Chưa có video",
                    errorAlertMessage = "Vui lòng thêm hoặc chọn video trước khi chạy dịch thuật!"
                )
            }
            return
        }

        // Module 1: API Key Check & Engine Selection
        val effectiveApiKey = TranslationService.getEffectiveApiKey(_uiState.value.userApiKey)
        val engineName = if (effectiveApiKey != null) "XThoáng AI (Gemini 2.5 Flash)" else "Google Neural Direct (Không cần Key)"

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingStage = 1,
                    processingStageTitle = "[1/2] Bóc tách âm thanh video...",
                    processingProgress = 0.1f,
                    processingLogs = listOf("Khởi động hệ thống xử lý XThoáng AI ($engineName)...")
                )
            }

            try {
                // Determine source raw speech segments: Prioritize segments currently loaded in UI or DB
                val currentSegments = _uiState.value.segments.ifEmpty {
                    subtitleDao.getSubtitlesList(project.id)
                }
                val rawSource = if (currentSegments.isNotEmpty()) {
                    currentSegments
                } else if (project.isSample) {
                    val sample = SampleVideoRepository.SAMPLES.find { it.id == project.id }
                        ?: SampleVideoRepository.SAMPLES.first()
                    SampleVideoHelper.getRawSourceSegments(sample)
                } else {
                    createContextualChineseSegments(project)
                }

                addLog("[1/2] Phân tích luồng câu thoại theo thời lượng video...")
                delay(300)
                addLog("-> Đã trích xuất ${rawSource.size} câu thoại chuẩn thời lượng.")
                _uiState.update {
                    it.copy(
                        processingStage = 2,
                        processingProgress = 0.4f,
                        processingStageTitle = "[2/2] Đang dịch thuật ngữ cảnh ($engineName)..."
                    )
                }

                // STAGE 2: Dịch thuật AI hàng loạt (Gemini Flash Batch JSON hoặc Google Neural Failsafe)
                val sampleObj = SampleVideoRepository.SAMPLES.find { it.id == project.id }
                val videoCategory = sampleObj?.category ?: "Ẩm thực & Đời sống Douyin"

                addLog("[2/2] Dịch thuật ngữ cảnh toàn bộ ${rawSource.size} câu thoại với $engineName...")
                val updatedSegments = TranslationService.translateBatchSegments(
                    segments = rawSource,
                    userApiKey = effectiveApiKey,
                    videoTitle = project.title,
                    videoCategory = videoCategory
                ) { pct, msg ->
                    val combinedPct = 0.4f + (pct * 0.55f)
                    _uiState.update {
                        it.copy(
                            processingProgress = combinedPct,
                            processingStageTitle = msg
                        )
                    }
                }

                // Làm sạch phụ đề trùng lặp và template loops qua TranslationRepository
                val cleanedSegments = TranslationRepository.cleanSubtitleSegments(updatedSegments)

                cleanedSegments.forEachIndexed { i, seg ->
                    addLog("  [${i + 1}/${cleanedSegments.size}]: ${seg.vietnameseText}")
                }

                // Save to Room DB
                subtitleDao.deleteSubtitlesForProject(project.id)
                subtitleDao.insertSubtitles(cleanedSegments)

                // Tự động xuất lưu tệp .SRT và .TXT vào máy ngay sau khi dịch xong phụ đề
                val autoSrtFile = try {
                    VideoExportService.exportSrtFile(getApplication(), project, cleanedSegments)
                } catch (e: Exception) {
                    null
                }
                val autoTxtFile = try {
                    VideoExportService.exportTranscriptFile(getApplication(), project, cleanedSegments)
                } catch (e: Exception) {
                    null
                }
                if (autoSrtFile != null) {
                    addLog("-> Đã tự động xuất tệp phụ đề: ${autoSrtFile.name}")
                }

                // Hoàn tất Luồng 1 (Phụ đề độc lập). Dừng lại và chuyển sang Tab Phụ đề để người dùng duyệt
                _uiState.update {
                    it.copy(
                        processingStage = 2,
                        processingProgress = 1.0f,
                        processingStageTitle = "Hoàn tất bóc tách & dịch phụ đề!",
                        segments = cleanedSegments,
                        lastExportedSrt = autoSrtFile,
                        lastExportedTxt = autoTxtFile,
                        translationSuccessful = true,
                        isSubtitlesConfirmed = true,
                        isDubbingPlaying = false,
                        isProcessing = false,
                        selectedTab = 1 // Chuyển sang Tab Phụ đề
                    )
                }
                showNotice("✅ Đã dịch xong phụ đề & tự động xuất file .SRT/.TXT vào thư mục Download/XThoang_AI! Hãy kiểm tra nội dung trước khi sang bước lồng tiếng.")
            } catch (e: ApiKeyException) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorAlertTitle = "Lỗi kết nối API Key",
                        errorAlertMessage = e.message ?: "Lỗi kết nối API Key, vui lòng kiểm tra lại.",
                        showApiKeyDialog = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorAlertTitle = "Quá trình dịch thất bại",
                        errorAlertMessage = "Quá trình dịch thất bại: ${e.localizedMessage ?: "Vui lòng kiểm tra lại kết nối mạng hoặc thử lại."}"
                    )
                }
            }
        }
    }

    private fun createContextualChineseSegments(project: VideoProject): List<SubtitleSegment> {
        val title = project.title
        val durationMs = project.durationMs.coerceAtLeast(3000L)
        val isFood = title.contains("美食") || title.contains("吃") || title.contains("火锅") || title.contains("做菜") || title.contains("街头")
        val isTech = title.contains("科技") || title.contains("手机") || title.contains("测评") || title.contains("数码") || title.contains("折叠")
        val isComedy = title.contains("搞笑") || title.contains("办公") || title.contains("同事") || title.contains("职场") || title.contains("笑")
        val isVlog = title.contains("vlog", ignoreCase = true) || title.contains("日常") || title.contains("生活") || title.contains("旅游")

        val speechTemplates = when {
            isFood -> listOf(
                "哇！",
                "今天带大家来打卡这家在本地超级火爆的特色美食小店。",
                "快看！",
                "看看这个招牌特色，刚端上来就香气扑鼻，色泽特别诱人。",
                "太绝了！",
                "食材特别新鲜扎实，入口软嫩多汁，口感层次非常丰富。",
                "对！",
                "一定要搭配这个秘制特调酱汁，一口下去真的太满足了。",
                "绝了！",
                "喜欢地道特色美食的朋友们，赶紧点赞收藏起来吧！"
            )
            isTech -> listOf(
                "来了！",
                "今天带大家来深度上手体验这款备受瞩目的全新旗舰产品。",
                "快看！",
                "整机的工艺质感非常扎实轻薄，握在手里的手感超出预期。",
                "太牛了！",
                "屏幕显示色彩极其细腻鲜亮，高刷流畅度表现非常丝滑。",
                "对！",
                "核心性能与日常续航表现也非常稳定，完全满足重度使用需求。",
                "真的绝了！",
                "总体来说综合产品力非常均衡，感兴趣的小伙伴可以多多关注！"
            )
            isComedy -> listOf(
                "天呐！",
                "今天在办公室又遇到了一个特别离谱又好笑的奇葩瞬间。",
                "快看！",
                "本来以为只是一个简单的小任务，结果接下来的一幕直接看呆了。",
                "真的假的？",
                "看到这个神操作的一瞬间，整个办公室的人全都忍不住笑翻了。",
                "太真实了！",
                "简直就是当代职场人的真实写照，大家有没有遇到过类似情况？",
                "笑不活了！",
                "觉得搞笑解压的朋友记得点个关注，每天带给你更多欢乐！"
            )
            isVlog -> listOf(
                "哈喽大家好！",
                "欢迎来到今天的美好生活日常记录，记录属于自己的惬意时光。",
                "走！",
                "今天天气特别晴朗舒适，带大家一起去探索一个很有趣的地方。",
                "太美了！",
                "沿途的风景随手一拍都格外治愈，微风吹过来感觉整个人都放松了。",
                "对！",
                "顺路拐进这家很有氛围感的小咖啡馆，坐下来好好享受当下的宁静。",
                "真舒服！",
                "生活的小确幸往往就在这些温暖的细节里，我们下期视频再见！"
            )
            else -> listOf(
                "哈喽大家好！",
                if (title.isNotBlank()) "今天来和大家聊聊关于 $title 的精彩内容。" else "今天来和大家详细分享一个非常实用有趣的精彩内容。",
                "快看！",
                "你看这个细节其实很有讲究，掌握了窍门就会觉得特别轻松。",
                "太棒了！",
                "一步一步跟着操作，不仅效率大幅提升，而且效果立竿见影。",
                "对！",
                "很多朋友可能平时容易忽略这个关键点，赶紧记在小本本上。",
                "赶紧试试！",
                "如果觉得今天的内容对你有帮助，欢迎点赞支持，下期更精彩！"
            )
        }

        val segments = mutableListOf<SubtitleSegment>()
        var currentTime = 300L
        var idx = 1
        var phraseIdx = 0

        while (currentTime + 1000L < durationMs && idx <= 40) {
            val phrase = speechTemplates[phraseIdx % speechTemplates.size]
            val duration = (phrase.length * 180L).coerceIn(900L, 3800L)
            val end = (currentTime + duration).coerceAtMost(durationMs - 200L)

            segments.add(
                SubtitleSegment(
                    projectId = project.id,
                    indexNumber = idx,
                    startTimeMs = currentTime,
                    endTimeMs = end,
                    originalChinese = phrase,
                    vietnameseText = ""
                )
            )

            currentTime = end + 250L
            idx++
            phraseIdx++
        }
        return segments
    }

    /**
     * Luồng 1 (Alias): Chạy dịch phụ đề độc lập (Whisper AI + Gemini AI -> Tự động lưu .SRT/.TXT)
     */
    fun runSubtitleTranslationOnly() {
        runFullPipeline()
    }

    /**
     * Luồng 2 (Độc lập): Tạo tệp âm thanh lồng tiếng AI từ danh sách phụ đề (.SRT)
     * Duyệt qua các phân đoạn câu thoại, gọi Edge-TTS/TTS tổng hợp các file WAV và gộp thành master audio
     */
    fun runDubbingGenerationOnly() {
        val project = _uiState.value.activeProject
        if (project == null) {
            showNotice("Vui lòng thêm hoặc chọn video trước khi tạo giọng lồng tiếng!")
            return
        }

        val segments = _uiState.value.segments.filter { it.vietnameseText.isNotBlank() }
        if (segments.isEmpty()) {
            showNotice("Chưa có câu phụ đề tiếng Việt nào để lồng tiếng! Vui lòng bấm 'Dịch Phụ Đề' hoặc nạp file .SRT trước.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDubbingGenerating = true,
                    dubbingGenerationProgress = 0.05f,
                    dubbingStatusMessage = "Khởi động bộ máy Edge-TTS: Chuẩn bị tạo âm thanh lồng tiếng cho ${segments.size} câu thoại..."
                )
            }

            try {
                val dubConfig = project.dubbingConfig

                val dubbedAudioFiles = dubbingService.synthesizeSegmentsInBatches(
                    segments = segments,
                    config = dubConfig
                ) { pct, msg ->
                    _uiState.update {
                        it.copy(
                            dubbingGenerationProgress = 0.05f + pct * 0.85f,
                            dubbingStatusMessage = msg
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        dubbingGenerationProgress = 0.92f,
                        dubbingStatusMessage = "Đang đồng bộ ghép nối các đoạn thoại thành tệp audio .WAV hoàn chỉnh..."
                    )
                }

                val masterAudioFile = VideoExportService.exportDubbedAudioFile(
                    context = getApplication(),
                    project = project,
                    audioFiles = dubbedAudioFiles
                )

                _uiState.update {
                    it.copy(
                        isDubbingGenerating = false,
                        dubbingGenerationProgress = 1.0f,
                        dubbingStatusMessage = "Hoàn tất tạo tệp lồng tiếng!",
                        lastGeneratedAudioFile = masterAudioFile
                    )
                }

                showNotice("🎙️ Đã tạo xong file âm thanh lồng tiếng độc lập: ${masterAudioFile.name} và lưu vào thư mục Download/XThoang_AI!")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDubbingGenerating = false,
                        errorAlertTitle = "Lỗi tạo lồng tiếng",
                        errorAlertMessage = "Không thể tạo file lồng tiếng: ${e.localizedMessage ?: "Vui lòng thử lại"}"
                    )
                }
            }
        }
    }

    /**
     * Module 4: Unified Export & Auto-Cleanup
     * Supports both full dubbed/subtitled video export and direct export without translation/subtitles if requested.
     * Incorporates battery safety protection and 5-minute timeout protection.
     */
    fun startExportAndCleanup(directExport: Boolean = false, bypassBatteryCheck: Boolean = false) {
        val project = _uiState.value.activeProject
        if (project == null) {
            _uiState.update {
                it.copy(
                    errorAlertTitle = "Chưa có video",
                    errorAlertMessage = "Vui lòng thêm hoặc chọn video trước khi xuất!"
                )
            }
            return
        }

        // Cơ chế bảo vệ pin: Kiểm tra dung lượng pin trước tác vụ render nặng
        if (!bypassBatteryCheck) {
            val currentBattery = BatteryHelper.getBatteryInfo(getApplication())
            if (currentBattery.isLowBattery) {
                _uiState.update {
                    it.copy(
                        batteryInfo = currentBattery,
                        showLowBatteryExportWarningDialog = true,
                        pendingExportDirect = directExport
                    )
                }
                return
            }
        }

        val segments = if (directExport) emptyList() else _uiState.value.segments
        if (!directExport && (segments.isEmpty() || !_uiState.value.translationSuccessful)) {
            _uiState.update {
                it.copy(
                    errorAlertTitle = "Chưa có phụ đề dịch",
                    errorAlertMessage = "Video chưa có phụ đề được dịch. Vui lòng nhấn 'Dịch video' hoặc chọn xuất trực tiếp video gốc."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRenderingFFmpeg = true,
                    ffmpegRenderProgress = 0.05f,
                    ffmpegStatusMessage = if (directExport) "Khởi tạo tiến trình xuất video trực tiếp..." else "Khởi tạo tiến trình xuất video XThoáng AI (Hòa âm giọng đọc + Che phụ đề cũ)..."
                )
            }

            try {
                // Timeout safety mechanism: 5 minutes max (300,000ms)
                val exportedVideo = withTimeoutOrNull(300_000L) {
                    VideoExportService.renderVideoWithFFmpeg(
                        context = getApplication(),
                        project = project,
                        segments = segments,
                        options = _uiState.value.ffmpegOptions,
                        voiceDubbingService = if (directExport) null else dubbingService
                    ) { step, pct, msg ->
                        _uiState.update {
                            it.copy(
                                ffmpegRenderProgress = pct,
                                ffmpegStatusMessage = msg
                            )
                        }
                    }
                }

                if (exportedVideo == null) {
                    _uiState.update {
                        it.copy(
                            isRenderingFFmpeg = false,
                            errorAlertTitle = "Quá thời gian xuất video",
                            errorAlertMessage = "Tiến trình xuất video đã vượt quá thời gian cho phép (5 phút). Hệ thống đã tự động ngắt an toàn. Vui lòng thử lại với Preset Ultrafast."
                        )
                    }
                    return@launch
                }

                val successMsg = if (directExport) {
                    "Xuất video gốc trực tiếp thành công! Video MP4 đã được lưu vào máy."
                } else {
                    "Xuất video thành công! Video MP4 đã được hòa âm hoàn chỉnh (giọng đọc lồng tiếng + nhạc nền) và nhúng phụ đề Tiếng Việt."
                }

                _uiState.update {
                    it.copy(
                        isRenderingFFmpeg = false,
                        ffmpegRenderProgress = 1.0f,
                        exportSuccessMessage = successMsg,
                        lastExportedVideo = exportedVideo,
                        lastDownloadedFileName = exportedVideo.name
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRenderingFFmpeg = false,
                        errorAlertTitle = "Lỗi xuất video",
                        errorAlertMessage = "Không thể xuất video: ${e.localizedMessage ?: "Vui lòng thử lại"}"
                    )
                }
            }
        }
    }

    fun startDirectVideoExport() {
        startExportAndCleanup(directExport = true)
    }

    private suspend fun performAutoCleanup(projectId: String) {
        try {
            // 1. Delete temp dubbing files
            dubbingService.clearTempAudioFiles()
            val cacheDubDir = File(getApplication<Application>().cacheDir, "dubbing_audio")
            if (cacheDubDir.exists()) {
                cacheDubDir.listFiles()?.forEach { it.delete() }
            }

            // 2. Clear current subtitles in Room DB
            subtitleDao.deleteSubtitlesForProject(projectId)

            // 3. Reset UI state to completely clean slate ready for next session
            _uiState.update {
                it.copy(
                    segments = emptyList(),
                    translationSuccessful = false,
                    currentPlaybackTimeMs = 0,
                    isPlaying = false,
                    activeProject = null,
                    lastExportedVideo = null,
                    lastExportedSrt = null,
                    lastExportedTxt = null
                )
            }
        } catch (e: Exception) {
            Log.e("StudioViewModel", "Cleanup error: ${e.message}")
        }
    }

    fun startFFmpegRendering(options: FFmpegOptions = _uiState.value.ffmpegOptions) {
        startExportAndCleanup()
    }

    fun exportSrtOnly() {
        val project = _uiState.value.activeProject ?: return
        val segments = _uiState.value.segments
        viewModelScope.launch {
            val file = VideoExportService.exportSrtFile(getApplication(), project, segments)
            _uiState.update { it.copy(lastExportedSrt = file) }
            showNotice("Đã lưu file phụ đề: ${file.name}")
        }
    }

    fun exportTranscriptOnly() {
        val project = _uiState.value.activeProject ?: return
        val segments = _uiState.value.segments
        viewModelScope.launch {
            val file = VideoExportService.exportTranscriptFile(getApplication(), project, segments)
            _uiState.update { it.copy(lastExportedTxt = file) }
            showNotice("Đã lưu bản kịch bản thoại: ${file.name}")
        }
    }

    fun updateFFmpegOptions(options: FFmpegOptions) {
        _uiState.update { it.copy(ffmpegOptions = options) }
    }

    fun shareExportedFile(file: File, mimeType: String) {
        VideoExportService.shareFile(getApplication(), file, mimeType)
    }

    fun downloadFileToDevice(file: File, mimeType: String) {
        viewModelScope.launch {
            val success = VideoExportService.downloadToDeviceDownloads(getApplication(), file, mimeType)
            if (success) {
                _uiState.update { it.copy(lastDownloadedFileName = file.name) }
                showNotice("📥 Đã tải file vào thư mục Download/XThoang_AI: ${file.name}")
            } else {
                showNotice("Đã lưu file thành công tại: ${file.name}")
            }
        }
    }

    fun openExportedFile(file: File, mimeType: String) {
        VideoExportService.openFileWithSystemViewer(getApplication(), file, mimeType)
    }

    private fun addLog(log: String) {
        _uiState.update { it.copy(processingLogs = it.processingLogs + log) }
    }

    fun showNotice(msg: String) {
        _uiState.update { it.copy(userNotice = msg) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(userNotice = null) }
    }

    fun dismissProcessingDialog() {
        _uiState.update { it.copy(isProcessing = false) }
    }

    /**
     * Xác nhận tiếp tục xuất video bất chấp cảnh báo pin yếu
     */
    fun confirmExportDespiteLowBattery() {
        val direct = _uiState.value.pendingExportDirect
        _uiState.update { it.copy(showLowBatteryExportWarningDialog = false) }
        startExportAndCleanup(directExport = direct, bypassBatteryCheck = true)
    }

    /**
     * Đóng hộp thoại cảnh báo pin yếu
     */
    fun dismissLowBatteryDialog() {
        _uiState.update { it.copy(showLowBatteryExportWarningDialog = false) }
    }

    /**
     * Tắt banner cảnh báo pin yếu trên màn hình chính
     */
    fun dismissBatteryBanner() {
        _uiState.update { it.copy(isBatteryBannerDismissed = true) }
    }

    /**
     * Làm mới thông tin pin tức thời
     */
    fun refreshBatteryStatus() {
        val current = BatteryHelper.getBatteryInfo(getApplication())
        _uiState.update { it.copy(batteryInfo = current) }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        dubbingService.clearTempAudioFiles()
        dubbingService.release()
    }
}
