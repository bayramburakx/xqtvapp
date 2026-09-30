package com.bayram.xqtvapp.data

enum class SourceType { STALKER, XTREAM, M3U }

data class PortalConfig(
    val portalUrl: String = "",
    val mac: String = ""
)

// Tum kaynaklar icin ortak medya modeli:
// - Canli TV: cmd = stalker komutu ya da direkt stream URL'i
// - VOD: cmd = direkt stream URL'i
data class StalkerChannel(
    val id: String,
    val name: String,
    val logo: String,
    val cmd: String,
    val genre: String
)

data class StalkerCategory(
    val id: String,
    val title: String
)

data class SessionData(
    val type: SourceType,
    val label: String,
    val channels: List<StalkerChannel>,
    val vod: List<StalkerChannel>,
    val categories: List<StalkerCategory>
)

data class EpisodeEntry(
    val id: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val url: String,
    val cover: String
)

data class SeriesEntry(
    val id: String,
    val name: String,
    val cover: String,
    val category: String,
    val episodes: List<EpisodeEntry> = emptyList()
)
