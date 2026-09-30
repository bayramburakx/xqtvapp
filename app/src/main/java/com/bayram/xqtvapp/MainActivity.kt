package com.bayram.xqtvapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.bayram.xqtvapp.data.StalkerChannel
import com.bayram.xqtvapp.data.StalkerClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>.dataStore by preferencesDataStore("portal")

val KEY_URL = stringPreferencesKey("portal_url")
val KEY_MAC = stringPreferencesKey("portal_mac")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNav()
                }
            }
        }
    }
}

@Composable
fun AppNav() {
    var screen by remember { mutableStateOf("login") }
    var portalUrl by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf<List<StalkerChannel>>(emptyList()) }
    var currentCmd by remember { mutableStateOf("") }
    var currentName by remember { mutableStateOf("") }

    when (screen) {
        "login" -> PortalScreen(
            onConnect = { url, m, list ->
                portalUrl = url; mac = m; channels = list
                screen = "list"
            }
        )
        "list" -> ChannelListScreen(
            channels = channels,
            portalUrl = portalUrl,
            mac = mac,
            onPlay = { ch, streamUrl ->
                currentCmd = streamUrl; currentName = ch.name
                screen = "player"
            },
            onLogout = { screen = "login" }
        )
        "player" -> PlayerScreen(
            streamUrl = currentCmd,
            title = currentName,
            onBack = { screen = "list" }
        )
    }
}

@Composable
fun PortalScreen(onConnect: (String, String, List<StalkerChannel>) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val p = ctx.dataStore.data.first()
        url = p[KEY_URL] ?: ""
        mac = p[KEY_MAC] ?: ""
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
        Text("XqTv Stalker Player", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = url, onValueChange = { url = it },
            label = { Text("Portal URL (http://host:8080/c/)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = mac, onValueChange = { mac = it.uppercase() },
            label = { Text("MAC (00:1A:79:XX:XX:XX)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                scope.launch {
                    loading = true; error = ""
                    try {
                        val c = StalkerClient(url.trim(), mac.trim())
                        if (!c.handshake()) { error = "Handshake başarısız. URL/MAC kontrol et." }
                        else {
                            val list = c.getChannels()
                            if (list.isEmpty()) error = "Bağlandı ama kanal listesi boş."
                            else {
                                ctx.dataStore.edit { it[KEY_URL] = url.trim(); it[KEY_MAC] = mac.trim() }
                                onConnect(url.trim(), mac.trim(), list)
                            }
                        }
                    } catch (e: Exception) { error = "Hata: ${e.message}" }
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading && url.isNotBlank() && mac.isNotBlank()
        ) { Text(if (loading) "Bağlanıyor..." else "Bağlan ve Kanalları Getir") }
    }
}

@Composable
fun ChannelListScreen(
    channels: List<StalkerChannel>,
    portalUrl: String,
    mac: String,
    onPlay: (StalkerChannel, String) -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("All") }
    var loadingUrl by remember { mutableStateOf<String?>(null) }

    val genres = remember(channels) { listOf("All") + channels.map { it.genre }.distinct().sorted() }
    val filtered = channels.filter {
        (genre == "All" || it.genre == genre) && (query.isBlank() || it.name.contains(query, true))
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${channels.size} kanal", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onLogout) { Text("Çıkış") }
        }
        OutlinedTextField(value = query, onValueChange = { query = it },
            label = { Text("Ara") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        Row(Modifier.padding(8.dp)) {
            genres.take(6).forEach { g ->
                FilterChip(selected = genre == g, onClick = { genre = g },
                    label = { Text(g.take(12)) }, modifier = Modifier.padding(end = 4.dp))
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered) { ch ->
                ListItem(
                    headlineContent = { Text(ch.name) },
                    supportingContent = { Text(ch.genre) },
                    leadingContent = {
                        AsyncImage(model = ch.logo, contentDescription = null,
                            modifier = Modifier.size(48.dp), contentScale = ContentScale.Fit)
                    },
                    trailingContent = { if (loadingUrl == ch.id) CircularProgressIndicator(Modifier.size(24.dp)) },
                    modifier = Modifier.clickable(enabled = loadingUrl == null) {
                        scope.launch {
                            loadingUrl = ch.id
                            try {
                                val client = StalkerClient(portalUrl, mac)
                                val stream = client.createLink(ch.cmd)
                                if (stream.isNotBlank()) onPlay(ch, stream)
                            } finally { loadingUrl = null }
                        }
                    }
                )
                HorizontalDivider()
            }
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
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp)) {
            TextButton(onClick = onBack) { Text("< Geri") }
            Text(title, modifier = Modifier.padding(12.dp))
        }
        AndroidView(factory = { PlayerView(it).apply { player = exo } },
            modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
