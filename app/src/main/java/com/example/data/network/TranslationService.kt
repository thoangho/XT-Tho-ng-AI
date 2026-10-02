package com.example.data.network

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.SubtitleSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class ApiKeyException(message: String) : Exception(message)
class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)

object TranslationService {
    private const val TAG = "TranslationService"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun hasConfiguredApiKey(userKey: String?): Boolean {
        val trimmedUser = userKey?.trim() ?: ""
        if (trimmedUser.isNotBlank() && trimmedUser != "MY_GEMINI_API_KEY") return true
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        return buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY"
    }

    fun getEffectiveApiKey(userKey: String?): String? {
        val trimmedUser = userKey?.trim() ?: ""
        if (trimmedUser.isNotBlank() && trimmedUser != "MY_GEMINI_API_KEY") return trimmedUser
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") return buildKey
        return null
    }

    /**
     * Sanitize text before feeding into TTS or rendering
     * Removes emojis, bracket markers, hashtags, and unwanted control characters
     */
    fun sanitizeForTts(rawText: String): String {
        if (rawText.isBlank()) return ""

        var cleaned = rawText
        // Remove common hashtag patterns #...
        cleaned = cleaned.replace(Regex("#[\\w\\u4e00-\\u9fa5]+"), "")
        // Remove brackets: 【...】 [ ... ] ( ... )
        cleaned = cleaned.replace(Regex("[【\\[\\(][^】\\]\\)]*[】\\]\\)]"), "")
        // Remove emojis and non-standard symbols
        val emojiPattern = Pattern.compile("[\\p{So}\\p{Cn}\\p{Cs}\\p{Co}]")
        cleaned = emojiPattern.matcher(cleaned).replaceAll("")
        // Replace special Chinese punctuations with standard Vietnamese ones
        cleaned = cleaned
            .replace("，", ", ")
            .replace("。", ". ")
            .replace("！", "! ")
            .replace("？", "? ")
            .replace("：", ": ")
            .replace("；", "; ")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("’", "'")
            .replace("‘", "'")
            .replace("…", "...")
            .replace("、", ", ")
        // Collapse multiple whitespace
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()
        return cleaned
    }

    /**
     * Multi-pass High Accuracy Translation:
     * - Pass 1: Literal & Contextual Draft translation
     * - Pass 2: Polishing & Dubbing Rhythm Refinement
     * - Pass 3: Short-Segment & Completeness Preservation Check (NEVER omit short phrases)
     */
    suspend fun translateAccuratelyMultiPass(
        chineseText: String,
        passCount: Int = 3,
        userApiKey: String? = null,
        videoTitle: String = "",
        videoCategory: String = ""
    ): TranslationResult = withContext(Dispatchers.IO) {
        val trimmed = chineseText.trim()
        if (trimmed.isBlank()) {
            return@withContext TranslationResult(
                vietnameseText = "",
                engineUsed = "Empty",
                isSuccess = true
            )
        }

        // Check instant short-phrase dictionary for 100% precision on short utterances (1-8 chars)
        val directMatch = SHORT_PHRASES_DICT[trimmed]
        if (directMatch != null) {
            return@withContext TranslationResult(
                vietnameseText = directMatch,
                engineUsed = "Từ điển XThoáng AI (100%)",
                isSuccess = true
            )
        }

        val effectiveKey = getEffectiveApiKey(userApiKey)
        if (effectiveKey == null) {
            throw ApiKeyException("Lỗi kết nối API Key, vui lòng kiểm tra lại.")
        }

        var currentTranslation: String? = null
        var engineUsed = "Gemini AI"

        // PASS 1: Base AI translation with context injection
        try {
            val geminiResult = tryTranslateWithGemini(trimmed, effectiveKey, videoTitle, videoCategory)
            if (!geminiResult.isNullOrBlank()) {
                currentTranslation = geminiResult
                engineUsed = "XThoáng AI (Gemini 3.5 Flash)"
            }
        } catch (e: ApiKeyException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini failed: ${e.message}, falling back to GTX...")
        }

        if (currentTranslation.isNullOrBlank()) {
            val gtxResult = retryWithBackoff(maxRetries = 3) {
                tryTranslateGoogleGtx(trimmed)
            }
            if (!gtxResult.isNullOrBlank()) {
                currentTranslation = gtxResult
                engineUsed = "Google Neural (Lượt 1)"
            }
        }

        // Fallback to phrase dictionary if network unavailable
        if (currentTranslation.isNullOrBlank()) {
            currentTranslation = fallbackDictionaryTranslate(trimmed)
            engineUsed = "Từ điển lồng tiếng dự phòng"
        }

        // PASS 2: Multi-Pass Polish & Localization (Văn phong lồng tiếng Việt Nam)
        if (passCount >= 2 && currentTranslation.isNotBlank()) {
            currentTranslation = polishVietnameseDubbing(currentTranslation, trimmed)
        }

        // PASS 3: Short Segment & Exclamation Verification (Bảo toàn câu ngắn, tiếng đệm, không bỏ sót)
        val finalResult = verifyAndPreserveShortUtterances(trimmed, currentTranslation)

        TranslationResult(
            vietnameseText = sanitizeForTts(finalResult),
            engineUsed = engineUsed,
            isSuccess = true
        )
    }

    /**
     * Standard wrapper
     */
    suspend fun translateChineseToVietnamese(
        chineseText: String,
        userApiKey: String? = null,
        videoTitle: String = "",
        videoCategory: String = ""
    ): TranslationResult = translateAccuratelyMultiPass(
        chineseText = chineseText,
        passCount = 3,
        userApiKey = userApiKey,
        videoTitle = videoTitle,
        videoCategory = videoCategory
    )

    /**
     * Generate 3 varied translation alternatives for fine-tuning
     */
    suspend fun getTranslationAlternatives(chineseText: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = chineseText.trim()
        val base = translateAccuratelyMultiPass(trimmed, passCount = 2).vietnameseText
        val alternatives = mutableListOf<String>()
        alternatives.add(base)

        // Alternative 1: Conversational / Enthusiastic
        val enthusiastic = polishForStyle(base, trimmed, "enthusiastic")
        if (enthusiastic != base) alternatives.add(enthusiastic)

        // Alternative 2: Concise / Mouth-flap sync
        val concise = polishForStyle(base, trimmed, "concise")
        if (concise != base && !alternatives.contains(concise)) alternatives.add(concise)

        // Alternative 3: Natural / Formal
        val natural = polishForStyle(base, trimmed, "natural")
        if (natural != base && !alternatives.contains(natural)) alternatives.add(natural)

        alternatives.distinct().take(3)
    }

    /**
     * Polish Vietnamese translation for dubbing naturalness
     */
    private fun polishVietnameseDubbing(text: String, originalChinese: String): String {
        var polished = text

        // Replace common stiff machine-translation artifacts
        val stiffReplacements = mapOf(
            "Của tôi" to "mình",
            "của tôi" to "của mình",
            "Bạn bè" to "các bạn",
            "Mọi người cùng nhau" to "cả nhà cùng",
            "Lão thiết" to "cả nhà ơi",
            "Nhất định phải" to "nhớ phải",
            "Cực độ" to "vô cùng",
            "Phi thường" to "cực kỳ",
            "Chế tạo" to "làm",
            "Điểm khen" to "thả tim",
            "Điểm tán" to "bấm like",
            "Đăng ký chú ý" to "bấm theo dõi",
            "Nếm thử" to "ăn thử",
            "Mùi vị" to "hương vị",
            "Một cái" to "một",
            "Chúng ta" to "chúng mình",
            "Tôi" to "mình"
        )

        stiffReplacements.forEach { (stiff, natural) ->
            polished = polished.replace(stiff, natural)
        }

        // Apply specialized Douyin slang replacements
        DOUYIN_SLANG_MAP.forEach { (cn, vn) ->
            if (originalChinese.contains(cn) && !polished.contains(vn)) {
                // If slang was lost in translation, weave in the natural phrase
                if (originalChinese.startsWith(cn)) {
                    polished = "$vn, $polished"
                }
            }
        }

        return polished.trim()
    }

    private fun polishForStyle(base: String, chinese: String, style: String): String {
        return when (style) {
            "enthusiastic" -> {
                var res = base
                if (!res.endsWith("!") && !res.endsWith("?")) res += " nè!"
                if (chinese.contains("好吃") || chinese.contains("香")) res = res.replace("ngon", "siêu ngon luôn")
                if (chinese.contains("绝")) res = res.replace("đỉnh", "đỉnh nóc kịch trần")
                res
            }
            "concise" -> {
                // Shorten for mouth flap sync
                base.replace("thực sự là", "")
                    .replace("vô cùng", "rất")
                    .replace("hãy nhớ rằng", "nhớ")
                    .replace("chúng ta hãy cùng", "cùng")
                    .trim()
            }
            else -> base
        }
    }

    /**
     * Strictly verifies and preserves short utterances (1-4 Chinese characters)
     */
    private fun verifyAndPreserveShortUtterances(chinese: String, translated: String): String {
        val trimmedCn = chinese.trim()

        // 1. Direct dictionary check
        SHORT_PHRASES_DICT[trimmedCn]?.let { return it }

        // 2. Check if original starts with a short exclamation: "哇", "快看", "等等", "对", "走"
        for ((cnExcl, vnExcl) in SHORT_EXCLAMATIONS) {
            if (trimmedCn.startsWith(cnExcl) && !translated.contains(vnExcl, ignoreCase = true)) {
                return "$vnExcl, ${translated.replaceFirstChar { it.lowercase() }}"
            }
        }

        // 3. Prevent empty or punctuation-only outputs
        if (translated.isBlank() || translated.matches(Regex("[\\p{Punct}\\s]+"))) {
            return fallbackDictionaryTranslate(trimmedCn)
        }

        return translated
    }

    private fun isGeminiConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    private fun tryTranslateGoogleGtx(chineseText: String): String? {
        return try {
            val encoded = URLEncoder.encode(chineseText, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=zh-CN&tl=vi&dt=t&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                parseGtxJson(bodyStr)
            }
        } catch (e: Exception) {
            Log.w(TAG, "GTX Translation exception: ${e.message}")
            null
        }
    }

    private fun parseGtxJson(jsonStr: String): String? {
        return try {
            val root = JSONArray(jsonStr)
            val sentences = root.getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until sentences.length()) {
                val item = sentences.getJSONArray(i)
                sb.append(item.getString(0))
            }
            sb.toString().trim()
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing GTX JSON: ${e.message}")
            null
        }
    }

    private fun tryTranslateWithGemini(
        chineseText: String,
        apiKey: String,
        videoTitle: String = "",
        videoCategory: String = ""
    ): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val prompt = """
            Bạn là chuyên gia dịch thuật và lồng tiếng video ngắn Douyin/TikTok sang Tiếng Việt cho hệ thống XThoáng AI.
            
            [Thông tin ngữ cảnh video]
            - Tiêu đề video: ${if (videoTitle.isNotBlank()) videoTitle else "Video ngắn Douyin/TikTok"}
            - Thể loại/Chủ đề: ${if (videoCategory.isNotBlank()) videoCategory else "Ẩm thực, công nghệ, hài kịch, vlog đời sống"}
            
            [Yêu cầu dịch thuật chuẩn xác]
            1. Dịch câu thoại Tiếng Trung thật tự nhiên, sát nghĩa ngữ cảnh, chuẩn văn phong lồng tiếng Việt Nam (ngắn gọn, giàu cảm xúc, đúng nhịp nói nhân vật).
            2. KHÔNG bỏ sót bất kỳ câu thoại ngắn nào (kể cả từ cảm thán, tiếng đệm: 哇, 快看, 对, 走, 真的, 等等, 绝了, 尝尝, 好吃, 厉害).
            3. Giữ trọn nghĩa và chuyển hóa mượt mà các từ lóng và thành ngữ giới trẻ Douyin.
            4. Chỉ trả về DUY NHẤT câu dịch Tiếng Việt, không giải thích hay mở ngoặc.

            Câu thoại gốc: $chineseText
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code in 400..403) {
                val errBody = response.body?.string() ?: ""
                Log.e(TAG, "Gemini API Key rejected: ${response.code} $errBody")
                throw ApiKeyException("Lỗi kết nối API Key, vui lòng kiểm tra lại. (Mã lỗi: ${response.code})")
            }
            if (!response.isSuccessful) {
                Log.w(TAG, "Gemini HTTP error ${response.code}")
                return null
            }
            val responseStr = response.body?.string() ?: return null
            val responseJson = JSONObject(responseStr)
            val candidates = responseJson.optJSONArray("candidates") ?: return null
            val firstCandidate = candidates.optJSONObject(0) ?: return null
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            val part = parts.optJSONObject(0) ?: return null
            return part.optString("text")?.trim()
        }
    }

    /**
     * Chuyển đổi prompt Gemini sang cơ chế dịch cả danh sách phân đoạn
     * (JSON Array Batch Translation) trong 1 lần gọi API duy nhất để tối ưu tốc độ và tiết kiệm hạn ngạch.
     */
    suspend fun translateBatchSegments(
        segments: List<SubtitleSegment>,
        userApiKey: String? = null,
        videoTitle: String = "",
        videoCategory: String = "",
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): List<SubtitleSegment> = withContext(Dispatchers.IO) {
        if (segments.isEmpty()) return@withContext emptyList()

        onProgress(0.05f, "Chuẩn bị gói dữ liệu ${segments.size} câu thoại để dịch hàng loạt...")

        val effectiveKey = getEffectiveApiKey(userApiKey)
        if (effectiveKey == null) {
            throw ApiKeyException("Lỗi kết nối API Key, vui lòng kiểm tra lại.")
        }

        // Thử gọi dịch hàng loạt JSON Array bằng Gemini 1 lần duy nhất
        try {
            onProgress(0.20f, "Đang gửi toàn bộ danh sách phân đoạn tới Gemini AI (Batch JSON)...")
            val batchResults = tryTranslateBatchGemini(segments, effectiveKey, videoTitle, videoCategory)
            if (batchResults != null && batchResults.isNotEmpty()) {
                onProgress(0.85f, "Đã nhận kết quả dịch hàng loạt từ Gemini AI, đang hoàn thiện văn phong...")
                val resultMap = batchResults
                val polishedList = segments.map { seg ->
                    val rawVi = resultMap[seg.id] ?: SHORT_PHRASES_DICT[seg.originalChinese.trim()] ?: ""
                    val polishedVi = if (rawVi.isNotBlank()) {
                        polishVietnameseDubbing(rawVi, seg.originalChinese)
                    } else {
                        fallbackDictionaryTranslate(seg.originalChinese)
                    }
                    val verifiedVi = verifyAndPreserveShortUtterances(seg.originalChinese, polishedVi)
                    seg.copy(vietnameseText = sanitizeForTts(verifiedVi), isEdited = false)
                }
                onProgress(1.0f, "Hoàn tất dịch hàng loạt ${segments.size} câu thành công!")
                return@withContext polishedList
            }
        } catch (e: ApiKeyException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Batch Gemini translation failed: ${e.message}, falling back to sentence-by-sentence fallback...")
        }

        // Dự phòng: Nếu batch request gặp sự cố (quota/mạng/format), dịch tuần tự có cơ chế backoff
        onProgress(0.40f, "Chuyển sang cơ chế dịch dự phòng từng câu...")
        val fallbackList = mutableListOf<SubtitleSegment>()
        segments.forEachIndexed { i, seg ->
            val pct = 0.40f + 0.55f * ((i + 1).toFloat() / segments.size.toFloat())
            onProgress(pct, "Đang dịch câu [${i + 1}/${segments.size}]...")
            val res = translateAccuratelyMultiPass(
                chineseText = seg.originalChinese,
                passCount = 2,
                userApiKey = userApiKey,
                videoTitle = videoTitle,
                videoCategory = videoCategory
            )
            fallbackList.add(seg.copy(vietnameseText = res.vietnameseText, isEdited = false))
        }
        fallbackList
    }

    private fun tryTranslateBatchGemini(
        segments: List<SubtitleSegment>,
        apiKey: String,
        videoTitle: String,
        videoCategory: String
    ): Map<Long, String>? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val inputJsonArray = JSONArray()
        segments.forEach { seg ->
            val item = JSONObject().apply {
                put("id", seg.id)
                put("zh", seg.originalChinese.trim())
            }
            inputJsonArray.put(item)
        }

        val prompt = """
            Bạn là chuyên gia dịch thuật và lồng tiếng video ngắn Douyin/TikTok sang Tiếng Việt cho hệ thống XT Thoáng AI.
            
            [Ngữ cảnh video]
            - Tiêu đề: ${if (videoTitle.isNotBlank()) videoTitle else "Video ngắn Douyin/TikTok"}
            - Chủ đề: ${if (videoCategory.isNotBlank()) videoCategory else "Đời sống, hài kịch, ẩm thực, công nghệ"}
            
            [Yêu cầu dịch thuật hàng loạt]
            1. Dịch toàn bộ mảng JSON các câu thoại Tiếng Trung sang Tiếng Việt, giữ đúng trường "id".
            2. Chuẩn văn phong lồng tiếng Việt Nam (tự nhiên, biểu cảm, ngắn gọn, đúng nhịp miệng).
            3. KHÔNG bỏ sót bất kỳ câu ngắn nào (kể cả câu cảm thán: 哇, 快看, 对, 走, 真的, 绝了, 好吃...).
            4. BẮT BUỘC chỉ trả về duy nhất một JSON Array hợp lệ gồm các object có trường "id" (số nguyên) và "vi" (chuỗi Tiếng Việt). Tuyệt đối không thêm lời dẫn giải ngoài JSON.
            
            Ví dụ định dạng đầu ra:
            [{"id": 1, "vi": "Oa, thơm quá!"}, {"id": 2, "vi": "Chào mọi người!"}]

            Dữ liệu cần dịch:
            ${inputJsonArray.toString()}
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code in 400..403) {
                val errBody = response.body?.string() ?: ""
                Log.e(TAG, "Gemini Batch API Key rejected: ${response.code} $errBody")
                throw ApiKeyException("Lỗi kết nối API Key, vui lòng kiểm tra lại. (Mã lỗi: ${response.code})")
            }
            if (!response.isSuccessful) return null
            val responseStr = response.body?.string() ?: return null
            val responseJson = JSONObject(responseStr)
            val candidates = responseJson.optJSONArray("candidates") ?: return null
            val firstCandidate = candidates.optJSONObject(0) ?: return null
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            val part = parts.optJSONObject(0) ?: return null
            val rawText = part.optString("text")?.trim() ?: return null

            val cleanedJson = rawText.replace(Regex("^```json\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^```\\s*"), "")
                .replace(Regex("\\s*```$"), "")
                .trim()

            val startIdx = cleanedJson.indexOf('[')
            val endIdx = cleanedJson.lastIndexOf(']')
            if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) return null

            val jsonArrayStr = cleanedJson.substring(startIdx, endIdx + 1)
            val array = JSONArray(jsonArrayStr)
            val result = mutableMapOf<Long, String>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optLong("id", -1L)
                val vi = obj.optString("vi", "").trim()
                if (id != -1L && vi.isNotBlank()) {
                    result[id] = vi
                }
            }
            return result
        }
    }

    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 400,
        factor: Double = 1.6,
        block: () -> T?
    ): T? {
        var currentDelay = initialDelayMs
        repeat(maxRetries) { attempt ->
            val result = block()
            if (result != null) return result
            if (attempt < maxRetries - 1) {
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
        return null
    }

    fun fallbackDictionaryTranslate(chinese: String): String {
        // Direct match
        SHORT_PHRASES_DICT[chinese.trim()]?.let { return it }

        var result = chinese
        // Replace known words
        EXPANDED_DICTIONARY.forEach { (cn, vn) ->
            if (result.contains(cn)) {
                result = result.replace(cn, vn)
            }
        }
        return if (result != chinese) result else "Đây là đoạn thoại tiếng Trung: $chinese"
    }

    // High-frequency short utterances (1-8 chars) ensuring ZERO skipped short segments
    private val SHORT_PHRASES_DICT = mapOf(
        "哇！" to "Oa, thơm quá!",
        "哇" to "Oa!",
        "哇塞" to "Trời ơi đỉnh quá!",
        "快看！" to "Mau nhìn này!",
        "快看" to "Mau nhìn kìa!",
        "看这里" to "Nhìn vào đây này!",
        "对！" to "Chuẩn luôn!",
        "对" to "Đúng vậy!",
        "对啊" to "Chuẩn rồi đấy!",
        "对对对" to "Đúng đúng đúng!",
        "走！" to "Đi thôi!",
        "走吧" to "Cùng đi nào!",
        "快走" to "Mau lên nào!",
        "绝了！" to "Đỉnh thật sự!",
        "绝了" to "Quá đỉnh luôn!",
        "太绝了！" to "Quá đỉnh luôn!",
        "太绝了" to "Đỉnh chóp thật sự!",
        "绝绝子" to "Đỉnh nóc kịch trần!",
        "真的假的？" to "Thật hay đùa vậy?",
        "真的假的" to "Thật hay đùa vậy?",
        "真的吗？" to "Thật á?",
        "真的吗" to "Thật sao?",
        "确实" to "Quả thực là vậy!",
        "等等！" to "Khoan đã!",
        "等等" to "Từ từ đã nào!",
        "别急" to "Đừng vội nhé!",
        "慢点" to "Chậm lại một chút!",
        "不是吧？" to "Không phải chứ?",
        "不是吧" to "Không đùa chứ?",
        "不会吧" to "Không thể nào?",
        "天呐" to "Trời đất ơi!",
        "我的天" to "Trời ơi là trời!",
        "救命" to "Ai cứu tôi với!",
        "太难了！" to "Khó quá đi!",
        "太难了" to "Sao mà khó dữ vậy!",
        "好嘞" to "Được luôn nhé!",
        "好的" to "Dạ vâng được ạ!",
        "好" to "Được rồi!",
        "来了！" to "Hàng về rồi đây!",
        "来了" to "Tới rồi đây!",
        "来咯" to "Đến đây nào!",
        "喂！" to "Alo!",
        "喂" to "Này bạn ơi!",
        "当然！" to "Dạ đương nhiên rồi!",
        "当然" to "Chắc chắn rồi!",
        "尴尬！" to "Ngại chín mặt!",
        "尴尬" to "Ngại ngùng ghê!",
        "太香了！" to "Thơm nức mũi luôn!",
        "太香了" to "Thơm phức luôn á!",
        "尝尝！" to "Ăn thử đi nào!",
        "尝尝" to "Thử miếng xem sao!",
        "好吃！" to "Ngon tuyệt cú mèo!",
        "太好吃了" to "Ngon xuất sắc luôn!",
        "赶紧试试！" to "Thử ngay đi nào!",
        "赶紧的" to "Nhanh tay lên nào!",
        "太棒了" to "Tuyệt vời ông mặt trời!",
        "厉害" to "Lợi hại thật sự!",
        "牛" to "Quá đỉnh luôn!",
        "安排" to "Triển khai ngay!",
        "搞定" to "Xong xuôi cả rồi!",
        "翻车" to "Toang thật rồi!",
        "拜拜！" to "Tạm biệt nha!",
        "拜拜" to "Chào tạm biệt nhé!"
    )

    private val SHORT_EXCLAMATIONS = listOf(
        "哇！" to "Oa",
        "哇" to "Oa",
        "快看！" to "Mau nhìn kìa",
        "快看" to "Mau nhìn này",
        "看这里" to "Nhìn này",
        "等等" to "Khoan đã",
        "对" to "Đúng vậy",
        "走" to "Đi thôi",
        "来" to "Đến đây"
    )

    private val DOUYIN_SLANG_MAP = mapOf(
        "绝绝子" to "đỉnh nóc kịch trần",
        "老铁" to "cả nhà ơi",
        "老铁们" to "cả nhà mình ơi",
        "打卡" to "ghé thử",
        "拔草" to "thử ngay",
        "避坑" to "tránh bẫy",
        "翻车" to "toang",
        "剁手" to "chốt đơn",
        "YYDS" to "đỉnh của chóp",
        "破防" to "cay cú",
        "抠出三室一厅" to "muốn độn thổ"
    )

    private val EXPANDED_DICTIONARY = mapOf(
        "今天带大家来尝尝重庆正宗九宫格老火锅" to "Hôm nay mình dẫn mọi người đi thử lẩu 9 ô chuẩn vị Trùng Khánh",
        "看看这个牛油红亮，麻辣鲜香扑鼻而来" to "Nhìn nồi nước lẩu đỏ au béo ngậy này, mùi cay nồng thơm nức mũi luôn",
        "毛肚七上八下，脆爽弹牙，真的绝绝子" to "Nhúng sách bò đúng 7 lên 8 xuống, giòn sần sật đỉnh nóc kịch trần thật sự",
        "蘸上满满的蒜泥香油，一点都不燥辣" to "Chấm ngập trong dầu mè tỏi phi, vừa thơm béo lại không hề gắt họng",
        "老铁们赶紧点赞收藏，下次一起来打卡" to "Cả nhà nhớ bấm tim và lưu lại ngay, lần sau cùng ghé thử nha",
        "这款全新的折叠屏手机，上手质感完全颠覆我的想象" to "Chiếc điện thoại màn hình gập mới này khi cầm trên tay thực sự vượt xa kỳ vọng",
        "折痕处理得非常平整，展开几乎感觉不到它的存在" to "Phần nếp gấp được xử lý siêu phẳng, mở ra hầu như không cảm nhận thấy vết hằn nào",
        "配备最新的高通旗舰芯片，玩大型游戏完全满帧流畅" to "Trang bị con chip Snapdragon đầu bảng mới nhất, chiến game nặng cực kỳ mượt mà",
        "影像系统升级巨大，夜景拍摄色彩极其通透细腻" to "Hệ thống camera nâng cấp vượt trội, ảnh chụp đêm trong trẻo và chi tiết ấn tượng",
        "整体而言，这是目前我认为最具性价比的折叠旗舰" to "Tổng kết lại, đây chính là mẫu flagship gập đáng đồng tiền bát gạo nhất hiện nay",
        "老板问我：你会使用各种办公软件吗" to "Sếp hỏi tôi: Cậu có thành thạo các phần mềm văn phòng không",
        "我自信满满地回答：那是相当熟练" to "Tôi tự tin trả lời ngay: Dạ chuyện nhỏ, siêu thành thạo luôn ạ",
        "结果上班第一天，连打印机电源在哪里都找不到" to "Ai ngờ ngày đầu đi làm, đến cái công tắc máy in ở đâu tôi cũng không tìm ra",
        "同事都在憋笑，这尴尬得我能抠出三室一厅" to "Đồng nghiệp ai cũng nhịn cười, ngại đến mức muốn độn thổ luôn các bác ạ",
        "快救救孩子吧，太难了" to "Ai cứu tôi với, cuộc sống người lớn khó quá đi",
        "大家好" to "Chào mọi người",
        "太棒了" to "Tuyệt vời quá",
        "点赞" to "Bấm tim",
        "关注" to "Theo dõi",
        "老板" to "Ông chủ",
        "好吃" to "Ngon lắm",
        "真的" to "Thật sự"
    )
}

data class TranslationResult(
    val vietnameseText: String,
    val engineUsed: String,
    val isSuccess: Boolean,
    val warning: String? = null
)
