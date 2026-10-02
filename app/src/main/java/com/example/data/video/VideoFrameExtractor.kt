package com.example.data.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoFrameExtractor {
    private const val TAG = "VideoFrameExtractor"
    private val memoryCache = object : LruCache<String, Bitmap>(30) {}

    /**
     * Extracts a frame from a local video file at the specified timestamp in milliseconds.
     * Uses LRU cache to avoid re-extracting frames during seeking or recomposition.
     */
    suspend fun extractFrame(videoPath: String, timeMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(videoPath)
        if (!file.exists() || file.length() == 0L) return@withContext null

        val roundedTime = (timeMs / 400L) * 400L
        val cacheKey = "${file.absolutePath}_$roundedTime"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val timeUs = (timeMs * 1000L).coerceAtLeast(0L)
            val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
                ?: retriever.frameAtTime

            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract frame at $timeMs ms from $videoPath: ${e.message}")
            null
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {}
        }
    }
}
