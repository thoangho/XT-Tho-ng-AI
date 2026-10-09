package com.example.data.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiKeyManager {
    private const val TAG = "ApiKeyManager"
    private const val PREFS_NAME = "xthoang_ai_prefs"
    private const val KEY_GEMINI_API = "user_gemini_api_key"
    private const val KEY_GEMINI_API_KEYS_LIST = "user_gemini_api_keys_list"

    // Atomic / thread-safe round-robin counter for rotating keys
    private val keyRotationIndex = java.util.concurrent.atomic.AtomicInteger(0)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Sanitizes raw API key string (trims, removes surrounding quotes or whitespace)
     */
    fun sanitizeApiKey(rawKey: String?): String {
        if (rawKey == null) return ""
        return rawKey.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()
    }

    /**
     * Validates Gemini API Key by sending a lightweight test request ('ping')
     * to Google AI Studio Generative Language API (gemini-2.5-flash).
     * Returns Result.success(true) if key is active and functional.
     */
    suspend fun validateGeminiApiKey(apiKey: String): Result<Boolean> {
        val cleanKey = sanitizeApiKey(apiKey)
        if (cleanKey.isBlank() || cleanKey == "MY_GEMINI_API_KEY") {
            return Result.failure(IllegalArgumentException("Vui lòng nhập API Key hợp lệ."))
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$cleanKey"
                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "ping")
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 16)
                        put("temperature", 0.1)
                    })
                }

                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseStr = response.body?.string().orEmpty()
                    when (response.code) {
                        200 -> {
                            val json = JSONObject(responseStr)
                            val candidates = json.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                Result.success(true)
                            } else {
                                Result.failure(Exception("API Key không phản hồi dữ liệu hợp lệ."))
                            }
                        }
                        400 -> {
                            Result.failure(Exception("API Key không đúng định dạng (Mã lỗi 400)."))
                        }
                        403 -> {
                            Result.failure(Exception("API Key bị từ chối hoặc chưa kích hoạt quyền Generative Language API (Mã lỗi 403)."))
                        }
                        429 -> {
                            Result.failure(Exception("API Key đã vượt quá hạn ngạch (Mã lỗi 429 - Quota Exceeded)."))
                        }
                        else -> {
                            Result.failure(Exception("Lỗi kết nối máy chủ Google AI Studio (Mã lỗi: ${response.code})."))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Validation failed", e)
                Result.failure(Exception("Không thể kết nối đến Google AI Studio: ${e.localizedMessage ?: "Lỗi kết nối"}"))
            }
        }
    }

    /**
     * Tách chuỗi nhập đa dòng/dấu phẩy/chấm phẩy thành danh sách các API Key hợp lệ và không trùng lặp
     */
    fun parseApiKeys(rawText: String?): List<String> {
        if (rawText.isNullOrBlank()) return emptyList()
        return rawText.split("\n", ",", ";", "\t")
            .map { sanitizeApiKey(it) }
            .filter { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
            .distinct()
    }

    /**
     * Lấy danh sách tất cả các Gemini API Key người dùng đã nạp
     */
    fun getSavedApiKeys(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_GEMINI_API_KEYS_LIST, null)
        val list = mutableListOf<String>()
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val k = sanitizeApiKey(array.optString(i))
                    if (k.isNotBlank() && k != "MY_GEMINI_API_KEY" && !list.contains(k)) {
                        list.add(k)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi phân tích cú pháp danh sách API Key: ${e.message}")
            }
        }
        // Fallback: Nếu danh sách trống nhưng có 1 key đơn lẻ đã lưu từ trước
        if (list.isEmpty()) {
            val single = prefs.getString(KEY_GEMINI_API, "") ?: ""
            val clean = sanitizeApiKey(single)
            if (clean.isNotBlank() && clean != "MY_GEMINI_API_KEY") {
                list.add(clean)
            }
        }
        return list
    }

    /**
     * Lưu danh sách nhiều API Key vào SharedPreferences
     */
    fun saveApiKeys(context: Context, keys: List<String>) {
        val cleanKeys = keys.map { sanitizeApiKey(it) }
            .filter { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
            .distinct()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        cleanKeys.forEach { jsonArray.put(it) }
        val primaryKey = cleanKeys.firstOrNull() ?: ""
        prefs.edit()
            .putString(KEY_GEMINI_API_KEYS_LIST, jsonArray.toString())
            .putString(KEY_GEMINI_API, primaryKey)
            .apply()
    }

    /**
     * Lấy API Key đầu tiên hoặc key đơn lẻ
     */
    fun getSavedApiKey(context: Context): String {
        val all = getSavedApiKeys(context)
        return all.firstOrNull() ?: ""
    }

    /**
     * Lưu 1 chuỗi chứa 1 hoặc nhiều API Key (phân cách bằng xuống dòng hoặc dấu phẩy)
     */
    fun saveApiKey(context: Context, key: String) {
        val parsed = parseApiKeys(key)
        saveApiKeys(context, parsed)
    }

    /**
     * Lấy key theo thứ tự xoay vòng Round-Robin từ danh sách
     */
    fun getNextRotatedKey(context: Context): String? {
        val allKeys = getSavedApiKeys(context)
        if (allKeys.isEmpty()) return null
        val idx = Math.floorMod(keyRotationIndex.getAndIncrement(), allKeys.size)
        return allKeys.getOrNull(idx)
    }

    /**
     * Cơ chế Round-Robin + Fallback:
     * Chạy action với key hiện tại trong danh sách. Nếu gặp lỗi Quota (429), Hết hạn hoặc Lỗi xác thực (401/403),
     * tự động fallback sang key kế tiếp trong danh sách để thử lại (retry) ngay lập tức trên background thread.
     */
    suspend fun <T> executeWithRoundRobinFallback(
        context: Context,
        candidateKeys: List<String> = emptyList(),
        action: suspend (apiKey: String) -> T?
    ): T? {
        val keys = if (candidateKeys.isNotEmpty()) {
            candidateKeys.map { sanitizeApiKey(it) }.filter { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }.distinct()
        } else {
            getSavedApiKeys(context)
        }

        if (keys.isEmpty()) {
            return null
        }

        // Bắt đầu từ vị trí round-robin hiện tại
        val startIndex = Math.floorMod(keyRotationIndex.getAndIncrement(), keys.size)
        val orderedKeys = List(keys.size) { i ->
            keys[(startIndex + i) % keys.size]
        }

        for ((attemptIndex, key) in orderedKeys.withIndex()) {
            try {
                val result = action(key)
                if (result != null) {
                    return result
                }
                Log.w(TAG, "Key #${attemptIndex + 1}/${orderedKeys.size} không trả về kết quả, thử key kế tiếp...")
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: e.message ?: ""
                Log.w(TAG, "Key #${attemptIndex + 1}/${orderedKeys.size} gặp lỗi ($msg). Tự động xoay vòng sang key tiếp theo...")
            }
        }
        return null
    }

    fun clearApiKey(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_GEMINI_API)
            .remove(KEY_GEMINI_API_KEYS_LIST)
            .apply()
    }
}
