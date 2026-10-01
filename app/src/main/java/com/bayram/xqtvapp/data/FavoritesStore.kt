package com.bayram.xqtvapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bayram.xqtvapp.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val KEY_FAV = stringPreferencesKey("favs_v2")
private val KEY_RECENT = stringPreferencesKey("recent_v2")

private const val FS = "\u001F"
private const val RS = "\u001E"

/** Favoriler + izlemeye devam et (DataStore, hafif ve senkronsuz). */
object FavoritesStore {
    fun favsFlow(ctx: Context): Flow<Set<String>> =
        ctx.dataStore.data.map { prefs ->
            (prefs[KEY_FAV] ?: "").split(RS).filter { it.isNotBlank() }.toSet()
        }

    suspend fun isFav(ctx: Context, id: String): Boolean =
        favsFlow(ctx).first().contains(id)

    /** true -> eklendi, false -> çıkarıldı */
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

    fun recentFlow(ctx: Context): Flow<List<StalkerChannel>> =
        ctx.dataStore.data.map { prefs ->
            (prefs[KEY_RECENT] ?: "").split(RS).mapNotNull { rec ->
                if (rec.isBlank()) return@mapNotNull null
                val f = rec.split(FS)
                if (f.size < 4) return@mapNotNull null
                // id|title|url|logo
                StalkerChannel(f[0], f[1], f[3], f[2], "Recent")
            }
        }

    suspend fun pushRecent(ctx: Context, ch: StalkerChannel) {
        ctx.dataStore.edit { p ->
            val cur = (p[KEY_RECENT] ?: "").split(RS).filter { it.isNotBlank() }.toMutableList()
            cur.removeAll { it.split(FS).firstOrNull() == ch.id }
            cur.add(0, listOf(ch.id, ch.name, ch.cmd, ch.logo).joinToString(FS))
            p[KEY_RECENT] = cur.take(20).joinToString(RS)
        }
    }
}
