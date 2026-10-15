package com.aistudio.dubstudio.vmqxrt.tts

/**
 * Danh mục giọng đọc Neural Engine (Edge-TTS)
 */
enum class NeuralVoice(
    val voiceId: String,
    val displayName: String,
    val description: String,
    val gender: String
) {
    HOAI_MY("vi-VN-HoaiMyNeural", "Hoài Mỹ", "Nữ - Truyền cảm", "Nữ"),
    NAM_MINH("vi-VN-NamMinhNeural", "Nam Minh", "Nam - Trầm ấm", "Nam");

    companion object {
        fun fromId(id: String?): NeuralVoice {
            if (id == null) return HOAI_MY
            return values().firstOrNull { it.voiceId.equals(id, ignoreCase = true) }
                ?: if (id.contains("nam", ignoreCase = true) || id.contains("minh", ignoreCase = true) || id.contains("giakhiem", ignoreCase = true)) NAM_MINH else HOAI_MY
        }
    }
}
