package com.example.data.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.DubbingConfig
import com.example.data.network.TranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

class VoiceDubbingService(private val context: Context) {
    private val TAG = "VoiceDubbingService"

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isVietnameseSupported = false

    var onDuckingChange: ((isDucking: Boolean) -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val viLocale = Locale.forLanguageTag("vi-VN")
                val result = tts?.setLanguage(viLocale)
                isVietnameseSupported = (result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED)
                isInitialized = true

                // Tối ưu thông số phát âm mặc định cho tiếng Việt:
                // Đặt tốc độ setSpeechRate(0.9f) giúp nhịp đọc chậm rãi, rõ dấu; Pitch 1.0f tự nhiên truyền cảm
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(1.0f)
                Log.d(TAG, "TTS initialized. Vietnamese supported: $isVietnameseSupported, speechRate: 0.9f")
            } else {
                Log.e(TAG, "TTS initialization failed with code $status")
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onDuckingChange?.invoke(true)
            }

            override fun onDone(utteranceId: String?) {
                onDuckingChange?.invoke(false)
            }

            override fun onError(utteranceId: String?) {
                onDuckingChange?.invoke(false)
            }
        })
    }

    /**
     * Tính toán speechRateDynamic dựa trên nhịp điệu tiếng Việt tự nhiên (baseRate = 0.9f)
     * Đảm bảo câu đọc chậm rãi, đủ thời gian phát âm tròn vành rõ chữ và nhấn nhá ngữ điệu.
     */
    fun calculateDynamicSpeechRate(
        vietnameseText: String,
        segmentDurationMs: Long,
        baseRate: Float = 0.9f,
        factor: Float = 0.30f
    ): Float {
        if (segmentDurationMs <= 0L) return baseRate
        val durationSec = (segmentDurationMs.toFloat() / 1000f).coerceAtLeast(0.5f)
        val sanitized = TranslationService.sanitizeForTts(vietnameseText)
        val words = sanitized.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val vietnameseWordsCount = words.size.coerceAtLeast(1)

        // Tính toán tốc độ đọc động: Cân đối giữa thời lượng câu và số âm tiết tiếng Việt
        val speechRateDynamic = (vietnameseWordsCount.toFloat() / durationSec) * factor
        // Giới hạn trong khoảng thong thả [0.80f..1.30f], giúp giữ nhịp đều, rõ dấu tiếng Việt
        return (speechRateDynamic * baseRate).coerceIn(0.80f, 1.30f)
    }

    /**
     * Tinh chỉnh Voice Tone & Pitch / SpeechRate cho từng giọng đọc tiếng Việt đa dạng:
     * - Quang Anh: Trẻ trung, tươi sáng, truyền cảm
     * - Gia Khiêm: Trầm ấm, điện ảnh, thuyết minh phim
     * - Dung Lồng Tiếng: Lồng tiếng diễn cảm, biến hóa drama & hoạt hình
     * - Adam: Hiện đại, công nghệ, dứt khoát
     * - Khánh Vy: Hoạt bát, năng động, review du lịch & ẩm thực, MC
     * - Bảo Anh: Ngọt ngào, dịu dàng, tâm sự, podcast lãng mạn
     * - Chi Mai: Thanh lịch, nhẹ nhàng, trong trẻo, đọc sách
     * - Kim Oanh: Trưởng thành, ấm áp, sâu lắng, tài liệu & lịch sử
     * - Nam Minh: Rõ ràng, đĩnh đạc, quảng cáo & hành động
     * - Hoài Mỹ: Chuẩn mực, mượt mà, kể chuyện & review
     * - Mai Phương: Miền Nam nhẹ nhàng, duyên dáng, mộc mạc
     */
    fun applyVoiceConfig(config: DubbingConfig, dynamicRate: Float? = null) {
        val engine = tts ?: return
        val voiceId = config.voiceId.lowercase()
        val voiceName = config.voiceName.lowercase()

        val isMale = config.isMale ||
                voiceId.contains("nam") || voiceName.contains("nam") ||
                voiceId.contains("quang") || voiceName.contains("quang") ||
                voiceId.contains("khiem") || voiceName.contains("khiêm") ||
                voiceId.contains("adam") || voiceName.contains("adam")

        try {
            // Lựa chọn Voice tiếng Việt phù hợp từ hệ thống Android TTS
            val availableVoices = engine.voices
            if (!availableVoices.isNullOrEmpty()) {
                val targetVoice = if (isMale) {
                    availableVoices.firstOrNull { v ->
                        v.locale.language == "vi" && (v.name.contains("male", ignoreCase = true) || v.name.contains("gft", ignoreCase = true))
                    } ?: availableVoices.firstOrNull { it.locale.language == "vi" && !it.name.contains("female", ignoreCase = true) }
                } else {
                    availableVoices.firstOrNull { v ->
                        v.locale.language == "vi" && (v.name.contains("female", ignoreCase = true) || v.name.contains("sfb", ignoreCase = true))
                    } ?: availableVoices.firstOrNull { it.locale.language == "vi" }
                }
                if (targetVoice != null) {
                    engine.voice = targetVoice
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Custom voice assignment: ${e.message}")
        }

        // Tinh chỉnh âm sắc (Pitch) & nhịp điệu (Rate) đặc trưng rõ rệt cho từng giọng đọc
        val basePitchModifier: Float
        val baseRateModifier: Float

        when {
            // 1. Gia Khiêm: Nam trầm ấm, điện ảnh, thuyết minh phim (trầm nhất)
            voiceName.contains("khiêm") || voiceId.contains("khiem") -> {
                basePitchModifier = 0.63f
                baseRateModifier = 0.86f
            }
            // 2. Nam Minh: Nam đĩnh đạc, rõ ràng, thời sự, quảng cáo
            voiceName.contains("nam minh") || voiceId.contains("namminh") -> {
                basePitchModifier = 0.70f
                baseRateModifier = 0.90f
            }
            // 3. Adam: Nam hiện đại, công nghệ, dứt khoát
            voiceName.contains("adam") || voiceId.contains("adam") -> {
                basePitchModifier = 0.75f
                baseRateModifier = 0.94f
            }
            // 4. Quang Anh: Nam trẻ trung, tươi sáng, truyền cảm
            voiceName.contains("quang anh") || voiceId.contains("quanganh") -> {
                basePitchModifier = 0.80f
                baseRateModifier = 0.98f
            }

            // 5. Kim Oanh: Nữ trưởng thành, ấm áp, sâu lắng, tài liệu & lịch sử
            voiceName.contains("oanh") || voiceId.contains("oanh") -> {
                basePitchModifier = 0.92f
                baseRateModifier = 0.86f
            }
            // 6. Bảo Anh: Nữ ngọt ngào, dịu dàng, tâm sự, podcast lãng mạn
            voiceName.contains("bảo anh") || voiceId.contains("baoanh") -> {
                basePitchModifier = 0.98f
                baseRateModifier = 0.88f
            }
            // 7. Hoài Mỹ: Nữ truyền cảm, mượt mà chuẩn mực
            voiceName.contains("hoài mỹ") || voiceId.contains("hoaimy") -> {
                basePitchModifier = 1.05f
                baseRateModifier = 0.92f
            }
            // 8. Mai Phương: Nữ miền Nam nhẹ nhàng, duyên dáng, mộc mạc
            voiceName.contains("phương") || voiceId.contains("phuong") -> {
                basePitchModifier = 1.10f
                baseRateModifier = 0.94f
            }
            // 9. Chi Mai: Nữ thanh lịch, nhẹ nhàng, trong trẻo, đọc sách
            voiceName.contains("chi mai") || voiceId.contains("chimai") -> {
                basePitchModifier = 1.15f
                baseRateModifier = 0.92f
            }
            // 10. Dung Lồng Tiếng: Nữ biến hóa, biểu cảm phim ảnh & hoạt hình
            voiceName.contains("dung") || voiceId.contains("dung") -> {
                basePitchModifier = 1.23f
                baseRateModifier = 0.97f
            }
            // 11. Khánh Vy: Nữ năng động, hoạt bát, review du lịch & ẩm thực, MC
            voiceName.contains("khánh vy") || voiceId.contains("khanhvy") -> {
                basePitchModifier = 1.32f
                baseRateModifier = 1.04f
            }
            else -> {
                basePitchModifier = if (isMale) 0.70f else 1.05f
                baseRateModifier = 0.92f
            }
        }

        // Tốc độ phát âm: Kết hợp giữa config.speechRate (chuẩn 0.9f) và baseRateModifier của từng giọng
        val userRate = dynamicRate ?: config.speechRate
        val effectiveRate = (userRate * baseRateModifier).coerceIn(0.65f, 1.45f)

        // Cao độ phát âm: Kết hợp giữa config.pitch (chuẩn 1.0f) và basePitchModifier của từng giọng
        // Dải pitch rộng [0.55f..1.50f] đảm bảo phân biệt rõ rệt giữa nam trầm (0.63f) và nữ cao/MC (1.32f)
        val effectivePitch = (basePitchModifier * config.pitch).coerceIn(0.55f, 1.50f)

        engine.setPitch(effectivePitch)
        engine.setSpeechRate(effectiveRate)
    }

    /**
     * Preview single sentence aloud
     */
    fun speakText(text: String, config: DubbingConfig, durationMs: Long = 0L) {
        val sanitized = TranslationService.sanitizeForTts(text)
        if (sanitized.isBlank()) return

        val dynamicRate = if (durationMs > 0L) calculateDynamicSpeechRate(text, durationMs, config.speechRate) else null
        applyVoiceConfig(config, dynamicRate)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, config.dubVoiceVolume)
        }
        tts?.speak(sanitized, TextToSpeech.QUEUE_FLUSH, params, "preview_${UUID.randomUUID()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        onDuckingChange?.invoke(false)
    }

    /**
     * Synthesizes audio to an actual file on disk for a subtitle segment
     * Performs sanitization, dynamic speech rate calibration, retry up to 3 times, and returns file path or null
     */
    suspend fun synthesizeSegmentToFile(
        text: String,
        config: DubbingConfig,
        segmentId: Long,
        durationMs: Long = 0L
    ): File? = withContext(Dispatchers.IO) {
        val sanitized = TranslationService.sanitizeForTts(text)
        if (sanitized.isBlank()) return@withContext null

        val cacheDir = File(context.cacheDir, "dubbing_audio")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val outputFile = File(cacheDir, "dub_seg_${segmentId}_${config.voiceId}.wav")
        if (outputFile.exists() && outputFile.length() > 0) {
            // Cached file already exists and valid
            return@withContext outputFile
        }

        val dynamicRate = if (durationMs > 0L) calculateDynamicSpeechRate(text, durationMs, config.speechRate) else null

        // Retry mechanism up to 3 times
        for (attempt in 1..3) {
            val success = synthesizeToFileInternal(sanitized, config, outputFile, dynamicRate)
            if (success && outputFile.exists() && outputFile.length() > 0) {
                return@withContext outputFile
            }
            delay(300L * attempt)
        }
        null
    }

    private suspend fun synthesizeToFileInternal(
        text: String,
        config: DubbingConfig,
        outputFile: File,
        dynamicRate: Float? = null
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val engine = tts
        if (engine == null || !isInitialized) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        applyVoiceConfig(config, dynamicRate)
        val utteranceId = "synth_${System.currentTimeMillis()}"

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}

            override fun onDone(id: String?) {
                if (id == utteranceId && continuation.isActive) {
                    continuation.resume(true)
                }
            }

            override fun onError(id: String?) {
                if (id == utteranceId && continuation.isActive) {
                    continuation.resume(false)
                }
            }
        })

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, config.dubVoiceVolume)
        }

        val result = engine.synthesizeToFile(text, params, outputFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            if (continuation.isActive) continuation.resume(false)
        }
    }

    /**
     * Garbage collection & cache cleanup: deletes all temp dubbing files
     */
    fun clearTempAudioFiles() {
        try {
            val cacheDir = File(context.cacheDir, "dubbing_audio")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { it.delete() }
            }
            Log.d(TAG, "Temp audio files cleared.")
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing temp audio files: ${e.message}")
        }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
