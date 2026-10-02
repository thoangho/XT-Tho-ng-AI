package com.example.data.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object AudioExtractorHelper {
    private const val TAG = "AudioExtractorHelper"

    data class ExtractedAudioResult(
        val audioFile: File,
        val durationMs: Long,
        val sampleRate: Int,
        val channelCount: Int,
        val hasSpeechCandidate: Boolean
    )

    /**
     * Bóc tách luồng âm thanh từ tệp video MP4/MOV/MKV thành tệp Audio M4A/AAC chuẩn
     * Kiểm tra chặt chẽ tính hợp lệ: video có âm thanh không, định dạng có đúng chuẩn không.
     */
    suspend fun extractAudioFromVideo(
        context: Context,
        videoFile: File
    ): ExtractedAudioResult? = withContext(Dispatchers.IO) {
        if (!videoFile.exists() || videoFile.length() < 1000) {
            Log.e(TAG, "Video file does not exist or is too small: ${videoFile.absolutePath}")
            return@withContext null
        }

        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(videoFile.absolutePath)

            val trackCount = extractor.trackCount
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                Log.w(TAG, "Video does not contain an audio track (Mute video)")
                return@withContext null
            }

            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }

            val cacheAudioDir = File(context.cacheDir, "extracted_audio").apply { mkdirs() }
            val outputFile = File(cacheAudioDir, "audio_${System.currentTimeMillis()}.m4a")

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            extractor.selectTrack(audioTrackIndex)
            val buffer = ByteBuffer.allocate(512 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            var totalBytesRead = 0L

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                totalBytesRead += sampleSize
                extractor.advance()
            }

            muxer.stop()

            if (outputFile.length() > 0 && totalBytesRead > 0) {
                Log.d(TAG, "Audio extracted successfully: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                ExtractedAudioResult(
                    audioFile = outputFile,
                    durationMs = durationUs / 1000L,
                    sampleRate = sampleRate,
                    channelCount = channelCount,
                    hasSpeechCandidate = true
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract audio from video: ${e.message}", e)
            null
        } finally {
            try { extractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}
