package com.bayram.xqtvapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bayram.xqtvapp.dataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val KEY_SOURCES = stringPreferencesKey("sources_v1")
val KEY_LAST_ID = stringPreferencesKey("last_source_id")

/** Kayitli yayin kaynagi (Stalker / Xtream / M3U). Sifre cihazda DataStore'da durur. */
data class SourceEntry(
    val id: String,
    val name: String,
    val type: String, // "stalker" | "xtream" | "m3u"
    val url: String = "",
    val user: String = "",
    val pass: String = ""
) {
    fun cacheKey(): String = "src_" + (id.hashCode().toUInt().toString(16))
    fun describe(): String = when (type) {
        "stalker" -> "Portal"
        "xtream" -> "Xtream Codes"
        else -> "M3U"
    }
}

object SourceStore {
    private val gson = Gson()
    private val listType = object : TypeToken<List<SourceEntry>>() {}.type

    fun flow(ctx: Context): Flow<List<SourceEntry>> =
        ctx.dataStore.data.map { p ->
            try {
                gson.fromJson<List<SourceEntry>>(p[KEY_SOURCES] ?: "[]", listType) ?: emptyList()
            } catch (_: Exception) { emptyList() }
        }

    suspend fun add(ctx: Context, e: SourceEntry) {
        ctx.dataStore.edit { p ->
            val cur = try {
                gson.fromJson<List<SourceEntry>>(p[KEY_SOURCES] ?: "[]", listType) ?: emptyList()
            } catch (_: Exception) { emptyList() }
            p[KEY_SOURCES] = gson.toJson(cur.filter { it.id != e.id } + e)
        }
    }

    suspend fun remove(ctx: Context, id: String) {
        ctx.dataStore.edit { p ->
            val cur = try {
                gson.fromJson<List<SourceEntry>>(p[KEY_SOURCES] ?: "[]", listType) ?: emptyList()
            } catch (_: Exception) { emptyList() }
            p[KEY_SOURCES] = gson.toJson(cur.filter { it.id != id })
        }
        ContentCache.clear(ctx, "src_" + (id.hashCode().toUInt().toString(16)))
    }

    suspend fun lastId(ctx: Context): String? =
        ctx.dataStore.data.first()[KEY_LAST_ID]?.takeIf { it.isNotBlank() }

    suspend fun setLastId(ctx: Context, id: String) {
        ctx.dataStore.edit { it[KEY_LAST_ID] = id }
    }
}
