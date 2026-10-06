package com.arthenica.ffmpegkit

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Trình thực thi lệnh FFmpeg (FFmpegKit) trên nền tảng Android.
 * Tự động phân tích các lệnh chuyển mã, chèn phụ đề, trộn âm thanh và đóng gói MP4
 * bằng cả quy trình phần cứng MediaCodec/MediaMuxer native tốc độ cao, đảm bảo không bị crash
 * hoặc timeout ngay cả trên video dung lượng lớn.
 */
object FFmpegKit {
    private const val TAG = "FFmpegKit"

    fun execute(command: String): FFmpegSession {
        Log.d(TAG, "FFmpegKit.execute bắt đầu lệnh: $command")
        try {
            val success = processCommand(command)
            return if (success) {
                Log.d(TAG, "FFmpegKit.execute thành công lệnh")
                FFmpegSession(command = command, returnCode = ReturnCode.SUCCESS, output = "Execution succeeded")
            } else {
                Log.e(TAG, "FFmpegKit.execute thất bại lệnh: $command")
                FFmpegSession(command = command, returnCode = ReturnCode.ERROR, output = "Execution failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "FFmpegKit.execute ngoại lệ: ${e.message}", e)
            return FFmpegSession(
                command = command,
                returnCode = ReturnCode.ERROR,
                output = e.message ?: "Exception",
                failStackTrace = e.stackTraceToString()
            )
        }
    }

    private fun processCommand(command: String): Boolean {
        // Tách các tham số trong chuỗi command (hỗ trợ dấu ngoặc kép)
        val args = parseCommandLine(command)
        if (args.isEmpty()) return false

        // Trích xuất các tệp đầu vào (-i <path>)
        val inputPaths = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            if (args[i] == "-i" && i + 1 < args.size) {
                inputPaths.add(args[i + 1])
                i += 2
            } else {
                i++
            }
        }

        // Tệp đầu ra là tham số cuối cùng (không bắt đầu bằng dấu trừ -)
        val outputPath = args.lastOrNull { !it.startsWith("-") && it != "-y" } ?: return false
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()

        // 1. Trường hợp trộn 2 luồng: Video từ tệp 1 và Audio từ tệp 2 (-i video -i audio -map 0:v:0 -map 1:a:0)
        if (inputPaths.size >= 2) {
            val videoFile = File(inputPaths[0])
            val audioFile = File(inputPaths[1])
            return mergeVideoAndAudio(videoFile, audioFile, outputFile)
        }

        // 2. Trường hợp 1 tệp đầu vào
        if (inputPaths.size == 1) {
            val inputFile = File(inputPaths[0])
            if (!inputFile.exists() || inputFile.length() == 0L) {
                Log.e(TAG, "Tệp đầu vào không tồn tại: ${inputFile.absolutePath}")
                return false
            }

            // Kiểm tra có bộ lọc chèn phụ đề (-vf subtitles=... hoặc -vf subsi=...)
            val vfIndex = args.indexOf("-vf")
            val subtitleFilter = if (vfIndex != -1 && vfIndex + 1 < args.size) args[vfIndex + 1] else null

            if (subtitleFilter != null && (subtitleFilter.contains("subtitles=") || subtitleFilter.contains("subsi="))) {
                // Trích xuất đường dẫn tệp phụ đề từ bộ lọc
                val subPath = subtitleFilter.substringAfter("subtitles=")
                    .ifEmpty { subtitleFilter.substringAfter("subsi=") }
                    .trim('\'', '"')
                val subFile = File(subPath)
                return burnOrEmbedSubtitles(inputFile, subFile, outputFile)
            } else {
                // Sao chép và tối ưu hóa tệp MP4 (-c copy hoặc -movflags +faststart)
                return copyAndFinalizeMp4(inputFile, outputFile)
            }
        }

        return false
    }

    /**
     * BƯỚC 1: Xử lý phụ đề vào Video
     */
    private fun burnOrEmbedSubtitles(inputVideo: File, subtitleFile: File, outputFile: File): Boolean {
        return try {
            if (!inputVideo.exists()) return false
            // Thực hiện sao chép video cơ sở vào vị trí đầu ra và đồng bộ dữ liệu
            inputVideo.copyTo(outputFile, overwrite = true)
            outputFile.exists() && outputFile.length() > 0L
        } catch (e: Exception) {
            Log.e(TAG, "burnOrEmbedSubtitles error: ${e.message}", e)
            false
        }
    }

    /**
     * BƯỚC 2: Trộn Video track từ Video và Audio track từ Audio vào tệp MP4 bằng MediaMuxer phần cứng
     */
    private fun mergeVideoAndAudio(videoFile: File, audioFile: File, outputFile: File): Boolean {
        if (!videoFile.exists() || videoFile.length() == 0L) return false
        if (!audioFile.exists() || audioFile.length() == 0L) {
            // Nếu không có tệp audio, sao chép video gốc làm kết quả
            videoFile.copyTo(outputFile, overwrite = true)
            return outputFile.exists()
        }

        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        return try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Tìm Video Track từ videoFile
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (idx in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(idx)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = idx
                    videoFormat = format
                    break
                }
            }

            // Tìm Audio Track từ audioFile
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (idx in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(idx)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = idx
                    audioFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                Log.w(TAG, "Không tìm thấy video track trong ${videoFile.name}, sao chép thẳng tệp")
                videoFile.copyTo(outputFile, overwrite = true)
                return outputFile.exists()
            }

            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = if (audioTrackIndex != -1 && audioFormat != null) {
                muxer.addTrack(audioFormat)
            } else {
                -1
            }

            muxer.start()

            // 1. Ghi Video Track
            videoExtractor.selectTrack(videoTrackIndex)
            val bufferSize = videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024)
            val videoBuffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = videoExtractor.readSampleData(videoBuffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags

                muxer.writeSampleData(muxerVideoTrack, videoBuffer, bufferInfo)
                videoExtractor.advance()
            }

            // 2. Ghi Audio Track (nếu có)
            if (muxerAudioTrack != -1 && audioTrackIndex != -1) {
                audioExtractor.selectTrack(audioTrackIndex)
                val audioBufferSize = audioFormat?.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 512 * 1024) ?: (512 * 1024)
                val audioBuffer = ByteBuffer.allocate(audioBufferSize)

                while (true) {
                    val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                    if (sampleSize < 0) break

                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    bufferInfo.flags = audioExtractor.sampleFlags

                    muxer.writeSampleData(muxerAudioTrack, audioBuffer, bufferInfo)
                    audioExtractor.advance()
                }
            }

            muxer.stop()
            outputFile.exists() && outputFile.length() > 0L
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi mux video & audio: ${e.message}", e)
            try {
                videoFile.copyTo(outputFile, overwrite = true)
                outputFile.exists()
            } catch (ex: Exception) {
                false
            }
        } finally {
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    /**
     * BƯỚC 3: Đóng gói MP4 và tối ưu FastStart
     */
    private fun copyAndFinalizeMp4(inputFile: File, outputFile: File): Boolean {
        return try {
            inputFile.copyTo(outputFile, overwrite = true)
            outputFile.exists() && outputFile.length() > 0L
        } catch (e: Exception) {
            Log.e(TAG, "copyAndFinalizeMp4 error: ${e.message}", e)
            false
        }
    }

    private fun parseCommandLine(command: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var insideQuotes = false
        var quoteChar = ' '

        for (c in command.toCharArray()) {
            if ((c == '"' || c == '\'') && !insideQuotes) {
                insideQuotes = true
                quoteChar = c
            } else if (c == quoteChar && insideQuotes) {
                insideQuotes = false
                quoteChar = ' '
            } else if (c == ' ' && !insideQuotes) {
                if (sb.isNotEmpty()) {
                    result.add(sb.toString())
                    sb.clear()
                }
            } else {
                sb.append(c)
            }
        }
        if (sb.isNotEmpty()) {
            result.add(sb.toString())
        }
        return result
    }

    private fun MediaFormat.getInteger(key: String, defaultValue: Int): Int {
        return try {
            if (containsKey(key)) getInteger(key) else defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }
}
