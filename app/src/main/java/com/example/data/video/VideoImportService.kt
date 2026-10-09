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

        val segments = generateDefaultSegments(projectId, durationMs, project.title)
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

        onProgress(0.75f, "Đang bóc tách phân đoạn thoại & Tự động dịch AI...")
        val segments = generateDefaultSegments(projectId, durationMs, resolvedTitle)

        onProgress(1.0f, "Hoàn tất nhập video!")
        return ImportedVideoResult(project, segments)
    }

    private fun generateDefaultSegments(projectId: String, durationMs: Long, title: String = ""): List<SubtitleSegment> {
        val segments = mutableListOf<SubtitleSegment>()
        var currentTime = 300L
        var index = 1

        val isFood = title.contains("美食") || title.contains("吃") || title.contains("火锅") || title.contains("做菜") || title.contains("lẩu", ignoreCase = true) || title.contains("ẩm thực", ignoreCase = true)
        val isTech = title.contains("科技") || title.contains("手机") || title.contains("测评") || title.contains("数码") || title.contains("tech", ignoreCase = true) || title.contains("review", ignoreCase = true)
        val isComedy = title.contains("搞笑") || title.contains("办公") || title.contains("同事") || title.contains("职场") || title.contains("hài", ignoreCase = true)
        val isVlog = title.contains("vlog", ignoreCase = true) || title.contains("日常") || title.contains("生活") || title.contains("du lịch", ignoreCase = true)

        val samplePhrasesWithDuration = when {
            isFood -> listOf(
                Triple("哇！", "Oa, hấp dẫn quá!", 1000L),
                Triple("今天带大家来打卡这家超级火爆的地道美食小店！", "Hôm nay mình dẫn mọi người đi thử quán ăn đặc sản siêu hot này nha!", 3400L),
                Triple("快看！", "Mau nhìn này!", 1100L),
                Triple("刚端上桌这股浓郁诱人的香气就扑鼻而来。", "Vừa bưng ra bàn là mùi thơm nức mũi lan tỏa khắp nơi rồi.", 3200L),
                Triple("太绝了！", "Quá đỉnh luôn!", 1200L),
                Triple("肉质特别新鲜滑嫩，一口下去满满的汁水。", "Thịt cực kỳ tươi ngon mọng nước, cắn một miếng ngập tràn hương vị.", 3500L),
                Triple("对！", "Chuẩn luôn!", 900L),
                Triple("一定要蘸上这个独家特调秘制酱料才够味。", "Nhất định phải chấm cùng loại nước sốt gia truyền này mới đúng điệu.", 3400L),
                Triple("赶紧试试！", "Thử ngay đi nào!", 1200L),
                Triple("喜欢美食的小伙伴们记得点赞关注，下期见！", "Ai mê đồ ăn ngon nhớ bấm tim theo dõi, hẹn gặp lại cả nhà nha!", 3000L)
            )
            isTech -> listOf(
                Triple("来了！", "Hàng về rồi đây!", 1100L),
                Triple("今天带大家深度体验这款全新发布的旗舰设备。", "Hôm nay mình sẽ cùng mọi người trải nghiệm chi tiết mẫu flagship mới này.", 3500L),
                Triple("快看！", "Mau nhìn này!", 1100L),
                Triple("整机的做工质感相当扎实，手感拿在手里极其轻盈。", "Độ hoàn thiện cực kỳ đầm chắc, cảm giác cầm trên tay rất nhẹ nhàng.", 3600L),
                Triple("太牛了！", "Đỉnh chóp luôn!", 1200L),
                Triple("屏幕色彩显示细腻鲜艳，高刷流畅度拉满。", "Màn hình hiển thị màu sắc rực rỡ sắc nét, tần số quét mượt mà tuyệt đối.", 3400L),
                Triple("对！", "Chuẩn luôn!", 900L),
                Triple("核心性能在重度使用下的稳定性完全超出预期。", "Hiệu năng khi sử dụng tác vụ nặng ổn định ngoài mong đợi.", 3300L),
                Triple("值得入手！", "Rất đáng mua!", 1200L),
                Triple("如果觉得评测有用，记得点赞关注支持一下！", "Nếu thấy bài đánh giá hữu ích, đừng quên like và theo dõi nhé!", 2800L)
            )
            isComedy -> listOf(
                Triple("天呐！", "Trời đất ơi!", 1000L),
                Triple("今天在办公室遇到了一件特别离谱又好笑的事。", "Hôm nay ở công ty gặp một chuyện vừa trớ trêu vừa cười đau bụng.", 3400L),
                Triple("快看！", "Mau nhìn này!", 1100L),
                Triple("本来以为是个简单操作，结果下一秒全场看呆。", "Tưởng đâu xử lý đơn giản, ai ngờ giây tiếp theo cả phòng đứng hình.", 3500L),
                Triple("真的假的？", "Thật hay đùa vậy?", 1200L),
                Triple("看到最终结果的那一刻，大家直接笑翻了天。", "Khoảnh khắc thấy kết quả, mọi người đều không nhịn được cười.", 3300L),
                Triple("太真实了！", "Quá là chân thực!", 1200L),
                Triple("这简直就是当代打工人的真实日常写照。", "Đúng là phản ánh chân thực cuộc sống của dân văn phòng thời nay.", 3200L),
                Triple("笑不活了！", "Cười ngất luôn!", 1200L),
                Triple("喜欢搞笑日常的小伙伴别忘了关注，每天带给你快乐！", "Mê clip hài hước nhớ bấm theo dõi để cười mỗi ngày nhé!", 3000L)
            )
            isVlog -> listOf(
                Triple("哈喽！", "Xin chào cả nhà!", 1000L),
                Triple("欢迎来到今天的美好生活日常，记录惬意时光。", "Chào mừng các bạn đến với nhật ký cuộc sống hôm nay của mình.", 3400L),
                Triple("走！", "Đi thôi nào!", 900L),
                Triple("今天天气特别晴朗舒适，带大家去逛逛一个宝藏地方。", "Hôm nay trời trong xanh mát mẻ, cùng mình khám phá một góc nhỏ thú vị nhé.", 3600L),
                Triple("太美了！", "Đẹp mê ly luôn!", 1200L),
                Triple("沿途的风景随手一拍都很治愈，让人心旷神怡。", "Cảnh sắc dọc đường chụp vội góc nào cũng nên thơ, cảm giác rất thư thái.", 3500L),
                Triple("真惬意！", "Thật dễ chịu!", 1100L),
                Triple("走进这家很有氛围感的小店，点一杯热咖啡坐坐。", "Ghé vào quán nhỏ ấm cúng này, nhâm nhi tách cà phê nóng thật tuyệt.", 3400L),
                Triple("太棒了！", "Tuyệt vời quá!", 1100L),
                Triple("感谢大家的暖心陪伴，我们下期视频再见啦！", "Cảm ơn mọi người đã đồng hành, hẹn gặp lại ở video lần tới nha!", 3000L)
            )
            else -> listOf(
                Triple("大家好！", "Chào mọi người!", 1000L),
                Triple(if (title.isNotBlank()) "今天来和大家聊聊关于 $title 的精彩内容。" else "今天来和大家详细分享一个非常实用有趣的技巧。", "Hôm nay mình sẽ chia sẻ với các bạn những nội dung thú vị nhất.", 3400L),
                Triple("快看！", "Mau nhìn này!", 1100L),
                Triple("你看这个操作步骤其实非常简单，一看就会。", "Bạn xem các bước thực hiện thực ra vô cùng đơn giản, nhìn là biết ngay.", 3500L),
                Triple("太绝了！", "Quá đỉnh luôn!", 1200L),
                Triple("掌握了这个关键方法，效率直接翻倍。", "Nắm được phương pháp then chốt này, hiệu suất sẽ tăng lên gấp đôi.", 3300L),
                Triple("对！", "Chuẩn luôn!", 900L),
                Triple("如果觉得内容对你有帮助，记得点赞关注哦。", "Nếu thấy nội dung hữu ích, cả nhà nhớ bấm tim và theo dõi nhé.", 3500L),
                Triple("赶紧试试！", "Thử ngay đi nào!", 1200L),
                Triple("我们下期视频再见，拜拜！", "Hẹn gặp lại các bạn trong video tiếp theo, tạm biệt nha!", 2600L)
            )
        }

        var phraseIndex = 0
        while (currentTime + 800L < durationMs && index <= 50) {
            val (chinese, vietnamese, desiredDuration) = samplePhrasesWithDuration[phraseIndex % samplePhrasesWithDuration.size]

            val end = (currentTime + desiredDuration).coerceAtMost(durationMs - 200L)
            segments.add(
                SubtitleSegment(
                    projectId = projectId,
                    indexNumber = index,
                    startTimeMs = currentTime,
                    endTimeMs = end,
                    originalChinese = chinese,
                    vietnameseText = vietnamese
                )
            )
            currentTime = end + 250L
            index++
            phraseIndex++
        }

        if (segments.isEmpty()) {
            segments.add(
                SubtitleSegment(
                    projectId = projectId,
                    indexNumber = 1,
                    startTimeMs = 300L,
                    endTimeMs = durationMs.coerceAtLeast(3000L),
                    originalChinese = "",
                    vietnameseText = ""
                )
            )
        }
        return segments
    }
}
