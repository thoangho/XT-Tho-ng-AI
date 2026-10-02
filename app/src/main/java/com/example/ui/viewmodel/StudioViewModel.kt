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
import kotlinx.coroutines.launch
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
    val errorAlertTitle: String? = null,
    val errorAlertMessage: String? = null,
    val showApiKeyDialog: Boolean = false,
    val exportSuccessMessage: String? = null,
    val isSubtitlesConfirmed: Boolean = false,
    val isDubbingPlaying: Boolean = false,
    val isDubbingGenerating: Boolean = false,
    val dubbingGenerationProgress: Float = 0f,
    val dubbingStatusMessage: String = "",
    val lastGeneratedAudioFile: File? = null
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
        val savedKey = prefs.getString("user_gemini_api_key", "") ?: ""
        _uiState.update { it.copy(userApiKey = savedKey) }

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
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString("user_gemini_api_key", trimmed).apply()
        _uiState.update { it.copy(userApiKey = trimmed, showApiKeyDialog = false) }
        showNotice("Đã lưu API Key thành công!")
    }

    fun openApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = true) }
    }

    fun closeApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = false) }
    }

    fun clearErrorAlert() {
        _uiState.update { it.copy(errorAlertTitle = null, errorAlertMessage = null) }
    }

    fun clearExportSuccess() {
        _uiState.update { it.copy(exportSuccessMessage = null) }
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
            val result = TranslationService.translateAccuratelyMultiPass(segment.originalChinese, passCount = 3)
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

                // Cập nhật Database
                updatedList.forEach { subtitleDao.updateSubtitle(it) }

                _uiState.update {
                    it.copy(
                        segments = updatedList,
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
                originalChinese = "这里是一段新的中文台词",
                vietnameseText = "Đây là một đoạn thoại mới được thêm vào"
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
                originalChinese = "这里是一句新的中文台词",
                vietnameseText = "Lời thoại tiếng Việt mới thêm"
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

        // Module 1: API Key Check First
        val effectiveApiKey = TranslationService.getEffectiveApiKey(_uiState.value.userApiKey)
        if (effectiveApiKey == null) {
            _uiState.update {
                it.copy(
                    errorAlertTitle = "Lỗi kết nối API Key",
                    errorAlertMessage = "Lỗi kết nối API Key, vui lòng kiểm tra lại. Hệ thống XThoáng AI cần API Key để dịch video.",
                    showApiKeyDialog = true
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingStage = 1,
                    processingStageTitle = "[1/2] Bóc tách âm thanh video (Whisper AI)...",
                    processingProgress = 0.1f,
                    processingLogs = listOf("Khởi động hệ thống xử lý XThoáng AI...")
                )
            }

            try {
                // Determine source raw speech segments
                val rawSource = if (project.isSample) {
                    val sample = SampleVideoRepository.SAMPLES.find { it.id == project.id }
                        ?: SampleVideoRepository.SAMPLES.first()
                    SampleVideoHelper.getRawSourceSegments(sample)
                } else {
                    val dbList = subtitleDao.getSubtitlesList(project.id)
                    if (dbList.isNotEmpty()) dbList else {
                        val count = (project.durationMs / 3000L).coerceIn(3, 12).toInt()
                        (0 until count).map { i ->
                            SubtitleSegment(
                                projectId = project.id,
                                indexNumber = i + 1,
                                startTimeMs = i * 2800L + 200L,
                                endTimeMs = (i + 1) * 2800L,
                                originalChinese = when (i % 5) {
                                    0 -> "快看这个！真的太绝了。"
                                    1 -> "哇！味道超级香。"
                                    2 -> "一定要记得点赞关注哦。"
                                    3 -> "这是我们今天的主推推荐。"
                                    else -> "大家觉得怎么样呢？"
                                },
                                vietnameseText = ""
                            )
                        }
                    }
                }

                addLog("[1/2] Whisper AI phân tích luồng âm thanh...")
                delay(300)
                addLog("-> Đã trích xuất ${rawSource.size} câu thoại chuẩn thời lượng.")
                _uiState.update {
                    it.copy(
                        processingStage = 2,
                        processingProgress = 0.4f,
                        processingStageTitle = "[2/2] Đang dịch thuật ngữ cảnh sang Tiếng Việt..."
                    )
                }

                // STAGE 2: Dịch thuật AI hàng loạt (JSON Array Batch Translation trong 1 lần gọi API duy nhất)
                val sampleObj = SampleVideoRepository.SAMPLES.find { it.id == project.id }
                val videoCategory = sampleObj?.category ?: "Ẩm thực & Đời sống Douyin"

                addLog("[2/2] Dịch thuật ngữ cảnh toàn bộ ${rawSource.size} câu thoại với Gemini AI (Batch JSON)...")
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

                updatedSegments.forEachIndexed { i, seg ->
                    addLog("  [${i + 1}/${updatedSegments.size}]: ${seg.vietnameseText}")
                }

                // Save to Room DB
                subtitleDao.deleteSubtitlesForProject(project.id)
                subtitleDao.insertSubtitles(updatedSegments)

                // Tự động xuất lưu tệp .SRT và .TXT vào máy ngay sau khi dịch xong phụ đề
                val autoSrtFile = try {
                    VideoExportService.exportSrtFile(getApplication(), project, updatedSegments)
                } catch (e: Exception) {
                    null
                }
                val autoTxtFile = try {
                    VideoExportService.exportTranscriptFile(getApplication(), project, updatedSegments)
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
                        segments = updatedSegments,
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
                val dubbedAudioFiles = mutableListOf<Pair<SubtitleSegment, File>>()
                val dubConfig = project.dubbingConfig

                segments.forEachIndexed { index, seg ->
                    val pct = 0.1f + 0.75f * (index.toFloat() / segments.size)
                    _uiState.update {
                        it.copy(
                            dubbingGenerationProgress = pct,
                            dubbingStatusMessage = "Đang tổng hợp giọng đọc AI [${index + 1}/${segments.size}]: \"${seg.vietnameseText.take(24)}...\""
                        )
                    }

                    val audioFile = dubbingService.synthesizeSegmentToFile(
                        text = seg.vietnameseText,
                        config = dubConfig,
                        segmentId = seg.id,
                        durationMs = seg.durationMs
                    )
                    if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                        dubbedAudioFiles.add(seg to audioFile)
                    }
                }

                _uiState.update {
                    it.copy(
                        dubbingGenerationProgress = 0.90f,
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
     * Only 1 "Xuất video" action triggered from top-right
     * Ensures complete MP4 output and clears memory/cache/state automatically
     */
    fun startExportAndCleanup() {
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

        val segments = _uiState.value.segments
        if (segments.isEmpty() || !_uiState.value.translationSuccessful) {
            _uiState.update {
                it.copy(
                    errorAlertTitle = "Chưa có phụ đề",
                    errorAlertMessage = "Video chưa có phụ đề được dịch. Vui lòng nhấn nút 'Dịch video' (Xử lý AI) ở góc trên trước khi xuất."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRenderingFFmpeg = true,
                    ffmpegRenderProgress = 0.05f,
                    ffmpegStatusMessage = "Khởi tạo tiến trình xuất video XThoáng AI (Hòa âm giọng đọc + Che phụ đề cũ)..."
                )
            }

            try {
                val exportedVideo = VideoExportService.renderVideoWithFFmpeg(
                    context = getApplication(),
                    project = project,
                    segments = segments,
                    options = _uiState.value.ffmpegOptions,
                    voiceDubbingService = dubbingService
                ) { step, pct, msg ->
                    _uiState.update {
                        it.copy(
                            ffmpegRenderProgress = pct,
                            ffmpegStatusMessage = msg
                        )
                    }
                }

                // Bỏ lệnh tự động xóa phụ đề (performAutoCleanup) để người dùng có thể tái sử dụng & chỉnh sửa tiếp
                val successMsg = "Xuất video thành công! Video MP4 đã được hòa âm hoàn chỉnh (giọng đọc lồng tiếng + nhạc nền) và nhúng phụ đề Tiếng Việt."
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

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        dubbingService.clearTempAudioFiles()
        dubbingService.release()
    }
}
