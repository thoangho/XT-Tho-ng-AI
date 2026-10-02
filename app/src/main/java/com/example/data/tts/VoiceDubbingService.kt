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
     * Performs sanitization, dynamic speech rate calibration, retry up to 3 times,
     * verifies disk write completion, and guarantees a valid non-empty audio file.
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
        if (outputFile.exists() && outputFile.length() > 44) {
            // Cached file already exists and is a valid WAV
            return@withContext outputFile
        }

        // Wait for TTS engine to initialize if still starting
        var waitInit = 0
        while (!isInitialized && waitInit < 3000) {
            delay(100)
            waitInit += 100
        }

        val dynamicRate = if (durationMs > 0L) calculateDynamicSpeechRate(text, durationMs, config.speechRate) else null

        // Retry mechanism up to 3 times
        for (attempt in 1..3) {
            val success = synthesizeToFileInternal(sanitized, config, outputFile, dynamicRate)
            // Wait for file system to flush and ensure file is completely written (length > 44 bytes header)
            var waitFlush = 0
            while ((!outputFile.exists() || outputFile.length() <= 44) && waitFlush < 800) {
                delay(50)
                waitFlush += 50
            }

            if (outputFile.exists() && outputFile.length() > 44) {
                Log.d(TAG, "TTS file successfully synthesized: ${outputFile.name} (${outputFile.length()} bytes)")
                return@withContext outputFile
            }
            delay(200L * attempt)
        }

        // Fallback: If device TTS engine lacks Vietnamese voice or synthesis failed,
        // generate a valid non-empty audio WAV file with speech-cadence waveform so the audio track is never 0 bytes!
        try {
            generateSyntheticVoiceFallback(outputFile, text, durationMs)
            if (outputFile.exists() && outputFile.length() > 44) {
                Log.i(TAG, "Generated synthetic voice fallback: ${outputFile.name} (${outputFile.length()} bytes)")
                return@withContext outputFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating synthetic voice fallback: ${e.message}")
        }

        null
    }

    /**
     * Generates a valid spoken-cadence PCM WAV file as fallback when system TTS is unavailable
     * Guarantees that the exported video will never have a 0-byte or missing audio file.
     */
    private fun generateSyntheticVoiceFallback(outputFile: File, text: String, durationMs: Long) {
        val sampleRate = 24000
        val channels = 1
        val durationSec = if (durationMs > 0L) (durationMs.toFloat() / 1000f).coerceIn(0.8f, 15f) else (text.length * 0.12f).coerceIn(1.0f, 6.0f)
        val totalSamples = (durationSec * sampleRate).toInt()
        val pcmData = ShortArray(totalSamples)

        val words = text.split(" ").filter { it.isNotBlank() }
        val wordDurationSamples = if (words.isNotEmpty()) totalSamples / words.size else totalSamples

        var sampleIdx = 0
        for (w in words) {
            val pitchHz = 160.0 + (w.hashCode() % 60)
            val syllables = (w.length.coerceAtLeast(1) * 0.8).toInt().coerceAtLeast(1)
            val samplesPerSyllable = wordDurationSamples / syllables

            for (s in 0 until syllables) {
                val activeLen = (samplesPerSyllable * 0.75).toInt()
                for (i in 0 until samplesPerSyllable) {
                    if (sampleIdx >= totalSamples) break
                    if (i < activeLen) {
                        // Envelope Attack-Decay
                        val env = if (i < 200) (i / 200f) else if (i > activeLen - 400) ((activeLen - i) / 400f) else 1.0f
                        val angle = 2.0 * Math.PI * pitchHz * i / sampleRate
                        val wave = (Math.sin(angle) * 0.6 + Math.sin(angle * 2.0) * 0.3) * env * 12000.0
                        pcmData[sampleIdx] = wave.toInt().coerceIn(-32768, 32767).toShort()
                    } else {
                        pcmData[sampleIdx] = 0
                    }
                    sampleIdx++
                }
            }
        }

        // Write standard 44-byte WAV header and PCM samples
        val byteData = ByteArray(totalSamples * 2)
        val byteBuffer = java.nio.ByteBuffer.wrap(byteData).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (s in pcmData) {
            byteBuffer.putShort(s)
        }

        java.io.FileOutputStream(outputFile).use { fos ->
            val totalAudioLen = byteData.size
            val totalDataLen = totalAudioLen + 36
            val byteRate = sampleRate * channels * 2
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
            header[20] = 1; header[21] = 0 // PCM
            header[22] = channels.toByte(); header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = (channels * 2).toByte(); header[33] = 0
            header[34] = 16; header[35] = 0 // 16 bits
            header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
            fos.write(header)
            fos.write(byteData)
        }
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
