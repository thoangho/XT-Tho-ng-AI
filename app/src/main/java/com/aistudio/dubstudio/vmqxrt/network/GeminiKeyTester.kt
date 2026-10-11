package com.aistudio.dubstudio.vmqxrt.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object GeminiKeyTester {
    private const val TAG = "GeminiKeyTester"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Kiểm tra tính hợp lệ của Gemini API Key.
     * KHÔNG giới hạn tiền tố (chấp nhận cả AIzaSy..., AQ..., và mọi chuỗi key từ Google AI Studio).
     * Gửi request kiểm tra tới v1beta endpoint: models/$modelName:generateContent?key={API_KEY}.
     */
    suspend fun validateKey(
        apiKey: String,
        modelName: String = "gemini-1.5-flash"
    ): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (cleanKey.isEmpty()) {
            return@withContext "LỖI: Key đang bị rỗng!"
        }

        // Tạo JSON payload kiểm tra gọn nhẹ
        val jsonBody = """
            {
              "contents": [{
                "parts":[{"text": "Hello"}]
              }]
            }
        """.trimIndent()
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        // 1. Thử với model được chỉ định (mặc định: gemini-1.5-flash)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$cleanKey"
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            Log.d(TAG, "Đang kiểm tra API Key với URL: https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=HIDDEN")
            val response = client.newCall(request).execute()
            val statusCode = response.code
            val responseBody = response.body?.string().orEmpty()

            Log.d(TAG, "HTTP Code: $statusCode")

            when (statusCode) {
                200 -> "SUCCESS (200): Key và Endpoint hoạt động hoàn hảo!"
                404 -> {
                    // Nếu model 1.5-flash gặp 404, tự động fallback sang gemini-2.5-flash
                    if (modelName != "gemini-2.5-flash") {
                        Log.w(TAG, "Model $modelName nhận mã 404, thử kiểm tra với model gemini-2.5-flash...")
                        val fallbackResult = testWithModel(cleanKey, "gemini-2.5-flash", jsonBody)
                        if (fallbackResult.startsWith("SUCCESS")) {
                            return@withContext "SUCCESS (200): Key hợp lệ! (Lưu ý: Đã chuyển sang model gemini-2.5-flash do $modelName trả về 404)"
                        }
                    }
                    "LỖI 404 (Not Found): Tên model '$modelName' hoặc cấu trúc URL bị sai. Hãy kiểm tra lại tên model."
                }
                400 -> "LỖI 400 (Bad Request): Định dạng JSON gửi lên bị sai hoặc Key không hợp lệ."
                403 -> "LỖI 403 (Forbidden): API Key bị từ chối/chưa bật Gemini API trong Google AI Studio."
                429 -> "LỖI 429 (Too Many Requests): Key đã vượt quá hạn ngạch (Rate Limit/Quota)."
                else -> "LỖI HTTP $statusCode: $responseBody"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi kết nối mạng: ${e.message}", e)
            "LỖI KẾT NỐI: ${e.localizedMessage ?: "Không thể kết nối đến máy chủ"}"
        }
    }

    suspend fun isKeyValid(apiKey: String): Boolean {
        val message = validateKey(apiKey)
        return message.startsWith("SUCCESS")
    }

    private fun testWithModel(apiKey: String, modelName: String, jsonBody: String): String {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val req = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 200) "SUCCESS (200)" else "ERROR (${resp.code})"
            }
        } catch (e: Exception) {
            "EXCEPTION: ${e.message}"
        }
    }
}
