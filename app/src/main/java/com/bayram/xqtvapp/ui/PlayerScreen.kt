package com.bayram.xqtvapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bayram.xqtvapp.PlayReq
import com.bayram.xqtvapp.data.EpisodeEntry
import kotlinx.coroutines.delay

private val NetflixRed = Color(0xFFE50914)
private val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.5f)
private val RESIZE_NAMES = listOf("Sığdır", "Doldur", "Zoom")
private val RESIZE_MODES = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
)

private data class TrackOpt(val groupIdx: Int, val trackIdx: Int, val label: String, val selected: Boolean)

fun fmtMs(ms: Long): String {
    if (ms < 0) return "00:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

/** Netflix tarzı player: kırmızı scrubber, sonraki bölüm, bölüm listesi, hız/altyazı/uyku. */
@Composable
fun PlayerScreen(req: PlayReq, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var currentUrl by remember { mutableStateOf(req.url) }
    var currentIdx by remember { mutableIntStateOf(req.episodeIndex) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }
    var resizeIdx by remember { mutableIntStateOf(0) }
    var speedIdx by remember { mutableIntStateOf(0) }
    var showUi by remember { mutableStateOf(true) }
    var uiTick by remember { mutableIntStateOf(0) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var showTracks by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var sleepMin by remember { mutableStateOf(0) }
    var trackTick by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(-1) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val hasSeries = req.episodes.isNotEmpty()
    val nextEp: EpisodeEntry? = if (hasSeries) req.episodes.getOrNull(currentIdx + 1) else null
    val displayTitle = if (hasSeries && currentIdx >= 0) {
        val ep = req.episodes.getOrNull(currentIdx)
        if (ep != null) "${req.seriesTitle} • S${ep.season} B${ep.episode}" else req.title
    } else req.title

    val exo = remember(currentUrl, retryKey) {
        errorMsg = null; buffering = true; playing = true; ended = false; countdown = -1
        try {
            ExoPlayer.Builder(ctx).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        buffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) errorMsg = null
                        if (state == Player.STATE_ENDED) {
                            playing = false; ended = true
                            if (nextEp != null) countdown = 10
                        }
                    }
                    override fun onIsPlayingChanged(v: Boolean) { playing = v }
                    override fun onPlayerError(error: PlaybackException) {
                        buffering = false; playing = false
                        errorMsg = "Oynatılamadı (${error.errorCodeName})"
                    }
                })
                setMediaItem(MediaItem.fromUri(currentUrl))
                setPlaybackSpeed(SPEEDS[speedIdx])
                prepare(); playWhenReady = true
            }
        } catch (e: Exception) {
            errorMsg = "Player kurulamadı: ${e.message}"
            buffering = false; playing = false
            null
        }
    }
    DisposableEffect(exo) { onDispose { exo?.release() } }

    LaunchedEffect(exo) {
        while (true) {
            pos = exo?.currentPosition ?: 0L
            dur = exo?.duration ?: 0L
            delay(500)
        }
    }
    LaunchedEffect(showUi, uiTick, playing) {
        if (showUi && playing && !ended) {
            delay(4000)
            showUi = false
        }
    }
    LaunchedEffect(sleepMin) {
        if (sleepMin > 0) {
            delay(sleepMin * 60_000L)
            exo?.pause()
            sleepMin = 0
        }
    }
    // sonraki bölüm geri sayımı
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        } else if (countdown == 0 && nextEp != null) {
            currentUrl = nextEp.url
            currentIdx++
            retryKey++
        }
    }

    val isLive = dur <= 0L
    val interaction = remember { MutableInteractionSource() }

    fun poke() { showUi = true; uiTick++ }
    fun playEpisode(idx: Int) {
        val ep = req.episodes.getOrNull(idx) ?: return
        currentIdx = idx
        currentUrl = ep.url
        retryKey++
    }
    fun switchFormat() {
        if (req.altUrl != null) {
            currentUrl = if (currentUrl == req.url) req.altUrl else req.url
            retryKey++
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = interaction, indication = null) { poke() }
    ) {
        if (exo != null) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).also {
                        it.player = exo
                        it.useController = false
                        it.resizeMode = RESIZE_MODES[resizeIdx]
                    }
                },
                update = { it.resizeMode = RESIZE_MODES[resizeIdx] },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (buffering && errorMsg == null && !ended) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NetflixRed, strokeWidth = 5.dp, modifier = Modifier.size(56.dp))
            }
        }

        // ---- üst bar ----
        if (showUi && !ended) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xDD000000), Color.Transparent)))
                    .padding(6.dp, 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Geri", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(displayTitle, color = Color.White, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                    if (hasSeries) Text(req.seriesTitle, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                }
                if (hasSeries) {
                    TextButton(onClick = { showEpisodes = true; poke() }) {
                        Text("Bölümler", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = { showTracks = true; trackTick++; poke() }) {
                    Text("Ses/Altyazı", color = Color.White, fontSize = 12.sp)
                }
                TextButton(onClick = { showMore = true; poke() }) {
                    Text("•••", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // ---- orta kontroller ----
        if (showUi && !buffering && errorMsg == null && !ended && exo != null) {
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLive) {
                    IconButton(onClick = { exo.seekBack(); poke() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.Replay10, "Geri 10", tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                }
                IconButton(
                    onClick = { if (playing) exo.pause() else exo.play(); poke() },
                    modifier = Modifier.size(78.dp).background(Color(0x66000000), CircleShape)
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null,
                        tint = Color.White, modifier = Modifier.size(52.dp)
                    )
                }
                if (!isLive) {
                    IconButton(onClick = { exo.seekForward(); poke() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.Forward10, "İleri 10", tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                }
            }
        }

        // ---- alt bar: Netflix kırmızı scrubber ----
        if (showUi && !ended) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                    .padding(16.dp, 10.dp, 16.dp, 14.dp)
            ) {
                if (isLive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("● CANLI", color = NetflixRed, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        if (req.altUrl != null) {
                            TextButton(onClick = { switchFormat(); poke() }) {
                                Text(if (currentUrl.endsWith(".ts")) "HLS dene" else "TS dene",
                                    color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtMs(pos), color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                            onValueChange = { f -> exo?.seekTo((f * dur).toLong()) },
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = NetflixRed, activeTrackColor = NetflixRed,
                                inactiveTrackColor = Color(0xFF4D4D4D)
                            )
                        )
                        Text(fmtMs(dur - pos) + " kaldı", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // ---- sonraki bölüm (dizi finali) ----
        if (ended && nextEp != null && errorMsg == null) {
            Box(
                Modifier.fillMaxSize().background(Color(0xEE000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sıradaki bölüm", color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "S${nextEp.season} • B${nextEp.episode} — ${nextEp.title}",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    if (countdown > 0) Text("$countdown sn içinde başlıyor...", color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { playEpisode(currentIdx + 1) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.Black)
                        Text("Hemen Oynat", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { countdown = -1 },
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                    ) { Text("Vazgeç", color = Color.White) }
                }
            }
        }

        // ---- hata ----
        if (errorMsg != null) {
            Box(Modifier.fillMaxSize().background(Color(0xEE000000)), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Yayın açılamadı", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(errorMsg!!, color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { retryKey++ },
                            colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Tekrar dene", color = Color.White) }
                        if (req.altUrl != null) {
                            OutlinedButton(onClick = { switchFormat() }, shape = RoundedCornerShape(10.dp)) {
                                Text(if (currentUrl == req.url) "TS ile dene" else "HLS ile dene", color = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBack) { Text("Geri dön", color = Color.Gray) }
                }
            }
        }
    }

    if (showEpisodes && hasSeries) {
        AlertDialog(
            onDismissRequest = { showEpisodes = false },
            title = { Text(req.seriesTitle, fontSize = 16.sp) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(req.episodes.size) { i ->
                        val ep = req.episodes[i]
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { playEpisode(i); showEpisodes = false }
                                .background(if (i == currentIdx) Color(0x22E50914) else Color.Transparent)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("S${ep.season} B${ep.episode}", color = NetflixRed, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(64.dp))
                            Text(ep.title, fontSize = 13.sp, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (i == currentIdx) {
                                Icon(Icons.Filled.PlayArrow, null, tint = NetflixRed, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEpisodes = false }) { Text("Kapat") } }
        )
    }

    if (showTracks && exo != null) {
        TrackDialog(exo = exo, tick = trackTick, onClose = { showTracks = false })
    }

    if (showMore && exo != null) {
        AlertDialog(
            onDismissRequest = { showMore = false },
            title = { Text("Oynatıcı ayarları", fontSize = 16.sp) },
            text = {
                Column {
                    MoreRow("Görüntü", RESIZE_NAMES[resizeIdx]) {
                        val i = (resizeIdx + 1) % 3
                        resizeIdx = i
                    }
                    MoreRow("Hız", "${SPEEDS[speedIdx]}x") {
                        speedIdx = (speedIdx + 1) % SPEEDS.size
                        exo.setPlaybackSpeed(SPEEDS[speedIdx])
                    }
                    MoreRow("Uyku", if (sleepMin > 0) "$sleepMin dk" else "Kapalı") {
                        showMore = false
                        showTimer = true
                    }
                    if (req.altUrl != null) {
                        MoreRow("Format", if (currentUrl.endsWith(".ts")) "TS" else "HLS") {
                            switchFormat()
                            showMore = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMore = false }) { Text("Kapat") } }
        )
    }

    if (showTimer) {
        AlertDialog(
            onDismissRequest = { showTimer = false },
            title = { Text("Uyku zamanlayıcı") },
            text = {
                Column {
                    listOf(0, 10, 20, 30, 60).forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { sleepMin = m; showTimer = false }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = sleepMin == m, onClick = { sleepMin = m; showTimer = false })
                            Spacer(Modifier.width(8.dp))
                            Text(if (m == 0) "Kapalı" else "$m dakika sonra duraklat")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTimer = false }) { Text("Kapat") } }
        )
    }
}

@Composable
private fun MoreRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Text(value, color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrackDialog(exo: ExoPlayer, tick: Int, onClose: () -> Unit) {
    key(tick) {
        val audio = mutableListOf<TrackOpt>()
        val text = mutableListOf<TrackOpt>()
        exo.currentTracks.groups.forEachIndexed { gi, g ->
            val isAudio = g.type == C.TRACK_TYPE_AUDIO
            val isText = g.type == C.TRACK_TYPE_TEXT
            if (!isAudio && !isText) return@forEachIndexed
            for (ti in 0 until g.length) {
                if (!g.isTrackSupported(ti)) continue
                val f = g.getTrackFormat(ti)
                val label = when {
                    isAudio -> (f.language ?: f.label ?: "Ses ${audio.size + 1}") +
                            (if (f.bitrate > 0) " • ${f.bitrate / 1000}k" else "")
                    else -> (f.language ?: f.label ?: "Altyazı ${text.size + 1}")
                }
                val opt = TrackOpt(gi, ti, label, g.isTrackSelected(ti))
                if (isAudio) audio.add(opt) else text.add(opt)
            }
        }
        val params = remember(tick) { exo.trackSelectionParameters }

        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Ses & Altyazı") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text("Ses", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (audio.isEmpty()) Text("Ses parçası bulunamadı", fontSize = 12.sp)
                    audio.forEach { o ->
                        TrackRow(o.label, o.selected) {
                            val group = exo.currentTracks.groups[o.groupIdx].mediaTrackGroup
                            exo.trackSelectionParameters = params.buildUpon()
                                .setOverrideForType(TrackSelectionOverride(group, listOf(o.trackIdx)))
                                .build()
                            onClose()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Altyazı", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    TrackRow("Kapalı", text.none { it.selected }) {
                        exo.trackSelectionParameters = params.buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                        onClose()
                    }
                    text.forEach { o ->
                        TrackRow(o.label, o.selected) {
                            val group = exo.currentTracks.groups[o.groupIdx].mediaTrackGroup
                            exo.trackSelectionParameters = params.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(group, listOf(o.trackIdx)))
                                .build()
                            onClose()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onClose) { Text("Kapat") } }
        )
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
