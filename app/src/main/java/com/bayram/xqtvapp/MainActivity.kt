package com.bayram.xqtvapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import coil.compose.AsyncImage
import com.bayram.xqtvapp.data.EpisodeEntry
import com.bayram.xqtvapp.data.FavoritesStore
import com.bayram.xqtvapp.data.M3uParser
import com.bayram.xqtvapp.data.SeriesEntry
import com.bayram.xqtvapp.data.StalkerChannel
import com.bayram.xqtvapp.data.StalkerClient
import com.bayram.xqtvapp.data.VodDetail
import com.bayram.xqtvapp.data.XtreamClient
import com.bayram.xqtvapp.ui.PlayerScreen
import com.bayram.xqtvapp.ui.fmtMs
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

private val Bg = Color(0xFF07090F)
private val Card2 = Color(0xFF141927)
private val AccentRed = Color(0xFFE50914)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Bg, surface = Bg, surfaceVariant = Card2,
                    primary = Color.White, onBackground = Color.White, onSurface = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) { AppNav() }
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
    val xPass: String = ""
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
    val resumeId: String = ""
)

@Composable
fun AppNav() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<Session?>(null) }
    var play by remember { mutableStateOf<PlayReq?>(null) }
    var movieDetail by remember { mutableStateOf<StalkerChannel?>(null) }
    var seriesDetail by remember { mutableStateOf<SeriesEntry?>(null) }

    fun openPlay(ch: StalkerChannel, url: String, alt: String?, startMs: Long = 0L) {
        scope.launch { FavoritesStore.pushRecent(ctx, ch, kind = "live") }
        play = PlayReq(ch.name, url, alt, isLive = true, startMs = startMs, resumeId = ch.id)
    }

    fun openMoviePlay(movie: StalkerChannel, url: String, startMs: Long = 0L) {
        scope.launch { FavoritesStore.pushRecent(ctx, movie, kind = "movie") }
        play = PlayReq(movie.name, url, null, isLive = false, startMs = startMs, resumeId = movie.id)
    }

    fun openEpisode(seriesName: String, cover: String, episodes: List<EpisodeEntry>, idx: Int, startMs: Long = 0L) {
        val ep = episodes.getOrNull(idx) ?: return
        val title = "$seriesName • S${ep.season} B${ep.episode}"
        val key = "series_" + ep.id
        scope.launch {
            FavoritesStore.pushRecent(
                ctx,
                StalkerChannel(key, title, cover, ep.url, "Series"),
                kind = "episode", seriesTitle = seriesName, epIdx = idx
            )
        }
        play = PlayReq(title, ep.url, null, seriesTitle = seriesName, episodes = episodes,
            episodeIndex = idx, isLive = false, startMs = startMs, resumeId = key)
    }

    when {
        play != null -> PlayerScreen(req = play!!, onBack = { play = null })
        movieDetail != null -> MovieDetailScreen(
            movie = movieDetail!!, session = session!!,
            onBack = { movieDetail = null },
            onSelect = { movieDetail = it },
            onPlay = { url, ms -> openMoviePlay(movieDetail!!, url, ms) }
        )
        seriesDetail != null -> SeriesDetailScreen(
            entry = seriesDetail!!, session = session!!,
            onBack = { seriesDetail = null },
            onPlayEpisode = { name, cover, eps, idx, ms -> openEpisode(name, cover, eps, idx, ms) }
        )
        session == null -> LoginScreen(onDone = { session = it })
        else -> HomeScreen(
            session = session!!,
            onLogout = { session = null },
            onPlayChannel = { ch, url, alt -> openPlay(ch, url, alt) },
            onOpenMovie = { movieDetail = it },
            onOpenSeries = { seriesDetail = it }
        )
    }
}

// ==================== GİRİŞ ====================

@Composable
fun LoginScreen(onDone: (Session) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var sUrl by remember { mutableStateOf("") }
    var sMac by remember { mutableStateOf("") }
    var xServer by remember { mutableStateOf("") }
    var xUser by remember { mutableStateOf("") }
    var xPass by remember { mutableStateOf("") }
    var mUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val p = ctx.dataStore.data.first()
        sUrl = p[KEY_S_URL] ?: ""; sMac = p[KEY_S_MAC] ?: ""
        xServer = p[KEY_X_SERVER] ?: ""; xUser = p[KEY_X_USER] ?: ""; xPass = p[KEY_X_PASS] ?: ""
        mUrl = p[KEY_M_URL] ?: ""
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF1A2340), Bg)))
                .padding(28.dp, 48.dp, 28.dp, 20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(AccentRed),
                        contentAlignment = Alignment.Center
                    ) { Text("X", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("XqTV", fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Text("Canlı • Film • Dizi", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Stalker", "Xtream", "M3U").forEachIndexed { i, t ->
                    FilterChip(
                        selected = tab == i, onClick = { tab = i; error = "" },
                        label = { Text(t, modifier = Modifier.padding(4.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                0 -> {
                    OutlinedTextField(sUrl, { sUrl = it }, label = { Text("Portal URL") }, placeholder = { Text("http://host:port/c/") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(sMac, { sMac = it.uppercase() }, label = { Text("MAC adresi") }, placeholder = { Text("00:1A:79:XX:XX:XX") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                }
                1 -> {
                    OutlinedTextField(xServer, { xServer = it }, label = { Text("Server URL") }, placeholder = { Text("http://host:port") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(xUser, { xUser = it }, label = { Text("Kullanıcı") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
                        OutlinedTextField(xPass, { xPass = it }, label = { Text("Şifre") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
                    }
                }
                2 -> {
                    OutlinedTextField(mUrl, { mUrl = it }, label = { Text("M3U linki") }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(14.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            if (error.isNotEmpty()) {
                Text(error, color = Color(0xFFFF9A9A), fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x33FF5252)).padding(12.dp))
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = {
                    scope.launch {
                        loading = true; error = ""
                        try {
                            when (tab) {
                                0 -> {
                                    val c = StalkerClient(sUrl.trim(), sMac.trim())
                                    if (!c.handshake()) error = "Bağlanamadı: ${c.lastError.ifBlank { "URL/MAC kontrol et" }}"
                                    else {
                                        val ch = c.getChannels()
                                        if (ch.isEmpty()) error = "Bağlandı ama liste boş bulundu"
                                        else {
                                            val vod = c.getVod(5)
                                            ctx.dataStore.edit { it[KEY_S_URL] = sUrl.trim(); it[KEY_S_MAC] = sMac.trim() }
                                            onDone(Session("Stalker", ch, vod, emptyList(), sUrl.trim(), sMac.trim()))
                                        }
                                    }
                                }
                                1 -> {
                                    val x = XtreamClient(xServer.trim(), xUser.trim(), xPass.trim())
                                    if (!x.login()) error = "Xtream: ${x.lastError.ifBlank { "giriş başarısız" }}"
                                    else {
                                        val ch = x.liveStreams()
                                        val movies = x.vodStreams()
                                        val series = x.seriesList()
                                        if (ch.isEmpty() && movies.isEmpty() && series.isEmpty()) error = "Giriş ok ama içerik boş"
                                        else {
                                            ctx.dataStore.edit {
                                                it[KEY_X_SERVER] = xServer.trim()
                                                it[KEY_X_USER] = xUser.trim()
                                                it[KEY_X_PASS] = xPass.trim()
                                            }
                                            onDone(Session("Xtream (${xUser.trim()})", ch, movies, series, xServer = xServer.trim(), xUser = xUser.trim(), xPass = xPass.trim()))
                                        }
                                    }
                                }
                                2 -> {
                                    val text = M3uParser.download(mUrl.trim())
                                    val all = M3uParser.parse(text)
                                    if (all.isEmpty()) error = "Listede içerik bulunamadı"
                                    else {
                                        ctx.dataStore.edit { it[KEY_M_URL] = mUrl.trim() }
                                        onDone(Session("M3U", all.filter { it.genre != "VOD" }.ifEmpty { all }, all.filter { it.genre == "VOD" }, emptyList()))
                                    }
                                }
                            }
                        } catch (e: Exception) { error = "Hata: ${e.message}" }
                        loading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) { Text(if (loading) "Bağlanıyor..." else "Hemen İzle", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            Text("Bilgilerin sadece bu cihazda saklanır.", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

// ==================== ANA EKRAN ====================

private enum class MainTab(val title: String, val icon: ImageVector) {
    HOME("Keşfet", Icons.Filled.Home), TV("Canlı", Icons.Filled.Tv), MOVIES("Film", Icons.Filled.Movie), SERIES("Dizi", Icons.Filled.PlayArrow), SEARCH("Ara", Icons.Filled.Search)
}

@Composable
fun HomeScreen(
    session: Session,
    onLogout: () -> Unit,
    onPlayChannel: (StalkerChannel, String, String?) -> Unit,
    onOpenMovie: (StalkerChannel) -> Unit,
    onOpenSeries: (SeriesEntry) -> Unit
) {
    var mainTab by remember { mutableStateOf(MainTab.HOME) }
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Card2) {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = mainTab == t, onClick = { mainTab = t },
                        icon = { Icon(t.icon, null) }, label = { Text(t.title, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (mainTab) {
                MainTab.HOME -> DiscoverTab(session, onLogout, onPlayChannel, onOpenMovie, onOpenSeries)
                MainTab.TV -> TvTab(session, onPlayChannel)
                MainTab.MOVIES -> MovieTab(session, onOpenMovie)
                MainTab.SERIES -> SeriesTab(session, onOpenSeries)
                MainTab.SEARCH -> SearchTab(session, onPlayChannel, onOpenMovie, onOpenSeries)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroSlider(
    items: List<StalkerChannel>,
    busyId: String?,
    onOpen: (StalkerChannel) -> Unit
) {
    val pager = rememberPagerState(pageCount = { items.size })
    LaunchedEffect(pager, items.size) {
        while (items.size > 1) {
            try {
                delay(5000)
                pager.animateScrollToPage((pager.currentPage + 1) % items.size)
            } catch (_: Exception) { break }
        }
    }
    Column {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { i ->
            val h = items[i]
            Box(
                Modifier.fillMaxWidth().padding(20.dp, 8.dp, 20.dp, 0.dp)
                    .clip(RoundedCornerShape(22.dp)).background(Card2)
                    .clickable { onOpen(h) }
            ) {
                AsyncImage(model = h.logo, contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Crop)
                Box(
                    Modifier.fillMaxWidth().height(220.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD07090F))))
                )
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Text("ÖNE ÇIKAN • ${i + 1}/${items.size}", color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text(h.name, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(h.genre, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onOpen(h) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.Black)
                            Text("Oynat", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        if (busyId == h.id) {
                            Spacer(Modifier.width(10.dp))
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
        // nokta gostergesi
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(items.size) { i ->
                Box(
                    Modifier.padding(3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i == pager.currentPage) Color.White else Color.Gray)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f))
        if (count > 0) Text("$count", color = Color.Gray, fontSize = 13.sp)
    }
}

@Composable
fun DiscoverTab(
    session: Session, onLogout: () -> Unit,
    onPlayChannel: (StalkerChannel, String, String?) -> Unit,
    onOpenMovie: (StalkerChannel) -> Unit,
    onOpenSeries: (SeriesEntry) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val favs by FavoritesStore.favsFlow(ctx).collectAsState(initial = emptySet())
    val recents by FavoritesStore.recentFlow(ctx).collectAsState(initial = emptyList())
    val resumeMap by FavoritesStore.resumeFlow(ctx).collectAsState(initial = emptyMap())
    var busy by remember { mutableStateOf<String?>(null) }

    val favChannels = session.channels.filter { favs.contains(it.id) }
    val favMovies = session.movies.filter { favs.contains(it.id) }
    val hero = session.movies.firstOrNull() ?: session.channels.firstOrNull()

    fun openLive(ch: StalkerChannel) {
        scope.launch {
            busy = ch.id
            try {
                if (session.stalkerUrl.isNotEmpty()) {
                    val url = StalkerClient(session.stalkerUrl, session.stalkerMac).createLink(ch.cmd)
                    if (url.isNotBlank()) onPlayChannel(ch, url, null)
                } else {
                    val alt = if (ch.cmd.endsWith(".m3u8")) ch.cmd.dropLast(5) + ".ts" else null
                    onPlayChannel(ch, ch.cmd, alt)
                }
            } finally { busy = null }
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(Bg)) {
        item {
            Row(Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(AccentRed), contentAlignment = Alignment.Center) {
                    Text("X", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("XqTV", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text(session.label, color = Color.Gray, fontSize = 11.sp)
                }
                IconButton(onClick = onLogout) { Icon(Icons.Filled.ExitToApp, "Çıkış", tint = Color.Gray) }
            }
        }

        // HERO SLIDER (otomatik kayar vitrin)
        run {
            val featured = (session.movies.take(5) + session.channels.take(3)).take(6)
            if (featured.isNotEmpty()) {
                item {
                    HeroSlider(
                        items = featured,
                        busyId = busy,
                        onOpen = { h ->
                            if (h.genre == "VOD" || session.movies.any { it.id == h.id }) onOpenMovie(h)
                            else openLive(h)
                        }
                    )
                }
            }
        }

        // DEVAM ET
        if (recents.isNotEmpty()) {
            item {
                SectionTitle("İzlemeye devam et", recents.size)
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recents, key = { it.id }) { r ->
                        val ri = resumeMap[r.id]
                        val prog = if (ri != null && ri.durMs > 0) ri.posMs.toFloat() / ri.durMs else 0f
                        MiniCard(r.name, r.logo, progress = prog) {
                            val ch = session.channels.find { it.id == r.id }
                                ?: session.movies.find { it.id == r.id } ?: r
                            if (session.movies.any { it.id == ch.id }) onOpenMovie(ch) else openLive(ch)
                        }
                    }
                }
            }
        }

        // LİSTEM
        if (favChannels.isNotEmpty() || favMovies.isNotEmpty()) {
            item {
                SectionTitle("Listem", favChannels.size + favMovies.size)
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(favChannels, key = { it.id }) { ch -> MiniCard(ch.name, ch.logo) { openLive(ch) } }
                    items(favMovies, key = { it.id }) { m -> MiniCard(m.name, m.logo) { onOpenMovie(m) } }
                }
            }
        }

        // CANLI ÖNİZLEME
        if (session.channels.isNotEmpty()) {
            item {
                SectionTitle("Popüler kanallar", session.channels.size)
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(session.channels.take(15), key = { it.id }) { ch ->
                        ChannelCard(ch, busy == ch.id, favs.contains(ch.id),
                            onFav = { scope.launch { FavoritesStore.toggle(ctx, ch.id) } },
                            onClick = { openLive(ch) })
                    }
                }
            }
        }

        // FİLMLER
        if (session.movies.isNotEmpty()) {
            item {
                SectionTitle("Trend filmler", session.movies.size)
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(session.movies.take(15), key = { it.id }) { m ->
                        PosterCard(m.name, m.genre, m.logo) { onOpenMovie(m) }
                    }
                }
            }
        }

        // DİZİLER
        if (session.series.isNotEmpty()) {
            item {
                SectionTitle("Popüler diziler", session.series.size)
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(session.series.take(15), key = { it.id }) { s ->
                        PosterCard(s.name, s.category, s.cover) { onOpenSeries(s) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun TvTab(session: Session, onPlayChannel: (StalkerChannel, String, String?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cat by remember { mutableStateOf("Tümü") }
    var q by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    val favs by FavoritesStore.favsFlow(ctx).collectAsState(initial = emptySet())

    val cats = remember(session) { listOf("Tümü") + session.channels.map { it.genre }.distinct().sorted() }
    val list = session.channels.filter {
        (cat == "Tümü" || it.genre == cat) && (q.isBlank() || it.name.contains(q, true))
    }

    fun open(ch: StalkerChannel) {
        scope.launch {
            busy = ch.id
            try {
                if (session.stalkerUrl.isNotEmpty()) {
                    val url = StalkerClient(session.stalkerUrl, session.stalkerMac).createLink(ch.cmd)
                    if (url.isNotBlank()) onPlayChannel(ch, url, null)
                } else {
                    val alt = if (ch.cmd.endsWith(".m3u8")) ch.cmd.dropLast(5) + ".ts" else null
                    onPlayChannel(ch, ch.cmd, alt)
                }
            } finally { busy = null }
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        SectionTitle("Canlı TV", list.size)
        OutlinedTextField(q, { q = it }, label = { Text("Kanal ara...") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(14.dp))
        LazyRow(contentPadding = PaddingValues(20.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cats) { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }) }
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)) {
            items(list, key = { it.id }) { ch ->
                ChannelCard(ch, busy == ch.id, favs.contains(ch.id),
                    onFav = { scope.launch { FavoritesStore.toggle(ctx, ch.id) } },
                    onClick = { open(ch) })
            }
        }
    }
}

@Composable
fun MovieTab(session: Session, onOpenMovie: (StalkerChannel) -> Unit) {
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Tümü") }
    val cats = remember(session) { listOf("Tümü") + session.movies.map { it.genre }.distinct().sorted() }
    val list = session.movies.filter {
        (cat == "Tümü" || it.genre == cat) && (q.isBlank() || it.name.contains(q, true))
    }
    Column(Modifier.fillMaxSize().background(Bg)) {
        SectionTitle("Filmler", list.size)
        OutlinedTextField(q, { q = it }, label = { Text("Film ara...") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(14.dp))
        LazyRow(contentPadding = PaddingValues(20.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cats) { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }) }
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)) {
            items(list, key = { it.id }) { m -> PosterCard(m.name, m.genre, m.logo) { onOpenMovie(m) } }
        }
    }
}

@Composable
fun SeriesTab(session: Session, onOpenSeries: (SeriesEntry) -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = session.series.filter { q.isBlank() || it.name.contains(q, true) }
    Column(Modifier.fillMaxSize().background(Bg)) {
        SectionTitle("Diziler", list.size)
        if (session.series.isEmpty()) {
            Text("Bu kaynakta dizi yok. Xtream ile giriş yap.", color = Color.Gray, modifier = Modifier.padding(20.dp))
            return@Column
        }
        OutlinedTextField(q, { q = it }, label = { Text("Dizi ara...") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)) {
            items(list, key = { it.id }) { s -> PosterCard(s.name, s.category, s.cover) { onOpenSeries(s) } }
        }
    }
}

@Composable
fun SearchTab(
    session: Session,
    onPlayChannel: (StalkerChannel, String, String?) -> Unit,
    onOpenMovie: (StalkerChannel) -> Unit,
    onOpenSeries: (SeriesEntry) -> Unit
) {
    var q by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }

    fun openLive(ch: StalkerChannel) {
        scope.launch {
            busy = ch.id
            try {
                val url = if (session.stalkerUrl.isNotEmpty())
                    StalkerClient(session.stalkerUrl, session.stalkerMac).createLink(ch.cmd) else ch.cmd
                val alt = if (session.stalkerUrl.isEmpty() && url.endsWith(".m3u8")) url.dropLast(5) + ".ts" else null
                if (url.isNotBlank()) onPlayChannel(ch, url, alt)
            } finally { busy = null }
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        SectionTitle("Ara", 0)
        OutlinedTextField(q, { q = it }, label = { Text("Kanal, film, dizi...") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(8.dp))
        if (q.isBlank()) {
            Text("Örn: spor, aksiyon, haber...", color = Color.Gray, modifier = Modifier.padding(20.dp))
            return@Column
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp)) {
            val chs = session.channels.filter { it.name.contains(q, true) }.take(30)
            val mvs = session.movies.filter { it.name.contains(q, true) }.take(30)
            val srs = session.series.filter { it.name.contains(q, true) }.take(20)
            if (chs.isNotEmpty()) {
                item { Text("Kanallar", fontWeight = FontWeight.Bold) }
                items(chs, key = { "c" + it.id }) { ch ->
                    ListItem(
                        headlineContent = { Text(ch.name) }, supportingContent = { Text(ch.genre) },
                        leadingContent = { AsyncImage(model = ch.logo, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))) },
                        trailingContent = { if (busy == ch.id) CircularProgressIndicator(Modifier.size(22.dp)) },
                        modifier = Modifier.clickable { openLive(ch) }
                    )
                }
            }
            if (mvs.isNotEmpty()) {
                item { Text("Filmler", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
                items(mvs, key = { "m" + it.id }) { m ->
                    ListItem(
                        headlineContent = { Text(m.name) },
                        leadingContent = { AsyncImage(model = m.logo, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))) },
                        modifier = Modifier.clickable { onOpenMovie(m) }
                    )
                }
            }
            if (srs.isNotEmpty()) {
                item { Text("Diziler", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
                items(srs, key = { "s" + it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        leadingContent = { AsyncImage(model = s.cover, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))) },
                        modifier = Modifier.clickable { onOpenSeries(s) }
                    )
                }
            }
        }
    }
}

// ==================== DETAYLAR ====================

@Composable
fun MovieDetailScreen(movie: StalkerChannel, session: Session, onBack: () -> Unit, onSelect: (StalkerChannel) -> Unit, onPlay: (String, Long) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val favs by FavoritesStore.favsFlow(ctx).collectAsState(initial = emptySet())
    var detail by remember { mutableStateOf<VodDetail?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(movie.id) {
        detail = null
        if (session.xServer.isNotEmpty() && movie.id.startsWith("vod_")) {
            try {
                detail = XtreamClient(session.xServer, session.xUser, session.xPass)
                    .vodInfo(movie.id.removePrefix("vod_"))
            } catch (_: Exception) { }
        }
    }

    val similar = remember(movie.id, session) {
        session.movies.filter { it.id != movie.id && it.genre == movie.genre }.take(12)
            .ifEmpty { session.movies.filter { it.id != movie.id }.take(12) }
    }

    val resume by FavoritesStore.entryFlow(ctx, movie.id).collectAsState(initial = null)

    fun playNow(fromMs: Long = 0L) {
        scope.launch {
            busy = true
            try {
                val url = if (session.stalkerUrl.isNotEmpty())
                    StalkerClient(session.stalkerUrl, session.stalkerMac).createLink(movie.cmd)
                else movie.cmd
                if (url.isNotBlank()) onPlay(url, fromMs)
            } finally { busy = false }
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(Bg)) {
        item {
            // --- vitrin ---
            Box(Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = detail?.cover?.ifBlank { movie.logo } ?: movie.logo,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(340.dp),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxWidth().height(340.dp).background(
                        Brush.verticalGradient(
                            listOf(Color(0x55000000), Color.Transparent, Bg),
                            startY = 0f, endY = 900f
                        )
                    )
                )
                TextButton(onClick = onBack, modifier = Modifier.padding(10.dp)) {
                    Text("< Geri", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp, 12.dp)) {
                    Text("FİLM", color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(movie.name, fontSize = 30.sp, fontWeight = FontWeight.Black, lineHeight = 32.sp)
                }
            }
            // --- meta ---
            Column(Modifier.padding(20.dp, 4.dp, 20.dp, 0.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (detail?.rating?.isNotBlank() == true) {
                        Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(17.dp))
                        Text(detail!!.rating, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    if (detail?.year?.isNotBlank() == true) MetaChip(detail!!.year)
                    if (detail?.duration?.isNotBlank() == true) MetaChip(detail!!.duration)
                    MetaChip("HD")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    listOfNotNull(
                        movie.genre.takeIf { it.isNotBlank() },
                        detail?.genre?.takeIf { it.isNotBlank() }
                    ).distinct().joinToString(" • "),
                    color = Color.Gray, fontSize = 12.sp
                )
                Spacer(Modifier.height(14.dp))
                // --- aksiyonlar (Netflix tarzi) ---
                Button(
                    onClick = { playNow(if (resume?.hasValid() == true) resume!!.posMs else 0L) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !busy
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (busy) "Açılıyor..."
                        else if (resume?.hasValid() == true) "Devam Et (${fmtMs(resume!!.posMs)})"
                        else "Oynat",
                        color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (resume?.hasValid() == true) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { playNow(0L) },
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !busy
                    ) { Text("Baştan Oynat", fontSize = 13.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { scope.launch { FavoritesStore.toggle(ctx, movie.id) } },
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(
                            if (favs.contains(movie.id)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            null,
                            tint = if (favs.contains(movie.id)) AccentRed else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (favs.contains(movie.id)) "Listemde ✓" else "+ Listeye Ekle", fontSize = 13.sp)
                    }
                }
                if (detail?.plot?.isNotBlank() == true) {
                    Spacer(Modifier.height(16.dp))
                    Text(detail!!.plot, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, lineHeight = 20.sp)
                }
                if (detail?.cast?.isNotBlank() == true) {
                    Spacer(Modifier.height(10.dp))
                    Text("Oyuncular: ", color = Color.Gray, fontSize = 12.sp)
                    Text(detail!!.cast, color = Color.Gray, fontSize = 13.sp)
                }
                if (detail?.director?.isNotBlank() == true) {
                    Spacer(Modifier.height(4.dp))
                    Text("Yönetmen: ${detail!!.director}", color = Color.Gray, fontSize = 13.sp)
                }
            }
        }
        if (similar.isNotEmpty()) {
            item {
                Spacer(Modifier.height(18.dp))
                Text("Bunları da beğenebilirsin", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(8.dp))
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(similar, key = { it.id }) { m ->
                        PosterCard(m.name, m.genre, m.logo) { onSelect(m) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MetaChip(t: String) {
    Text(t, fontSize = 11.sp, color = Color.LightGray,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Card2).padding(6.dp, 3.dp))
}

@Composable
fun SeriesDetailScreen(
    entry: SeriesEntry,
    session: Session,
    onBack: () -> Unit,
    onPlayEpisode: (String, String, List<EpisodeEntry>, Int, Long) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val favs by FavoritesStore.favsFlow(ctx).collectAsState(initial = emptySet())
    val resumeMap by FavoritesStore.resumeFlow(ctx).collectAsState(initial = emptyMap())
    var full by remember { mutableStateOf<SeriesEntry?>(if (entry.episodes.isNotEmpty()) entry else null) }
    var season by remember { mutableIntStateOf(-1) }
    var loading by remember { mutableStateOf(false) }

    var resumeEpIdx by remember { mutableIntStateOf(-1) }
    var resumeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(entry.id) {
        full = if (entry.episodes.isNotEmpty()) entry else null
        if (full == null && session.xServer.isNotEmpty()) {
            loading = true
            try {
                full = XtreamClient(session.xServer, session.xUser, session.xPass).seriesEpisodes(entry.id) ?: entry
                season = full?.episodes?.firstOrNull()?.season ?: -1
            } finally { loading = false }
        } else season = entry.episodes.firstOrNull()?.season ?: -1
    }

    // kaldigi bolum: kayitli siraya gore ilk gecerli pozisyon
    LaunchedEffect(full) {
        val eps = full?.episodes ?: return@LaunchedEffect
        val idx = FavoritesStore.seriesResumeIdx(ctx, eps.map { "series_" + it.id })
        if (idx >= 0) {
            resumeEpIdx = idx
            val info = FavoritesStore.resumeFlow(ctx).first()[("series_" + eps[idx].id)]
            resumeMs = info?.posMs ?: 0L
            season = eps[idx].season
        }
    }

    val seasons = remember(full) { full?.episodes?.map { it.season }?.distinct()?.sorted() ?: emptyList() }
    val eps = remember(full, season) { full?.episodes?.filter { season == -1 || it.season == season } ?: emptyList() }
    val allEps = remember(full) { full?.episodes ?: emptyList() }

    LazyColumn(Modifier.fillMaxSize().background(Bg)) {
        item {
            // --- vitrin ---
            Box(Modifier.fillMaxWidth()) {
                AsyncImage(model = entry.cover, contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(300.dp), contentScale = ContentScale.Crop)
                Box(
                    Modifier.fillMaxWidth().height(300.dp).background(
                        Brush.verticalGradient(listOf(Color(0x55000000), Color.Transparent, Bg))
                    )
                )
                TextButton(onClick = onBack, modifier = Modifier.padding(10.dp)) {
                    Text("< Geri", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp, 12.dp)) {
                    Text("DİZİ • ${entry.category}", color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(entry.name, fontSize = 30.sp, fontWeight = FontWeight.Black, lineHeight = 32.sp)
                    Text(
                        if (loading) "Bölümler yükleniyor..."
                        else "${seasons.size} Sezon • ${allEps.size} Bölüm",
                        color = Color.LightGray, fontSize = 13.sp
                    )
                }
            }
            Column(Modifier.padding(20.dp, 4.dp, 20.dp, 0.dp)) {
                val resumeEp = allEps.getOrNull(resumeEpIdx)
                Button(
                    onClick = {
                        if (allEps.isEmpty()) return@Button
                        if (resumeEp != null) onPlayEpisode(entry.name, entry.cover, allEps, resumeEpIdx, resumeMs)
                        else onPlayEpisode(entry.name, entry.cover, allEps, 0, 0L)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !loading && allEps.isNotEmpty()
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (loading) "Yükleniyor..."
                        else if (resumeEp != null) "S${resumeEp.season} B${resumeEp.episode} • Devam Et (${fmtMs(resumeMs)})"
                        else "Oynat",
                        color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { scope.launch { FavoritesStore.toggle(ctx, entry.id) } },
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(
                        if (favs.contains(entry.id)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        null,
                        tint = if (favs.contains(entry.id)) AccentRed else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (favs.contains(entry.id)) "Listemde ✓" else "+ Listeye Ekle", fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                Text("Bölümler", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                if (seasons.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons) { s ->
                            FilterChip(selected = season == s, onClick = { season = s }, label = { Text("Sezon $s") })
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        items(eps, key = { it.id }) { ep ->
            val idx = allEps.indexOfFirst { it.id == ep.id }
            val epProg = resumeMap[("series_" + ep.id)]?.let { ri ->
                if (ri.durMs > 0) ri.posMs.toFloat() / ri.durMs else 0f
            } ?: 0f
            Card(
                modifier = Modifier.fillMaxWidth().height(108.dp).padding(horizontal = 20.dp, vertical = 5.dp)
                    .clickable { onPlayEpisode(entry.name, entry.cover, allEps, idx, 0L) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Card2)
            ) {
                Column(Modifier.padding(10.dp, 10.dp, 10.dp, 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text("${ep.episode}", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.Gray,
                            modifier = Modifier.width(38.dp))
                        AsyncImage(model = ep.cover.ifBlank { entry.cover }, contentDescription = null,
                            modifier = Modifier.width(118.dp).height(62.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("S${ep.season} • B${ep.episode}", color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(ep.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(ep.url.substringAfterLast(".").uppercase() + " • HD", color = Color.Gray, fontSize = 11.sp)
                        }
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(21.dp)).background(Color.White),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(24.dp))
                        }
                    }
                    if (epProg > 0.02f) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { epProg.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                            color = AccentRed, trackColor = Color(0xFF2A3040)
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ==================== KARTLAR ====================

@Composable
fun ChannelCard(ch: StalkerChannel, busy: Boolean, isFav: Boolean, onFav: () -> Unit, onClick: () -> Unit) {
    Card(modifier = Modifier.height(186.dp).clickable(onClick = onClick), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Card2)) {
        Box(Modifier.padding(10.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    if (ch.logo.isNotBlank()) AsyncImage(model = ch.logo, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else Text(ch.name.take(2).uppercase(), fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                    if (busy) CircularProgressIndicator(Modifier.size(26.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(ch.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, modifier = Modifier.height(34.dp))
                Text(ch.genre, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Color.Gray)
            }
            IconButton(onClick = onFav, modifier = Modifier.align(Alignment.TopEnd).size(30.dp)) {
                Icon(
                    if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                    tint = if (isFav) AccentRed else Color.Gray, modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun PosterCard(title: String, subtitle: String, logo: String, onClick: () -> Unit) {
    Card(modifier = Modifier.width(150.dp).height(300.dp).clickable(onClick = onClick), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Card2)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(196.dp), contentAlignment = Alignment.Center) {
                if (logo.isNotBlank()) AsyncImage(model = logo, contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                else Text(title.take(2).uppercase(), fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.Gray)
            }
            Spacer(Modifier.height(6.dp))
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, modifier = Modifier.height(34.dp))
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun MiniCard(title: String, logo: String, progress: Float = 0f, onClick: () -> Unit) {
    Card(modifier = Modifier.width(230.dp).height(76.dp).clickable(onClick = onClick), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Card2)) {
        Column(Modifier.padding(10.dp, 10.dp, 10.dp, 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (logo.isNotBlank()) AsyncImage(model = logo, contentDescription = null,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
                else Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF232B45)), contentAlignment = Alignment.Center) {
                    Text(title.take(1).uppercase(), fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(10.dp))
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.PlayArrow, null, tint = Color.Gray, modifier = Modifier.size(22.dp))
            }
            if (progress > 0.02f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = AccentRed, trackColor = Color(0xFF2A3040)
                )
            }
        }
    }
}
