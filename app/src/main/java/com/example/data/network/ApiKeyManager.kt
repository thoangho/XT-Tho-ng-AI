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

    fun getSavedApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GEMINI_API, "") ?: ""
    }

    fun saveApiKey(context: Context, key: String) {
        val cleanKey = sanitizeApiKey(key)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GEMINI_API, cleanKey).apply()
    }

    fun clearApiKey(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_GEMINI_API).apply()
    }
}
