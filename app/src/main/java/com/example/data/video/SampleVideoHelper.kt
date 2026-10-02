package com.example.data.video

import android.content.Context
import android.util.Log
import com.example.data.model.SampleVideoItem
import com.example.data.model.SubtitleSegment
import com.example.data.model.VideoProject
import java.io.File
import java.io.FileOutputStream

object SampleVideoHelper {
    private const val TAG = "SampleVideoHelper"

    /**
     * Extracts and returns the real local MP4 file path of the sample video
     */
    fun getOrCreateSampleVideoUri(context: Context, sample: SampleVideoItem): String {
        val dir = File(context.filesDir, "sample_videos").apply { mkdirs() }
        val targetFile = File(dir, "${sample.id}.mp4")
        if (!targetFile.exists() || targetFile.length() < 1000) {
            try {
                context.assets.open("sample_videos/${sample.id}.mp4").use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Extracted sample video asset to ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Could not extract sample asset: ${e.message}")
            }
        }
        return if (targetFile.exists() && targetFile.length() > 0) {
            targetFile.absolutePath
        } else {
            "sample://${sample.id}"
        }
    }

    fun getRawSourceSegments(sample: SampleVideoItem): List<SubtitleSegment> {
        return sample.initialSegments.mapIndexed { index, seed ->
            SubtitleSegment(
                projectId = sample.id,
                indexNumber = index + 1,
                startTimeMs = seed.startMs,
                endTimeMs = seed.endMs,
                originalChinese = seed.chinese,
                vietnameseText = ""
            )
        }
    }

    fun createProjectFromSample(context: Context, sample: SampleVideoItem): Pair<VideoProject, List<SubtitleSegment>> {
        val uri = getOrCreateSampleVideoUri(context, sample)
        val project = VideoProject(
            id = sample.id,
            title = sample.title,
            videoUri = uri,
            durationMs = sample.durationSeconds * 1000L,
            aspectRatio = sample.aspectRatio,
            isSample = true
        )
        val segments = getRawSourceSegments(sample)
        return Pair(project, segments)
    }
}
