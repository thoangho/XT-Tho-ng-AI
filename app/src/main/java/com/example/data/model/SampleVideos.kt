package com.example.data.model

data class SampleVideoItem(
    val id: String,
    val title: String,
    val category: String,
    val aspectRatio: AspectRatio,
    val durationSeconds: Int,
    val description: String,
    val badge: String,
    val initialSegments: List<SubtitleSegmentSeed> = emptyList()
)

data class SubtitleSegmentSeed(
    val startMs: Long,
    val endMs: Long,
    val chinese: String,
    val vietnamese: String
)

object SampleVideoRepository {
    // Đã xóa sạch toàn bộ danh sách các clip mẫu và câu thoại mẫu theo yêu cầu.
    // Đảm bảo không còn dữ liệu mẫu trong mã nguồn.
    val SAMPLES: List<SampleVideoItem> = emptyList()
}
