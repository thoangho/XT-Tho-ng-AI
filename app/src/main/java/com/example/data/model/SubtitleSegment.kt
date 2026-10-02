package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subtitle_segments")
data class SubtitleSegment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: String,
    val indexNumber: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val originalChinese: String,
    val vietnameseText: String,
    val isEdited: Boolean = false,
    val isApproved: Boolean = true,
    val audioPath: String? = null
) {
    val durationMs: Long
        get() = (endTimeMs - startTimeMs).coerceAtLeast(0)

    val startTimeFormatted: String
        get() = formatTimestamp(startTimeMs)

    val endTimeFormatted: String
        get() = formatTimestamp(endTimeMs)

    companion object {
        fun formatTimestamp(ms: Long): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val millis = ms % 1000
            return String.format("%02d:%02d.%02d", minutes, seconds, millis / 10)
        }

        fun formatSrtTimestamp(ms: Long): String {
            val totalSeconds = ms / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            val millis = ms % 1000
            return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
        }
    }
}
