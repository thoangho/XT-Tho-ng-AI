package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class AspectRatio(val displayName: String, val ratioWidth: Int, val ratioHeight: Int, val defaultMaskY: Float) {
    VERTICAL_9_16("Dọc (9:16 - TikTok / Shorts)", 9, 16, 75f),
    HORIZONTAL_16_9("Ngang (16:9 - YouTube / FB)", 16, 9, 82f),
    SQUARE_1_1("Vuông (1:1 - Feed)", 1, 1, 80f)
}

enum class TranslationEngine(val displayName: String, val description: String) {
    GOOGLE_GTX("Google GTX Direct", "Tốc độ cao, không giới hạn, không cần key"),
    GEMINI_FLASH("Gemini AI Studio", "Dịch tự nhiên, hiểu thành ngữ, tiếng lóng Douyin"),
    MULTI_TIER_FAILSAFE("Đa tầng tự động", "Tầng 1 GTX -> Tầng 2 Gemini -> Tầng 3 An toàn")
}

data class MaskConfig(
    val yPercent: Float = 75f,         // 75% for 9:16 vertical, 82% for 16:9 horizontal
    val widthPercent: Float = 92f,     // Width of mask bar as % of video width
    val heightPercent: Float = 9f,     // Height of mask bar as % of video height
    val opacity: Float = 0.95f,        // 0.0 (transparent) to 1.0 (100% solid black)
    val cornerRadiusDp: Int = 8,
    val isBlurEffect: Boolean = false,
    val colorHex: String = "#000000"
)

data class SubtitleConfig(
    val fontSizeSp: Int = 16,
    val textColorHex: String = "#FFFFFF",
    val strokeColorHex: String = "#000000",
    val strokeWidthDp: Float = 2.5f,
    val hasDropShadow: Boolean = true,
    val isBold: Boolean = true,
    val maxLines: Int = 2
)

data class DubbingConfig(
    val voiceId: String = "vi-VN-HoaiMyNeural",
    val voiceName: String = "Hoài Mỹ (Nữ - Miền Bắc)",
    val isMale: Boolean = false,
    val speechRate: Float = 0.9f,
    val pitch: Float = 1.0f,
    val originalAudioVolume: Float = 0.10f, // 10% BGM ducking
    val dubVoiceVolume: Float = 1.0f       // 100% Vietnamese voice
)

@Entity(tableName = "video_projects")
data class VideoProject(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val videoUri: String,
    val durationMs: Long = 0,
    val aspectRatio: AspectRatio = AspectRatio.VERTICAL_9_16,
    val maskConfig: MaskConfig = MaskConfig(yPercent = AspectRatio.VERTICAL_9_16.defaultMaskY),
    val subtitleConfig: SubtitleConfig = SubtitleConfig(),
    val dubbingConfig: DubbingConfig = DubbingConfig(),
    val translationEngine: TranslationEngine = TranslationEngine.MULTI_TIER_FAILSAFE,
    val isSample: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
