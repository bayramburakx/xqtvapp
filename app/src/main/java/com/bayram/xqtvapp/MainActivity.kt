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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.bayram.xqtvapp.data.M3uParser
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
private val Accent = Color(0xFFFFFFFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Bg, surface = Bg, surfaceVariant = Surface2,
                    primary = Accent, onBackground = Color.White, onSurface = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) { AppNav() }
            }
        }
    }
}

// ---------- Navigation ----------

data class Session(
    val label: String,
    val channels: List<StalkerChannel>,
    val vod: List<StalkerChannel>,
    val stalkerUrl: String = "",
    val stalkerMac: String = ""
)

@Composable
fun AppNav() {
    var session by remember { mutableStateOf<Session?>(null) }
    var playUrl by remember { mutableStateOf("") }
    var playTitle by remember { mutableStateOf("") }

    when {
        playUrl.isNotEmpty() -> PlayerScreen(streamUrl = playUrl, title = playTitle) {
            playUrl = ""
        }
        session == null -> LoginScreen(onDone = { session = it })
        else -> HomeScreen(
            session = session!!,
            onLogout = { session = null },
            onPlay = { ch, url -> playTitle = ch.name; playUrl = url }
        )
    }
}

// ---------- Login (3 kaynak) ----------

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

    Column(
        Modifier.fillMaxSize().background(Bg).padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
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
                OutlinedTextField(mUrl, { mUrl = it }, label = { Text("M3U URL (http://.../playlist.m3u8)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
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
                                    if (ch.isEmpty()) error = "Bağlandı ama liste boş (portal sayfalı yapı kullanıyor olabilir, tekrar dene)"
                                    else {
                                        val vod = c.getVod(3)
                                        ctx.dataStore.edit { it[KEY_S_URL] = sUrl.trim(); it[KEY_S_MAC] = sMac.trim() }
                                        onDone(Session("Stalker", ch, vod, sUrl.trim(), sMac.trim()))
                                    }
                                }
                            }
                            1 -> {
                                val x = XtreamClient(xServer.trim(), xUser.trim(), xPass.trim())
                                if (!x.login()) error = "Xtream: ${x.lastError.ifBlank { "giriş başarısız" }}"
                                else {
                                    val ch = x.liveStreams()
                                    val vod = x.vodStreams()
                                    if (ch.isEmpty() && vod.isEmpty()) error = "Giriş ok ama içerik boş"
                                    else {
                                        ctx.dataStore.edit {
                                            it[KEY_X_SERVER] = xServer.trim()
                                            it[KEY_X_USER] = xUser.trim()
                                            it[KEY_X_PASS] = xPass.trim()
                                        }
                                        onDone(Session("Xtream (${xUser.trim()})", ch, vod))
                                    }
                                }
                            }
                            2 -> {
                                val text = M3uParser.download(mUrl.trim())
                                val all = M3uParser.parse(text)
                                val ch = all.filter { it.genre != "VOD" }
                                val vod = all.filter { it.genre == "VOD" }
                                if (all.isEmpty()) error = "Listede içerik bulunamadı"
                                else {
                                    ctx.dataStore.edit { it[KEY_M_URL] = mUrl.trim() }
                                    onDone(Session("M3U", ch.ifEmpty { all }, vod))
                                }
                            }
                        }
                    } catch (e: Exception) { error = "Hata: ${e.message}" }
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !loading
        ) { Text(if (loading) "Yükleniyor..." else "Bağlan", fontSize = 16.sp) }
    }
}

// ---------- Apple TV tarzı ana ekran ----------

@Composable
fun HomeScreen(session: Session, onLogout: () -> Unit, onPlay: (StalkerChannel, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Tümü") }
    var resolving by remember { mutableStateOf<String?>(null) }

    val liveCats = remember(session) { listOf("Tümü") + session.channels.map { it.genre }.distinct().sorted() }
    val live = session.channels.filter {
        (cat == "Tümü" || it.genre == cat) && (query.isBlank() || it.name.contains(query, true))
    }
    val vodFiltered = session.vod.filter { query.isBlank() || it.name.contains(query, true) }
    val hero = live.firstOrNull()

    fun resolve(ch: StalkerChannel) {
        scope.launch {
            resolving = ch.id
            try {
                val url = if (session.stalkerUrl.isNotEmpty())
                    StalkerClient(session.stalkerUrl, session.stalkerMac).createLink(ch.cmd)
                else ch.cmd
                if (url.isNotBlank()) onPlay(ch, url)
            } finally { resolving = null }
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(Bg)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("XqTV", fontWeight = FontWeight.Black, fontSize = 26.sp)
                    Text(
                        "${session.label} • ${session.channels.size} kanal" +
                                (if (session.vod.isNotEmpty()) " • ${session.vod.size} film" else ""),
                        color = Color.Gray, fontSize = 12.sp
                    )
                }
                TextButton(onClick = onLogout) { Text("Çıkış", color = Color.Gray) }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Ara: kanal, film...") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))
        }

        // Hero (öne çıkan)
        if (hero != null && query.isBlank()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Surface2)
                        .clickable { resolve(hero) }.padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = hero.logo, contentDescription = null,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("ÖNE ÇIKAN", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(hero.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(hero.genre, color = Color.Gray, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { resolve(hero) }, enabled = resolving == null) {
                                Text(if (resolving == hero.id) "Açılıyor..." else "▶ Oynat")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // Kategori rayı
        item {
            Text("Kategoriler", Modifier.padding(start = 20.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            LazyRow(contentPadding = PaddingValues(20.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(liveCats) { c ->
                    FilterChip(
                        selected = cat == c, onClick = { cat = c },
                        label = { Text(c) }
                    )
                }
            }
        }

        // Kanal rayı
        item {
            Text("Canlı TV (${live.size})", Modifier.padding(start = 20.dp, top = 8.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (live.isEmpty()) Text("Bu filtrede sonuç yok.", Modifier.padding(20.dp), color = Color.Gray)
            LazyRow(contentPadding = PaddingValues(20.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(live.take(60)) { ch ->
                    TvCard(title = ch.name, subtitle = ch.genre, logo = ch.logo,
                        loading = resolving == ch.id, onClick = { resolve(ch) })
                }
            }
            if (live.size > 60) Text("+${live.size - 60} kanal daha — aramayı kullan", Modifier.padding(start = 20.dp), color = Color.Gray, fontSize = 12.sp)
        }

        // VOD rayı
        if (vodFiltered.isNotEmpty()) {
            item {
                Text("Filmler & Diziler (${vodFiltered.size})", Modifier.padding(start = 20.dp, top = 8.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                LazyRow(contentPadding = PaddingValues(20.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(vodFiltered.take(60)) { v ->
                        TvCard(title = v.name, subtitle = v.genre, logo = v.logo,
                            loading = resolving == v.id, poster = true, onClick = { resolve(v) })
                    }
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
fun TvCard(title: String, subtitle: String, logo: String, loading: Boolean, poster: Boolean = false, onClick: () -> Unit) {
    val w = if (poster) 140.dp else 190.dp
    val h = if (poster) 200.dp else 120.dp
    Card(
        modifier = Modifier.width(w).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface2)
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(h), contentAlignment = Alignment.Center) {
                if (logo.isNotBlank()) AsyncImage(
                    model = logo, contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit
                ) else Text(title.take(2).uppercase(), fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                if (loading) CircularProgressIndicator(Modifier.size(28.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun PlayerScreen(streamUrl: String, title: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val exo = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare(); playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { exo.release() } }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< Geri", color = Color.White) }
            Text(title, modifier = Modifier.padding(12.dp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AndroidView(factory = { PlayerView(it).apply { player = exo } },
            modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
