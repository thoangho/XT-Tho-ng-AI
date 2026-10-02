package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.AspectRatio
import com.example.data.model.DubbingConfig
import com.example.data.model.MaskConfig
import com.example.data.model.SubtitleConfig
import com.example.data.model.TranslationEngine
import org.json.JSONObject

class Converters {
    @TypeConverter
    fun fromAspectRatio(value: AspectRatio): String = value.name

    @TypeConverter
    fun toAspectRatio(value: String): AspectRatio = try {
        AspectRatio.valueOf(value)
    } catch (_: Exception) {
        AspectRatio.VERTICAL_9_16
    }

    @TypeConverter
    fun fromTranslationEngine(value: TranslationEngine): String = value.name

    @TypeConverter
    fun toTranslationEngine(value: String): TranslationEngine = try {
        TranslationEngine.valueOf(value)
    } catch (_: Exception) {
        TranslationEngine.MULTI_TIER_FAILSAFE
    }

    @TypeConverter
    fun fromMaskConfig(config: MaskConfig): String {
        val json = JSONObject()
        json.put("yPercent", config.yPercent.toDouble())
        json.put("widthPercent", config.widthPercent.toDouble())
        json.put("heightPercent", config.heightPercent.toDouble())
        json.put("opacity", config.opacity.toDouble())
        json.put("cornerRadiusDp", config.cornerRadiusDp)
        json.put("isBlurEffect", config.isBlurEffect)
        json.put("colorHex", config.colorHex)
        return json.toString()
    }

    @TypeConverter
    fun toMaskConfig(value: String?): MaskConfig {
        if (value.isNullOrBlank()) return MaskConfig()
        return try {
            val json = JSONObject(value)
            MaskConfig(
                yPercent = json.optDouble("yPercent", 75.0).toFloat(),
                widthPercent = json.optDouble("widthPercent", 92.0).toFloat(),
                heightPercent = json.optDouble("heightPercent", 9.0).toFloat(),
                opacity = json.optDouble("opacity", 0.95).toFloat(),
                cornerRadiusDp = json.optInt("cornerRadiusDp", 8),
                isBlurEffect = json.optBoolean("isBlurEffect", false),
                colorHex = json.optString("colorHex", "#000000")
            )
        } catch (_: Exception) {
            MaskConfig()
        }
    }

    @TypeConverter
    fun fromSubtitleConfig(config: SubtitleConfig): String {
        val json = JSONObject()
        json.put("fontSizeSp", config.fontSizeSp)
        json.put("textColorHex", config.textColorHex)
        json.put("strokeColorHex", config.strokeColorHex)
        json.put("strokeWidthDp", config.strokeWidthDp.toDouble())
        json.put("hasDropShadow", config.hasDropShadow)
        json.put("isBold", config.isBold)
        json.put("maxLines", config.maxLines)
        return json.toString()
    }

    @TypeConverter
    fun toSubtitleConfig(value: String?): SubtitleConfig {
        if (value.isNullOrBlank()) return SubtitleConfig()
        return try {
            val json = JSONObject(value)
            SubtitleConfig(
                fontSizeSp = json.optInt("fontSizeSp", 16),
                textColorHex = json.optString("textColorHex", "#FFFFFF"),
                strokeColorHex = json.optString("strokeColorHex", "#000000"),
                strokeWidthDp = json.optDouble("strokeWidthDp", 2.5).toFloat(),
                hasDropShadow = json.optBoolean("hasDropShadow", true),
                isBold = json.optBoolean("isBold", true),
                maxLines = json.optInt("maxLines", 2)
            )
        } catch (_: Exception) {
            SubtitleConfig()
        }
    }

    @TypeConverter
    fun fromDubbingConfig(config: DubbingConfig): String {
        val json = JSONObject()
        json.put("voiceId", config.voiceId)
        json.put("voiceName", config.voiceName)
        json.put("isMale", config.isMale)
        json.put("speechRate", config.speechRate.toDouble())
        json.put("pitch", config.pitch.toDouble())
        json.put("originalAudioVolume", config.originalAudioVolume.toDouble())
        json.put("dubVoiceVolume", config.dubVoiceVolume.toDouble())
        return json.toString()
    }

    @TypeConverter
    fun toDubbingConfig(value: String?): DubbingConfig {
        if (value.isNullOrBlank()) return DubbingConfig()
        return try {
            val json = JSONObject(value)
            DubbingConfig(
                voiceId = json.optString("voiceId", "vi-VN-HoaiMyNeural"),
                voiceName = json.optString("voiceName", "Hoài Mỹ (Nữ - Miền Bắc)"),
                isMale = json.optBoolean("isMale", false),
                speechRate = json.optDouble("speechRate", 0.9).toFloat(),
                pitch = json.optDouble("pitch", 1.0).toFloat(),
                originalAudioVolume = json.optDouble("originalAudioVolume", 0.10).toFloat(),
                dubVoiceVolume = json.optDouble("dubVoiceVolume", 1.0).toFloat()
            )
        } catch (_: Exception) {
            DubbingConfig()
        }
    }
}
