package com.bayram.xqtvapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bayram.xqtvapp.KEY_M_URL
import com.bayram.xqtvapp.KEY_S_MAC
import com.bayram.xqtvapp.KEY_S_URL
import com.bayram.xqtvapp.KEY_X_PASS
import com.bayram.xqtvapp.KEY_X_SERVER
import com.bayram.xqtvapp.KEY_X_USER
import com.bayram.xqtvapp.Session
import com.bayram.xqtvapp.dataStore
import kotlinx.coroutines.flow.first

val KEY_LAST = stringPreferencesKey("last_source")

suspend fun loadStalkerSession(url: String, mac: String): Session {
    val c = StalkerClient(url.trim(), mac.trim())
    if (!c.handshake()) throw Exception(c.lastError.ifBlank { "URL/MAC kontrol et" })
    val ch = c.getChannels()
    if (ch.isEmpty()) throw Exception("Bağlandı ama liste boş bulundu")
    val vod = c.getVod(5)
    return Session("Stalker", ch, vod, emptyList(), url.trim(), mac.trim())
}

suspend fun loadXtreamSession(server: String, user: String, pass: String): Session {
    val x = XtreamClient(server.trim(), user.trim(), pass.trim())
    if (!x.login()) throw Exception(x.lastError.ifBlank { "giriş başarısız" })
    val ch = x.liveStreams()
    val movies = x.vodStreams()
    val series = x.seriesList()
    if (ch.isEmpty() && movies.isEmpty() && series.isEmpty()) throw Exception("Giriş ok ama içerik boş")
    return Session(
        "Xtream (${user.trim()})", ch, movies, series,
        xServer = server.trim(), xUser = user.trim(), xPass = pass.trim()
    )
}

suspend fun loadM3uSession(url: String): Session {
    val text = M3uParser.download(url.trim())
    val all = M3uParser.parse(text)
    if (all.isEmpty()) throw Exception("Listede içerik bulunamadı")
    return Session("M3U", all.filter { it.genre != "VOD" }.ifEmpty { all }, all.filter { it.genre == "VOD" }, emptyList())
}

suspend fun markLastSource(ctx: Context, which: String) {
    ctx.dataStore.edit { it[KEY_LAST] = which }
}

suspend fun clearSavedSources(ctx: Context) {
    ctx.dataStore.edit {
        it.remove(KEY_S_URL); it.remove(KEY_S_MAC)
        it.remove(KEY_X_SERVER); it.remove(KEY_X_USER); it.remove(KEY_X_PASS)
        it.remove(KEY_M_URL); it.remove(KEY_LAST)
    }
}

/** Kayitli son kaynaga otomatik giris; kayit yoksa/hata varsa null doner. */
suspend fun tryAutoLogin(ctx: Context): Session {
    val p = ctx.dataStore.data.first()
    return when (p[KEY_LAST]) {
        "stalker" -> {
            val u = p[KEY_S_URL] ?: ""
            val m = p[KEY_S_MAC] ?: ""
            if (u.isBlank() || m.isBlank()) throw Exception("kayıt eksik")
            loadStalkerSession(u, m)
        }
        "xtream" -> {
            val s = p[KEY_X_SERVER] ?: ""
            val u = p[KEY_X_USER] ?: ""
            val pw = p[KEY_X_PASS] ?: ""
            if (s.isBlank() || u.isBlank()) throw Exception("kayıt eksik")
            loadXtreamSession(s, u, pw)
        }
        "m3u" -> {
            val u = p[KEY_M_URL] ?: ""
            if (u.isBlank()) throw Exception("kayıt eksik")
            loadM3uSession(u)
        }
        else -> throw Exception("kayıtlı oturum yok")
    }
}
