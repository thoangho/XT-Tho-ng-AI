package com.example.data.subtitle

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.SubtitleSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.util.regex.Pattern

data class RawSubtitleEntry(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

data class StretchedSubtitleResult(
    val originalCount: Int,
    val videoDurationMs: Long,
    val lastOriginalEndMs: Long,
    val stretchRatio: Float,
    val segments: List<SubtitleSegment>
)

object SubtitleFileService {
    private const val TAG = "SubtitleFileService"

    /**
     * Chuyển đổi chuỗi mốc thời gian SRT/VTT sang mili-giây (milliseconds)
     * Hỗ trợ:
     * - SRT: 00:01:23,456
     * - VTT: 00:01:23.456 hoặc 01:23.456
     * Bao bọc hoàn toàn trong try-catch để ngăn chặn NumberFormatException hoặc IndexOutOfBoundsException.
     */
    fun parseTimestampToMs(timestampStr: String): Long {
        return try {
            val clean = timestampStr.trim().replace(',', '.')
            val parts = clean.split(':')
            when (parts.size) {
                3 -> { // HH:mm:ss.SSS
                    val hours = parts[0].trim().toLongOrNull() ?: 0L
                    val minutes = parts[1].trim().toLongOrNull() ?: 0L
                    val secParts = parts[2].trim().split('.')
                    val seconds = secParts[0].toLongOrNull() ?: 0L
                    val millis = if (secParts.size > 1) {
                        secParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                    } else 0L
                    (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + millis
                }
                2 -> { // mm:ss.SSS
                    val minutes = parts[0].trim().toLongOrNull() ?: 0L
                    val secParts = parts[1].trim().split('.')
                    val seconds = secParts[0].toLongOrNull() ?: 0L
                    val millis = if (secParts.size > 1) {
                        secParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                    } else 0L
                    (minutes * 60000L) + (seconds * 1000L) + millis
                }
                else -> 0L
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi khi phân tích cú pháp mốc thời gian '$timestampStr': ${e.message}")
            0L
        }
    }

    /**
     * Bóc tách toàn bộ nội dung file phụ đề .srt hoặc .vtt.
     * Xử lý triệt để:
     * - Ký tự BOM của UTF-8 (\uFEFF)
     * - Các định dạng WebVTT (header, note, style)
     * - Loại bỏ thẻ định dạng HTML/WebVTT (<b>, <i>, <font>, <c>...)
     * - Các dòng rác, cú pháp lệch chuẩn mà không gây crash ứng dụng
     */
    fun parseSubtitles(rawContent: String): List<RawSubtitleEntry> {
        val cleanContent = rawContent.removePrefix("\uFEFF").replace("\u200B", "").trim()
        if (cleanContent.isEmpty()) {
            return emptyList()
        }

        val lines = cleanContent.lines().map { it.trim() }
        val entries = mutableListOf<RawSubtitleEntry>()

        // Hỗ trợ cả SRT (00:00:00,000) và VTT (00:00.000 hoặc 00:00:00.000)
        val timePattern = Pattern.compile("(\\d{1,2}:\\d{2}(?::\\d{2})?[,\\.]\\d{1,3})\\s*-->\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?[,\\.]\\d{1,3})")
        val htmlTagPattern = Pattern.compile("<[^>]*>")

        var currentIndex = 1
        var currentStart = -1L
        var currentEnd = -1L
        val currentTextLines = mutableListOf<String>()

        fun commitEntry() {
            try {
                if (currentStart >= 0L && currentEnd > currentStart && currentTextLines.isNotEmpty()) {
                    val rawText = currentTextLines.joinToString("\n").trim()
                    val cleanedText = htmlTagPattern.matcher(rawText).replaceAll("").trim()
                    if (cleanedText.isNotBlank()) {
                        entries.add(
                            RawSubtitleEntry(
                                index = currentIndex++,
                                startTimeMs = currentStart,
                                endTimeMs = currentEnd,
                                text = cleanedText
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bỏ qua một mục phụ đề bị lỗi cú pháp: ${e.message}")
            }
            currentStart = -1L
            currentEnd = -1L
            currentTextLines.clear()
        }

        for (line in lines) {
            try {
                if (line.isEmpty()) {
                    commitEntry()
                    continue
                }
                // Bỏ qua WebVTT headers và metadata
                if (line.startsWith("WEBVTT", ignoreCase = true) ||
                    line.startsWith("NOTE", ignoreCase = true) ||
                    line.startsWith("STYLE", ignoreCase = true) ||
                    line.startsWith("REGION", ignoreCase = true)
                ) {
                    continue
                }

                val matcher = timePattern.matcher(line)
                if (matcher.find()) {
                    commitEntry()
                    currentStart = parseTimestampToMs(matcher.group(1) ?: "")
                    currentEnd = parseTimestampToMs(matcher.group(2) ?: "")
                    // Nếu thời điểm kết thúc nhỏ hơn hoặc bằng bắt đầu, điều chỉnh tối thiểu 1 giây
                    if (currentEnd <= currentStart && currentStart >= 0L) {
                        currentEnd = currentStart + 1200L
                    }
                } else if (currentStart >= 0L) {
                    // Bỏ qua dòng số thứ tự nếu xuất hiện
                    if (currentTextLines.isEmpty() && line.toIntOrNull() != null) {
                        continue
                    }
                    currentTextLines.add(line)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi phân tích dòng '$line': ${e.message}")
            }
        }
        commitEntry()

        return entries.sortedBy { it.startTimeMs }
    }

    /**
     * Thuật toán co giãn thời gian (Time Stretching Algorithm):
     * Tính Tỷ lệ = Thời lượng Video / Thời điểm kết thúc của dòng phụ đề cuối.
     * Sau đó, nhân toàn bộ các mốc Start/End time của từng dòng phụ đề với tỷ lệ này.
     * 
     * Kiểm tra phòng chống triệt để:
     * - Lỗi chia cho 0 (Divide by Zero) khi videoDurationMs <= 0 hoặc lastOriginalEndMs <= 0
     * - Kết quả NaN, Infinite, hoặc âm
     */
    fun stretchSubtitlesToFitVideo(
        rawEntries: List<RawSubtitleEntry>,
        videoDurationMs: Long,
        projectId: String
    ): StretchedSubtitleResult {
        // Kiểm tra danh sách rỗng
        if (rawEntries.isEmpty()) {
            throw IllegalArgumentException("Tệp phụ đề không có dòng nội dung hợp lệ nào.")
        }

        // Kiểm tra thời lượng video <= 0 (Divide by Zero phòng ngừa)
        if (videoDurationMs <= 0L) {
            throw IllegalArgumentException(
                "Không thể co giãn phụ đề: Thời lượng video không hợp lệ (${videoDurationMs}ms). Vui lòng chọn hoặc nạp một video có thời lượng lớn hơn 0."
            )
        }

        // Thời điểm kết thúc của dòng phụ đề cuối
        val lastOriginalEndMs = rawEntries.maxOfOrNull { it.endTimeMs } ?: 0L

        // Kiểm tra thời lượng phụ đề <= 0 (Divide by Zero phòng ngừa)
        if (lastOriginalEndMs <= 0L) {
            throw IllegalArgumentException(
                "Không thể co giãn phụ đề: Mốc thời gian kết thúc của file phụ đề không hợp lệ (${lastOriginalEndMs}ms)."
            )
        }

        // Tính Tỷ lệ = Thời lượng Video / Thời điểm kết thúc của dòng phụ đề cuối
        val ratio = videoDurationMs.toFloat() / lastOriginalEndMs.toFloat()

        // Kiểm tra tính hợp lệ của số thực sau phép chia
        if (ratio.isNaN() || ratio.isInfinite() || ratio <= 0f) {
            throw ArithmeticException(
                "Lỗi tính toán tỷ lệ co giãn thời gian: Tỷ lệ không hợp lệ ($ratio). Phép chia cho 0 hoặc tràn số."
            )
        }

        // Nhân toàn bộ các mốc Start/End time của từng dòng phụ đề với tỷ lệ này
        val stretchedSegments = rawEntries.mapIndexed { idx, entry ->
            val stretchedStart = (entry.startTimeMs * ratio).toLong().coerceIn(0L, videoDurationMs)
            val stretchedEnd = (entry.endTimeMs * ratio).toLong().coerceIn(stretchedStart + 200L, videoDurationMs)

            SubtitleSegment(
                projectId = projectId,
                indexNumber = idx + 1,
                startTimeMs = stretchedStart,
                endTimeMs = stretchedEnd,
                originalChinese = entry.text,
                vietnameseText = entry.text,
                isApproved = true,
                isEdited = false
            )
        }

        Log.d(TAG, "Time Stretching thành công: lastEnd=${lastOriginalEndMs}ms, videoDuration=${videoDurationMs}ms, ratio=$ratio")

        return StretchedSubtitleResult(
            originalCount = rawEntries.size,
            videoDurationMs = videoDurationMs,
            lastOriginalEndMs = lastOriginalEndMs,
            stretchRatio = ratio,
            segments = stretchedSegments
        )
    }

    /**
     * Lấy tên tệp tin an toàn từ ContentResolver (Scoped Storage)
     */
    fun getFileName(context: Context, uri: Uri): String {
        var fileName = "subtitle.srt"
        try {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex) ?: fileName
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Không thể đọc tên file từ Uri: ${e.message}")
        }
        return fileName
    }

    /**
     * Đọc và giải mã file phụ đề từ Uri thiết bị, thực thi thuật toán co giãn thời gian.
     * Đảm bảo:
     * 1. Phân luồng chuẩn: Đọc I/O trên Dispatchers.IO, tính toán CPU trên Dispatchers.Default.
     * 2. Quản lý quyền Scoped Storage: Bắt chặt SecurityException, FileNotFoundException.
     * 3. Bắt lỗi định dạng và chia cho 0 với thông báo thân thiện.
     */
    suspend fun loadAndStretchFromUri(
        context: Context,
        uri: Uri,
        videoDurationMs: Long,
        projectId: String
    ): StretchedSubtitleResult {
        // 1. Kiểm tra thời lượng video trước khi đọc
        if (videoDurationMs <= 0L) {
            throw IllegalArgumentException(
                "Thời lượng video hiện tại bằng 0 hoặc chưa sẵn sàng. Vui lòng chọn hoặc phát video trước khi tải phụ đề!"
            )
        }

        val fileName = getFileName(context, uri)

        // 2. Đọc nội dung file từ Scoped Storage trên luồng I/O (Dispatchers.IO)
        val rawContent: String = withContext(Dispatchers.IO) {
            val contentBuilder = StringBuilder()
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            contentBuilder.append(line).append('\n')
                            line = reader.readLine()
                        }
                    }
                } ?: throw FileNotFoundException("Không thể mở luồng dữ liệu (Input Stream) từ tệp $fileName.")
            } catch (e: SecurityException) {
                Log.e(TAG, "Lỗi cấp quyền Scoped Storage khi truy cập Uri $uri", e)
                throw SecurityException("Ứng dụng không có quyền đọc tệp tin '$fileName'. Vui lòng chọn lại tệp tin qua bộ chọn file của hệ thống.")
            } catch (e: FileNotFoundException) {
                Log.e(TAG, "Tệp không tồn tại: $uri", e)
                throw FileNotFoundException("Không tìm thấy tệp tin '$fileName' trên thiết bị hoặc tệp đã bị xóa.")
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi đọc tệp từ Uri: $uri", e)
                throw IllegalStateException("Lỗi khi đọc tệp '$fileName': ${e.localizedMessage ?: "Dữ liệu không đọc được."}")
            }
            contentBuilder.toString()
        }

        // 3. Phân tích cú pháp và co giãn thời gian trên luồng CPU (Dispatchers.Default) để không làm đơ giao diện
        return withContext(Dispatchers.Default) {
            val rawEntries = parseSubtitles(rawContent)
            if (rawEntries.isEmpty()) {
                throw IllegalArgumentException(
                    "Tệp '$fileName' không chứa dữ liệu phụ đề hợp lệ theo chuẩn .srt hoặc .vtt. Vui lòng kiểm tra lại mốc thời gian (00:00:00,000 --> 00:00:00,000)."
                )
            }

            stretchSubtitlesToFitVideo(rawEntries, videoDurationMs, projectId)
        }
    }
}
