package com.bayram.xqtvapp.data

import android.content.Context
import com.google.gson.Gson
import java.io.File

/**
 * Icerik onbellegi: liste bir kez cekilir, cihaza JSON olarak yazilir.
 * Sonraki acilislar onbelikten aninda acilir; sadece sure dolunca
 * (12 sa) veya kullanici "Yenile"ye basinca agdan tazelenir.
 */
data class CachedPayload(
    val savedAt: Long = 0L,
    val label: String = "",
    val channels: List<StalkerChannel> = emptyList(),
    val movies: List<StalkerChannel> = emptyList(),
    val series: List<SeriesEntry> = emptyList()
)

object ContentCache {
    const val TTL_MS = 12L * 60 * 60 * 1000
    private val gson = Gson()

    private fun file(ctx: Context, key: String): File =
        File(ctx.filesDir, "portio_cache_$key.json")

    suspend fun load(ctx: Context, key: String): CachedPayload? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val f = file(ctx, key)
                if (!f.exists()) return@withContext null
                gson.fromJson(f.readText(), CachedPayload::class.java)
            } catch (_: Exception) { null }
        }

    suspend fun save(ctx: Context, key: String, payload: CachedPayload) {
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                file(ctx, key).writeText(gson.toJson(payload))
            }
        } catch (_: Exception) { }
    }

    suspend fun clear(ctx: Context, key: String) {
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                file(ctx, key).delete()
            }
        } catch (_: Exception) { }
    }

    fun isFresh(p: CachedPayload?): Boolean =
        p != null && (System.currentTimeMillis() - p.savedAt) < TTL_MS
}
