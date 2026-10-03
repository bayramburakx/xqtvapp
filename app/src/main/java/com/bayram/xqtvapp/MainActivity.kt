package com.bayram.xqtvapp

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import coil.compose.AsyncImage
import com.bayram.xqtvapp.data.ContentCache
import com.bayram.xqtvapp.data.CountryGroup
import com.bayram.xqtvapp.data.COUNTRIES
import com.bayram.xqtvapp.data.countryDef
import com.bayram.xqtvapp.data.EpgCache
import com.bayram.xqtvapp.data.groupByCountry
import com.bayram.xqtvapp.data.EpgEntry
import com.bayram.xqtvapp.data.EpisodeEntry
import com.bayram.xqtvapp.data.FavoritesStore
import com.bayram.xqtvapp.data.SeriesEntry
import com.bayram.xqtvapp.data.SourceEntry
import com.bayram.xqtvapp.data.SourceStore
import com.bayram.xqtvapp.data.StalkerChannel
import com.bayram.xqtvapp.data.StalkerClient
import com.bayram.xqtvapp.data.XtreamClient
import com.bayram.xqtvapp.ui.AddSourceScreen
import com.bayram.xqtvapp.ui.LoadingRingScreen
import com.bayram.xqtvapp.ui.MovieDetailScreen
import com.bayram.xqtvapp.ui.PAcc1
import com.bayram.xqtvapp.ui.PBg
import com.bayram.xqtvapp.ui.PBg2
import com.bayram.xqtvapp.ui.PGlass
import com.bayram.xqtvapp.ui.PLine
import com.bayram.xqtvapp.ui.PLive
import com.bayram.xqtvapp.ui.POk
import com.bayram.xqtvapp.ui.PTx
import com.bayram.xqtvapp.ui.PTx2
import com.bayram.xqtvapp.ui.PlayerScreen
import com.bayram.xqtvapp.ui.ProgressLine
import com.bayram.xqtvapp.ui.SectionHead
import com.bayram.xqtvapp.ui.SeriesDetailScreen
import com.bayram.xqtvapp.ui.SourceCard
import com.bayram.xqtvapp.ui.SourcesScreen
import com.bayram.xqtvapp.ui.TvTab
import com.bayram.xqtvapp.ui.SplashScreen
import com.bayram.xqtvapp.ui.clickableNoRipple
import com.bayram.xqtvapp.ui.initialsOf
import com.bayram.xqtvapp.ui.randomMac
import com.bayram.xqtvapp.ui.remainingText
import com.bayram.xqtvapp.ui.tileBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val Context.dataStore by preferencesDataStore("xqtv")

val KEY_S_URL = stringPreferencesKey("stalker_url")
val KEY_S_MAC = stringPreferencesKey("stalker_mac")
val KEY_X_SERVER = stringPreferencesKey("xtream_server")
val KEY_X_USER = stringPreferencesKey("xtream_user")
val KEY_X_PASS = stringPreferencesKey("xtream_pass")
val KEY_M_URL = stringPreferencesKey("m3u_url")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = PBg, surface = PBg, surfaceVariant = Color(0xFF15151F),
                    primary = Color.White, onBackground = Color.White, onSurface = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = PBg) { AppNav() }
            }
        }
    }
}

data class Session(
    val label: String,
    val channels: List<StalkerChannel>,
    val movies: List<StalkerChannel>,
    val series: List<SeriesEntry>,
    val stalkerUrl: String = "",
    val stalkerMac: String = "",
    val xServer: String = "",
    val xUser: String = "",
    val xPass: String = "",
    val m3uUrl: String = "",
    val sourceId: String = "",
    val sourceName: String = ""
)

data class PlayReq(
    val title: String,
    val url: String,
    val altUrl: String?,
    val seriesTitle: String = "",
    val episodes: List<EpisodeEntry> = emptyList(),
    val episodeIndex: Int = -1,
    val isLive: Boolean = false,
    val startMs: Long = 0L,
    val resumeId: String = "",
    val headers: Map<String, String> = emptyMap(),
    val zap: List<StalkerChannel> = emptyList(),
    val zapIndex: Int = -1,
    val xServer: String = "",
    val xUser: String = "",
    val xPass: String = "",
    val stalkerUrl: String = "",
    val stalkerMac: String = "",
    val m3uUrl: String = ""
)

private sealed interface Root {
    data object Splash : Root
    data object Sources : Root
    data object Add : Root
    data class Loading(val src: SourceEntry, val force: Boolean) : Root
    data object Home : Root
}

@Composable
fun AppNav() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var root by remember { mutableStateOf<Root>(Root.Splash) }
    var session by remember { mutableStateOf<Session?>(null) }
    var play by remember { mutableStateOf<PlayReq?>(null) }
    var movieDetail by remember { mutableStateOf<StalkerChannel?>(null) }
    var seriesDetail by remember { mutableStateOf<SeriesEntry?>(null) }
    var searchScope by remember { mutableStateOf<String?>(null) }
    var sources by remember { mutableStateOf<List<SourceEntry>>(emptyList()) }
    var counts by remember { mutableStateOf<Map<String, Triple<Int, Int, Int>>>(emptyMap()) }
    var lastId by remember { mutableStateOf<String?>(null) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    suspend fun reloadSources() {
        // Disk IO: ana threadi kilitlememek icin IO'ya tasinir (sayfa gecis kasmalarini onler)
        val list = kotlinx.coroutines.withContext(Dispatchers.IO) {
            SourceStore.flow(ctx).first()
        }
        sources = list
        lastId = kotlinx.coroutines.withContext(Dispatchers.IO) { SourceStore.lastId(ctx) }
        counts = kotlinx.coroutines.withContext(Dispatchers.IO) {
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
        play = PlayReq(ch.name, url, alt, isLive = true, resumeId = ch.id, headers = headers,
            zap = zap, zapIndex = zapIdx,
            xServer = s?.xServer ?: "", xUser = s?.xUser ?: "", xPass = s?.xPass ?: "",
            stalkerUrl = s?.stalkerUrl ?: "", stalkerMac = s?.stalkerMac ?: "",
            m3uUrl = s?.m3uUrl ?: "")
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
        play = PlayReq(title, ep.url, null, seriesTitle = seriesName, episodes = episodes,
            episodeIndex = idx, isLive = false, startMs = startMs, resumeId = key)
    }

    // kaynak degisince headers: Xtream canli icin Referer
    fun xtreamHeaders(): Map<String, String> {
        val s = session ?: return emptyMap()
        return if (s.xServer.isNotBlank()) mapOf("Referer" to s.xServer.trimEnd('/') + "/") else emptyMap()
    }

    toastMsg?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            toastMsg = null
        }
    }

    when (val r = root) {
        Root.Splash -> SplashScreen(onDone = {
            scope.launch {
                reloadSources()
                val last = SourceStore.lastId(ctx)
                val target = sources.find { it.id == last } ?: sources.firstOrNull()
                root = if (target != null) Root.Loading(target, force = false) else Root.Sources
            }
        })
        Root.Sources -> SourcesScreen(
            sources = sources,
            activeId = session?.sourceId ?: lastId,
            counts = counts,
            onPick = { root = Root.Loading(it, force = false) },
            onAdd = { root = Root.Add },
            onBack = if (session != null) {
                { root = Root.Home }
            } else null
        )
        Root.Add -> AddSourceScreen(
            initialMac = randomMac(),
            onDone = { e ->
                scope.launch {
                    SourceStore.add(ctx, e)
                    reloadSources()
                    root = Root.Loading(e, force = true)
                }
            },
            onBack = {
                scope.launch { reloadSources() }
                root = if (sources.isNotEmpty()) Root.Sources else Root.Sources
            }
        )
        is Root.Loading -> LoadingRingScreen(
            src = r.src,
            forceRefresh = r.force,
            onDone = { s, fromCache, added ->
                session = s
                scope.launch { reloadSources() }
                if (!fromCache && added > 0) toastMsg = "$added yeni içerik eklendi"
                root = Root.Home
            },
            onCancel = {
                root = if (session != null) Root.Home else Root.Sources
            }
        )
        Root.Home -> {
            val s = session
            if (s == null) {
                LaunchedEffect(Unit) { root = Root.Sources }
            } else {
                HomeScreen(
                    session = s,
                    onSourceSwitch = { root = Root.Sources },
                    onSearch = { searchScope = it },
                    onRefresh = { root = Root.Loading(
                        SourceEntry(s.sourceId, s.sourceName,
                            when {
                                s.stalkerUrl.isNotBlank() -> "stalker"
                                s.xServer.isNotBlank() -> "xtream"
                                else -> "m3u"
                            },
                            url = s.stalkerUrl.ifBlank { s.xServer },
                            user = s.stalkerMac.ifBlank { s.xUser },
                            pass = s.xPass),
                        force = true
                    ) },
                    onLogout = {
                        scope.launch {
                            SourceStore.remove(ctx, s.sourceId)
                            session = null
                            reloadSources()
                            root = Root.Sources
                        }
                    },
                    onPlayChannel = { ch, url, alt, h, z, zi -> openPlay(ch, url, alt, h, z, zi) },
                    onOpenMovie = { movieDetail = it },
                    onOpenSeries = { seriesDetail = it }
                )
            }
        }
    }

    // overlay'ler ozel: ayni anda tek ekran (player detayin ustunu ortmez, yerine gecer)
    when {
        play != null -> PlayerScreen(req = play!!, onBack = { play = null })
        movieDetail != null && session != null -> MovieDetailScreen(
            movie = movieDetail!!, session = session!!,
            onBack = { movieDetail = null },
            onSelect = { movieDetail = it },
            onPlay = { url, ms, h -> openMoviePlay(movieDetail!!, url, ms, h) }
        )
        seriesDetail != null && session != null -> SeriesDetailScreen(
            entry = seriesDetail!!, session = session!!,
            onBack = { seriesDetail = null },
            onSelect = { seriesDetail = it },
            onPlayEpisode = { name, cover, eps, idx, ms -> openEpisode(name, cover, eps, idx, ms) }
        )
        searchScope != null && session != null -> SearchScreen(
            session = session!!,
            filter = searchScope ?: "all",
            onClose = { searchScope = null },
            onPlayChannel = { ch, url, alt, h, z, zi -> searchScope = null; openPlay(ch, url, alt, h, z, zi) },
            onOpenMovie = { searchScope = null; movieDetail = it },
            onOpenSeries = { searchScope = null; seriesDetail = it }
        )
    }
}

// ==================== ANA EKRAN ====================

private enum class MainTab(val title: String, val icon: ImageVector) {
    HOME("Ana Sayfa", Icons.Filled.Home),
    TV("Canlı TV", Icons.Filled.Tv),
    MOVIES("Filmler", Icons.Filled.Movie),
    SERIES("Diziler", Icons.Filled.PlayArrow),
    SETTINGS("Ayarlar", Icons.Filled.Settings)
}

@Composable
fun HomeScreen(
    session: Session,
    onSourceSwitch: () -> Unit,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onPlayChannel: (StalkerChannel, String, String?, Map<String, String>, List<StalkerChannel>, Int) -> Unit,
    onOpenMovie: (StalkerChannel) -> Unit,
    onOpenSeries: (SeriesEntry) -> Unit
) {
    // Sekme donusumde korunur; sekmeler key ile ayrilir, gereksiz yeniden yukleme azalir
    var tabIdx by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val tab = MainTab.entries[tabIdx.coerceIn(0, MainTab.entries.size - 1)]
    fun setTab(v: MainTab) { tabIdx = MainTab.entries.indexOf(v).coerceAtLeast(0) }
    val act = LocalContext.current as? Activity
    var lastBack by remember { mutableLongStateOf(0L) }
    var showCats by remember { mutableStateOf(false) }
    BackHandler {
        val now = System.currentTimeMillis()
        if (now - lastBack < 2000) act?.finish()
        else {
            lastBack = now
            Toast.makeText(act, "Çıkmak için tekrar bas", Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B12))) {
        androidx.compose.runtime.key(tabIdx) {
            when (tab) {
                MainTab.HOME ->
                    DiscoverTab(session, onSourceSwitch, onSearch, onPlayChannel, onOpenMovie, onOpenSeries)
                MainTab.TV ->
                    TvTab(session, onSearch, onPlayChannel)
                MainTab.MOVIES ->
                    MovieTab(session, onSearch, onOpenMovie)
                MainTab.SERIES ->
                    SeriesTab(session, onSearch, onOpenSeries)
                MainTab.SETTINGS ->
                    SettingsTab(session, onRefresh, onSourceSwitch, onLogout,
                        onOpenCats = { showCats = true })
            }
        }
        // yuzen cam alt menu (opak + cizgili)
        Row(
            Modifier.align(Alignment.BottomCenter)
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0xF214141D))
                .border(1.dp, PLine, RoundedCornerShape(30.dp))
                .padding(8.dp)
        ) {
            MainTab.entries.forEach { t ->
                val on = tab == t
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                        .background(if (on) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                        .clickableNoRipple { setTab(t) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(t.icon, null, tint = if (on) Color.White else PTx2,
                        modifier = Modifier.size(23.dp))
                    Text(t.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = if (on) Color.White else PTx2)
                }
            }
        }
        // Kategori yonetimi: alt menunun ustunu kaplayan tam ekran sayfa
        if (showCats) {
            CategoriesScreen(session = session, onClose = { showCats = false })
        }
    }
}

@Composable
private fun HomeHeader(
    sourceName: String,
    typeLabel: String,
    onSourceSwitch: () -> Unit,
    onSearch: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(20.dp, 14.dp, 20.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("Portio", fontSize = 26.sp, fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp, modifier = Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(99.dp)).background(PGlass)
                .clickableNoRipple(onSourceSwitch)
                .padding(horizontal = 14.dp, vertical = 0.dp).height(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(POk))
            Spacer(Modifier.width(6.dp))
            Text("$typeLabel · $sourceName", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp))
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(PGlass)
                .clickableNoRipple({ onSearch("all") }),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Search, "Ara", tint = Color.White, modifier = Modifier.size(18.dp)) }
    }
}

// ==================== KESFET ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscoverTab(
    session: Session,
    onSourceSwitch: () -> Unit,
    onSearch: (String) -> Unit,
    onPlayChannel: (StalkerChannel, String, String?, Map<String, String>, List<StalkerChannel>, Int) -> Unit,
    onOpenMovie: (StalkerChannel) -> Unit,
    onOpenSeries: (SeriesEntry) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val favs by FavoritesStore.favsFlow(ctx).collectAsState(initial = emptySet())
    val recents by FavoritesStore.recentFlow(ctx).collectAsState(initial = emptyList())
    val resumeMap by FavoritesStore.resumeFlow(ctx).collectAsState(initial = emptyMap())
    val hidden by FavoritesStore.hiddenCatsFlow(ctx).collectAsState(initial = emptySet())
    // Gizli kategoriler Ana Sayfa raflarinda da yok: gorunur kopya uzerinden calisilir
    val vis = remember(session, hidden) {
        session.copy(
            channels = session.channels.filter { !isCatHidden(hidden, "live", it.genre) },
            movies = session.movies.filter { !isCatHidden(hidden, "movie", it.genre) },
            series = session.series.filter { !isCatHidden(hidden, "series", it.category) }
        )
    }
    var busy by remember { mutableStateOf<String?>(null) }
    var glowColor by remember { mutableStateOf(Color(0xFF4B35D6)) }
    val glowAnim by animateColorAsState(glowColor, animationSpec = tween(800), label = "homeGlow")

    fun openLive(ch: StalkerChannel) {
        scope.launch {
            busy = ch.id
            try {
                if (session.stalkerUrl.isNotEmpty()) {
                    val sc = StalkerClient(session.stalkerUrl, session.stalkerMac)
                    val url = sc.createLink(ch.cmd)
                    if (url.isNotBlank()) onPlayChannel(ch, url, null, sc.streamHeaders(),
                        vis.channels, vis.channels.indexOfFirst { it.id == ch.id })
                } else {
                    val headers = if (session.xServer.isNotBlank())
                        mapOf("Referer" to session.xServer.trimEnd('/') + "/") else emptyMap()
                    val alt = if (ch.cmd.endsWith(".m3u8")) ch.cmd.dropLast(5) + ".ts" else null
                    onPlayChannel(ch, ch.cmd, alt, headers,
                        vis.channels, vis.channels.indexOfFirst { it.id == ch.id })
                }
            } finally { busy = null }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B12))) {
        FullGlow(glowAnim)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp)
        ) {
        item {
            HomeHeader(
                sourceName = session.sourceName.ifBlank { session.label },
                typeLabel = when {
                    session.stalkerUrl.isNotBlank() -> "Portal"
                    session.xServer.isNotBlank() -> "Xtream"
                    else -> "M3U"
                },
                onSourceSwitch = onSourceSwitch, onSearch = onSearch
            )
        }
        item {
            HeroSlider(vis, busyId = busy,
                onGlow = { glowColor = it },
                onOpenMovie = onOpenMovie, onOpenSeries = onOpenSeries,
                onOpenLive = { openLive(it) })
        }

        if (recents.isNotEmpty()) {
            item {
                SectionHead("İzlemeye devam et", 0, "Tümü", onAction = {})
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recents.take(10), key = { it.id }) { r ->
                        val ri = resumeMap[r.id]
                        val prog = if (ri != null && ri.durMs > 0) ri.posMs.toFloat() / ri.durMs else 0f
                        val sub = when {
                            ri?.kind == "episode" -> "${ri.seriesTitle} · ${remainingText(ri.posMs, ri.durMs)}"
                            ri != null && ri.durMs > 0 -> "Film · ${remainingText(ri.posMs, ri.durMs)}"
                            else -> r.genre
                        }
                        ContinueCard(r.name, sub, r.logo, prog) {
                            val ch = vis.channels.find { it.id == r.id }
                                ?: vis.movies.find { it.id == r.id } ?: r
                            if (vis.movies.any { it.id == ch.id }) onOpenMovie(ch) else openLive(ch)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(18.dp)) }
        if (vis.channels.isNotEmpty()) {
            item {
                SectionHead("Şimdi canlı", vis.channels.size, "Rehber", onAction = {})
                LiveRail(vis, onPlay = { openLive(it) }, busyId = busy)
            }
        }

        item { Spacer(Modifier.height(18.dp)) }
        if (vis.movies.isNotEmpty()) {
            item {
                SectionHead("Yeni filmler", vis.movies.size, "Tümü", onAction = {})
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(vis.movies.take(10), key = { it.id }) { m ->
                        PosterCard128(m.name, m.logo) { onOpenMovie(m) }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(18.dp)) }
        if (vis.series.isNotEmpty()) {
            item {
                SectionHead("Popüler diziler", vis.series.size, "Tümü", onAction = {})
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(vis.series.take(10), key = { it.id }) { s ->
                        PosterCard128(s.name, s.cover) { onOpenSeries(s) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ==================== HERO SLIDER ====================

@Composable
private fun FullGlow(color: Color) {
    Box(
        Modifier.fillMaxWidth().height(560.dp)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = 0.5f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                        radius = size.width * 1.1f
                    )
                )
            }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroSlider(
    session: Session,
    busyId: String?,
    onGlow: (Color) -> Unit,
    onOpenMovie: (StalkerChannel) -> Unit,
    onOpenSeries: (SeriesEntry) -> Unit,
    onOpenLive: (StalkerChannel) -> Unit
) {
    data class Slide(
        val badge: String, val title: String, val meta: String,
        val cta: String, val key: String, val art: String, val open: () -> Unit
    )
    val slides = remember(session) {
        val out = mutableListOf<Slide>()
        session.movies.take(2).forEach { m ->
            out.add(Slide("Yeni eklendi", m.name, "Film • ${m.genre}",
                "Oynat", m.id, m.logo, { onOpenMovie(m) }))
        }
        session.series.take(2).forEach { s ->
            val n = s.episodes.size
            out.add(Slide("Dizi", s.name, "${s.category} • $n bölüm",
                "Oynat", s.id, s.cover, { onOpenSeries(s) }))
        }
        session.channels.take(1).forEach { c ->
            out.add(Slide("Canlı", c.name, "${c.genre} • Şimdi",
                "Canlı izle", c.id, c.logo, { onOpenLive(c) }))
        }
        out.take(5)
    }
    if (slides.isEmpty()) return
    val glowPalette = remember {
        listOf(
            Color(0xFF4B35D6), Color(0xFFFF7A45), Color(0xFF18C6B0),
            Color(0xFFF2B84B), Color(0xFFE84A8F), Color(0xFF4AA3FF)
        )
    }
    val pager = rememberPagerState(pageCount = { slides.size })
    LaunchedEffect(pager.currentPage, slides.size) {
        if (slides.isNotEmpty()) {
            onGlow(glowPalette[kotlin.math.abs(slides[pager.currentPage].key.hashCode()) % glowPalette.size])
        }
    }
    LaunchedEffect(pager, slides.size) {
        while (slides.size > 1) {
            try {
                delay(6000)
                pager.animateScrollToPage((pager.currentPage + 1) % slides.size)
            } catch (_: Exception) { break }
        }
    }
    Column {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp) { i ->
            val s = slides[i]
            Box(
                Modifier.fillMaxWidth().height(470.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(tileBrush(s.key))
                    .clickableNoRipple { s.open() }
            ) {
                if (s.art.isNotBlank()) {
                    AsyncImage(model = s.art, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xEB05050C)))
                ))
                Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(11.dp, 5.dp)) {
                        Text(s.badge, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(s.title, fontSize = 28.sp, lineHeight = 30.sp,
                        fontWeight = FontWeight.ExtraBold, color = Color.White,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    Text(s.meta, fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f))
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { s.open() },
                            shape = RoundedCornerShape(99.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Text("▶  ${s.cta}", color = Color.Black, fontSize = 16.sp,
                                fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.size(50.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center) {
                            Text("+", color = Color.White, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.Center) {
            repeat(slides.size) { i ->
                Box(Modifier.padding(3.dp)
                    .then(
                        if (i == pager.currentPage) Modifier.width(20.dp).height(6.dp)
                        else Modifier.size(6.dp)
                    )
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        if (i == pager.currentPage) Color.White
                        else Color.White.copy(alpha = 0.45f)
                    ))
            }
        }
    }
}

// ==================== CANLI RAFI (EPG'li) ====================

@Composable
private fun LiveRail(
    session: Session,
    onPlay: (StalkerChannel) -> Unit,
    busyId: String?
) {
    val ctx = LocalContext.current
    var epg by remember { mutableStateOf<Map<String, EpgEntry>>(emptyMap()) }
    // Ana sayfa rafi: sadece kaynak EPG (internet yok). Arka planda liste indirip
    // isitma yapmaz; rehber/oyuncu internet yedegini kullanir.
    LaunchedEffect(session.sourceId) {
        try {
            val sem = kotlinx.coroutines.sync.Semaphore(3)
            coroutineScope {
                session.channels.take(6).map { ch ->
                    async(Dispatchers.IO) {
                        sem.acquire()
                        try {
                            val e = try {
                                com.bayram.xqtvapp.data.EpgXml.lookupNow(ctx, session, ch, includeInternet = false)
                            } catch (_: Exception) { null }
                            ch.id to e
                        } finally { sem.release() }
                    }
                }.awaitAll().forEach { (id, e) ->
                    if (e != null) epg = epg + (id to e)
                }
            }
        } catch (_: Exception) { }
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(session.channels.take(15), key = { it.id }) { ch ->
            LiveCard(ch, epg[ch.id], busyId == ch.id) { onPlay(ch) }
        }
    }
}

@Composable
private fun LiveCard(ch: StalkerChannel, epg: EpgEntry?, busy: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF15151F)),
        border = androidx.compose.foundation.BorderStroke(1.dp, PLine),
        modifier = Modifier.width(210.dp).height(118.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ch.logo.isNotBlank()) {
                    AsyncImage(model = ch.logo, contentDescription = null,
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit)
                } else {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                        .background(tileBrush(ch.id)),
                        contentAlignment = Alignment.Center) {
                        Text(initialsOf(ch.name), color = Color.White,
                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(ch.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                LiveBadge()
            }
            Spacer(Modifier.height(6.dp))
            Text(epg?.title ?: ch.genre, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(epg?.range() ?: "", fontSize = 12.sp, color = PTx2,
                maxLines = 1, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
            } else if (epg != null) {
                ProgressLine(epg.progress(), color = PLive)
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun LiveBadge() {
    var blink by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1600)
            blink = !blink
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape)
            .background(PLive.copy(alpha = if (blink) 1f else 0.25f)))
        Spacer(Modifier.width(4.dp))
        Text("CANLI", color = PLive, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ==================== KARTLAR ====================

@Composable
fun ContinueCard(title: String, sub: String, logo: String, progress: Float, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.width(236.dp).height(132.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(Modifier.fillMaxSize().background(tileBrush(title))) {
            if (logo.isNotBlank()) {
                AsyncImage(model = logo, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))
            ))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub.isNotBlank()) {
                    Text(sub, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (progress > 0.02f) {
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(progress)
                }
            }
        }
    }
}

/** Kategori gizleme filtresi: hidden icinde "live:X" / "movie:X" / "series:X" anahtarlari. */
fun isCatHidden(hidden: Set<String>, kind: String, name: String) =
    hidden.contains("$kind:$name")

@Composable
fun CatChips(cats: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(20.dp, 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(cats) { c ->
            FilterChip(
                selected = selected == c,
                onClick = { onSelect(c) },
                label = { Text(c) },
                shape = RoundedCornerShape(99.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color.White,
                    selectedLabelColor = Color.Black
                )
            )
        }
    }
}

/** Kaydirmali ray (az ogeli listelerde ic ice lazy yerine: kasmayi onler). */
@Composable
fun RailRow(hPad: androidx.compose.ui.unit.Dp = 20.dp, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = hPad),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun FeatBanner(title: String, sub: String, tag: String, art: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(300.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(tileBrush(title))
            .clickableNoRipple(onClick)
    ) {
        if (art.isNotBlank()) {
            AsyncImage(model = art, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, Color(0xE605050C)))
        ))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Box(Modifier.clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .padding(11.dp, 5.dp)) {
                Text(tag, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun GridPosterCell(title: String, art: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickableNoRipple(onClick),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(16.dp))
            .background(tileBrush(title))) {
            if (art.isNotBlank()) {
                AsyncImage(model = art, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xBF000000)))
            ))
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
    }
}

@Composable
fun PosterCard128(title: String, logo: String, onClick: () -> Unit) {
    Column(Modifier.width(128.dp).clickableNoRipple(onClick),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(18.dp))
            .background(tileBrush(title))) {
            if (logo.isNotBlank()) {
                AsyncImage(model = logo, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))
            ))
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
        }
    }
}

@Composable
fun ChannelCard(ch: StalkerChannel, busy: Boolean, isFav: Boolean, onFav: () -> Unit, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF15151F)),
        border = androidx.compose.foundation.BorderStroke(1.dp, PLine),
        modifier = Modifier.height(186.dp)
    ) {
        Box(Modifier.padding(10.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    if (ch.logo.isNotBlank()) {
                        AsyncImage(model = ch.logo, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    } else {
                        Text(initialsOf(ch.name), fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold, color = PTx2)
                    }
                    if (busy) CircularProgressIndicator(Modifier.size(26.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(ch.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
                    modifier = Modifier.height(34.dp))
                Text(ch.genre, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp, color = PTx2)
            }
        }
    }
}

// ==================== SEKMELER ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieTab(session: Session, onSearch: (String) -> Unit, onOpenMovie: (StalkerChannel) -> Unit) {
    val ctx = LocalContext.current
    val hidden by FavoritesStore.hiddenCatsFlow(ctx).collectAsState(initial = emptySet())
    var cat by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("Tümü") }
    val cats = remember(session, hidden) {
        listOf("Tümü") + session.movies.map { it.genre }.distinct()
            .filter { !isCatHidden(hidden, "movie", it) }.sorted()
    }
    LaunchedEffect(cats, cat) {
        if (cat != "Tümü" && !cats.contains(cat)) cat = "Tümü"
    }
    val list = remember(session, cat, hidden) {
        session.movies.filter {
            (cat == "Tümü" || it.genre == cat) && !isCatHidden(hidden, "movie", it.genre)
        }
    }
    val feat = remember(list) { list.take(3) }
    val pager = rememberPagerState(pageCount = { feat.size.coerceAtLeast(1) })
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    // Sayfalama: ilk 30 poster, kaydirdikca +30 (60 yerine 30 -> ilk acilis 2x hizli, kasma az)
    var shown by remember(list) { mutableIntStateOf(30) }
    val lastVis = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    LaunchedEffect(lastVis, list.size) {
        if (lastVis >= shown - 10 && shown < list.size) shown = (shown + 30).coerceAtMost(list.size)
    }
    val shownList = remember(list, shown) { list.take(shown) }
    // "Tümü" modunda tur raflari: hepsini tek izgara yerine kesfet raflari
    val genreRails = remember(list) {
        if (list.size < 20) emptyList()
        else list.groupBy { it.genre }
            .filter { it.value.size >= 4 }
            .toList().sortedByDescending { it.second.size }.take(4)
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B12))) {
        // Sayfa basligi: sol kenar 20.dp (PAGE_H). Izgara ici basliklar hPad=0 kullanir,
        // izgara contentPadding 20.dp oldugu icin F harfiyle birebir hizalanir.
        Row(Modifier.fillMaxWidth().padding(20.dp, 18.dp, 20.dp, 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Filmler", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.5).sp)
                Text("${list.size} film", color = PTx2, fontSize = 14.sp)
            }
            Box(Modifier.size(38.dp).clip(CircleShape).background(PGlass)
                .clickableNoRipple({ onSearch("movie") }), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Search, "Ara", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        CatChips(cats, cat) { cat = it }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (feat.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column {
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { i ->
                            val f = feat[i % feat.size]
                            FeatBanner(title = f.name, sub = f.genre, tag = "Öne çıkan",
                                art = f.logo) { onOpenMovie(f) }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.Center) {
                            repeat(feat.size) { i ->
                                Box(Modifier.padding(3.dp)
                                    .then(if (i == pager.currentPage) Modifier.width(20.dp).height(6.dp)
                                    else Modifier.size(6.dp))
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (i == pager.currentPage) Color.White
                                    else Color.White.copy(alpha = 0.45f)))
                            }
                        }
                    }
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column {
                        // hPad=0 -> izgara kenarina yaslanir = "Filmler" F harfiyle ayni hiza
                        SectionHead("Yeni eklenenler", 0, hPad = 0.dp)
                        RailRow(hPad = 0.dp) {
                            list.take(10).forEach { m ->
                                Box(Modifier.width(128.dp)) {
                                    GridPosterCell(m.name, m.logo) { onOpenMovie(m) }
                                }
                            }
                        }
                    }
                }
                // Tur raflari (yalnizca "Tümü" seciliyken): tek dev izgara yerine kesfet
                if (cat == "Tümü") {
                    genreRails.forEach { (g, items) ->
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Column(Modifier.padding(top = 6.dp)) {
                                SectionHead(g, items.size, "Tümü", hPad = 0.dp,
                                    onAction = { cat = g })
                                RailRow(hPad = 0.dp) {
                                    items.take(10).forEach { m ->
                                        Box(Modifier.width(128.dp)) {
                                            GridPosterCell(m.name, m.logo) { onOpenMovie(m) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    // Izgara basligi da hizali: "Kesfet" F ile ayni dikeyde baslar
                    SectionHead(
                        if (cat == "Tümü") "Keşfet" else cat,
                        list.size, hPad = 0.dp
                    )
                }
            }
            items(shownList, key = { it.id }, contentType = { "poster" }) { m ->
                GridPosterCell(m.name, m.logo) { onOpenMovie(m) }
            }
            if (shown < list.size) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesTab(session: Session, onSearch: (String) -> Unit, onOpenSeries: (SeriesEntry) -> Unit) {
    val ctx = LocalContext.current
    val hidden by FavoritesStore.hiddenCatsFlow(ctx).collectAsState(initial = emptySet())
    var cat by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("Tümü") }
    val cats = remember(session, hidden) {
        listOf("Tümü") + session.series.map { it.category }.distinct()
            .filter { !isCatHidden(hidden, "series", it) }.sorted()
    }
    LaunchedEffect(cats, cat) {
        if (cat != "Tümü" && !cats.contains(cat)) cat = "Tümü"
    }
    val list = remember(session, cat, hidden) {
        session.series.filter {
            (cat == "Tümü" || it.category == cat) && !isCatHidden(hidden, "series", it.category)
        }
    }
    val feat = remember(list) { list.take(3) }
    val pager = rememberPagerState(pageCount = { feat.size.coerceAtLeast(1) })
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var shown by remember(list) { mutableIntStateOf(30) }
    val lastVis = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    LaunchedEffect(lastVis, list.size) {
        if (lastVis >= shown - 10 && shown < list.size) shown = (shown + 30).coerceAtMost(list.size)
    }
    val shownList = remember(list, shown) { list.take(shown) }
    val catRails = remember(list) {
        if (list.size < 12) emptyList()
        else list.groupBy { it.category }
            .filter { it.value.size >= 3 }
            .toList().sortedByDescending { it.second.size }.take(4)
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B12))) {
        Row(Modifier.fillMaxWidth().padding(20.dp, 18.dp, 20.dp, 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Diziler", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.5).sp)
                Text("${list.size} dizi", color = PTx2, fontSize = 14.sp)
            }
            Box(Modifier.size(38.dp).clip(CircleShape).background(PGlass)
                .clickableNoRipple({ onSearch("series") }), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Search, "Ara", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        CatChips(cats, cat) { cat = it }
        if (list.isEmpty()) {
            Text(
                if (session.series.isEmpty()) "Bu kaynakta dizi yok."
                else "Tüm kategoriler gizli.\nAyarlar > Kategoriler'den açabilirsin.",
                color = PTx2, modifier = Modifier.padding(20.dp)
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (feat.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column {
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { i ->
                            val s = feat[i % feat.size]
                            FeatBanner(title = s.name, sub = s.category, tag = "Yeni sezon",
                                art = s.cover) { onOpenSeries(s) }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.Center) {
                            repeat(feat.size) { i ->
                                Box(Modifier.padding(3.dp)
                                    .then(if (i == pager.currentPage) Modifier.width(20.dp).height(6.dp)
                                    else Modifier.size(6.dp))
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (i == pager.currentPage) Color.White
                                    else Color.White.copy(alpha = 0.45f)))
                            }
                        }
                    }
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column {
                        // hPad=0 -> "Diziler" D harfiyle ayni hiza
                        SectionHead("Yeni bölümler", 0, hPad = 0.dp)
                        RailRow(hPad = 0.dp) {
                            list.take(10).forEach { s ->
                                Box(Modifier.width(128.dp)) {
                                    GridPosterCell(s.name, s.cover) { onOpenSeries(s) }
                                }
                            }
                        }
                    }
                }
                if (cat == "Tümü") {
                    catRails.forEach { (g, items) ->
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Column(Modifier.padding(top = 6.dp)) {
                                SectionHead(g, items.size, "Tümü", hPad = 0.dp,
                                    onAction = { cat = g })
                                RailRow(hPad = 0.dp) {
                                    items.take(10).forEach { s ->
                                        Box(Modifier.width(128.dp)) {
                                            GridPosterCell(s.name, s.cover) { onOpenSeries(s) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    SectionHead(if (cat == "Tümü") "Keşfet" else cat, list.size, hPad = 0.dp)
                }
            }
            items(shownList, key = { it.id }, contentType = { "poster" }) { s ->
                GridPosterCell(s.name, s.cover) { onOpenSeries(s) }
            }
            if (shown < list.size) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(
    session: Session,
    onRefresh: () -> Unit,
    onSourceSwitch: () -> Unit,
    onLogout: () -> Unit,
    onOpenCats: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val autoplay by FavoritesStore.autoplayFlow(ctx).collectAsState(initial = true)
    val (audioPref, subPref) = FavoritesStore.trackPrefsFlow(ctx).collectAsState(initial = Pair("auto", "off")).value
    val defEpg by FavoritesStore.defaultEpgFlow(ctx).collectAsState(initial = "")
    val hiddenCats by FavoritesStore.hiddenCatsFlow(ctx).collectAsState(initial = emptySet())
    var confirmDelete by remember { mutableStateOf(false) }
    var avDialog by remember { mutableStateOf(false) }
    var epgDialog by remember { mutableStateOf(false) }
    var epgText by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFF0B0B12)),
        contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 110.dp)
    ) {
        item {
            Text("Ayarlar", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.5).sp)
            Spacer(Modifier.height(16.dp))
            SourceCard(
                name = session.sourceName.ifBlank { session.label },
                sub = "${when {
                    session.stalkerUrl.isNotBlank() -> "Portal"
                    session.xServer.isNotBlank() -> "Xtream Codes"
                    else -> "M3U"
                }} · ${session.channels.size} kanal · ${session.movies.size} film · ${session.series.size} dizi",
                tile = when {
                    session.stalkerUrl.isNotBlank() -> "PT"
                    session.xServer.isNotBlank() -> "XT"
                    else -> "M3U"
                },
                active = true, onClick = onSourceSwitch
            )
            Spacer(Modifier.height(12.dp))
        }
        item {
            SettingsRow("İçeriği yenile", "Sunucudan güncel listeyi çek") { onRefresh() }
            SettingsRow(
                "Otomatik oynat",
                if (autoplay) "Bölüm bitince sıradaki başlar" else "Kapalı",
                check = autoplay,
                onCheck = { scope.launch { FavoritesStore.setAutoplay(ctx, it) } }
            ) { }
            SettingsRow(
                "Varsayılan ses",
                when (audioPref) {
                    "tr" -> "Türkçe"
                    "en" -> "İngilizce"
                    else -> "Otomatik"
                }
            ) { avDialog = true }
            SettingsRow(
                "Varsayılan altyazı",
                when (subPref) {
                    "tr" -> "Türkçe"
                    "en" -> "İngilizce"
                    "auto" -> "Otomatik"
                    else -> "Kapalı"
                }
            ) { avDialog = true }
            SettingsRow(
                "Varsayılan EPG listesi",
                if (defEpg.isBlank()) "Kaynakta yoksa internetten dener" else defEpg.take(42) + "…"
            ) {
                epgText = defEpg
                epgDialog = true
            }
            SettingsRow("Kaynak değiştir", "Kayıtlı listeler") { onSourceSwitch() }
            SettingsRow(
                "Kategoriler",
                if (hiddenCats.isEmpty()) "Tümü görünür" else "${hiddenCats.size} kategori gizli"
            ) { onOpenCats() }
            if (!confirmDelete) {
                SettingsRow("Kaynağı sil", "Bu cihazdan kaldır", danger = true) {
                    confirmDelete = true
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    OutlinedButton(onClick = { confirmDelete = false },
                        modifier = Modifier.weight(1f)) { Text("Vazgeç") }
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PLive)
                    ) { Text("Evet, sil", color = Color.White) }
                }
            }
            if (epgDialog) {
                AlertDialog(
                    onDismissRequest = { epgDialog = false },
                    title = { Text("Varsayılan EPG listesi", fontSize = 18.sp) },
                    text = {
                        Column {
                            Text("Kaynağın kendi program bilgisi yoksa bu adresteki XMLTV listesi kullanılır.",
                                color = PTx2, fontSize = 13.sp)
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = epgText, onValueChange = { epgText = it },
                                placeholder = { Text("https://…/epg.xml") },
                                singleLine = false, minLines = 2,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                FavoritesStore.setDefaultEpg(ctx, epgText)
                                epgDialog = false
                            }
                        }) { Text("Kaydet") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            scope.launch {
                                FavoritesStore.setDefaultEpg(ctx, "")
                                epgDialog = false
                            }
                        }) { Text("Temizle") }
                    }
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("Portio v2.8.6 • Tüm yayınların tek yerde.",
                color = PTx2, fontSize = 12.sp)
            if (avDialog) {
                AlertDialog(
                    onDismissRequest = { avDialog = false },
                    title = { Text("Ses ve altyazı", fontSize = 18.sp) },
                    text = {
                        Column {
                            Text("Ses", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            AvPrefRow("Otomatik ses", audioPref == "auto") {
                                scope.launch { FavoritesStore.setTrackPrefs(ctx, "auto", subPref) }
                            }
                            AvPrefRow("Türkçe dublaj", audioPref == "tr") {
                                scope.launch { FavoritesStore.setTrackPrefs(ctx, "tr", subPref) }
                            }
                            AvPrefRow("Orijinal dil (İngilizce)", audioPref == "en") {
                                scope.launch { FavoritesStore.setTrackPrefs(ctx, "en", subPref) }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Altyazı", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            AvPrefRow("Kapalı", subPref == "off") {
                                scope.launch { FavoritesStore.setTrackPrefs(ctx, audioPref, "off") }
                            }
                            AvPrefRow("Otomatik", subPref == "auto") {
                                scope.launch { FavoritesStore.setTrackPrefs(ctx, audioPref, "auto") }
                            }
                            AvPrefRow("Türkçe", subPref == "tr") {
                                scope.launch { FavoritesStore.setTrackPrefs(ctx, audioPref, "tr") }
                            }
                            AvPrefRow("İngilizce", subPref == "en") {
                                scope.launch { FavoritesStore.setTrackPrefs(ctx, audioPref, "en") }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { avDialog = false }) { Text("Kapat") } }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(session: Session, onClose: () -> Unit) {
    BackHandler { onClose() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hidden by FavoritesStore.hiddenCatsFlow(ctx).collectAsState(initial = emptySet())
    val overrides by FavoritesStore.catCountryFlow(ctx).collectAsState(initial = emptyMap())
    var tab by remember { mutableIntStateOf(0) } // 0 canli, 1 film, 2 dizi
    var q by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(setOf<String>()) }
    var assign by remember { mutableStateOf<Triple<String, String, String>?>(null) } // tur, hamAd, etiket

    data class KindTab(val key: String, val title: String, val unit: String, val cats: List<Pair<String, Int>>)
    val tabs = remember(session) {
        listOf(
            KindTab("live", "Canlı TV", "kanal",
                session.channels.groupBy { it.genre }.map { (k, v) -> k to v.size }.sortedBy { it.first }),
            KindTab("movie", "Filmler", "film",
                session.movies.groupBy { it.genre }.map { (k, v) -> k to v.size }.sortedBy { it.first }),
            KindTab("series", "Diziler", "dizi",
                session.series.groupBy { it.category }.map { (k, v) -> k to v.size }.sortedBy { it.first })
        )
    }
    val allKeys = remember(tabs) {
        tabs.flatMap { t -> t.cats.map { (n, _) -> "${t.key}:$n" } }.toSet()
    }
    val groupsAll = remember(tabs, overrides) {
        tabs.map { t -> groupByCountry(t.key, t.cats, overrides) }
    }
    val cur = tabs[tab]
    // Sekme degisince ilk ulkeyi acik getir
    LaunchedEffect(tab) {
        open = groupsAll[tab].firstOrNull()?.code?.let { setOf(it) } ?: emptySet()
    }
    fun isOn(key: String) = !hidden.contains(key)

    // Arama: ulke adi/kodu ya da alt kategori tutarsa goster
    val qt = q.trim()
    val groups = remember(groupsAll, tab, qt) {
        val g = groupsAll[tab]
        if (qt.isBlank()) g
        else g.mapNotNull { cg ->
            val def = countryDef(cg.code)
            if ((def.trName + " " + def.code).contains(qt, true)) cg
            else {
                val subs = cg.subs.filter { s ->
                    s.label.contains(qt, true) || s.raws.any { it.contains(qt, true) }
                }
                if (subs.isEmpty()) null else cg.copy(subs = subs)
            }
        }
    }

    // Alt bar istatistikleri (3 sekme toplam): secili ulke + gorunur icerik
    val selCountries = remember(groupsAll, hidden) {
        groupsAll.flatMap { gl -> gl.filter { cg -> cg.subs.any { s -> s.keys.any { isOn(it) } } } }
            .map { it.code }.toSet()
    }
    val visTotals = remember(groupsAll, hidden, tabs) {
        tabs.mapIndexed { i, t ->
            groupsAll[i].sumOf { cg ->
                cg.subs.filter { s -> s.keys.any { isOn(it) } }.sumOf { it.count }
            } to t.unit
        }
    }

    fun toast(m: String) {
        Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B12))) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 180.dp)
        ) {
            item {
                Text("‹ Ayarlar", color = Color(0xFF4AA3FF), fontSize = 17.sp,
                    modifier = Modifier.clickableNoRipple(onClose).padding(vertical = 8.dp))
                Text("Ülke ve kategoriler", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.5).sp, modifier = Modifier.padding(4.dp, 4.dp, 4.dp, 8.dp))
                val totCats = tabs.sumOf { it.cats.size }
                Text(
                    "Kaynağında $totCats kategori var. Sadece izlemek istediklerini aç, " +
                        "gerisi uygulamada gizlenir.",
                    color = PTx2, fontSize = 14.sp, lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(14.dp))
                // 3'lu tab (Kanallar/Rehber segmesiyle ayni dil)
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(99.dp)).background(PGlass).padding(4.dp)
                ) {
                    tabs.forEachIndexed { i, t ->
                        Box(
                            Modifier.weight(1f).height(38.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (tab == i) Color.White else Color.Transparent)
                                .clickableNoRipple { tab = i },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(t.title, color = if (tab == i) Color.Black else PTx2,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = q, onValueChange = { q = it },
                    placeholder = { Text("Ülke veya kategori ara", color = PTx2, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, "Ara", tint = PTx2, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (q.isNotEmpty()) {
                            TextButton(onClick = { q = "" }) { Text("Temizle", color = PTx2, fontSize = 13.sp) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = PBg2, focusedContainerColor = PBg2,
                        unfocusedBorderColor = PLine, focusedBorderColor = PAcc1
                    )
                )
                Spacer(Modifier.height(12.dp))
                // Hizli cipler: Sadece ABD / Sadece Turkiye / Tumunu sec / Temizle
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quicks: List<Pair<String, () -> Unit>> = listOf(
                        "Sadece ABD" to { onlyCountry("US", groupsAll, allKeys, ctx, scope) },
                        "Sadece Türkiye" to { onlyCountry("TR", groupsAll, allKeys, ctx, scope) },
                        "Tümünü seç" to {
                            scope.launch { FavoritesStore.replaceHiddenCats(ctx, emptySet()) }
                        },
                        "Temizle" to {
                            scope.launch { FavoritesStore.replaceHiddenCats(ctx, allKeys) }
                        }
                    )
                    quicks.forEach { (label, fn) ->
                        OutlinedButton(
                            onClick = fn,
                            shape = RoundedCornerShape(99.dp),
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (groups.isEmpty()) {
                item {
                    Text("Eşleşen ülke veya kategori yok.", color = PTx2, fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 24.dp))
                }
            }
            groups.forEach { cg ->
                val def = countryDef(cg.code)
                val onSubs = cg.subs.count { s -> s.keys.all { isOn(it) } }
                val state = when {
                    onSubs == 0 -> 0
                    onSubs == cg.subs.size -> 2
                    else -> 1
                }
                val visCount = cg.subs.filter { s -> s.keys.any { isOn(it) } }.sumOf { it.count }
                item(key = cur.key + cg.code) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = PGlass),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PLine),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickableNoRipple {
                                        open = if (open.contains(cg.code)) open - cg.code else open + cg.code
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(def.flag, fontSize = 28.sp,
                                    modifier = Modifier.width(34.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(def.trName, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.3).sp)
                                    Text("$onSubs/${cg.subs.size} kategori · $visCount ${cur.unit}",
                                        color = PTx2, fontSize = 12.sp)
                                }
                                TriToggle(state) {
                                    scope.launch {
                                        val keys = cg.subs.flatMap { it.keys }.toSet()
                                        FavoritesStore.replaceHiddenCats(
                                            ctx,
                                            if (state == 2) hidden + keys else hidden - keys
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(if (open.contains(cg.code)) "﹀" else "›",
                                    color = PTx2, fontSize = 18.sp)
                            }
                            if (open.contains(cg.code)) {
                                HorizontalDivider(color = PLine)
                                cg.subs.forEach { s ->
                                    val subOn = s.keys.all { isOn(it) }
                                    Row(
                                        Modifier.fillMaxWidth().padding(14.dp, 11.dp, 14.dp, 11.dp)
                                            .padding(start = 46.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(s.label, fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium)
                                            Text(
                                                s.raws.joinToString(" · ").take(60),
                                                color = PTx2, fontSize = 11.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (cg.code == "XX") {
                                            TextButton(
                                                onClick = {
                                                    assign = Triple(cur.key, s.raws.first(), s.label)
                                                }
                                            ) { Text("Ülke ata", fontSize = 12.sp) }
                                        } else {
                                            Text("${s.count}", color = PTx2, fontSize = 13.sp,
                                                modifier = Modifier.padding(end = 10.dp))
                                        }
                                        Switch(
                                            checked = subOn,
                                            onCheckedChange = {
                                                scope.launch {
                                                    FavoritesStore.replaceHiddenCats(
                                                        ctx,
                                                        if (subOn) hidden + s.keys else hidden - s.keys.toSet()
                                                    )
                                                }
                                            }
                                        )
                                    }
                                    HorizontalDivider(color = PLine,
                                        modifier = Modifier.padding(start = 60.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        // Alt bar: istatistik + Uygula
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color(0xF214141D))
                .padding(18.dp, 14.dp, 18.dp, 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (selCountries.isEmpty()) "Hiçbir şey seçili değil"
                    else "${selCountries.size} ülke seçili",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    "Canlı ${visTotals.getOrNull(0)?.first ?: 0} · " +
                        "Film ${visTotals.getOrNull(1)?.first ?: 0} · " +
                        "Dizi ${visTotals.getOrNull(2)?.first ?: 0}",
                    color = PTx2, fontSize = 12.sp
                )
            }
            Button(
                onClick = {
                    if (selCountries.isEmpty()) toast("En az bir kategori seç")
                    else {
                        toast("Filtre uygulandı · ${selCountries.size} ülke")
                        onClose()
                    }
                },
                shape = RoundedCornerShape(99.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                modifier = Modifier.height(50.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 0.dp)
            ) { Text("Uygula", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
    // Ulke ata sayfasi
    if (assign != null) {
        val (kind, raw, label) = assign!!
        ModalBottomSheet(
            onDismissRequest = { assign = null },
            containerColor = PBg2,
            dragHandle = {
                Box(Modifier.padding(10.dp).width(40.dp).height(5.dp)
                    .clip(RoundedCornerShape(9.dp)).background(PLine))
            }
        ) {
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 24.dp)) {
                Text("Ülke ata", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("“$label” hangi ülkeye ait? Atama kaydedilir, liste yenilense de değişmez.",
                    color = PTx2, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                COUNTRIES.filter { it.code != "XX" }.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickableNoRipple {
                            scope.launch {
                                FavoritesStore.setCatCountry(ctx, kind, raw, c.code)
                                assign = null
                                toast("“$label” → ${c.trName}")
                            }
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(c.flag, fontSize = 24.sp, modifier = Modifier.width(36.dp))
                        Text(c.trName, fontSize = 16.sp)
                    }
                    HorizontalDivider(color = PLine)
                }
            }
        }
    }
}

/** "Sadece ABD / Sadece Turkiye": 3 sekmede birden yalnizca o ulke acik kalir. */
private fun onlyCountry(
    code: String,
    groupsAll: List<List<CountryGroup>>,
    allKeys: Set<String>,
    ctx: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch {
        val keep = groupsAll.flatMap { gl ->
            gl.firstOrNull { it.code == code }?.subs?.flatMap { it.keys } ?: emptyList()
        }.toSet()
        FavoritesStore.replaceHiddenCats(ctx, allKeys - keep)
    }
}

@Composable
private fun TriToggle(state: Int, onClick: () -> Unit) {
    // 0 kapali, 1 yarim, 2 acik (tasarimdaki yesil uc durumlu anahtar)
    val bg = when (state) {
        2 -> POk
        1 -> POk.copy(alpha = 0.5f)
        else -> Color(0xFF787880).copy(alpha = 0.55f)
    }
    val frac = when (state) {
        2 -> 1f
        1 -> 0.5f
        else -> 0f
    }
    Box(
        Modifier.size(46.dp, 28.dp).clip(CircleShape).background(bg)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.padding(start = 2.dp).offset(x = (18 * frac).dp)
                .size(24.dp).clip(CircleShape).background(Color.White)
        )
    }
}

@Composable
private fun AvPrefRow(label: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        RadioButton(selected = on, onClick = onClick)
    }
    HorizontalDivider(color = PLine)
}

@Composable
private fun SettingsRow(
    title: String,
    sub: String,
    danger: Boolean = false,
    check: Boolean? = null,
    onCheck: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PGlass),
        border = androidx.compose.foundation.BorderStroke(1.dp, PLine),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (danger) PLive else Color.White)
                Text(sub, fontSize = 13.sp, color = PTx2)
            }
            if (check != null && onCheck != null) {
                Switch(checked = check, onCheckedChange = onCheck)
            } else {
                Text("›", color = PTx2, fontSize = 20.sp)
            }
        }
    }
}

// ==================== ARAMA ====================

@Composable
fun SearchScreen(
    session: Session,
    filter: String = "all",
    onClose: () -> Unit,
    onPlayChannel: (StalkerChannel, String, String?, Map<String, String>, List<StalkerChannel>, Int) -> Unit,
    onOpenMovie: (StalkerChannel) -> Unit,
    onOpenSeries: (SeriesEntry) -> Unit
) {
    BackHandler { onClose() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hidden by FavoritesStore.hiddenCatsFlow(ctx).collectAsState(initial = emptySet())
    var q by remember { mutableStateOf("") }
    // Arama debounce: her harfte filtre + liste yeniden kurma kasmayi onler
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(q) {
        delay(300)
        debounced = q.trim()
    }
    var busy by remember { mutableStateOf<String?>(null) }
    var glowColor by remember { mutableStateOf(Color(0xFF4B35D6)) }
    val glowAnim by animateColorAsState(glowColor, animationSpec = tween(800), label = "homeGlow")

    fun openLive(ch: StalkerChannel) {
        scope.launch {
            busy = ch.id
            try {
                if (session.stalkerUrl.isNotEmpty()) {
                    val sc = StalkerClient(session.stalkerUrl, session.stalkerMac)
                    val url = sc.createLink(ch.cmd)
                    if (url.isNotBlank()) onPlayChannel(ch, url, null, sc.streamHeaders(),
                        session.channels, session.channels.indexOfFirst { it.id == ch.id })
                } else {
                    val headers = if (session.xServer.isNotBlank())
                        mapOf("Referer" to session.xServer.trimEnd('/') + "/") else emptyMap()
                    val url = ch.cmd
                    val alt = if (url.endsWith(".m3u8")) url.dropLast(5) + ".ts" else null
                    if (url.isNotBlank()) onPlayChannel(ch, url, alt, headers,
                        session.channels, session.channels.indexOfFirst { it.id == ch.id })
                }
            } finally { busy = null }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B12))) {
        Row(Modifier.fillMaxWidth().padding(20.dp, 18.dp, 20.dp, 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ara", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                if (filter != "all") {
                    Text(
                        when (filter) {
                            "live" -> "Yalnızca kanallar"
                            "movie" -> "Yalnızca filmler"
                            else -> "Yalnızca diziler"
                        },
                        color = PTx2, fontSize = 13.sp
                    )
                }
            }
            TextButton(onClick = onClose) { Text("Kapat", color = PTx2) }
        }
        OutlinedTextField(q, { q = it }, label = { Text("Kanal, film, dizi...") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(14.dp), singleLine = true)
        Spacer(Modifier.height(8.dp))
        if (debounced.isBlank()) {
            Text("Örn: spor, aksiyon, haber...", color = PTx2,
                modifier = Modifier.padding(20.dp))
            return@Column
        }
        // Filtreler remember'da: her recompose'da yeniden filtre yok
        val chs = remember(debounced, filter, session, hidden) {
            if (filter == "all" || filter == "live")
                session.channels.filter {
                    it.name.contains(debounced, true) && !isCatHidden(hidden, "live", it.genre)
                }.take(30) else emptyList()
        }
        val mvs = remember(debounced, filter, session, hidden) {
            if (filter == "all" || filter == "movie")
                session.movies.filter {
                    it.name.contains(debounced, true) && !isCatHidden(hidden, "movie", it.genre)
                }.take(30) else emptyList()
        }
        val srs = remember(debounced, filter, session, hidden) {
            if (filter == "all" || filter == "series")
                session.series.filter {
                    it.name.contains(debounced, true) && !isCatHidden(hidden, "series", it.category)
                }.take(20) else emptyList()
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 20.dp)) {
            if (chs.isNotEmpty()) {
                item { Text("Kanallar", fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(20.dp, 8.dp, 20.dp, 4.dp)) }
                items(chs, key = { "c" + it.id }) { ch ->
                    ListItem(
                        headlineContent = { Text(ch.name) },
                        supportingContent = { Text(ch.genre) },
                        leadingContent = {
                            if (ch.logo.isNotBlank()) AsyncImage(model = ch.logo,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                        },
                        trailingContent = {
                            if (busy == ch.id) CircularProgressIndicator(Modifier.size(22.dp))
                        },
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { openLive(ch) }
                    )
                }
            }
            if (mvs.isNotEmpty()) {
                item { Text("Filmler", fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(20.dp, 12.dp, 20.dp, 4.dp)) }
                items(mvs, key = { "m" + it.id }) { m ->
                    ListItem(
                        headlineContent = { Text(m.name) },
                        leadingContent = {
                            if (m.logo.isNotBlank()) AsyncImage(model = m.logo,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                        },
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onOpenMovie(m) }
                    )
                }
            }
            if (srs.isNotEmpty()) {
                item { Text("Diziler", fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(20.dp, 12.dp, 20.dp, 4.dp)) }
                items(srs, key = { "s" + it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        leadingContent = {
                            if (s.cover.isNotBlank()) AsyncImage(model = s.cover,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                        },
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onOpenSeries(s) }
                    )
                }
            }
        }
    }
}
