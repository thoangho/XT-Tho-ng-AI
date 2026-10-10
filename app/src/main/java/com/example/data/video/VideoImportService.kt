package com.example.data.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.AspectRatio
import com.example.data.model.MaskConfig
import com.example.data.model.SubtitleSegment
import com.example.data.model.VideoProject
import com.example.data.network.TranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object VideoImportService {
    private const val TAG = "VideoImportService"

    private const val USER_AGENT_MOBILE = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"
    private const val USER_AGENT_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val noRedirectClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    data class ImportedVideoResult(
        val project: VideoProject,
        val segments: List<SubtitleSegment>
    )

    data class ResolvedMediaInfo(
        val directVideoUrl: String,
        val title: String? = null
    )

    /**
     * Bóc tách URL hợp lệ từ chuỗi văn bản người dùng nhập vào
     * (Hỗ trợ người dùng sao chép toàn bộ tin nhắn chia sẻ từ Douyin/TikTok)
     */
    fun extractUrlFromText(input: String): String? {
        val trimmed = input.trim()
        val matcher = Pattern.compile("https?://[a-zA-Z0-9./_?&=%#\\-]+").matcher(trimmed)
        return if (matcher.find()) {
            matcher.group(0)
        } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            null
        }
    }

    /**
     * Trích xuất item_id của video Douyin/TikTok bằng Regex
     */
    fun extractItemId(url: String): String? {
        // Pattern 1: /video/7345678901234567890
        val pVideo = Pattern.compile("/(?:video|note)/(\\d{15,22})")
        val mVideo = pVideo.matcher(url)
        if (mVideo.find()) return mVideo.group(1)

        // Pattern 2: modal_id=7345678901234567890
        val pModal = Pattern.compile("(?:modal_id|item_id|itemId)=(\\d{15,22})")
        val mModal = pModal.matcher(url)
        if (mModal.find()) return mModal.group(1)

        // Pattern 3: Dãy số ID 18-20 chữ số bất kỳ trong URL
        val pId = Pattern.compile("\\b(\\d{18,20})\\b")
        val mId = pId.matcher(url)
        if (mId.find()) return mId.group(1)

        return null
    }

    /**
     * Phân tích và trích xuất đường dẫn video gốc trực tiếp (no-watermark MP4)
     * từ link chia sẻ Douyin, TikTok hoặc URL MP4 thông thường.
     */
    suspend fun resolveDirectStreamUrl(
        rawInput: String,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ResolvedMediaInfo = withContext(Dispatchers.IO) {
        val cleanUrl = extractUrlFromText(rawInput)
            ?: throw IllegalArgumentException("Không tìm thấy đường link URL hợp lệ trong nội dung đã nhập!")

        onProgress(0.05f, "Đang phân tích định dạng liên kết...")

        // Trường hợp 1: URL trực tiếp file MP4 / M3U8
        if (cleanUrl.endsWith(".mp4", ignoreCase = true) ||
            cleanUrl.contains(".mp4?", ignoreCase = true) ||
            cleanUrl.endsWith(".mov", ignoreCase = true)
        ) {
            return@withContext ResolvedMediaInfo(directVideoUrl = cleanUrl)
        }

        val isDouyin = cleanUrl.contains("douyin.com", ignoreCase = true) || cleanUrl.contains("iesdouyin.com", ignoreCase = true)
        val isTikTok = cleanUrl.contains("tiktok.com", ignoreCase = true)

        if (isDouyin || isTikTok) {
            onProgress(0.10f, "Đang mở rộng link chia sẻ ${if (isDouyin) "Douyin" else "TikTok"}...")

            // Lấy URL chuyển hướng (Canonical Redirect URL)
            var canonicalUrl = cleanUrl
            try {
                val headReq = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", USER_AGENT_MOBILE)
                    .build()
                httpClient.newCall(headReq).execute().use { resp ->
                    canonicalUrl = resp.request.url.toString()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error following redirect: ${e.message}")
            }

            val itemId = extractItemId(canonicalUrl) ?: extractItemId(cleanUrl)
            onProgress(0.15f, "Đã trích xuất mã video ID: ${itemId ?: "Tự động"}...")

            // Phương án 1: Gọi TikWM API trung gian (Hỗ trợ bóc tách Douyin & TikTok không logo chất lượng cao)
            try {
                val encoded = URLEncoder.encode(canonicalUrl, "UTF-8")
                val tikWmUrl = "https://www.tikwm.com/api/?url=$encoded"
                val tikReq = Request.Builder()
                    .url(tikWmUrl)
                    .header("User-Agent", USER_AGENT_MOBILE)
                    .build()

                httpClient.newCall(tikReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: ""
                        val json = JSONObject(bodyStr)
                        if (json.optInt("code", -1) == 0) {
                            val data = json.optJSONObject("data")
                            val playUrl = data?.optString("play")
                            val title = data?.optString("title")
                            if (!playUrl.isNullOrBlank()) {
                                Log.d(TAG, "TikWM resolved direct MP4: $playUrl")
                                return@withContext ResolvedMediaInfo(
                                    directVideoUrl = playUrl,
                                    title = title?.take(40)
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "TikWM API lookup failed: ${e.message}")
            }

            // Phương án 2 (Đặc thù Douyin): Gọi API iesdouyin.com để lấy play_addr trực tiếp
            if (isDouyin && itemId != null) {
                try {
                    onProgress(0.18f, "Đang truy xuất luồng phát video không logo Douyin (play_addr)...")
                    val itemInfoUrl = "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=$itemId"
                    val itemReq = Request.Builder()
                        .url(itemInfoUrl)
                        .header("User-Agent", USER_AGENT_MOBILE)
                        .header("Referer", "https://www.douyin.com/")
                        .build()

                    httpClient.newCall(itemReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bodyStr = resp.body?.string() ?: ""
                            val json = JSONObject(bodyStr)
                            val itemList = json.optJSONArray("item_list")
                            if (itemList != null && itemList.length() > 0) {
                                val item = itemList.getJSONObject(0)
                                val title = item.optString("desc")
                                val videoObj = item.optJSONObject("video")
                                val playAddrObj = videoObj?.optJSONObject("play_addr")
                                val urlList = playAddrObj?.optJSONArray("url_list")
                                if (urlList != null && urlList.length() > 0) {
                                    val rawPlayUrl = urlList.getString(0)
                                    // Thay thế 'playwm' (có watermark) thành 'play' (không logo watermark)
                                    val noWatermarkUrl = rawPlayUrl.replace("playwm", "play")
                                    Log.d(TAG, "Douyin iteminfo resolved: $noWatermarkUrl")
                                    return@withContext ResolvedMediaInfo(
                                        directVideoUrl = noWatermarkUrl,
                                        title = title.take(40)
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Douyin iteminfo failed: ${e.message}")
                }
            }

            // Phương án 3: Cào mã HTML tìm thẻ video/play_addr
            try {
                val pageReq = Request.Builder()
                    .url(canonicalUrl)
                    .header("User-Agent", USER_AGENT_MOBILE)
                    .header("Referer", "https://www.douyin.com/")
                    .build()
                httpClient.newCall(pageReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string() ?: ""
                        val pPlay = Pattern.compile("play_addr.*?url_list.*?(https?://[^\"'\\s]+)")
                        val mPlay = pPlay.matcher(html)
                        if (mPlay.find()) {
                            val rawPlay = mPlay.group(1)
                            if (!rawPlay.isNullOrBlank()) {
                                val found = rawPlay.replace("\\u002F", "/").replace("playwm", "play")
                                return@withContext ResolvedMediaInfo(directVideoUrl = found)
                            }
                        }

                        val pMp4 = Pattern.compile("src=[\"'](https?://[^\"'\\s]+\\.mp4[^\"'\\s]*)[\"']")
                        val mMp4 = pMp4.matcher(html)
                        if (mMp4.find()) {
                            val direct = mMp4.group(1)
                            if (!direct.isNullOrBlank()) {
                                return@withContext ResolvedMediaInfo(directVideoUrl = direct)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Page scrape failed: ${e.message}")
            }
        }

        // Mặc định: Trả về chính URL đã trích xuất
        ResolvedMediaInfo(directVideoUrl = cleanUrl)
    }

    /**
     * Import a video file from a content URI (Device Gallery, Files, Downloads)
     */
    suspend fun importVideoFromUri(
        context: Context,
        uri: Uri,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ImportedVideoResult = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Đang mở tệp video từ thiết bị...")

        val importsDir = File(context.filesDir, "imported_videos").apply { mkdirs() }
        val targetFile = File(importsDir, "video_${System.currentTimeMillis()}.mp4")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                var totalBytes = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                }
            }
        } ?: throw IllegalStateException("Không thể đọc tệp video từ thiết bị.")

        onProgress(0.4f, "Đang phân tích thông số kỹ thuật (Resolution, Duration)...")
        extractAndBuildProject(context, targetFile, uri, onProgress)
    }

    /**
     * Download and import a video from an online URL (Douyin, TikTok, MP4 direct)
     * Đã tích hợp bộ bóc tách Regex và giải mã direct no-watermark MP4.
     */
    suspend fun importVideoFromUrl(
        context: Context,
        videoUrl: String,
        customTitle: String? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ImportedVideoResult = withContext(Dispatchers.IO) {
        onProgress(0.05f, "Bắt đầu trích xuất đường dẫn video từ link...")

        // 1. Phân tích Regex & lấy link stream MP4 trực tiếp không logo
        val resolved = resolveDirectStreamUrl(videoUrl, onProgress)
        val finalUrl = resolved.directVideoUrl
        val extractedTitle = resolved.title ?: customTitle

        onProgress(0.20f, "Đang kết nối tới máy chủ luồng video MP4...")

        val request = Request.Builder()
            .url(finalUrl)
            .header("User-Agent", USER_AGENT_MOBILE)
            .header("Referer", if (videoUrl.contains("douyin")) "https://www.douyin.com/" else "https://www.tiktok.com/")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Tải video thất bại (Mã lỗi HTTP ${response.code})")
        }

        val body = response.body ?: throw IllegalStateException("Phản hồi video rỗng")
        val contentType = body.contentType()?.toString() ?: ""

        // Kiểm tra xem phản hồi có bị kẹt ở trang HTML hay không
        if (contentType.contains("text/html", ignoreCase = true)) {
            val htmlPreview = body.string().take(500)
            Log.e(TAG, "Stuck on HTML page instead of MP4: $htmlPreview")
            throw IllegalStateException("Liên kết chưa cung cấp luồng video trực tiếp (đang trả về mã HTML). Vui lòng thử lại với link chia sẻ Douyin/TikTok khác.")
        }

        val importsDir = File(context.filesDir, "imported_videos").apply { mkdirs() }
        val targetFile = File(importsDir, "url_video_${System.currentTimeMillis()}.mp4")

        val contentLength = body.contentLength()
        onProgress(0.30f, "Đang tải video về máy...")

        body.byteStream().use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var downloaded = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (contentLength > 0) {
                        val progress = 0.30f + (downloaded.toFloat() / contentLength.toFloat()) * 0.35f
                        onProgress(progress, "Đang tải: ${(downloaded / 1024 / 1024)}MB / ${(contentLength / 1024 / 1024)}MB")
                    }
                }
            }
        }

        if (targetFile.length() < 1000) {
            throw IllegalStateException("Tệp tải về quá nhỏ (${targetFile.length()} bytes), có thể link đã hết hạn hoặc bị chặn.")
        }

        onProgress(0.70f, "Đang bóc tách thông số kỹ thuật video...")
        extractAndBuildProject(context, targetFile, null, onProgress, extractedTitle)
    }

    /**
     * Create a manual project with custom duration
     */
    fun createManualProject(
        title: String,
        durationSeconds: Int,
        isVertical: Boolean
    ): ImportedVideoResult {
        val durationMs = durationSeconds * 1000L
        val aspectRatio = if (isVertical) AspectRatio.VERTICAL_9_16 else AspectRatio.HORIZONTAL_16_9
        val projectId = "custom_${System.currentTimeMillis()}"

        val project = VideoProject(
            id = projectId,
            title = title.ifBlank { "Dự án Dịch Mới" },
            videoUri = "simulated://custom/$projectId",
            durationMs = durationMs,
            aspectRatio = aspectRatio,
            maskConfig = MaskConfig(
                yPercent = aspectRatio.defaultMaskY,
                widthPercent = if (isVertical) 92f else 88f,
                heightPercent = if (isVertical) 9f else 8f
            ),
            isSample = false
        )

        val segments = emptyList<SubtitleSegment>()
        return ImportedVideoResult(project, segments)
    }

    private fun extractAndBuildProject(
        context: Context,
        file: File,
        sourceUri: Uri?,
        onProgress: (Float, String) -> Unit,
        forcedTitle: String? = null
    ): ImportedVideoResult {
        val retriever = MediaMetadataRetriever()
        var durationMs = 15000L
        var width = 720
        var height = 1280
        var resolvedTitle = forcedTitle ?: "Video Nhập (${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())})"

        try {
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (!durStr.isNullOrBlank()) {
                durationMs = durStr.toLongOrNull()?.coerceAtLeast(1000L) ?: 15000L
            }
            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (!wStr.isNullOrBlank() && !hStr.isNullOrBlank()) {
                width = wStr.toIntOrNull() ?: 720
                height = hStr.toIntOrNull() ?: 1280
            }

            if (forcedTitle == null && sourceUri != null) {
                val cursor = context.contentResolver.query(sourceUri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val name = it.getString(nameIndex)
                            if (!name.isNullOrBlank()) {
                                resolvedTitle = name.removeSuffix(".mp4").removeSuffix(".mov").removeSuffix(".mkv")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract full video metadata: ${e.message}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        val isVertical = height >= width
        val aspectRatio = if (isVertical) AspectRatio.VERTICAL_9_16 else AspectRatio.HORIZONTAL_16_9
        val projectId = "custom_${System.currentTimeMillis()}"

        val project = VideoProject(
            id = projectId,
            title = resolvedTitle,
            videoUri = file.absolutePath,
            durationMs = durationMs,
            aspectRatio = aspectRatio,
            maskConfig = MaskConfig(
                yPercent = aspectRatio.defaultMaskY,
                widthPercent = if (isVertical) 92f else 88f,
                heightPercent = if (isVertical) 9f else 8f
            ),
            isSample = false
        )

        onProgress(0.9f, "Hoàn tất xử lý thông tin video!")
        // Đảm bảo khi người dùng tải/chọn video mới, danh sách phụ đề ban đầu luôn TRỐNG (emptyList()),
        // chỉ hiển thị dữ liệu thực tế thu được từ API Gemini.
        val segments = emptyList<SubtitleSegment>()

        onProgress(1.0f, "Hoàn tất nhập video!")
        return ImportedVideoResult(project, segments)
    }
}

