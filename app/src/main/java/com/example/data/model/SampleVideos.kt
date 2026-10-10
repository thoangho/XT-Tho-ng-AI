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
    // Đã xóa sạch toàn bộ các câu thoại mẫu cứng (như các câu "快看!", "Mau nhìn này!"...)
    // Đảm bảo khi khởi tạo bất kỳ video nào, danh sách phụ đề ban đầu luôn TRỐNG (emptyList())
    val SAMPLES = listOf(
        SampleVideoItem(
            id = "sample_douyin_street_food",
            title = "Ẩm thực Trùng Khánh: Lẩu cay tê siêu cay",
            category = "Ẩm thực Douyin",
            aspectRatio = AspectRatio.VERTICAL_9_16,
            durationSeconds = 18,
            description = "Video ẩm thực đường phố Douyin",
            badge = "HOT DOUYIN",
            initialSegments = emptyList()
        ),
        SampleVideoItem(
            id = "sample_tech_review",
            title = "Trên tay Smartphone gập thế hệ mới nhất",
            category = "Công nghệ Review",
            aspectRatio = AspectRatio.HORIZONTAL_16_9,
            durationSeconds = 20,
            description = "Video đánh giá công nghệ màn hình ngang 16:9",
            badge = "16:9 TECH",
            initialSegments = emptyList()
        ),
        SampleVideoItem(
            id = "sample_office_comedy",
            title = "Tình huống công sở: Ngày đầu làm việc",
            category = "Hài kịch Douyin",
            aspectRatio = AspectRatio.VERTICAL_9_16,
            durationSeconds = 17,
            description = "Clip hài kịch châm biếm công sở",
            badge = "HÀI HƯỚC",
            initialSegments = emptyList()
        )
    )
}
