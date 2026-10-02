package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.SubtitleSegment
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleDao {
    @Query("SELECT * FROM subtitle_segments WHERE projectId = :projectId ORDER BY startTimeMs ASC")
    fun getSubtitlesForProject(projectId: String): Flow<List<SubtitleSegment>>

    @Query("SELECT * FROM subtitle_segments WHERE projectId = :projectId ORDER BY startTimeMs ASC")
    suspend fun getSubtitlesList(projectId: String): List<SubtitleSegment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtitles(subtitles: List<SubtitleSegment>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtitle(subtitle: SubtitleSegment): Long

    @Update
    suspend fun updateSubtitle(subtitle: SubtitleSegment)

    @Delete
    suspend fun deleteSubtitle(subtitle: SubtitleSegment)

    @Query("DELETE FROM subtitle_segments WHERE projectId = :projectId")
    suspend fun deleteSubtitlesForProject(projectId: String)
}
