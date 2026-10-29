package com.iptv.player

data class Channel(
    val name: String,
    val url: String,
    val group: String = "",
    val logo: String = "",
    val tvgId: String = "",
    val tvgName: String = "",
    val catchup: String = "",
    val catchupDays: Int = 0,
    val catchupSource: String = "",
) {
    /** Стабильный ключ канала (используется для избранного и истории). */
    val key: String get() = url
    val hasArchive: Boolean get() = catchup.isNotBlank() && catchupDays > 0
}

data class Playlist(
    val id: String,
    val name: String,
    /** URL или путь к локальному кэшу файла. */
    val source: String,
    val isUrl: Boolean,
    val epgUrl: String = "",
    /** Слова через запятую: каналы/группы с ними в названии скрываются (реклама, заставки). */
    val filter: String = "",
)

data class Program(
    val start: Long,
    val stop: Long,
    val title: String,
    val desc: String = "",
)

data class HistoryItem(
    val channelKey: String,
    val name: String,
    val logo: String,
    val time: Long,
    /** Заполнено, если смотрели передачу из архива. */
    val programTitle: String = "",
    val programStart: Long = 0L,
    val programStop: Long = 0L,
)

data class EpgData(
    val byId: Map<String, List<Program>> = emptyMap(),
    val nameToId: Map<String, String> = emptyMap(),
) {
    fun forChannel(ch: Channel): List<Program> {
        if (ch.tvgId.isNotBlank()) byId[ch.tvgId]?.let { return it }
        val id = nameToId[ch.tvgName.lowercase()] ?: nameToId[ch.name.lowercase()]
        return if (id != null) byId[id].orEmpty() else emptyList()
    }
}

data class Settings(
    val ambient: Boolean = true,
    val ambientStrength: Float = 0.8f,
    val preset: String = "standard",
    val brightness: Float = 0f,   // -0.3 .. 0.3
    val contrast: Float = 1f,     // 0.7 .. 1.4
    val saturation: Float = 1f,   // 0 .. 2
    val maxQuality: Boolean = false,
    val overlaySec: Int = 5,
) {
    val hasPictureFx: Boolean get() = brightness != 0f || contrast != 1f || saturation != 1f
    /** Для подсветки и цветокоррекции нужен TextureView; без эффектов быстрее SurfaceView (4K/8K). */
    val needsTexture: Boolean get() = ambient || hasPictureFx
}

object Presets {
    val all: List<Pair<String, String>> = listOf(
        "standard" to "Стандарт", "cinema" to "Кино", "vivid" to "Яркий",
        "sport" to "Спорт", "night" to "Ночной",
    )

    fun apply(s: Settings, id: String): Settings = when (id) {
        "cinema" -> s.copy(preset = id, brightness = -0.02f, contrast = 1.12f, saturation = 0.95f)
        "vivid" -> s.copy(preset = id, brightness = 0.03f, contrast = 1.15f, saturation = 1.4f)
        "sport" -> s.copy(preset = id, brightness = 0.02f, contrast = 1.1f, saturation = 1.25f)
        "night" -> s.copy(preset = id, brightness = -0.08f, contrast = 0.95f, saturation = 0.9f)
        else -> s.copy(preset = "standard", brightness = 0f, contrast = 1f, saturation = 1f)
    }
}

/** Что сейчас воспроизводится. */
data class NowPlaying(
    val channel: Channel,
    val playUrl: String,
    val program: Program? = null,
    val isArchive: Boolean = false,
)
