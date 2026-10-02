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

data class LoadResult(
    val session: Session,
    val fromCache: Boolean,
    val added: Int
)

private fun sessionFromCache(src: SourceEntry, c: CachedPayload): Session =
    Session(
        label = c.label.ifBlank { src.name },
        channels = c.channels, movies = c.movies, series = c.series,
        stalkerUrl = if (src.type == "stalker") src.url else "",
        stalkerMac = if (src.type == "stalker") src.user else "",
        xServer = if (src.type == "xtream") src.url else "",
        xUser = if (src.type == "xtream") src.user else "",
        xPass = if (src.type == "xtream") src.pass else "",
        sourceId = src.id, sourceName = src.name
    )

/**
 * Kaynak yukleme: once taze onbellege bakilir (aninda acilis), yoksa/bayatsa
 * agdan cekilir ve onbellege yazilir. onStep yukleme halkasini besler.
 */
suspend fun loadSource(
    ctx: Context,
    src: SourceEntry,
    forceRefresh: Boolean,
    onStep: (phase: String, ch: Int, mv: Int, sr: Int) -> Unit = { _, _, _, _ -> }
): LoadResult {
    val old = ContentCache.load(ctx, src.cacheKey())
    if (!forceRefresh && ContentCache.isFresh(old) && old != null &&
        (old.channels.isNotEmpty() || old.movies.isNotEmpty() || old.series.isNotEmpty())
    ) {
        return LoadResult(sessionFromCache(src, old), fromCache = true, added = 0)
    }
    onStep("connect", 0, 0, 0)
    val session = when (src.type) {
        "stalker" -> {
            val c = StalkerClient(src.url.trim(), src.user.trim())
            if (!c.handshake()) throw Exception(c.lastError.ifBlank { "URL/MAC kontrol et" })
            val ch = c.getChannels()
            onStep("channels", ch.size, 0, 0)
            if (ch.isEmpty()) throw Exception("Bağlandı ama liste boş bulundu")
            val vod = c.getVod(5)
            onStep("movies", ch.size, vod.size, 0)
            Session(src.name, ch, vod, emptyList(), src.url.trim(), src.user.trim(),
                sourceId = src.id, sourceName = src.name)
        }
        "xtream" -> {
            val x = XtreamClient(src.url.trim(), src.user.trim(), src.pass.trim())
            if (!x.login()) throw Exception(x.lastError.ifBlank { "giriş başarısız" })
            onStep("connect", 0, 0, 0)
            val ch = x.liveStreams()
            onStep("channels", ch.size, 0, 0)
            val movies = x.vodStreams()
            onStep("movies", ch.size, movies.size, 0)
            val series = x.seriesList()
            onStep("series", ch.size, movies.size, series.size)
            if (ch.isEmpty() && movies.isEmpty() && series.isEmpty()) throw Exception("Giriş ok ama içerik boş")
            Session("Xtream (${src.user.trim()})", ch, movies, series,
                xServer = src.url.trim(), xUser = src.user.trim(), xPass = src.pass.trim(),
                sourceId = src.id, sourceName = src.name)
        }
        else -> {
            val text = M3uParser.download(src.url.trim())
            onStep("channels", 0, 0, 0)
            val all = M3uParser.parse(text)
            if (all.isEmpty()) throw Exception("Listede içerik bulunamadı")
            val ch = all.filter { it.genre != "VOD" }.ifEmpty { all }
            val vod = all.filter { it.genre == "VOD" }
            onStep("movies", ch.size, vod.size, 0)
            Session(src.name, ch, vod, emptyList(), m3uUrl = src.url.trim(), sourceId = src.id, sourceName = src.name)
        }
    }
    // yeni icerik sayisi
    val oldIds = old?.let { it.channels.map { c -> c.id } + it.movies.map { m -> m.id } + it.series.map { s -> s.id } }?.toSet() ?: emptySet()
    val newIds = (session.channels.map { it.id } + session.movies.map { it.id } + session.series.map { it.id }).toSet()
    val added = (newIds - oldIds).size
    ContentCache.save(
        ctx, src.cacheKey(),
        CachedPayload(System.currentTimeMillis(), session.label, session.channels, session.movies, session.series)
    )
    onStep("done", session.channels.size, session.movies.size, session.series.size)
    return LoadResult(session, fromCache = false, added = added)
}

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
    val ch = all.filter { it.genre != "VOD" }.ifEmpty { all }
    return Session("M3U", ch, all.filter { it.genre == "VOD" }, emptyList(), m3uUrl = url.trim())
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
