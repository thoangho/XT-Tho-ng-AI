package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DubbingConfig
import com.example.data.model.SubtitleSegment
import com.example.data.network.TranslationService
import com.example.data.tts.VoiceDubbingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DubbingViewModel(application: Application? = null) : ViewModel() {

    private var voiceDubbingService: VoiceDubbingService? = application?.let { VoiceDubbingService(it) }
    private var dubbingConfig: DubbingConfig = DubbingConfig()

    private val _dubbingProgress = MutableStateFlow(0) // StateFlow<Int> (0 to 100)
    val dubbingProgress: StateFlow<Int> = _dubbingProgress.asStateFlow()

    private val _statusText = MutableStateFlow("Sẵn sàng")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _isDubbing = MutableStateFlow(false)
    val isDubbing: StateFlow<Boolean> = _isDubbing.asStateFlow()

    private val _generatedAudioFiles = MutableStateFlow<List<File>>(emptyList())
    val generatedAudioFiles: StateFlow<List<File>> = _generatedAudioFiles.asStateFlow()

    fun initService(context: Context) {
        if (voiceDubbingService == null) {
            voiceDubbingService = VoiceDubbingService(context.applicationContext)
        }
    }

    fun updateDubbingConfig(config: DubbingConfig) {
        this.dubbingConfig = config
    }

    fun processAudioDubbing(subtitleList: List<String>) {
        viewModelScope.launch {
            if (subtitleList.isEmpty()) {
                _statusText.value = "Danh sách phụ đề trống"
                _dubbingProgress.value = 0
                return@launch
            }

            _isDubbing.value = true
            _dubbingProgress.value = 0
            val audioFiles = mutableListOf<File>()
            val total = subtitleList.size

            subtitleList.forEachIndexed { index, sentence ->
                val current = index + 1
                val percentInt = ((current.toFloat() / total) * 100).toInt()
                _statusText.value = "Đang tạo giọng đọc $current/$total câu - $percentInt%"
                _dubbingProgress.value = percentInt

                // Gọi API TTS tạo file âm thanh
                val audioFile = generateTTSAudioForSentence(sentence, index.toLong())
                if (audioFile != null && audioFile.exists()) {
                    audioFiles.add(audioFile)
                }
            }

            _generatedAudioFiles.value = audioFiles
            _isDubbing.value = false
            _dubbingProgress.value = 100
            _statusText.value = "Tạo lồng tiếng hoàn tất!"
        }
    }

    suspend fun generateTTSAudioForSentence(text: String, segmentId: Long = 0L): File? = withContext(Dispatchers.IO) {
        val sanitized = TranslationService.sanitizeForTts(text)
        if (sanitized.isBlank()) return@withContext null

        voiceDubbingService?.synthesizeSegmentToFile(
            text = sanitized,
            config = dubbingConfig,
            segmentId = segmentId,
            durationMs = 0L
        )
    }

    fun processSegmentsDubbing(segments: List<SubtitleSegment>, config: DubbingConfig? = null) {
        val currentConfig = config ?: dubbingConfig
        viewModelScope.launch {
            if (segments.isEmpty()) {
                _statusText.value = "Danh sách phụ đề trống"
                _dubbingProgress.value = 0
                return@launch
            }

            _isDubbing.value = true
            _dubbingProgress.value = 0
            val total = segments.size

            val service = voiceDubbingService
            if (service != null) {
                val results = service.synthesizeSegmentsInBatches(
                    segments = segments,
                    config = currentConfig
                ) { pct, _ ->
                    val current = (pct * total).toInt().coerceIn(1, total)
                    val percentInt = (pct * 100).toInt().coerceIn(0, 100)
                    _dubbingProgress.value = percentInt
                    _statusText.value = "Đang tạo giọng đọc $current/$total câu - $percentInt%"
                }
                _generatedAudioFiles.value = results.map { it.second }
            } else {
                val texts = segments.map { it.vietnameseText }
                processAudioDubbing(texts)
            }

            _isDubbing.value = false
            _dubbingProgress.value = 100
            _statusText.value = "Tạo lồng tiếng hoàn tất!"
        }
    }

    fun stopDubbing() {
        voiceDubbingService?.stopSpeaking()
        _isDubbing.value = false
        _dubbingProgress.value = 0
        _statusText.value = "Đã dừng lồng tiếng"
    }

    override fun onCleared() {
        super.onCleared()
        voiceDubbingService?.stopSpeaking()
    }
}
