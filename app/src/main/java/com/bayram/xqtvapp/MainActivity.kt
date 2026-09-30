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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.bayram.xqtvapp.data.M3uParser
import com.bayram.xqtvapp.data.SeriesEntry
import com.bayram.xqtvapp.data.StalkerChannel
import com.bayram.xqtvapp.data.StalkerClient
import com.bayram.xqtvapp.data.XtreamClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val Context.dataStore by preferencesDataStore("xqtv")

val KEY_S_URL = stringPreferencesKey("stalker_url")
val KEY_S_MAC = stringPreferencesKey("stalker_mac")
val KEY_X_SERVER = stringPreferencesKey("xtream_server")
val KEY_X_USER = stringPreferencesKey("xtream_user")
val KEY_X_PASS = stringPreferencesKey("xtream_pass")
val KEY_M_URL = stringPreferencesKey("m3u_url")

private val Bg = Color(0xFF0B0E14)
private val Surface2 = Color(0xFF161A23)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Bg, surface = Bg, surfaceVariant = Surface2,
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
    var session by remember { mutableStateOf<Session?>(null) }
    var play by remember { mutableStateOf<PlayReq?>(null) }

    when {
        play != null -> PlayerScreen(req = play!!, onBack = { play = null })
        session == null -> LoginScreen(onDone = { session = it })
        else -> HomeScreen(
            session = session!!,
            onLogout = { session = null },
            onPlay = { title, url, alt -> play = PlayReq(title, url, alt) }
        )
    }
}

// ---------- Login ----------

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

    Column(Modifier.fillMaxSize().background(Bg).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("XqTV", fontSize = 44.sp, fontWeight = FontWeight.Black)
        Text("Stalker • Xtream • M3U", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        TabRow(selectedTabIndex = tab, containerColor = Bg) {
            listOf("Stalker", "Xtream", "M3U").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i; error = "" }, text = { Text(t) })
            }
        }
        Spacer(Modifier.height(16.dp))
        when (tab) {
            0 -> {
                OutlinedTextField(sUrl, { sUrl = it }, label = { Text("Portal URL (http://host:port/c/)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(sMac, { sMac = it.uppercase() }, label = { Text("MAC 00:1A:79:XX:XX:XX") }, modifier = Modifier.fillMaxWidth())
            }
            1 -> {
                OutlinedTextField(xServer, { xServer = it }, label = { Text("Server URL (http://host:port)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(xUser, { xUser = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(xPass, { xPass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
            }
            2 -> {
                OutlinedTextField(mUrl, { mUrl = it }, label = { Text("M3U URL") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (error.isNotEmpty()) Text(error, color = Color(0xFFFF8080), fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
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
                                        onDone(
                                            Session(
                                                "Xtream (${xUser.trim()})", ch, movies, series,
                                                xServer = xServer.trim(), xUser = xUser.trim(), xPass = xPass.trim()
                                            )
                                        )
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
            modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !loading
        ) { Text(if (loading) "Yükleniyor..." else "Bağlan", fontSize = 16.sp) }
    }
}

// ---------- Ana ekran: sol menü + ızgara ----------

private enum class Section(val title: String, val icon: ImageVector) {
    TV("Canlı TV", Icons.Filled.PlayArrow),
    MOVIES("Filmler", Icons.Filled.PlayArrow),
    SERIES("Diziler", Icons.Filled.PlayArrow),
    SEARCH("Ara", Icons.Filled.Search),
}

@Composable
fun HomeScreen(session: Session, onLogout: () -> Unit, onPlay: (String, String, String?) -> Unit) {
    var section by remember { mutableStateOf(if (session.channels.isNotEmpty()) Section.TV else Section.MOVIES) }
    var seriesDetail by remember { mutableStateOf<SeriesEntry?>(null) }

    Row(Modifier.fillMaxSize().background(Bg)) {
        NavigationRail(containerColor = Surface2) {
            Spacer(Modifier.height(12.dp))
            Section.entries.forEach { s ->
                val count = when (s) {
                    Section.TV -> session.channels.size
                    Section.MOVIES -> session.movies.size
                    Section.SERIES -> session.series.size
                    Section.SEARCH -> 0
                }
                NavigationRailItem(
                    selected = section == s && seriesDetail == null,
                    onClick = { seriesDetail = null; section = s },
                    icon = { Icon(s.icon, contentDescription = null) },
                    label = { Text(if (count > 0) "${s.title} ($count)" else s.title, fontSize = 10.sp) }
                )
            }
            Spacer(Modifier.weight(1f))
            NavigationRailItem(
                selected = false, onClick = onLogout,
                icon = { Icon(Icons.Filled.ExitToApp, contentDescription = null) },
                label = { Text("Çıkış", fontSize = 10.sp) }
            )
            Spacer(Modifier.height(12.dp))
        }

        Box(Modifier.weight(1f)) {
            when {
                seriesDetail != null -> SeriesDetailScreen(
                    entry = seriesDetail!!, session = session,
                    onBack = { seriesDetail = null },
                    onPlay = onPlay
                )
                section == Section.TV -> TvGrid(session, onPlay)
                section == Section.MOVIES -> MovieGrid(session, onPlay)
                section == Section.SERIES -> SeriesGrid(session, onOpen = { seriesDetail = it })
                section == Section.SEARCH -> SearchScreen(session, onPlay, onOpenSeries = { seriesDetail = it })
            }
        }
    }
}

@Composable
private fun Header(label: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 4.dp)) {
        Text("XqTV • $label", fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text(sub, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun TvGrid(session: Session, onPlay: (String, String, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var cat by remember { mutableStateOf("Tümü") }
    var q by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf("") }

    val cats = remember(session) { listOf("Tümü") + session.channels.map { it.genre }.distinct().sorted() }
    val list = session.channels.filter {
        (cat == "Tümü" || it.genre == cat) && (q.isBlank() || it.name.contains(q, true))
    }

    fun open(ch: StalkerChannel) {
        scope.launch {
            busy = ch.id; err = ""
            try {
                if (session.stalkerUrl.isNotEmpty()) {
                    val url = StalkerClient(session.stalkerUrl, session.stalkerMac).createLink(ch.cmd)
                    if (url.isBlank()) err = "Stream linki alınamadı"
                    else onPlay(ch.name, url, null)
                } else {
                    // Xtream/M3U: direkt URL. HLS basarisiz olursa TS alternatifi playerda sunulur.
                    val alt = if (ch.cmd.endsWith(".m3u8")) ch.cmd.dropLast(5) + ".ts" else null
                    onPlay(ch.name, ch.cmd, alt)
                }
            } catch (e: Exception) { err = "Açılamadı: ${e.message}" }
            busy = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header("Canlı TV", "${list.size} kanal • ${session.label}")
        OutlinedTextField(q, { q = it }, label = { Text("Kanal ara...") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        LazyRow(contentPadding = PaddingValues(20.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cats) { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }) }
        }
        if (err.isNotEmpty()) Text(err, color = Color(0xFFFF8080), modifier = Modifier.padding(horizontal = 20.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            items(list, key = { it.id }) { ch ->
                ChannelCard(ch, busy == ch.id) { open(ch) }
            }
        }
    }
}

@Composable
fun MovieGrid(session: Session, onPlay: (String, String, String?) -> Unit) {
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Tümü") }
    val cats = remember(session) { listOf("Tümü") + session.movies.map { it.genre }.distinct().sorted() }
    val list = session.movies.filter {
        (cat == "Tümü" || it.genre == cat) && (q.isBlank() || it.name.contains(q, true))
    }
    Column(Modifier.fillMaxSize()) {
        Header("Filmler", "${list.size} film • tamamı listelendi")
        OutlinedTextField(q, { q = it }, label = { Text("Film ara...") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        LazyRow(contentPadding = PaddingValues(20.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cats) { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }) }
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            items(list, key = { it.id }) { m ->
                PosterCard(m.name, m.genre, m.logo) { onPlay(m.name, m.cmd, null) }
            }
        }
    }
}

@Composable
fun SeriesGrid(session: Session, onOpen: (SeriesEntry) -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = session.series.filter { q.isBlank() || it.name.contains(q, true) }
    Column(Modifier.fillMaxSize()) {
        Header("Diziler", "${list.size} dizi")
        if (session.series.isEmpty()) {
            Text("Bu kaynakta dizi listesi yok (Stalker/M3U). Xtream ile giriş yap.", color = Color.Gray, modifier = Modifier.padding(20.dp))
            return@Column
        }
        OutlinedTextField(q, { q = it }, label = { Text("Dizi ara...") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            items(list, key = { it.id }) { s ->
                PosterCard(s.name, s.category, s.cover) { onOpen(s) }
            }
        }
    }
}

@Composable
fun SeriesDetailScreen(entry: SeriesEntry, session: Session, onBack: () -> Unit, onPlay: (String, String, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var full by remember { mutableStateOf<SeriesEntry?>(if (entry.episodes.isNotEmpty()) entry else null) }
    var season by remember { mutableIntStateOf(-1) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(entry.id) {
        if (full == null && session.xServer.isNotEmpty()) {
            loading = true
            try {
                val x = XtreamClient(session.xServer, session.xUser, session.xPass)
                full = x.seriesEpisodes(entry.id) ?: entry
                season = full?.episodes?.firstOrNull()?.season ?: -1
            } finally { loading = false }
        } else {
            season = entry.episodes.firstOrNull()?.season ?: -1
        }
    }

    val seasons = remember(full) { full?.episodes?.map { it.season }?.distinct()?.sorted() ?: emptyList() }
    val eps = remember(full, season) { full?.episodes?.filter { season == -1 || it.season == season } ?: emptyList() }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TextButton(onClick = onBack) { Text("< Diziler") }
            Row(Modifier.padding(20.dp)) {
                AsyncImage(model = entry.cover, contentDescription = null,
                    modifier = Modifier.width(140.dp).height(200.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(entry.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(entry.category, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (loading) "Bölümler yükleniyor..."
                        else if (full == null) "Bölüm bilgisi alınamadı"
                        else "${seasons.size} sezon • ${full!!.episodes.size} bölüm",
                        color = Color.Gray, fontSize = 13.sp
                    )
                }
            }
            if (seasons.size > 1) {
                LazyRow(contentPadding = PaddingValues(20.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons) { s ->
                        FilterChip(selected = season == s, onClick = { season = s }, label = { Text("Sezon $s") })
                    }
                }
            }
        }
        items(eps, key = { it.id }) { ep ->
            ListItem(
                headlineContent = { Text("S${ep.season}:B${ep.episode} • ${ep.title}") },
                supportingContent = { Text(ep.url.substringAfterLast(".").uppercase()) },
                trailingContent = {
                    TextButton(onClick = {
                        scope.launch { onPlay("${entry.name} ${ep.title}", ep.url, null) }
                    }) { Text("Oynat") }
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun SearchScreen(session: Session, onPlay: (String, String, String?) -> Unit, onOpenSeries: (SeriesEntry) -> Unit) {
    var q by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }

    fun openLive(ch: StalkerChannel) {
        scope.launch {
            busy = ch.id
            try {
                val url = if (session.stalkerUrl.isNotEmpty())
                    StalkerClient(session.stalkerUrl, session.stalkerMac).createLink(ch.cmd) else ch.cmd
                val alt = if (!session.stalkerUrl.isNotEmpty() && url.endsWith(".m3u8")) url.dropLast(5) + ".ts" else null
                if (url.isNotBlank()) onPlay(ch.name, url, alt)
            } finally { busy = null }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header("Ara", "Kanallar, filmler ve dizilerde ara")
        OutlinedTextField(q, { q = it }, label = { Text("Yazmaya başla...") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        if (q.isBlank()) {
            Text("Aramak için yukarı yaz.", color = Color.Gray, modifier = Modifier.padding(20.dp))
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
                        headlineContent = { Text(ch.name) },
                        supportingContent = { Text(ch.genre) },
                        leadingContent = { AsyncImage(model = ch.logo, contentDescription = null, modifier = Modifier.size(44.dp)) },
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
                        leadingContent = { AsyncImage(model = m.logo, contentDescription = null, modifier = Modifier.size(44.dp)) },
                        modifier = Modifier.clickable { onPlay(m.name, m.cmd, null) }
                    )
                }
            }
            if (srs.isNotEmpty()) {
                item { Text("Diziler", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
                items(srs, key = { "s" + it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        leadingContent = { AsyncImage(model = s.cover, contentDescription = null, modifier = Modifier.size(44.dp)) },
                        modifier = Modifier.clickable { onOpenSeries(s) }
                    )
                }
            }
        }
    }
}

// ---------- Kartlar ----------

@Composable
fun ChannelCard(ch: StalkerChannel, busy: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface2)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                if (ch.logo.isNotBlank()) AsyncImage(model = ch.logo, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text(ch.name.take(2).uppercase(), fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                if (busy) CircularProgressIndicator(Modifier.size(26.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(ch.genre, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun PosterCard(title: String, subtitle: String, logo: String, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface2)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                if (logo.isNotBlank()) AsyncImage(model = logo, contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                else Text(title.take(2).uppercase(), fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.Gray)
            }
            Spacer(Modifier.height(6.dp))
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

// ---------- Profesyonel player ----------

private val RESIZE_NAMES = listOf("Sığdır", "Doldur", "Yakınlaştır")
private val RESIZE_MODES = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
)

@Composable
fun PlayerScreen(req: PlayReq, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var currentUrl by remember { mutableStateOf(req.url) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var retryKey by remember { mutableIntStateOf(0) }
    var resizeIdx by remember { mutableIntStateOf(0) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val exo = remember(currentUrl, retryKey) {
        errorMsg = null; buffering = true
        try {
            ExoPlayer.Builder(ctx).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        buffering = state == Player.STATE_BUFFERING
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        buffering = false
                        errorMsg = "Oynatılamadı (${error.errorCodeName}): ${error.message}"
                    }
                })
                setMediaItem(MediaItem.fromUri(currentUrl))
                prepare(); playWhenReady = true
            }
        } catch (e: Exception) {
            errorMsg = "Player kurulamadı: ${e.message}"
            buffering = false
            null
        }
    }
    DisposableEffect(exo) { onDispose { exo?.release() } }

    // HLS <-> TS gecisi mevcut playerView'e uygulanir
    LaunchedEffect(resizeIdx) { playerView?.resizeMode = RESIZE_MODES[resizeIdx] }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< Geri", color = Color.White) }
            Text(req.title, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (req.altUrl != null) {
                val isTs = currentUrl.endsWith(".ts")
                TextButton(onClick = {
                    currentUrl = if (isTs) req.url else req.altUrl
                }) { Text(if (isTs) "HLS dene" else "TS dene", color = Color.White) }
            }
            TextButton(onClick = { resizeIdx = (resizeIdx + 1) % 3 }) {
                Text(RESIZE_NAMES[resizeIdx], color = Color.White)
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (exo != null) {
                AndroidView(
                    factory = { c -> PlayerView(c).also { it.player = exo; it.resizeMode = RESIZE_MODES[resizeIdx]; playerView = it } },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (buffering && errorMsg == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Yükleniyor...", color = Color.White)
                    }
                }
            }
            if (errorMsg != null) {
                Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Yayın açılamadı", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(errorMsg!!, color = Color.Gray, fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { retryKey++ }) { Text("Tekrar dene") }
                            if (req.altUrl != null) {
                                Button(onClick = {
                                    currentUrl = if (currentUrl == req.url) req.altUrl else req.url
                                    retryKey++
                                }) { Text(if (currentUrl == req.url) "TS ile dene" else "HLS ile dene") }
                            }
                        }
                    }
                }
            }
        }
        Text(
            (if (currentUrl.endsWith(".ts")) "TS" else "HLS") + " • " + RESIZE_NAMES[resizeIdx],
            color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(12.dp)
        )
    }
}
