package com.bayram.xqtvapp.tv

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.bayram.xqtvapp.PlayReq
import com.bayram.xqtvapp.Session
import com.bayram.xqtvapp.data.ContentCache
import com.bayram.xqtvapp.data.EpisodeEntry
import com.bayram.xqtvapp.data.FavoritesStore
import com.bayram.xqtvapp.data.SeriesEntry
import com.bayram.xqtvapp.data.SourceEntry
import com.bayram.xqtvapp.data.SourceStore
import com.bayram.xqtvapp.data.StalkerChannel
import com.bayram.xqtvapp.data.StalkerClient
import com.bayram.xqtvapp.data.loadSource
import com.bayram.xqtvapp.ui.AddSourceScreen
import com.bayram.xqtvapp.ui.LoadingRingScreen
import com.bayram.xqtvapp.ui.SourcesScreen
import com.bayram.xqtvapp.ui.SplashScreen
import com.bayram.xqtvapp.ui.randomMac
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TvAppTheme { TvAppNav() } }
    }
}

private sealed interface TvRoot {
    data object Splash : TvRoot
    data object Sources : TvRoot
    data object Add : TvRoot
    data class Loading(val src: SourceEntry, val force: Boolean) : TvRoot
    data object Home : TvRoot
}

@Composable
fun TvAppNav() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var root by remember { mutableStateOf<TvRoot>(TvRoot.Splash) }
    var session by remember { mutableStateOf<Session?>(null) }
    var play by remember { mutableStateOf<PlayReq?>(null) }
    var movieDetail by remember { mutableStateOf<StalkerChannel?>(null) }
    var seriesDetail by remember { mutableStateOf<SeriesEntry?>(null) }
    var showFilter by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf<List<SourceEntry>>(emptyList()) }
    var counts by remember { mutableStateOf<Map<String, Triple<Int, Int, Int>>>(emptyMap()) }
    var lastId by remember { mutableStateOf<String?>(null) }

    suspend fun reloadSources() {
        val list = withContext(Dispatchers.IO) { SourceStore.flow(ctx).first() }
        sources = list
        lastId = withContext(Dispatchers.IO) { SourceStore.lastId(ctx) }
        counts = withContext(Dispatchers.IO) {
            list.associate { s ->
                val c = ContentCache.load(ctx, s.cacheKey())
                s.cacheKey() to Triple(c?.channels?.size ?: 0, c?.movies?.size ?: 0, c?.series?.size ?: 0)
            }
        }
    }

    fun openPlay(
        ch: StalkerChannel, url: String, alt: String?,
        headers: Map<String, String> = emptyMap(),
        zap: List<StalkerChannel> = emptyList(), zapIdx: Int = -1
    ) {
        scope.launch { FavoritesStore.pushRecent(ctx, ch, kind = "live") }
        val s = session
        play = PlayReq(
            ch.name, url, alt, isLive = true, resumeId = ch.id, headers = headers,
            zap = zap, zapIndex = zapIdx,
            xServer = s?.xServer ?: "", xUser = s?.xUser ?: "", xPass = s?.xPass ?: "",
            stalkerUrl = s?.stalkerUrl ?: "", stalkerMac = s?.stalkerMac ?: "",
            m3uUrl = s?.m3uUrl ?: ""
        )
    }

    fun openMoviePlay(movie: StalkerChannel, url: String, startMs: Long = 0L, headers: Map<String, String> = emptyMap()) {
        scope.launch { FavoritesStore.pushRecent(ctx, movie, kind = "movie") }
        play = PlayReq(movie.name, url, null, isLive = false, startMs = startMs, resumeId = movie.id, headers = headers)
    }

    fun openEpisode(seriesName: String, cover: String, episodes: List<EpisodeEntry>, idx: Int, startMs: Long = 0L) {
        val ep = episodes.getOrNull(idx) ?: return
        val title = "$seriesName • S${ep.season} B${ep.episode}"
        val key = "series_" + ep.id
        scope.launch {
            FavoritesStore.pushRecent(
                ctx, StalkerChannel(key, title, cover, ep.url, "Series"),
                kind = "episode", seriesTitle = seriesName, epIdx = idx
            )
        }
        play = PlayReq(
            title, ep.url, null, seriesTitle = seriesName, episodes = episodes,
            episodeIndex = idx, isLive = false, startMs = startMs, resumeId = key
        )
    }

    when (val r = root) {
        TvRoot.Splash -> SplashScreen(onDone = {
            scope.launch {
                reloadSources()
                val last = SourceStore.lastId(ctx)
                val target = sources.find { it.id == last } ?: sources.firstOrNull()
                root = if (target != null) TvRoot.Loading(target, force = false) else TvRoot.Sources
            }
        })
        TvRoot.Sources -> SourcesScreen(
            sources = sources,
            activeId = session?.sourceId ?: lastId,
            counts = counts,
            onPick = { root = TvRoot.Loading(it, force = false) },
            onAdd = { root = TvRoot.Add },
            onBack = if (session != null) {
                { root = TvRoot.Home }
            } else null
        )
        TvRoot.Add -> AddSourceScreen(
            initialMac = randomMac(),
            onDone = { e ->
                scope.launch {
                    SourceStore.add(ctx, e)
                    reloadSources()
                    root = TvRoot.Loading(e, force = true)
                }
            },
            onBack = {
                scope.launch { reloadSources() }
                root = TvRoot.Sources
            }
        )
        is TvRoot.Loading -> LoadingRingScreen(
            src = r.src,
            forceRefresh = r.force,
            onDone = { s, _, _ ->
                session = s
                scope.launch { reloadSources() }
                root = TvRoot.Home
            },
            onCancel = {
                root = if (session != null) TvRoot.Home else TvRoot.Sources
            }
        )
        TvRoot.Home -> {
            val s = session
            if (s == null) {
                LaunchedEffect(Unit) { root = TvRoot.Sources }
            } else {
                TvHomeEntry(
                    session = s,
                    onSourceSwitch = { root = TvRoot.Sources },
                    onRefresh = {
                        root = TvRoot.Loading(
                            SourceEntry(
                                s.sourceId, s.sourceName,
                                when {
                                    s.stalkerUrl.isNotBlank() -> "stalker"
                                    s.xServer.isNotBlank() -> "xtream"
                                    else -> "m3u"
                                },
                                url = s.stalkerUrl.ifBlank { s.xServer },
                                user = s.stalkerMac.ifBlank { s.xUser },
                                pass = s.xPass
                            ),
                            force = true
                        )
                    },
                    onLogout = {
                        scope.launch {
                            SourceStore.remove(ctx, s.sourceId)
                            session = null
                            reloadSources()
                            root = TvRoot.Sources
                        }
                    },
                    onPlayChannel = { ch, url, alt, h, z, zi -> openPlay(ch, url, alt, h, z, zi) },
                    onOpenMovie = { movieDetail = it },
                    onOpenSeries = { seriesDetail = it },
                    onOpenFilter = { showFilter = true }
                )
            }
        }
    }

    when {
        play != null -> TvPlayerScreen(req = play!!, onBack = { play = null })
        movieDetail != null && session != null -> TvMovieDetail(
            movie = movieDetail!!, session = session!!,
            onBack = { movieDetail = null },
            onSelect = { movieDetail = it },
            onPlay = { url, ms, h -> openMoviePlay(movieDetail!!, url, ms, h) }
        )
        seriesDetail != null && session != null -> TvSeriesDetail(
            entry = seriesDetail!!, session = session!!,
            onBack = { seriesDetail = null },
            onSelect = { seriesDetail = it },
            onPlayEpisode = { name, cover, eps, idx, ms -> openEpisode(name, cover, eps, idx, ms) }
        )
        showFilter && session != null -> TvCountryFilter(
            session = session!!, onClose = { showFilter = false }
        )
    }
}

/** Ana ekrana giris: TV kumandasiyla cikis icin tek Geri yeter. */
@Composable
fun TvHomeEntry(
    session: Session,
    onSourceSwitch: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onPlayChannel: (StalkerChannel, String, String?, Map<String, String>, List<StalkerChannel>, Int) -> Unit,
    onOpenMovie: (StalkerChannel) -> Unit,
    onOpenSeries: (SeriesEntry) -> Unit,
    onOpenFilter: () -> Unit
) {
    val act = LocalContext.current as? Activity
    BackHandler { act?.finish() }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B12))) {
        TvHomeScreen(
            session = session,
            onSourceSwitch = onSourceSwitch,
            onRefresh = onRefresh,
            onLogout = onLogout,
            onPlayChannel = onPlayChannel,
            onOpenMovie = onOpenMovie,
            onOpenSeries = onOpenSeries,
            onOpenFilter = onOpenFilter
        )
    }
}

fun tvToast(ctx: android.content.Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}
