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

    /**
     * Ghép danh sách các tệp âm thanh lồng tiếng vào video gốc bằng lệnh FFmpeg:
     * 1. Sinh danh sách concat_list.txt và ghép nối âm thanh thành master audio
     * 2. Hòa trộn âm thanh lồng tiếng vào video gốc bằng FFmpeg:
     *    ffmpeg -i input.mp4 -i dubbed.wav -c:v copy -c:a aac -map 0:v:0 -map 1:a:0 output.mp4 -y
     */
    fun stitchDubbedAudioWithVideo(
        context: Context,
        inputVideo: File,
        audioFiles: List<File>,
        outputFile: File,
        onComplete: (Boolean, File?) -> Unit
    ) {
        viewModelScope.launch {
            if (!inputVideo.exists() || audioFiles.isEmpty()) {
                _statusText.value = "Tệp video hoặc âm thanh không hợp lệ"
                onComplete(false, null)
                return@launch
            }

            _isDubbing.value = true
            _statusText.value = "Bước 1/2: Đang ghép nối danh sách âm thanh lồng tiếng..."
            _dubbingProgress.value = 30

            val mergedAudioFile = withContext(Dispatchers.IO) {
                try {
                    val cacheDir = context.cacheDir
                    val concatListFile = File(cacheDir, "concat_list_${System.currentTimeMillis()}.txt")
                    val sb = StringBuilder()
                    for (file in audioFiles) {
                        if (file.exists() && file.length() > 0) {
                            sb.append("file '${file.absolutePath}'\n")
                        }
                    }
                    concatListFile.writeText(sb.toString())

                    val masterAudio = File(cacheDir, "master_dubbed_${System.currentTimeMillis()}.wav")
                    val concatCmd = "-f concat -safe 0 -i \"${concatListFile.absolutePath}\" -c copy \"${masterAudio.absolutePath}\" -y"
                    val session = com.arthenica.ffmpegkit.FFmpegKit.execute(concatCmd)

                    concatListFile.delete()

                    if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode) && masterAudio.exists()) {
                        masterAudio
                    } else {
                        // Fallback: nếu chỉ có 1 file
                        audioFiles.firstOrNull { it.exists() }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DubbingVM", "Lỗi ghép audio: ${e.message}", e)
                    null
                }
            }

            if (mergedAudioFile == null || !mergedAudioFile.exists()) {
                _isDubbing.value = false
                _statusText.value = "Lỗi ghép nối âm thanh"
                onComplete(false, null)
                return@launch
            }

            _statusText.value = "Bước 2/2: Đang hòa trộn âm thanh vào video qua FFmpeg..."
            _dubbingProgress.value = 70

            val success = withContext(Dispatchers.IO) {
                try {
                    val mergeCmd = "-i \"${inputVideo.absolutePath}\" -i \"${mergedAudioFile.absolutePath}\" -c:v copy -c:a aac -map 0:v:0 -map 1:a:0 \"${outputFile.absolutePath}\" -y"
                    val session = com.arthenica.ffmpegkit.FFmpegKit.execute(mergeCmd)
                    com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode) && outputFile.exists() && outputFile.length() > 0
                } catch (e: Exception) {
                    android.util.Log.e("DubbingVM", "Lỗi merge video & audio: ${e.message}", e)
                    false
                } finally {
                    mergedAudioFile.delete()
                }
            }

            _isDubbing.value = false
            _dubbingProgress.value = if (success) 100 else 0
            _statusText.value = if (success) "Hoàn tất ghép video lồng tiếng!" else "Ghép video thất bại"
            onComplete(success, if (success) outputFile else null)
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
