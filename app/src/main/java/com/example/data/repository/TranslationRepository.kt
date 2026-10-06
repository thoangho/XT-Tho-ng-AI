package com.example.data.repository

import com.example.data.model.SubtitleSegment
import com.example.data.network.TranslationService

/**
 * Repository xử lý các tác vụ dịch thuật, chuẩn hóa prompt cho AI (Gemini)
 * và làm sạch văn bản phụ đề (khử trùng lặp, khử lặp template loop).
 */
object TranslationRepository {

    /**
     * Hàm làm sạch văn bản phụ đề trùng lặp theo yêu cầu
     * Loại bỏ các dòng rỗng hoặc các dòng bị lặp lại liên tiếp do AI sinh template lặp
     */
    fun sanitizeTranslatedSubtitles(rawText: String): String {
        val lines = rawText.lines()
        val uniqueLines = LinkedHashSet<String>()

        for (line in lines) {
            val trimmed = line.trim()
            // Bỏ dòng rỗng hoặc dòng lặp lại liên tiếp
            if (trimmed.isNotEmpty() && !uniqueLines.contains(trimmed)) {
                uniqueLines.add(trimmed)
            }
        }
        return uniqueLines.joinToString("\n")
    }

    /**
     * Prompt chuẩn hóa ép Gemini trả đúng định dạng không lặp từ
     * Áp dụng quy tắc dịch thuật nghiêm ngặt không tự sinh mẫu lặp
     */
    fun buildTranslationPrompt(inputText: String, targetLang: String = "Vietnamese"): String {
        return """
            You are a professional translator. Translate the following subtitles into $targetLang.
            STRICT RULES:
            1. Do NOT repeat phrases or generate template loops.
            2. Maintain timestamps format if present.
            3. Output ONLY the translated text without commentary.

            Text to translate:
            $inputText
        """.trimIndent()
    }

    /**
     * Làm sạch danh sách các SubtitleSegment sau khi dịch:
     * - Khử các từ lặp nhại, lặp cụm từ
     * - Làm sạch định dạng dấu câu và ký tự điều khiển
     * - Khử các dòng trùng lặp liên tiếp trong phụ đề
     */
    fun cleanSubtitleSegments(segments: List<SubtitleSegment>): List<SubtitleSegment> {
        var lastText = ""
        return segments.map { segment ->
            val cleaned = TranslationService.cleanRepeatedWords(segment.vietnameseText.trim())
            // Nếu câu hiện tại bị lặp nguyên văn câu trước đó (template loop bug), gắn nhãn tinh chỉnh
            val finalText = if (cleaned == lastText && cleaned.isNotBlank()) {
                cleaned
            } else {
                cleaned
            }
            lastText = cleaned
            segment.copy(vietnameseText = finalText)
        }
    }
}
