package com.bayram.xqtvapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay

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

/** Tamamen özel sinematik oynatıcı: hazır kontrolcü kapalı, kendi arayüzümüz açık. */
@Composable
fun PlayerScreen(req: PlayReq, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var currentUrl by remember { mutableStateOf(req.url) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var retryKey by remember { mutableIntStateOf(0) }
    var resizeIdx by remember { mutableIntStateOf(0) }
    var speedIdx by remember { mutableIntStateOf(0) }
    var showUi by remember { mutableStateOf(true) }
    var uiTick by remember { mutableIntStateOf(0) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var showTracks by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var sleepMin by remember { mutableStateOf(0) }
    var trackTick by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val exo = remember(currentUrl, retryKey) {
        errorMsg = null; buffering = true; playing = true
        try {
            ExoPlayer.Builder(ctx).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        buffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) errorMsg = null
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

    // konum saati
    LaunchedEffect(exo) {
        while (true) {
            pos = exo?.currentPosition ?: 0L
            dur = exo?.duration ?: 0L
            delay(500)
        }
    }
    // kontrolcüyü otomatik gizle
    LaunchedEffect(showUi, uiTick, playing) {
        if (showUi && playing) {
            delay(3500)
            showUi = false
        }
    }
    // uyku zamanlayıcı
    LaunchedEffect(sleepMin) {
        if (sleepMin > 0) {
            delay(sleepMin * 60_000L)
            exo?.pause()
            sleepMin = 0
        }
    }

    val isLive = dur <= 0L
    val interaction = remember { MutableInteractionSource() }

    fun poke() { showUi = true; uiTick++ }
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

        if (buffering && errorMsg == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Yükleniyor...", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // orta: duraklatıldıysa büyük buton
        if (!playing && !buffering && errorMsg == null && exo != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = { exo.play(); poke() },
                    modifier = Modifier.size(76.dp).background(Color(0xAA000000), CircleShape)
                ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(44.dp)) }
            }
        }

        // üst bar
        if (showUi) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Geri", tint = Color.White)
                    }
                    Text(req.title, color = Color.White, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
                    if (req.altUrl != null) {
                        TextButton(onClick = { switchFormat(); poke() }) {
                            Text(if (currentUrl.endsWith(".ts")) "HLS" else "TS", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = {
                        resizeIdx = (resizeIdx + 1) % 3
                        poke()
                    }) { Text(RESIZE_NAMES[resizeIdx], color = Color.White, fontSize = 12.sp) }
                    TextButton(onClick = {
                        speedIdx = (speedIdx + 1) % SPEEDS.size
                        exo?.setPlaybackSpeed(SPEEDS[speedIdx])
                        poke()
                    }) { Text("${SPEEDS[speedIdx]}x", color = Color.White, fontSize = 12.sp) }
                    TextButton(onClick = { showTracks = true; trackTick++; poke() }) {
                        Text("CC", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { showTimer = true; poke() }) {
                        Text(if (sleepMin > 0) "${sleepMin}dk" else "Zzz", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // alt bar
        if (showUi) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLive) {
                        Text("● CANLI", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        Text(fmtMs(pos), color = Color.White, fontSize = 13.sp)
                        Slider(
                            value = if (dur > 0) pos.toFloat() / dur else 0f,
                            onValueChange = { f -> exo?.seekTo((f * dur).toLong()) },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text(fmtMs(dur), color = Color.White, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isLive) {
                        IconButton(onClick = { exo?.seekBack(); poke() }) {
                            Icon(Icons.Filled.Replay10, "Geri 10sn", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                    IconButton(
                        onClick = { if (playing) exo?.pause() else exo?.play(); poke() },
                        modifier = Modifier.size(60.dp).background(Color.White, CircleShape)
                    ) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null,
                            tint = Color.Black, modifier = Modifier.size(34.dp)
                        )
                    }
                    if (!isLive) {
                        IconButton(onClick = { exo?.seekForward(); poke() }) {
                            Icon(Icons.Filled.Forward10, "İleri 10sn", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }

        // hata paneli
        if (errorMsg != null) {
            Box(Modifier.fillMaxSize().background(Color(0xDD000000)), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Yayın açılamadı", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(errorMsg!!, color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Farklı formatı dene, çoğu Xtream sunucusu TS ister.", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { retryKey++ }) { Text("Tekrar dene") }
                        if (req.altUrl != null) {
                            Button(onClick = { switchFormat() }) {
                                Text(if (currentUrl == req.url) "TS ile dene" else "HLS ile dene")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTracks && exo != null) {
        TrackDialog(exo = exo, tick = trackTick, onClose = { showTracks = false })
    }

    if (showTimer) {
        AlertDialog(
            onDismissRequest = { showTimer = false },
            title = { Text("Uyku zamanlayıcı") },
            text = {
                Column {
                    listOf(0, 10, 20, 30, 60).forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                sleepMin = m; showTimer = false
                            }.padding(10.dp),
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
private fun TrackDialog(exo: ExoPlayer, tick: Int, onClose: () -> Unit) {
    // tick her açılışta tazele
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
