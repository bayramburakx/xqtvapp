package com.bayram.xqtvapp.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EpgEntry(
    val title: String,
    val startEpoch: Long,
    val endEpoch: Long
) {
    fun progress(nowSec: Long = System.currentTimeMillis() / 1000): Float {
        if (endEpoch <= startEpoch) return 0f
        return ((nowSec - startEpoch).toFloat() / (endEpoch - startEpoch)).coerceIn(0f, 1f)
    }

    fun range(): String {
        return try {
            val f = SimpleDateFormat("HH:mm", Locale.getDefault())
            "${f.format(Date(startEpoch * 1000))} – ${f.format(Date(endEpoch * 1000))}"
        } catch (_: Exception) { "" }
    }
}

/** Kisa EPG onbellegi (30 dk): ayni kanala tekrar tekrar sorulmaz. */
object EpgCache {
    private const val TTL_MS = 30L * 60 * 1000
    private data class Row(val at: Long, val epg: EpgEntry?)
    private val mem = LinkedHashMap<String, Row>()
    private const val MAX = 200

    suspend fun get(client: XtreamClient, streamId: String): EpgEntry? {
        val now = System.currentTimeMillis()
        synchronized(mem) {
            mem[streamId]?.let { if (now - it.at < TTL_MS) return it.epg }
        }
        val epg = try {
            client.nowPlaying(streamId)?.let { EpgEntry(it.title, it.startEpoch, it.endEpoch) }
        } catch (_: Exception) { null }
        synchronized(mem) {
            if (mem.size > MAX) mem.remove(mem.keys.first())
            mem[streamId] = Row(now, epg)
        }
        return epg
    }
}
