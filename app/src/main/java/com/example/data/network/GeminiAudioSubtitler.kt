package com.example.data.network

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.SubtitleSegment
import com.example.data.video.AudioExtractorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * GeminiAudioSubtitler - Hệ thống bóc tách âm thanh và tạo phụ đề tự động (STT + Dịch thuật)
 * Sử dụng Gemini 2.5 Flash / 1.5 Flash Multimodal qua Google AI Studio REST API.
 * Tích hợp tự động xoay vòng API Key (Round-Robin) và Fallback khi gặp Rate Limit (429).
 */
object GeminiAudioSubtitler {
    private const val TAG = "GeminiAudioSubtitler"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Bóc tách âm thanh từ tệp Video MP4, sau đó gửi tới Gemini để nhận diện lời thoại và tạo phụ đề song ngữ
     */
    suspend fun extractAudioAndGenerateSubtitles(
        context: Context,
        videoFile: File,
        userApiKey: String? = null,
        videoTitle: String = "",
        projectId: String = ""
    ): Result<List<SubtitleSegment>> = withContext(Dispatchers.IO) {
        if (!videoFile.exists() || videoFile.length() < 1000) {
            return@withContext Result.failure(IllegalArgumentException("Tệp video không tồn tại hoặc rỗng."))
        }

        Log.d(TAG, "Đang bóc tách audio từ video: ${videoFile.name}...")
        val audioResult = AudioExtractorHelper.extractAudioFromVideo(context, videoFile)
        if (audioResult == null || !audioResult.audioFile.exists()) {
            return@withContext Result.failure(IllegalStateException("Không thể trích xuất luồng âm thanh từ video."))
        }

        try {
            generateSubtitlesFromAudioFile(
                audioFile = audioResult.audioFile,
                userApiKey = userApiKey,
                videoTitle = videoTitle,
                projectId = projectId
            )
        } finally {
            // Tạm giữ hoặc dọn tệp cache nếu cần
        }
    }

    /**
     * Gửi tệp âm thanh (M4A/WAV/MP3) lên Gemini REST API để bóc tách lời thoại và mốc thời gian
     */
    suspend fun generateSubtitlesFromAudioFile(
        audioFile: File,
        userApiKey: String? = null,
        videoTitle: String = "",
        projectId: String = ""
    ): Result<List<SubtitleSegment>> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() < 1000) {
            return@withContext Result.failure(IllegalArgumentException("Tệp âm thanh không hợp lệ."))
        }

        val keys = getEffectiveKeys(userApiKey)
        if (keys.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Chưa cấu hình Gemini API Key hợp lệ."))
        }

        val audioBytes = try {
            audioFile.readBytes()
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Không thể đọc dữ liệu âm thanh: ${e.message}", e))
        }

        if (audioBytes.size > 20 * 1024 * 1024) {
            return@withContext Result.failure(IllegalArgumentException("Kích thước tệp âm thanh quá lớn (>20MB) cho inline payload."))
        }

        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        val mimeType = when {
            audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav"
            audioFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mp3"
            else -> "audio/mp4"
        }

        val prompt = """
            Bạn là chuyên gia bóc tách lời thoại video Douyin/TikTok và biên dịch sang Tiếng Việt cho ứng dụng XT Thoáng AI.
            Hãy nghe kỹ tệp âm thanh này và trích xuất TOÀN BỘ lời thoại tiếng Trung thành các phân đoạn phụ đề có mốc thời gian bắt đầu và kết thúc (tính bằng mili-giây ms), đồng thời dịch sang Tiếng Việt tự nhiên, chuẩn văn phong lồng tiếng.
            Tiêu đề video tham khảo: ${if (videoTitle.isNotBlank()) videoTitle else "Video ngắn Douyin/TikTok"}

            YÊU CẦU ĐỊNH DẠNG BẮT BUỘC:
            - Chỉ trả về DUY NHẤT một JSON Array hợp lệ, tuyệt đối không có văn bản giải thích nào khác ngoài JSON.
            - Mỗi phần tử trong mảng phải có các trường:
              "id": số thứ tự nguyên tăng dần (1, 2, 3...)
              "start": thời điểm bắt đầu (ms)
              "end": thời điểm kết thúc (ms)
              "zh": câu thoại gốc tiếng Trung
              "vi": bản dịch Tiếng Việt tự nhiên, lưu loát, không lặp từ
            
            Ví dụ:
            [{"id": 1, "start": 300, "end": 2200, "zh": "哇，太好吃了！", "vi": "Oa, ngon xuất sắc luôn!"}]
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", mimeType)
                                put("data", base64Audio)
                            })
                        })
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("topP", 0.8)
                put("maxOutputTokens", 8192)
            })
        }

        val requestPayload = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        // Thử lần lượt qua danh sách keys (Round-robin + Fallback khi gặp lỗi 429 hoặc xác thực)
        var lastError: Exception? = null
        for ((idx, key) in keys.withIndex()) {
            val models = listOf("gemini-2.5-flash", "gemini-1.5-flash")
            for (model in models) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
                val request = Request.Builder().url(url).post(requestPayload).build()

                try {
                    Log.d(TAG, "Đang gửi âm thanh tới Gemini ($model) với key #${idx + 1}/${keys.size}...")
                    val response = httpClient.newCall(request).execute()
                    val responseCode = response.code
                    val responseBody = response.body?.string().orEmpty()

                    if (responseCode == 200) {
                        val parsedSegments = parseGeminiSubtitleResponse(responseBody, projectId)
                        if (parsedSegments.isNotEmpty()) {
                            Log.d(TAG, "Thành công trích xuất ${parsedSegments.size} câu phụ đề từ Gemini ($model)!")
                            return@withContext Result.success(parsedSegments)
                        } else {
                            lastError = Exception("Gemini phản hồi nhưng không tìm thấy dữ liệu phụ đề JSON hợp lệ.")
                        }
                    } else if (responseCode == 429) {
                        Log.w(TAG, "Key #${idx + 1} vượt hạn ngạch 429. Chuyển sang key kế tiếp...")
                        lastError = Exception("Lỗi 429 (Too Many Requests / Quota Exceeded)")
                        break // Thoát thử model, sang key tiếp theo
                    } else if (responseCode == 404) {
                        Log.w(TAG, "Model $model nhận 404, thử model tiếp theo...")
                        continue
                    } else {
                        Log.w(TAG, "Gemini $model lỗi HTTP $responseCode: $responseBody")
                        lastError = Exception("Lỗi HTTP $responseCode: $responseBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi kết nối khi gọi Gemini: ${e.message}", e)
                    lastError = e
                }
            }
        }

        Result.failure(lastError ?: Exception("Không thể trích xuất phụ đề từ âm thanh."))
    }

    /**
     * Xuất danh sách phụ đề ra định dạng tệp chuẩn SRT
     */
    fun formatSegmentsToSrt(segments: List<SubtitleSegment>): String {
        val sb = StringBuilder()
        segments.sortedBy { it.startTimeMs }.forEachIndexed { index, seg ->
            sb.append("${index + 1}\n")
            sb.append("${formatTimestamp(seg.startTimeMs)} --> ${formatTimestamp(seg.endTimeMs)}\n")
            sb.append("${seg.vietnameseText.ifBlank { seg.originalChinese }}\n\n")
        }
        return sb.toString().trim()
    }

    private fun formatTimestamp(ms: Long): String {
        val totalSec = ms / 1000
        val millis = ms % 1000
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return String.format("%02d:%02d:%02d,%03d", hours, mins, secs, millis)
    }

    private fun parseGeminiSubtitleResponse(responseBody: String, projectId: String): List<SubtitleSegment> {
        return try {
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates") ?: return emptyList()
            val first = candidates.optJSONObject(0) ?: return emptyList()
            val content = first.optJSONObject("content") ?: return emptyList()
            val parts = content.optJSONArray("parts") ?: return emptyList()
            val textPart = parts.optJSONObject(0)?.optString("text").orEmpty().trim()

            // Làm sạch markdown JSON wrapper
            val cleanJson = textPart
                .replace(Regex("^```json\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^```\\s*"), "")
                .replace(Regex("\\s*```$"), "")
                .trim()

            val startIdx = cleanJson.indexOf('[')
            val endIdx = cleanJson.lastIndexOf(']')
            if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) return emptyList()

            val array = JSONArray(cleanJson.substring(startIdx, endIdx + 1))
            val result = mutableListOf<SubtitleSegment>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val start = obj.optLong("start", 0L)
                val end = obj.optLong("end", start + 1500L)
                val zh = obj.optString("zh", "").trim()
                val vi = obj.optString("vi", "").trim()

                if (zh.isNotBlank() || vi.isNotBlank()) {
                    val sanitizedVi = TranslationService.sanitizeForTts(vi.ifBlank { zh })
                    result.add(
                        SubtitleSegment(
                            projectId = projectId,
                            indexNumber = i + 1,
                            startTimeMs = start,
                            endTimeMs = if (end > start) end else start + 1200L,
                            originalChinese = zh.ifBlank { "..." },
                            vietnameseText = sanitizedVi,
                            isApproved = true,
                            isEdited = false
                        )
                    )
                }
            }
            result.sortedBy { it.startTimeMs }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi phân tích JSON phụ đề: ${e.message}", e)
            emptyList()
        }
    }

    private fun getEffectiveKeys(userApiKey: String?): List<String> {
        val keys = mutableListOf<String>()
        val parsed = ApiKeyManager.parseApiKeys(userApiKey)
        keys.addAll(parsed)
        val buildKey = ApiKeyManager.sanitizeApiKey(BuildConfig.GEMINI_API_KEY)
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY" && !keys.contains(buildKey)) {
            keys.add(buildKey)
        }
        return keys
    }
}
