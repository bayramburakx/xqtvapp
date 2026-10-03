package com.bayram.xqtvapp.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.bayram.xqtvapp.PlayReq
import com.bayram.xqtvapp.Session
import com.bayram.xqtvapp.data.EpgEntry
import com.bayram.xqtvapp.data.EpgXml
import com.bayram.xqtvapp.data.FavoritesStore
import com.bayram.xqtvapp.data.StalkerChannel
import com.bayram.xqtvapp.data.StalkerClient
import com.bayram.xqtvapp.ui.PlayerBackend
import com.bayram.xqtvapp.ui.fmtMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TvPlayerScreen(req: PlayReq, onBack: () -> Unit) {
    BackHandler { onBack() }
    if (req.isLive) TvLivePlayer(req = req, onBack = onBack)
    else TvVodPlayer(req = req, onBack = onBack)
}

private fun epgNow(list: List<EpgEntry>): EpgEntry? {
    val now = System.currentTimeMillis() / 1000
    return list.firstOrNull { it.startEpoch <= now && now < it.endEpoch }
}

private fun epgNext(list: List<EpgEntry>): EpgEntry? {
    val now = System.currentTimeMillis() / 1000
    return list.firstOrNull { it.startEpoch >= now }
}

@Composable
private fun TvLivePlayer(req: PlayReq, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var currentCh by remember(req) { mutableStateOf(req.zap.getOrNull(req.zapIndex)) }
    var currentIdx by remember(req) { mutableIntStateOf(req.zapIndex) }
    var currentUrl by remember(req) { mutableStateOf(req.url) }
    var currentHeaders by remember(req) { mutableStateOf(req.headers) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var retryKey by remember { mutableIntStateOf(0) }
    var showUi by remember { mutableStateOf(true) }
    var dayEpg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    val uiFr = remember { FocusRequester() }
    val hiddenFr = remember { FocusRequester() }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        PlayerBackend.immersive(view, true)
        onDispose {
            view.keepScreenOn = false
            PlayerBackend.immersive(view, false)
        }
    }

    LaunchedEffect(currentCh?.id) {
        dayEpg = emptyList()
        val ch = currentCh ?: return@LaunchedEffect
        try {
            val s = Session(
                "", emptyList(), emptyList(), emptyList(),
                xServer = req.xServer, xUser = req.xUser, xPass = req.xPass,
                m3uUrl = req.m3uUrl
            )
            dayEpg = EpgXml.lookupDay(ctx, s, ch, includeInternet = true)
        } catch (_: Exception) { }
    }
    val nowEpg = remember(dayEpg) { epgNow(dayEpg) }
    val nextEpg = remember(dayEpg) { epgNext(dayEpg) }

    val exo = remember(currentUrl, retryKey) {
        errorMsg = null; buffering = true; playing = true
        try {
            PlayerBackend.build(ctx, currentUrl, isLive = true, currentHeaders).apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        buffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) errorMsg = null
                    }

                    override fun onIsPlayingChanged(v: Boolean) { playing = v }
                    override fun onPlayerError(error: PlaybackException) {
                        buffering = false; playing = false
                        errorMsg = "Yayın açılamadı (${error.errorCodeName})"
                    }
                })
                prepare(); playWhenReady = true
            }
        } catch (e: Exception) {
            errorMsg = "Player kurulamadı: ${e.message}"
            buffering = false; playing = false
            null
        }
    }
    DisposableEffect(exo) { onDispose { try { exo?.release() } catch (_: Exception) { } } }

    LaunchedEffect(showUi, playing) {
        if (showUi && playing) {
            delay(5000)
            showUi = false
        }
    }
    LaunchedEffect(showUi) {
        if (!showUi) {
            try { hiddenFr.requestFocus() } catch (_: Exception) { }
        }
    }

    fun zapTo(idx: Int) {
        val list = req.zap
        if (list.isEmpty()) return
        val i = ((idx % list.size) + list.size) % list.size
        val target = list[i]
        buffering = true
        errorMsg = null
        scope.launch(Dispatchers.IO) {
            try {
                if (req.stalkerUrl.isNotBlank()) {
                    val sc = StalkerClient(req.stalkerUrl, req.stalkerMac)
                    val url = sc.createLink(target.cmd)
                    if (url.isNotBlank()) {
                        currentCh = target; currentIdx = i
                        currentUrl = url; currentHeaders = sc.streamHeaders()
                        dayEpg = emptyList()
                        retryKey++
                    }
                } else {
                    val headers = if (req.xServer.isNotBlank())
                        mapOf("Referer" to req.xServer.trimEnd('/') + "/") else emptyMap()
                    currentCh = target; currentIdx = i
                    currentUrl = target.cmd; currentHeaders = headers
                    dayEpg = emptyList()
                    retryKey++
                }
            } catch (_: Exception) { buffering = false }
        }
    }

    val title = currentCh?.name ?: req.title

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.DirectionLeft -> { zapTo(currentIdx - 1); true }
                    Key.DirectionRight -> { zapTo(currentIdx + 1); true }
                    else -> false
                }
            }
    ) {
        if (exo != null) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).also {
                        it.player = exo
                        it.useController = false
                    }
                },
                update = { it.player = exo },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (buffering && errorMsg == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(54.dp))
            }
        }
        if (!showUi) {
            // Gizli odak alani: OK basilica arayuz geri gelir
            Box(
                Modifier.fillMaxSize()
                    .focusRequester(hiddenFr)
                    .focusable()
                    .tvClickableNoRipple { showUi = true }
            )
            Box(
                Modifier.align(Alignment.TopEnd).padding(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0x59000000))
                    .padding(12.dp, 6.dp)
            ) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (showUi) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                    .padding(48.dp, 12.dp, 48.dp, 32.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(RoundedCornerShape(7.dp))
                            .background(if (playing) Color(0xFFFF453A) else Color.White.copy(alpha = 0.2f))
                            .padding(12.dp, 6.dp)
                    ) {
                        Text(
                            if (playing) "CANLI" else "DURAKLATILDI",
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        (nowEpg?.title ?: title) +
                            (nextEpg?.let { " · Sırada: ${it.title}" } ?: ""),
                        color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (nowEpg != null) {
                    TvThinProgress(nowEpg.progress())
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvCircleBtn(Icons.Filled.ArrowBack, "Geri") { onBack() }
                    TvCircleBtn(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (playing) "Duraklat" else "Oynat",
                        focusFirst = true
                    ) {
                        if (playing) exo?.pause() else exo?.play()
                    }
                    if (req.zap.isNotEmpty()) {
                        TvTextBtn("Önceki kanal") { zapTo(currentIdx - 1) }
                        TvTextBtn("Sonraki kanal") { zapTo(currentIdx + 1) }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("← → kanal değiştir", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
                }
            }
            // Ust bar
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                    .padding(48.dp, 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(currentCh?.genre ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                }
            }
        }
        if (errorMsg != null) {
            TvErrorBox(errorMsg!!, onRetry = { retryKey++ }, onBack = onBack)
        }
        LaunchedEffect(showUi) {
            if (showUi) {
                try { uiFr.requestFocus() } catch (_: Exception) { }
            }
        }
    }
}

@Composable
private fun TvVodPlayer(req: PlayReq, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var currentUrl by remember(req) { mutableStateOf(req.url) }
    var currentIdx by remember(req) { mutableIntStateOf(req.episodeIndex) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }
    var showUi by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var resumeKey by remember(req) { mutableStateOf(req.resumeId) }
    val hiddenFr = remember { FocusRequester() }

    val autoplay by FavoritesStore.autoplayFlow(ctx).collectAsState(initial = true)
    val tvResume by FavoritesStore.tvResumeFlow(ctx).collectAsState(initial = true)
    val trackPrefs by FavoritesStore.trackPrefsFlow(ctx).collectAsState(initial = Pair("auto", "off"))
    var prefsApplied by remember(req) { mutableStateOf(false) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        PlayerBackend.immersive(view, true)
        onDispose {
            view.keepScreenOn = false
            PlayerBackend.immersive(view, false)
        }
    }

    val hasSeries = req.episodes.isNotEmpty()
    val nextEp = if (hasSeries) req.episodes.getOrNull(currentIdx + 1) else null
    val displayTitle = if (hasSeries && currentIdx >= 0) {
        val ep = req.episodes.getOrNull(currentIdx)
        if (ep != null) "${req.seriesTitle} • S${ep.season} B${ep.episode}" else req.title
    } else req.title

    val exo = remember(currentUrl, retryKey) {
        errorMsg = null; buffering = true; playing = true; ended = false
        try {
            PlayerBackend.build(ctx, currentUrl, isLive = false, req.headers).apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        buffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) {
                            errorMsg = null
                            if (!prefsApplied) {
                                prefsApplied = true
                                PlayerBackend.applyTrackPrefs(this@apply, trackPrefs.first, trackPrefs.second)
                            }
                            if (tvResume && req.startMs > 10_000L && currentPosition < 5_000L) {
                                seekTo(req.startMs)
                            }
                        }
                        if (state == Player.STATE_ENDED) {
                            playing = false; ended = true
                            scope.launch(Dispatchers.IO) {
                                FavoritesStore.clearPosition(ctx, resumeKey)
                                if (resumeKey.startsWith("series_")) {
                                    FavoritesStore.markWatched(ctx, resumeKey)
                                }
                            }
                        }
                    }

                    override fun onIsPlayingChanged(v: Boolean) { playing = v }
                    override fun onPlayerError(error: PlaybackException) {
                        buffering = false; playing = false
                        errorMsg = "Oynatılamadı (${error.errorCodeName})"
                    }
                })
                prepare(); playWhenReady = true
            }
        } catch (e: Exception) {
            errorMsg = "Player kurulamadı: ${e.message}"
            buffering = false; playing = false
            null
        }
    }
    DisposableEffect(exo) { onDispose { try { exo?.release() } catch (_: Exception) { } } }

    LaunchedEffect(exo, currentUrl) {
        while (true) {
            delay(5000)
            val p = exo?.currentPosition ?: 0L
            val d = exo?.duration ?: 0L
            pos = p; if (d > 0) dur = d
            if (resumeKey.isNotEmpty() && d > 0 && p > 5_000L && p < (d * 0.98).toLong()) {
                FavoritesStore.savePosition(ctx, resumeKey, p, d)
            }
        }
    }
    LaunchedEffect(exo) {
        while (true) {
            pos = exo?.currentPosition ?: 0L
            val d = exo?.duration ?: 0L
            if (d > 0) dur = d
            delay(500)
        }
    }
    LaunchedEffect(showUi, playing) {
        if (showUi && playing && !ended) {
            delay(5000)
            showUi = false
        }
    }
    LaunchedEffect(showUi) {
        if (!showUi) {
            try { hiddenFr.requestFocus() } catch (_: Exception) { }
        }
    }
    // Otomatik siradaki bolum
    LaunchedEffect(ended) {
        if (ended && nextEp != null && autoplay) {
            delay(1500)
            currentIdx++
            currentUrl = nextEp.url
            resumeKey = "series_" + nextEp.id
            retryKey++
        }
    }

    fun goBack() {
        val p = exo?.currentPosition ?: 0L
        val d = exo?.duration ?: 0L
        if (resumeKey.isNotEmpty() && d > 0 && p > 5_000L && p < (d * 0.98).toLong()) {
            scope.launch(Dispatchers.IO) { FavoritesStore.savePosition(ctx, resumeKey, p, d) }
        }
        onBack()
    }

    fun seekBy(ms: Long) {
        exo?.seekTo(((exo?.currentPosition ?: 0L) + ms).coerceAtLeast(0L))
        showUi = true
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.DirectionLeft -> { seekBy(-10_000); true }
                    Key.DirectionRight -> { seekBy(10_000); true }
                    else -> false
                }
            }
    ) {
        if (exo != null) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).also {
                        it.player = exo
                        it.useController = false
                    }
                },
                update = { it.player = exo },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (buffering && errorMsg == null && !ended) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(54.dp))
            }
        }
        if (!showUi && !ended && errorMsg == null) {
            Box(
                Modifier.fillMaxSize()
                    .focusRequester(hiddenFr)
                    .focusable()
                    .tvClickableNoRipple { showUi = true }
            )
        }
        if (showUi && !ended && errorMsg == null) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE0000000))))
                    .padding(48.dp, 12.dp, 48.dp, 32.dp)
            ) {
                Text(displayTitle, color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                TvThinProgress(if (dur > 0) pos.toFloat() / dur else 0f)
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(fmtMs(pos), color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Text(fmtMs(dur), color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvCircleBtn(Icons.Filled.ArrowBack, "Geri") { goBack() }
                    TvCircleBtn(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (playing) "Duraklat" else "Oynat",
                        focusFirst = true
                    ) { if (playing) exo?.pause() else exo?.play() }
                    TvTextBtn("-10 sn") { seekBy(-10_000) }
                    TvTextBtn("+10 sn") { seekBy(10_000) }
                    if (nextEp != null) {
                        TvTextBtn("Sonraki bölüm") {
                            currentIdx++
                            currentUrl = nextEp.url
                            resumeKey = "series_" + nextEp.id
                            retryKey++
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("← → 10 sn", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                    .padding(48.dp, 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(displayTitle, color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
            }
        }
        if (ended && errorMsg == null && nextEp == null) {
            TvEndBox(
                title = "Bitti",
                onReplay = { retryKey++ },
                onBack = { goBack() }
            )
        }
        if (errorMsg != null) {
            TvErrorBox(errorMsg!!, onRetry = { retryKey++ }, onBack = { goBack() })
        }
    }
}

@Composable
private fun TvThinProgress(frac: Float) {
    Box(
        Modifier.fillMaxWidth().height(6.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = 0.25f))
    ) {
        Box(
            Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(6.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White)
        )
    }
}

@Composable
private fun TvCircleBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    focusFirst: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier.size(64.dp)
            .then(if (focusFirst) Modifier.focusRequester(fr) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusRing(focused, CircleShape, 1.1f)
            .clip(CircleShape)
            .background(Color(0x9E222230))
    ) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(28.dp))
    }
    if (focusFirst) {
        LaunchedEffect(Unit) {
            try { fr.requestFocus() } catch (_: Exception) { }
        }
    }
}

@Composable
private fun TvTextBtn(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.height(64.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusRing(focused, 99.dp, 1.07f)
            .clip(RoundedCornerShape(99.dp))
            .background(Color(0x9E222230))
            .tvClickableNoRipple(onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TvErrorBox(msg: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val fr = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color(0xEE000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Yayın açılamadı", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Spacer(Modifier.height(10.dp))
            Text(msg, color = Color.Gray, fontSize = 18.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton("Tekrar dene", primary = true, onClick = onRetry, focusMe = fr)
                TvButton("Geri dön", primary = false, onClick = onBack)
            }
        }
    }
    LaunchedEffect(Unit) {
        try { fr.requestFocus() } catch (_: Exception) { }
    }
}

@Composable
private fun TvEndBox(title: String, onReplay: () -> Unit, onBack: () -> Unit) {
    val fr = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color(0xEE000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton("Baştan izle", primary = true, onClick = onReplay, focusMe = fr)
                TvButton("Geri dön", primary = false, onClick = onBack)
            }
        }
    }
    LaunchedEffect(Unit) {
        try { fr.requestFocus() } catch (_: Exception) { }
    }
}
