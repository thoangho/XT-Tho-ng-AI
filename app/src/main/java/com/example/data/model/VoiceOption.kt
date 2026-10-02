package com.example.data.model

data class VoiceOption(
    val id: String,
    val name: String,
    val region: String,
    val gender: String,
    val description: String,
    val avatarEmoji: String,
    val category: String = "Tất cả",
    val isDefault: Boolean = false
) {
    companion object {
        val ALL_VOICES = listOf(
            VoiceOption(
                id = "vi-VN-HoaiMyNeural",
                name = "Hoài Mỹ",
                region = "Miền Bắc",
                gender = "Nữ",
                description = "Giọng truyền cảm chuẩn mực, mượt mà (kể chuyện, tin tức, review video)",
                avatarEmoji = "👩",
                category = "Kể chuyện & Review",
                isDefault = true
            ),
            VoiceOption(
                id = "vi-VN-QuangAnhNeural",
                name = "Quang Anh",
                region = "Miền Bắc",
                gender = "Nam",
                description = "Giọng nam trẻ trung, tươi sáng, truyền cảm, nhịp đọc năng động tự nhiên",
                avatarEmoji = "👦",
                category = "Trẻ trung & Vlog"
            ),
            VoiceOption(
                id = "vi-VN-GiaKhiemNeural",
                name = "Gia Khiêm",
                region = "Miền Bắc",
                gender = "Nam",
                description = "Giọng nam trầm ấm, nội lực, điện ảnh, thuyết minh phim & phóng sự",
                avatarEmoji = "🎬",
                category = "Điện ảnh & Thuyết minh"
            ),
            VoiceOption(
                id = "vi-VN-DungLongTiengNeural",
                name = "Dung Lồng Tiếng",
                region = "Miền Nam",
                gender = "Nữ",
                description = "Giọng lồng tiếng chuyên nghiệp, biến hóa, giàu cảm xúc drama & hoạt hình",
                avatarEmoji = "🎙️",
                category = "Lồng tiếng & Hoạt hình"
            ),
            VoiceOption(
                id = "vi-VN-AdamNeural",
                name = "Adam",
                region = "Hiện đại",
                gender = "Nam",
                description = "Giọng nam hiện đại, sắc sảo, tự tin, review công nghệ & vlog xu hướng",
                avatarEmoji = "🎧",
                category = "Công nghệ & Đời sống"
            ),
            VoiceOption(
                id = "vi-VN-KhanhVyNeural",
                name = "Khánh Vy",
                region = "Miền Bắc",
                gender = "Nữ",
                description = "Giọng nữ hoạt bát, truyền cảm hứng, review du lịch & ẩm thực, MC cuốn hút",
                avatarEmoji = "✨",
                category = "Năng động & MC"
            ),
            VoiceOption(
                id = "vi-VN-BaoAnhNeural",
                name = "Bảo Anh",
                region = "Miền Nam",
                gender = "Nữ",
                description = "Giọng nữ ngọt ngào, dịu dàng, tâm sự đêm khuya, podcast chữa lành & lãng mạn",
                avatarEmoji = "🌸",
                category = "Tâm sự & Podcast"
            ),
            VoiceOption(
                id = "vi-VN-ChiMaiNeural",
                name = "Chi Mai",
                region = "Miền Bắc",
                gender = "Nữ",
                description = "Giọng nữ thanh lịch, nhẹ nhàng, trong trẻo, đọc sách & bản tin báo chí",
                avatarEmoji = "📖",
                category = "Đọc sách & Bản tin"
            ),
            VoiceOption(
                id = "vi-VN-KimOanhNeural",
                name = "Kim Oanh",
                region = "Miền Bắc",
                gender = "Nữ",
                description = "Giọng nữ trưởng thành, ấm áp, sâu lắng, phim tài liệu & lịch sử",
                avatarEmoji = "📻",
                category = "Tài liệu & Lịch sử"
            ),
            VoiceOption(
                id = "vi-VN-NamMinhNeural",
                name = "Nam Minh",
                region = "Miền Bắc",
                gender = "Nam",
                description = "Giọng rõ ràng, dứt khoát, đĩnh đạc (hướng dẫn, quảng cáo, hành động)",
                avatarEmoji = "👨",
                category = "Quảng cáo & Tin tức"
            ),
            VoiceOption(
                id = "vi-VN-MaiPhuongNeural",
                name = "Mai Phương",
                region = "Miền Nam",
                gender = "Nữ",
                description = "Giọng miền Nam nhẹ nhàng, duyên dáng, mộc mạc và tự nhiên",
                avatarEmoji = "👧",
                category = "Duyên dáng & Thư giãn"
            )
        )

        fun findById(id: String): VoiceOption =
            ALL_VOICES.find { it.id.equals(id, ignoreCase = true) || it.name.contains(id, ignoreCase = true) }
                ?: ALL_VOICES.first()
    }
}
