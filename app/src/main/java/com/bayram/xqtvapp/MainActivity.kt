package com.bayram.xqtvapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.bayram.xqtvapp.data.FavoritesStore
import com.bayram.xqtvapp.data.M3uParser
import com.bayram.xqtvapp.data.SeriesEntry
import com.bayram.xqtvapp.data.StalkerChannel
import com.bayram.xqtvapp.data.StalkerClient
import com.bayram.xqtvapp.data.VodDetail
import com.bayram.xqtvapp.data.XtreamClient
import com.bayram.xqtvapp.ui.PlayerScreen
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

data class PlayReq(val title: String, val url: String, val altUrl: String?)

@Composable
fun AppNav() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<Session?>(null) }
    var play by remember { mutableStateOf<PlayReq?>(null) }
    var movieDetail by remember { mutableStateOf<StalkerChannel?>(null) }
    var seriesDetail by remember { mutableStateOf<SeriesEntry?>(null) }

    fun openPlay(ch: StalkerChannel, url: String, alt: String?) {
        scope.launch { FavoritesStore.pushRecent(ctx, ch) }
        play = PlayReq(ch.name, url, alt)
    }

    when {
        play != null -> PlayerScreen(req = play!!, onBack = { play = null })
        movieDetail != null -> MovieDetailScreen(
            movie = movieDetail!!, session = session!!,
            onBack = { movieDetail = null },
            onPlay = { url -> openPlay(movieDetail!!, url, null) }
        )
        seriesDetail != null -> SeriesDetailScreen(
            entry = seriesDetail!!, session = session!!,
            onBack = { seriesDetail = null },
            onPlay = { title, url -> openPlay(StalkerChannel(title, title, seriesDetail!!.cover, url, "Series"), url, null) }
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

        // HERO
        if (hero != null) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(20.dp, 8.dp)
                        .clip(RoundedCornerShape(22.dp)).background(Card2)
                        .clickable {
                            if (hero.genre == "VOD" || session.movies.any { it.id == hero.id }) onOpenMovie(hero)
                            else openLive(hero)
                        }
                ) {
                    AsyncImage(model = hero.logo, contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(210.dp), contentScale = ContentScale.Crop)
                    Box(
                        Modifier.fillMaxWidth().height(210.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD07090F))))
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                        Text("ÖNE ÇIKAN", color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text(hero.name, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(hero.genre, color = Color.LightGray, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    if (hero.genre == "VOD" || session.movies.any { it.id == hero.id }) onOpenMovie(hero)
                                    else openLive(hero)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.Black)
                                Text("Oynat", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            if (busy == hero.id) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterVertically))
                        }
                    }
                }
            }
        }

        // DEVAM ET
        if (recents.isNotEmpty()) {
            item {
                SectionTitle("İzlemeye devam et", recents.size)
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recents, key = { it.id }) { r ->
                        MiniCard(r.name, r.logo) {
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
fun MovieDetailScreen(movie: StalkerChannel, session: Session, onBack: () -> Unit, onPlay: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val favs by FavoritesStore.favsFlow(ctx).collectAsState(initial = emptySet())
    var detail by remember { mutableStateOf<VodDetail?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(movie.id) {
        if (session.xServer.isNotEmpty() && movie.id.startsWith("vod_")) {
            try {
                detail = XtreamClient(session.xServer, session.xUser, session.xPass)
                    .vodInfo(movie.id.removePrefix("vod_"))
            } catch (_: Exception) { }
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(Bg)) {
        item {
            Box(Modifier.fillMaxWidth()) {
                AsyncImage(model = detail?.cover?.ifBlank { movie.logo } ?: movie.logo,
                    contentDescription = null, modifier = Modifier.fillMaxWidth().height(260.dp),
                    contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxWidth().height(260.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Bg))))
                TextButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                    Text("< Geri", color = Color.White)
                }
            }
            Column(Modifier.padding(20.dp, 0.dp, 20.dp, 20.dp)) {
                Text(movie.name, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (detail?.rating?.isNotBlank() == true) {
                        Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                        Text(detail!!.rating, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    if (detail?.year?.isNotBlank() == true) MetaChip(detail!!.year)
                    if (detail?.duration?.isNotBlank() == true) MetaChip(detail!!.duration)
                    MetaChip(movie.genre)
                }
                if (detail?.genre?.isNotBlank() == true) {
                    Spacer(Modifier.height(6.dp))
                    Text(detail!!.genre, color = Color.Gray, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val url = if (session.stalkerUrl.isNotEmpty())
                                        StalkerClient(session.stalkerUrl, session.stalkerMac).createLink(movie.cmd)
                                    else movie.cmd
                                    if (url.isNotBlank()) onPlay(url)
                                } finally { busy = false }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).height(52.dp),
                        enabled = !busy
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.Black)
                        Text(if (busy) "Açılıyor..." else "Oynat", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { scope.launch { FavoritesStore.toggle(ctx, movie.id) } },
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.height(52.dp)
                    ) {
                        Icon(
                            if (favs.contains(movie.id)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            null,
                            tint = if (favs.contains(movie.id)) AccentRed else Color.White
                        )
                    }
                }
                if (detail?.plot?.isNotBlank() == true) {
                    Spacer(Modifier.height(14.dp))
                    Text("Konu", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(detail!!.plot, color = Color.LightGray, fontSize = 14.sp)
                }
                if (detail?.cast?.isNotBlank() == true) {
                    Spacer(Modifier.height(10.dp))
                    Text("Oyuncular", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(detail!!.cast, color = Color.Gray, fontSize = 13.sp)
                }
                if (detail?.director?.isNotBlank() == true) {
                    Spacer(Modifier.height(6.dp))
                    Text("Yönetmen: ${detail!!.director}", color = Color.Gray, fontSize = 13.sp)
                }
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
fun SeriesDetailScreen(entry: SeriesEntry, session: Session, onBack: () -> Unit, onPlay: (String, String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val favs by FavoritesStore.favsFlow(ctx).collectAsState(initial = emptySet())
    var full by remember { mutableStateOf<SeriesEntry?>(if (entry.episodes.isNotEmpty()) entry else null) }
    var season by remember { mutableIntStateOf(-1) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(entry.id) {
        if (full == null && session.xServer.isNotEmpty()) {
            loading = true
            try {
                full = XtreamClient(session.xServer, session.xUser, session.xPass).seriesEpisodes(entry.id) ?: entry
                season = full?.episodes?.firstOrNull()?.season ?: -1
            } finally { loading = false }
        } else season = entry.episodes.firstOrNull()?.season ?: -1
    }

    val seasons = remember(full) { full?.episodes?.map { it.season }?.distinct()?.sorted() ?: emptyList() }
    val eps = remember(full, season) { full?.episodes?.filter { season == -1 || it.season == season } ?: emptyList() }

    LazyColumn(Modifier.fillMaxSize().background(Bg)) {
        item {
            TextButton(onClick = onBack) { Text("< Diziler", color = Color.White) }
            Row(Modifier.padding(20.dp, 0.dp, 20.dp, 20.dp)) {
                AsyncImage(model = entry.cover, contentDescription = null,
                    modifier = Modifier.width(130.dp).height(190.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.name, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(entry.category, color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (loading) "Bölümler yükleniyor..."
                        else if (full == null) "Bölüm bilgisi alınamadı"
                        else "${seasons.size} sezon • ${full!!.episodes.size} bölüm",
                        color = Color.Gray, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { scope.launch { FavoritesStore.toggle(ctx, entry.id) } }) {
                        Icon(
                            if (favs.contains(entry.id)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            null,
                            tint = if (favs.contains(entry.id)) AccentRed else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (favs.contains(entry.id)) "Listemde" else "Listeye ekle", fontSize = 12.sp)
                    }
                }
            }
            if (seasons.size > 1) {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons) { s ->
                        FilterChip(selected = season == s, onClick = { season = s }, label = { Text("Sezon $s") })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        items(eps, key = { it.id }) { ep ->
            ListItem(
                headlineContent = { Text("S${ep.season}:B${ep.episode} • ${ep.title}") },
                supportingContent = { Text(ep.url.substringAfterLast(".").uppercase()) },
                trailingContent = {
                    TextButton(onClick = { onPlay("${entry.name} ${ep.title}", ep.url) }) { Text("Oynat") }
                }
            )
            HorizontalDivider()
        }
    }
}

// ==================== KARTLAR ====================

@Composable
fun ChannelCard(ch: StalkerChannel, busy: Boolean, isFav: Boolean, onFav: () -> Unit, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Card2)) {
        Box(Modifier.padding(10.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.Center) {
                    if (ch.logo.isNotBlank()) AsyncImage(model = ch.logo, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else Text(ch.name.take(2).uppercase(), fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                    if (busy) CircularProgressIndicator(Modifier.size(26.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
    Card(modifier = Modifier.width(150.dp).clickable(onClick = onClick), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Card2)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                if (logo.isNotBlank()) AsyncImage(model = logo, contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                else Text(title.take(2).uppercase(), fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.Gray)
            }
            Spacer(Modifier.height(6.dp))
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun MiniCard(title: String, logo: String, onClick: () -> Unit) {
    Card(modifier = Modifier.width(220.dp).clickable(onClick = onClick), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Card2)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (logo.isNotBlank()) AsyncImage(model = logo, contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
            else Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF232B45)), contentAlignment = Alignment.Center) {
                Text(title.take(1).uppercase(), fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.PlayArrow, null, tint = Color.Gray)
        }
    }
}
