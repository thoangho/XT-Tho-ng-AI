package com.aistudio.dubstudio.vmqxrt.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object GeminiTester {
    private const val TAG = "GeminiTest"
    private val client = OkHttpClient()

    /**
     * Hàm test gọi trực tiếp REST API của Gemini.
     * Trả về kết quả phân tích nguyên nhân lỗi dựa trên HTTP Code thu được.
     * Hỗ trợ tự động kiểm tra model hiện đại (gemini-2.5-flash) nếu gemini-1.5-flash bị 404.
     */
    suspend fun testGeminiApiKey(apiKey: String, modelName: String = "gemini-1.5-flash"): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext "LỖI: Key đang bị rỗng!"
        }

        val result = executeGeminiTest(cleanKey, modelName)
        
        // Nếu model gemini-1.5-flash bị 404 (do phiên bản cũ không còn khả dụng trên API v1beta),
        // thử kiểm tra nhanh thêm với gemini-2.5-flash để hỗ trợ chẩn đoán chính xác cho người dùng
        if (result.startsWith("LỖI 404") && modelName == "gemini-1.5-flash") {
            val fallbackResult = executeGeminiTest(cleanKey, "gemini-2.5-flash")
            if (fallbackResult.startsWith("SUCCESS")) {
                return@withContext "LƯU Ý: Model 'gemini-1.5-flash' trả về 404 (đã ngừng hỗ trợ trên v1beta). Tuy nhiên Key hoạt động hoàn hảo với 'gemini-2.5-flash'!"
            }
        }

        result
    }

    private fun executeGeminiTest(cleanKey: String, modelName: String): String {
        // Endpoint chuẩn của Google Gemini API v1beta
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$cleanKey"

        val jsonBody = """
            {
              "contents": [{
                "parts":[{"text": "Hello"}]
              }]
            }
        """.trimIndent()

        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            Log.d(TAG, "Đang gửi request tới URL: https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=HIDDEN")
            val response = client.newCall(request).execute()
            val statusCode = response.code
            val responseBody = response.body?.string() ?: ""

            Log.d(TAG, "HTTP Code: $statusCode")
            Log.d(TAG, "Response: $responseBody")

            when (statusCode) {
                200 -> "SUCCESS (200): Key và Endpoint hoạt động hoàn hảo!"
                404 -> "LỖI 404 (Not Found): Tên model '$modelName' hoặc cấu trúc URL bị sai. Hãy kiểm tra lại tên model."
                400 -> "LỖI 400 (Bad Request): Định dạng JSON gửi lên bị sai hoặc Key không hợp lệ."
                403 -> "LỖI 403 (Forbidden): API Key bị từ chối/chưa bật Gemini API trong Google AI Studio."
                429 -> "LỖI 429 (Too Many Requests / Quota Exceeded): API Key đã vượt quá giới hạn lượt gọi."
                else -> "LỖI HTTP $statusCode: $responseBody"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi kết nối mạng: ${e.message}", e)
            "LỖI KẾT NỐI: ${e.localizedMessage}"
        }
    }
}
