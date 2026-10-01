package com.bayram.xqtvapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bayram.xqtvapp.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val KEY_FAV = stringPreferencesKey("favs_v2")
private val KEY_RECENT = stringPreferencesKey("recent_v2")
val KEY_AUTOPLAY = booleanPreferencesKey("autoplay_next")

private const val FS = "\u001F"
"
private const val RS = "\u001E"
"

/**
 * kayit formati: id|title|url|logo|posMs|durMs|kind|seriesTitle|epIdx
 * kind: live | movie | episode
 */
data class ResumeInfo(
    val posMs: Long = 0L,
    val durMs: Long = 0L,
    val kind: String = "",
    val seriesTitle: String = "",
    val epIdx: Int = -1
) {
    fun hasValid(): Boolean = posMs > 10_000L && (durMs <= 0L || posMs < (durMs * 0.95).toLong())
}

object FavoritesStore {
    fun favsFlow(ctx: Context): Flow<Set<String>> =
        ctx.dataStore.data.map { prefs ->
            (prefs[KEY_FAV] ?: "").split(RS).filter { it.isNotBlank() }.toSet()
        }

    suspend fun toggle(ctx: Context, id: String): Boolean {
        var added = false
        ctx.dataStore.edit { p ->
            val cur = (p[KEY_FAV] ?: "").split(RS).filter { it.isNotBlank() }.toMutableSet()
            added = if (cur.contains(id)) {
                cur.remove(id); false
            } else {
                cur.add(id); true
            }
            p[KEY_FAV] = cur.joinToString(RS)
        }
        return added
    }

    fun autoplayFlow(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[KEY_AUTOPLAY] ?: true }

    suspend fun setAutoplay(ctx: Context, v: Boolean) {
        ctx.dataStore.edit { it[KEY_AUTOPLAY] = v }
    }

    private fun parseRec(line: String): Triple<StalkerChannel, ResumeInfo, String>? {
        if (line.isBlank()) return null
        val f = line.split(FS)
        if (f.size < 4) return null
        val ch = StalkerChannel(f[0], f[1], f[3], f[2], "Recent")
        val info = ResumeInfo(
            posMs = f.getOrNull(4)?.toLongOrNull() ?: 0L,
            durMs = f.getOrNull(5)?.toLongOrNull() ?: 0L,
            kind = f.getOrNull(6) ?: "",
            seriesTitle = f.getOrNull(7) ?: "",
            epIdx = f.getOrNull(8)?.toIntOrNull() ?: -1
        )
        return Triple(ch, info, f[0])
    }

    private fun render(id: String, title: String, url: String, logo: String, info: ResumeInfo): String =
        listOf(
            id, title, url, logo,
            info.posMs.toString(), info.durMs.toString(),
            info.kind, info.seriesTitle, info.epIdx.toString()
        ).joinToString(FS)

    fun recentFlow(ctx: Context): Flow<List<StalkerChannel>> =
        ctx.dataStore.data.map { prefs ->
            (prefs[KEY_RECENT] ?: "").split(RS).mapNotNull { parseRec(it)?.first }
        }

    fun resumeFlow(ctx: Context): Flow<Map<String, ResumeInfo>> =
        ctx.dataStore.data.map { prefs ->
            (prefs[KEY_RECENT] ?: "").split(RS).mapNotNull { parseRec(it) }
                .associate { it.third to it.second }
        }

    fun entryFlow(ctx: Context, id: String): Flow<ResumeInfo?> =
        resumeFlow(ctx).map { it[id] }

    suspend fun pushRecent(
        ctx: Context, ch: StalkerChannel,
        kind: String = "", seriesTitle: String = "", epIdx: Int = -1
    ) {
        ctx.dataStore.edit { p ->
            val cur = (p[KEY_RECENT] ?: "").split(RS).filter { it.isNotBlank() }.toMutableList()
            val oldInfo = cur.firstOrNull { it.split(FS).firstOrNull() == ch.id }
                ?.let { parseRec(it)?.second } ?: ResumeInfo()
            cur.removeAll { it.split(FS).firstOrNull() == ch.id }
            val info = oldInfo.copy(
                kind = kind.ifBlank { oldInfo.kind },
                seriesTitle = seriesTitle.ifBlank { oldInfo.seriesTitle },
                epIdx = if (epIdx >= 0) epIdx else oldInfo.epIdx
            )
            cur.add(0, render(ch.id, ch.name, ch.cmd, ch.logo, info))
            p[KEY_RECENT] = cur.take(25).joinToString(RS)
        }
    }

    suspend fun savePosition(ctx: Context, id: String, posMs: Long, durMs: Long) {
        ctx.dataStore.edit { p ->
            val cur = (p[KEY_RECENT] ?: "").split(RS).filter { it.isNotBlank() }.toMutableList()
            val i = cur.indexOfFirst { it.split(FS).firstOrNull() == id }
            if (i < 0) return@edit
            val (ch, info, _) = parseRec(cur[i]) ?: return@edit
            cur[i] = render(ch.id, ch.name, ch.cmd, ch.logo, info.copy(posMs = posMs, durMs = durMs))
            // izlenen en üste tasinir
            val moved = cur.removeAt(i)
            cur.add(0, moved)
            p[KEY_RECENT] = cur.take(25).joinToString(RS)
        }
    }

    suspend fun clearPosition(ctx: Context, id: String) {
        ctx.dataStore.edit { p ->
            val cur = (p[KEY_RECENT] ?: "").split(RS).filter { it.isNotBlank() }.toMutableList()
            val i = cur.indexOfFirst { it.split(FS).firstOrNull() == id }
            if (i < 0) return@edit
            val (ch, info, _) = parseRec(cur[i]) ?: return@edit
            cur[i] = render(ch.id, ch.name, ch.cmd, ch.logo, info.copy(posMs = 0L, durMs = 0L))
            p[KEY_RECENT] = cur.take(25).joinToString(RS)
        }
    }

    /** Seride kaldigi bolum: recency sirasinda ilk gecerli pozisyonlu bolumun indexi. */
    suspend fun seriesResumeIdx(ctx: Context, epIds: List<String>): Int {
        val recs = resumeFlow(ctx).first()
        // recentFlow sirasi kaybolur (map), o yuzden ham sirayi kullan
        val order = (ctx.dataStore.data.first()[KEY_RECENT] ?: "").split(RS)
            .mapNotNull { it.split(FS).firstOrNull()?.takeIf { s -> s.isNotBlank() } }
        for (id in order) {
            val info = recs[id] ?: continue
            if (info.hasValid() && id in epIds) return epIds.indexOf(id)
        }
        return -1
    }
}
