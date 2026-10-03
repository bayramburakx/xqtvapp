package com.bayram.xqtvapp.tv

import android.util.TypedValue
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.bayram.xqtvapp.PlayReq
import com.bayram.xqtvapp.Session
import com.bayram.xqtvapp.data.EpgEntry
import com.bayram.xqtvapp.data.EpgXml
import com.bayram.xqtvapp.data.FavoritesStore
import com.bayram.xqtvapp.data.StalkerChannel
import com.bayram.xqtvapp.data.StalkerClient
import com.bayram.xqtvapp.ui.PLine
import com.bayram.xqtvapp.ui.PTx2
import com.bayram.xqtvapp.ui.PlayerBackend
import com.bayram.xqtvapp.ui.fmtMs
import com.bayram.xqtvapp.ui.initialsOf
import com.bayram.xqtvapp.ui.tileBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TvSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val TvResizeNames = listOf("Sığdır", "Doldur", "Zoom")
private val TvResizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
)

@Composable
fun TvPlayerScreen(req: PlayReq, onBack: () -> Unit) {
    BackHandler { onBack() }
    if (req.isLive) TvLivePlayer(req = req, onBack = onBack)
    else TvVodPlayer(req = req, onBack = onBack)
}

private fun epgNowTv(list: List<EpgEntry>): EpgEntry? {
    val now = System.currentTimeMillis() / 1000
    return list.firstOrNull { it.startEpoch <= now && now < it.endEpoch }
}

private fun epgNextTv(list: List<EpgEntry>): EpgEntry? {
    val now = System.currentTimeMillis() / 1000
    return list.firstOrNull { it.startEpoch >= now }
}

// ==================== CANLI (telefon tasarımıyla) ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvLivePlayer(req: PlayReq, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val errScope = rememberCoroutineScope()

    var currentCh by remember(req) { mutableStateOf(req.zap.getOrNull(req.zapIndex)) }
    var currentIdx by remember(req) { mutableIntStateOf(req.zapIndex) }
    var currentUrl by remember(req) { mutableStateOf(req.url) }
    var currentAlt by remember(req) { mutableStateOf(req.altUrl) }
    var currentHeaders by remember(req) { mutableStateOf(req.headers) }
    var triedAlt by remember(req) { mutableStateOf(false) }
    var zapping by remember(req) { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var retryKey by remember { mutableIntStateOf(0) }
    var resizeIdx by remember { mutableIntStateOf(0) }
    var muted by remember { mutableStateOf(false) }
    var qualityH by remember { mutableIntStateOf(-1) }
    var showUi by remember { mutableStateOf(true) }
    var showTracks by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showChannels by remember { mutableStateOf(false) }
    var showProgram by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var sleepMin by remember { mutableIntStateOf(0) }
    var trackTick by remember { mutableIntStateOf(0) }
    var osd by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var dayEpg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    val hiddenFr = remember { FocusRequester() }
    val playFr = remember { FocusRequester() }
    var uiFocusGiven by remember { mutableStateOf(false) }

    val favs by FavoritesStore.favsFlow(ctx).collectAsState(initial = emptySet())
    val chId = currentCh?.id ?: req.resumeId
    val isFav = favs.contains(chId)

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
            val s = Session("", emptyList(), emptyList(), emptyList(),
                xServer = req.xServer, xUser = req.xUser, xPass = req.xPass,
                m3uUrl = req.m3uUrl)
            dayEpg = EpgXml.lookupDay(ctx, s, ch)
        } catch (_: Exception) { }
    }
    val nowEpg = remember(dayEpg) { epgNowTv(dayEpg) }
    val nextEpg = remember(dayEpg) { epgNextTv(dayEpg) }

    val exo = remember(currentUrl, retryKey) {
        errorMsg = null; buffering = true; playing = true
        try {
            PlayerBackend.build(ctx, currentUrl, isLive = true, currentHeaders).apply {
                volume = if (muted) 0f else 1f
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        buffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) errorMsg = null
                    }
                    override fun onIsPlayingChanged(v: Boolean) { playing = v }
                    override fun onPlayerError(error: PlaybackException) {
                        if (req.altUrl != null && !triedAlt && currentUrl == req.url) {
                            triedAlt = true
                            currentUrl = req.altUrl
                            retryKey++
                        } else {
                            buffering = false; playing = false
                            val base = "Yayın açılamadı (${error.errorCodeName})"
                            errorMsg = base
                            errScope.launch {
                                val st = PlayerBackend.probeStatus(currentUrl, currentHeaders)
                                errorMsg = "$base • Sunucu: $st"
                            }
                        }
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
    LaunchedEffect(osd) {
        if (osd != null) {
            delay(2200)
            osd = null
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1800)
            toast = null
        }
    }
    LaunchedEffect(sleepMin) {
        if (sleepMin > 0) {
            delay(sleepMin * 60_000L)
            exo?.pause()
            sleepMin = 0
        }
    }
    LaunchedEffect(showUi) {
        // Odak sadece ilk acilista Oynat'a verilir; sonrasi kumandada kalir.
        // (Her gosterimde odak calmak, haplar arasi gezintiyi bozuyordu.)
        if (showUi && !uiFocusGiven) {
            uiFocusGiven = true
            try { playFr.requestFocus() } catch (_: Exception) { }
        } else if (!showUi) {
            try { hiddenFr.requestFocus() } catch (_: Exception) { }
        }
    }

    fun poke() { showUi = true }

    fun applyZap(ch: StalkerChannel, idx: Int, url: String, alt: String?, headers: Map<String, String>) {
        currentCh = ch
        currentIdx = idx
        currentUrl = url
        currentAlt = alt
        currentHeaders = headers
        triedAlt = false
        dayEpg = emptyList()
        osd = ch.name
        retryKey++
        poke()
    }

    fun zapTo(idx: Int) {
        val list = req.zap
        if (list.isEmpty() || zapping) return
        val i = ((idx % list.size) + list.size) % list.size
        val target = list[i]
        zapping = true
        buffering = true
        errorMsg = null
        scope.launch(Dispatchers.IO) {
            try {
                if (req.stalkerUrl.isNotBlank()) {
                    val sc = StalkerClient(req.stalkerUrl, req.stalkerMac)
                    val url = sc.createLink(target.cmd)
                    if (url.isNotBlank()) applyZap(target, i, url, null, sc.streamHeaders())
                } else {
                    val alt = if (target.cmd.endsWith(".m3u8")) target.cmd.dropLast(5) + ".ts" else null
                    val headers = if (req.xServer.isNotBlank())
                        mapOf("Referer" to req.xServer.trimEnd('/') + "/") else emptyMap()
                    if (i == req.zapIndex && req.altUrl != null) {
                        applyZap(target, i, req.url, req.altUrl, req.headers)
                    } else {
                        applyZap(target, i, target.cmd, alt, headers)
                    }
                }
            } catch (_: Exception) { buffering = false }
            zapping = false
        }
    }

    val title = currentCh?.name ?: req.title
    val meta = buildString {
        append(currentCh?.genre ?: "")
        if (qualityH > 0) append(" · ${qualityH}p") else append(" · Otomatik")
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                // Arayuz acikken oklar sadece odakta gezinir; kanal degismez.
                // Arayuz kapaliyken: yukari/asagi = kanal, sag/sol = arayuzu acar.
                if (showUi || errorMsg != null) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.DirectionUp -> { zapTo(currentIdx - 1); true }
                    Key.DirectionDown -> { zapTo(currentIdx + 1); true }
                    Key.DirectionLeft, Key.DirectionRight -> { poke(); true }
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
                        it.resizeMode = TvResizeModes[resizeIdx]
                    }
                },
                update = {
                    it.player = exo
                    it.resizeMode = TvResizeModes[resizeIdx]
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (buffering && errorMsg == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(54.dp))
            }
        }
        if (!showUi) {
            Box(
                Modifier.fillMaxSize()
                    .focusRequester(hiddenFr)
                    .focusable()
                    .tvClickableNoRipple { poke() }
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
        // OSD: zap bildirimi
        osd?.let {
            Column(Modifier.align(Alignment.TopStart).padding(48.dp, 90.dp)) {
                Text(it, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(nowEpg?.title ?: (currentCh?.genre ?: ""),
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (showUi) {
            // Ust bar
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                    .padding(40.dp, 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvCircleBtn(Icons.Filled.ArrowBack, onBack)
                Spacer(Modifier.width(14.dp))
                if (currentCh?.logo?.isNotBlank() == true) {
                    AsyncImage(model = currentCh!!.logo, contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(meta, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TvCircleBtn(
                    Icons.Filled.Star,
                    { scope.launch { FavoritesStore.toggle(ctx, chId) } },
                    tint = if (isFav) Color(0xFFFFD60A) else Color.White
                )
                Spacer(Modifier.width(10.dp))
                TvCircleBtn(
                    if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    {
                        muted = !muted
                        exo?.volume = if (muted) 0f else 1f
                        poke()
                    }
                )
            }
            // Orta: onceki / oynat / sonraki
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (req.zap.isNotEmpty()) {
                    TvZapBtn(
                        "▲",
                        req.zap.getOrNull((currentIdx - 1 + req.zap.size) % req.zap.size)?.name ?: "",
                        onClick = { zapTo(currentIdx - 1); poke() }
                    )
                }
                TvPlayBtn(playing = playing, focusMe = playFr, onClick = {
                    if (playing) exo?.pause() else exo?.play()
                    poke()
                })
                if (req.zap.isNotEmpty()) {
                    TvZapBtn(
                        "▼",
                        req.zap.getOrNull((currentIdx + 1) % req.zap.size)?.name ?: "",
                        onClick = { zapTo(currentIdx + 1); poke() }
                    )
                }
            }
            // Alt bar
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                    .padding(48.dp, 10.dp, 48.dp, 28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvLivePill(playing, onResume = {
                        exo?.play()
                        poke()
                        scope.launch { toast = "Canlı yayına dönüldü" }
                    })
                    Spacer(Modifier.width(12.dp))
                    val left = nowEpg?.let { (it.endEpoch - System.currentTimeMillis() / 1000) / 60 }
                    Text(
                        (if (left != null && left > 0) "$left dk kaldı" else "") +
                            (nextEpg?.let { " · Sırada: ${it.title}" } ?: ""),
                        color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(nowEpg?.title ?: title, color = Color.White,
                    fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                if (nowEpg != null) {
                    TvThinProgress(nowEpg.progress())
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Text(nowEpg.range().substringBefore("–").trim(),
                            color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Text(nowEpg.range().substringAfter("–").trim(),
                            color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                } else {
                    Spacer(Modifier.height(12.dp))
                }
                TvPillRow(
                    listOf("Kanallar", "Program", "Ses ve altyazı", "Kalite", "Görüntü"),
                    onPick = {
                        when (it) {
                            "Kanallar" -> showChannels = true
                            "Program" -> showProgram = true
                            "Ses ve altyazı" -> { showTracks = true; trackTick++ }
                            "Kalite" -> showQuality = true
                            "Görüntü" -> showMore = true
                        }
                        poke()
                    },
                    labelOf = {
                        when (it) {
                            "Kalite" -> if (qualityH > 0) "${qualityH}p" else "Otomatik"
                            "Görüntü" -> TvResizeNames[resizeIdx]
                            else -> it
                        }
                    }
                )
            }
        }
        toast?.let {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 190.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color(0xD91E1E2C))
                .padding(20.dp, 12.dp)) {
                Text(it, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (errorMsg != null) {
            TvErrorBox(errorMsg!!, onRetry = { retryKey++ }, onBack = onBack)
        }
    }

    // Yan kanal paneli (kumandayla gezilir, ilk odakta mevcut kanal)
    if (showChannels) {
        TvChannelPanel(
            channels = req.zap,
            currentId = currentCh?.id,
            onPick = { i ->
                showChannels = false
                zapTo(i)
            },
            onClose = { showChannels = false }
        )
    }
    // Program sayfasi
    if (showProgram) {
        TvSheet(title = "${currentCh?.name ?: ""} · Program", onClose = { showProgram = false }) {
            val now = System.currentTimeMillis() / 1000
            val rows = dayEpg.filter { it.endEpoch > now - 3600 }.take(12)
            if (rows.isEmpty()) {
                Text("Program bilgisi yok.", color = PTx2, fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 12.dp))
            }
            rows.forEachIndexed { i, p ->
                val isNow = p.startEpoch <= now && now < p.endEpoch
                TvSheetRow(
                    title = p.title,
                    sub = p.range(),
                    trailing = if (isNow) "ŞİMDİ" else "",
                    focusFirst = i == 0,
                    onClick = { if (isNow) showProgram = false }
                )
            }
        }
    }
    if (showTracks && exo != null) {
        TvTrackSheet(exo = exo, tick = trackTick, onClose = { showTracks = false })
    }
    if (showQuality && exo != null) {
        val heights = remember(exo, trackTick) { PlayerBackend.videoHeights(exo) }
        TvSheet(title = "Kalite", onClose = { showQuality = false }) {
            TvSheetRow("Otomatik (önerilen)", "", qualityH == -1, focusFirst = true) {
                qualityH = -1
                PlayerBackend.setQuality(exo, null)
                showQuality = false
            }
            heights.forEach { h ->
                TvSheetRow("${h}p", "", qualityH == h) {
                    qualityH = h
                    PlayerBackend.setQuality(exo, h)
                    showQuality = false
                }
            }
            if (heights.isEmpty()) {
                Text("Tek kalite mevcut.", color = PTx2, fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
    if (showMore) {
        TvSheet(title = "Görüntü oranı", onClose = { showMore = false }) {
            TvResizeNames.forEachIndexed { i, n ->
                TvSheetRow(n, "", resizeIdx == i, focusFirst = i == 0) {
                    resizeIdx = i
                    showMore = false
                }
            }
            TvSheetRow(
                if (sleepMin > 0) "Uyku: $sleepMin dk (kapat)" else "Uyku zamanlayıcı",
                "", false
            ) {
                showMore = false
                showTimer = true
            }
        }
    }
    if (showTimer) {
        TvSheet(title = "Uyku zamanlayıcı", onClose = { showTimer = false }) {
            listOf(0, 10, 20, 30, 60).forEachIndexed { i, m ->
                TvSheetRow(
                    if (m == 0) "Kapalı" else "$m dakika sonra duraklat",
                    "", sleepMin == m, focusFirst = i == 0
                ) {
                    sleepMin = m
                    showTimer = false
                }
            }
        }
    }
}

// ==================== VOD (telefon tasarımıyla) ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvVodPlayer(req: PlayReq, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val errScope = rememberCoroutineScope()

    var currentUrl by remember(req) { mutableStateOf(req.url) }
    var currentIdx by remember(req) { mutableIntStateOf(req.episodeIndex) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }
    var resizeIdx by remember { mutableIntStateOf(0) }
    var speedIdx by remember { mutableIntStateOf(2) }
    var muted by remember { mutableStateOf(false) }
    var qualityH by remember { mutableIntStateOf(-1) }
    var subSizeIdx by remember { mutableIntStateOf(1) }
    var showUi by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var showTracks by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showAspect by remember { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    var trackTick by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(-1) }
    var seekFlash by remember { mutableStateOf<String?>(null) }
    var resumeToast by remember { mutableStateOf(req.startMs > 10_000L) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val autoplay by FavoritesStore.autoplayFlow(ctx).collectAsState(initial = true)
    val tvResume by FavoritesStore.tvResumeFlow(ctx).collectAsState(initial = true)
    val saveScope = rememberCoroutineScope()
    var resumeKey by remember(req) { mutableStateOf(req.resumeId) }
    val trackPrefs by FavoritesStore.trackPrefsFlow(ctx).collectAsState(initial = Pair("auto", "off"))
    val subSizePref by FavoritesStore.subSizeFlow(ctx).collectAsState(initial = 1)
    var prefsApplied by remember(req) { mutableStateOf(false) }
    val hiddenFr = remember { FocusRequester() }
    val playFr = remember { FocusRequester() }
    var uiFocusGiven by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        PlayerBackend.immersive(view, true)
        onDispose {
            view.keepScreenOn = false
            PlayerBackend.immersive(view, false)
        }
    }
    LaunchedEffect(subSizePref) {
        subSizeIdx = subSizePref
        playerView?.subtitleView?.setFixedTextSize(
            TypedValue.COMPLEX_UNIT_SP, PlayerBackend.SUB_SIZES_SP[subSizePref.coerceIn(0, 2)]
        )
    }

    val hasSeries = req.episodes.isNotEmpty()
    val nextEp = if (hasSeries) req.episodes.getOrNull(currentIdx + 1) else null
    val displayTitle = if (hasSeries && currentIdx >= 0) {
        val ep = req.episodes.getOrNull(currentIdx)
        if (ep != null) "${req.seriesTitle} • S${ep.season} B${ep.episode}" else req.title
    } else req.title

    val exo = remember(currentUrl, retryKey) {
        errorMsg = null; buffering = true; playing = true; ended = false; countdown = -1
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
                            saveScope.launch(Dispatchers.IO) {
                                FavoritesStore.clearPosition(ctx, resumeKey)
                                if (resumeKey.startsWith("series_")) {
                                    FavoritesStore.markWatched(ctx, resumeKey)
                                }
                            }
                            if (nextEp != null && autoplay) countdown = 10
                        }
                    }
                    override fun onIsPlayingChanged(v: Boolean) { playing = v }
                    override fun onPlayerError(error: PlaybackException) {
                        buffering = false; playing = false
                        val base = "Oynatılamadı (${error.errorCodeName})"
                        errorMsg = base
                        errScope.launch {
                            val st = PlayerBackend.probeStatus(currentUrl, req.headers)
                            errorMsg = "$base • Sunucu: $st"
                        }
                    }
                })
                setPlaybackSpeed(TvSpeeds[speedIdx])
                prepare(); playWhenReady = true
            }
        } catch (e: Exception) {
            errorMsg = "Player kurulamadı: ${e.message}"
            buffering = false; playing = false
            null
        }
    }

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
    DisposableEffect(exo) {
        onDispose { try { exo?.release() } catch (_: Exception) { } }
    }

    fun saveNow() {
        val p = exo?.currentPosition ?: 0L
        val d = exo?.duration ?: 0L
        if (resumeKey.isNotEmpty() && d > 0 && p > 5_000L && p < (d * 0.98).toLong()) {
            saveScope.launch(Dispatchers.IO) {
                FavoritesStore.savePosition(ctx, resumeKey, p, d)
            }
        }
    }
    fun goBack() { saveNow(); onBack() }

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
    LaunchedEffect(seekFlash) {
        if (seekFlash != null) {
            delay(600)
            seekFlash = null
        }
    }
    LaunchedEffect(resumeToast) {
        if (resumeToast) {
            delay(6000)
            resumeToast = false
        }
    }
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        } else if (countdown == 0 && nextEp != null) {
            currentUrl = nextEp.url
            currentIdx++
            resumeKey = "series_" + nextEp.id
            retryKey++
        }
    }
    LaunchedEffect(showUi) {
        // Odak sadece ilk acilista Oynat'a verilir; sonrasi kumandada kalir.
        if (showUi && !ended && errorMsg == null && !uiFocusGiven) {
            uiFocusGiven = true
            try { playFr.requestFocus() } catch (_: Exception) { }
        } else if (!showUi) {
            try { hiddenFr.requestFocus() } catch (_: Exception) { }
        }
    }

    fun poke() { showUi = true }
    fun togglePlay() {
        if (playing) exo?.pause() else exo?.play()
        poke()
    }
    fun seekBy(ms: Long) {
        exo?.seekTo(((exo?.currentPosition ?: 0L) + ms).coerceAtLeast(0L))
        seekFlash = if (ms < 0) "−10 sn" else "+10 sn"
        poke()
    }
    fun playEpisode(idx: Int) {
        val ep = req.episodes.getOrNull(idx) ?: return
        currentIdx = idx
        currentUrl = ep.url
        resumeKey = "series_" + ep.id
        retryKey++
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                // Arayuz acikken oklar sadece odakta gezinir (sarma calmaz).
                // Sarma yalnizca sarma cubugu odaktayken olur; kapaliyken oklar arayuzu acar.
                if (showUi || ended || errorMsg != null) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.DirectionLeft, Key.DirectionRight,
                    Key.DirectionUp, Key.DirectionDown -> { poke(); true }
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
                        it.resizeMode = TvResizeModes[resizeIdx]
                        playerView = it
                    }
                },
                update = {
                    it.player = exo
                    it.resizeMode = TvResizeModes[resizeIdx]
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (buffering && errorMsg == null && !ended) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(54.dp))
            }
        }
        // Sarma geri bildirimi
        seekFlash?.let { side ->
            Box(
                Modifier.align(if (side.startsWith("−")) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 60.dp).size(120.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(side, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Devam bildirimi
        if (resumeToast && errorMsg == null && !ended) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xDE1E1E2C))
                    .padding(20.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${fmtMs(req.startMs)}’den devam ediliyor",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(14.dp))
                TvPillBtn("Baştan başla", primary = true) {
                    exo?.seekTo(0L)
                    resumeToast = false
                    poke()
                }
            }
        }
        if (!showUi && !ended && errorMsg == null) {
            Box(
                Modifier.fillMaxSize()
                    .focusRequester(hiddenFr)
                    .focusable()
                    .tvClickableNoRipple { poke() }
            )
        }
        if (showUi && !ended && errorMsg == null) {
            // Ust bar
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                    .padding(40.dp, 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvCircleBtn(Icons.Filled.ArrowBack, onClick = { goBack() })
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(displayTitle, color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (hasSeries) Text(req.seriesTitle, color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp, maxLines = 1)
                }
                TvCircleBtn(
                    if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    onClick = {
                        muted = !muted
                        exo?.volume = if (muted) 0f else 1f
                        poke()
                    }
                )
            }
            // Orta: oynat (sarma: cubuk odaktayken sag/sol, basili tutunca surekli)
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvPlayBtn(playing = playing, focusMe = playFr, onClick = { togglePlay() })
            }
            // Alt bar: odaklanabilir sarma cubugu + haplar
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE0000000))))
                    .padding(48.dp, 10.dp, 48.dp, 28.dp)
            ) {
                TvSeekBar(
                    frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                    posText = fmtMs(pos),
                    durText = fmtMs(dur),
                    onScrub = { ms -> seekBy(ms) }
                )
                Spacer(Modifier.height(14.dp))
                TvPillRow(
                    listOf("Ses ve altyazı", "Hız", "Bölümler", "Kalite", "Görüntü")
                        .filter { it != "Bölümler" || hasSeries },
                    onPick = {
                        when (it) {
                            "Ses ve altyazı" -> { showTracks = true; trackTick++ }
                            "Hız" -> showSpeed = true
                            "Bölümler" -> showEpisodes = true
                            "Kalite" -> showQuality = true
                            "Görüntü" -> showAspect = true
                        }
                        poke()
                    },
                    labelOf = {
                        when (it) {
                            "Hız" -> "${TvSpeeds[speedIdx]}x"
                            "Kalite" -> if (qualityH > 0) "${qualityH}p" else "Otomatik"
                            "Görüntü" -> TvResizeNames[resizeIdx]
                            else -> it
                        }
                    }
                )
            }
        }
        // Sonraki bolum / bitti
        if (ended && errorMsg == null) {
            val nx = nextEp
            TvEndBox2(
                top = "Sıradaki bölüm",
                title = if (nx != null) "S${nx.season} • B${nx.episode} — ${nx.title}" else "Bitti",
                sub = if (countdown > 0) "$countdown sn içinde başlıyor..." else "",
                primaryLabel = if (nx != null) "Hemen Oynat" else "Baştan izle",
                onPrimary = {
                    if (nx != null) playEpisode(currentIdx + 1) else retryKey++
                },
                onBack = {
                    countdown = -1
                    goBack()
                }
            )
        }
        if (errorMsg != null) {
            TvErrorBox(errorMsg!!, onRetry = { retryKey++ }, onBack = { goBack() })
        }
    }

    if (showEpisodes && hasSeries) {
        TvEpisodePanel(
            title = req.seriesTitle,
            episodes = req.episodes,
            current = currentIdx,
            onPick = { playEpisode(it); showEpisodes = false },
            onClose = { showEpisodes = false }
        )
    }
    if (showTracks && exo != null) {
        TvTrackSheet(
            exo = exo, tick = trackTick,
            subSize = subSizeIdx,
            onSubSize = { i ->
                subSizeIdx = i
                scope.launch(Dispatchers.IO) {
                    FavoritesStore.setSubSize(ctx, i)
                }
                playerView?.subtitleView?.setFixedTextSize(
                    TypedValue.COMPLEX_UNIT_SP, PlayerBackend.SUB_SIZES_SP[i.coerceIn(0, 2)]
                )
            },
            onClose = { showTracks = false }
        )
    }
    if (showSpeed && exo != null) {
        TvSheet(title = "Oynatma hızı", onClose = { showSpeed = false }) {
            TvSpeeds.forEachIndexed { i, s ->
                TvSheetRow("${s}x" + if (s == 1f) " (normal)" else "", "", speedIdx == i, focusFirst = i == 2) {
                    speedIdx = i
                    exo.setPlaybackSpeed(s)
                    showSpeed = false
                }
            }
        }
    }
    if (showQuality && exo != null) {
        val heights = remember(exo, trackTick, retryKey) { PlayerBackend.videoHeights(exo) }
        TvSheet(title = "Kalite", onClose = { showQuality = false }) {
            TvSheetRow("Otomatik (önerilen)", "", qualityH == -1, focusFirst = true) {
                qualityH = -1
                PlayerBackend.setQuality(exo, null)
                showQuality = false
            }
            heights.forEach { h ->
                TvSheetRow("${h}p", "", qualityH == h) {
                    qualityH = h
                    PlayerBackend.setQuality(exo, h)
                    showQuality = false
                }
            }
        }
    }
    if (showAspect) {
        TvSheet(title = "Görüntü oranı", onClose = { showAspect = false }) {
            TvResizeNames.forEachIndexed { i, n ->
                TvSheetRow(n, "", resizeIdx == i, focusFirst = i == 0) {
                    resizeIdx = i
                    showAspect = false
                }
            }
        }
    }
}

// ==================== TV ORTAK PARCALAR ====================

@Composable
private fun TvCircleBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: DpSize = DpSize(56.dp, 56.dp),
    tint: Color = Color.White,
    focusFirst: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier.size(size.width, size.height)
            .then(if (focusFirst) Modifier.focusRequester(fr) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusRing(focused, 32.dp, 1.1f)
            .clip(CircleShape)
            .background(Color(0x9E222230))
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
    }
    if (focusFirst) {
        LaunchedEffect(Unit) {
            try { fr.requestFocus() } catch (_: Exception) { }
        }
    }
}

private data class DpSize(val width: androidx.compose.ui.unit.Dp, val height: androidx.compose.ui.unit.Dp)

@Composable
private fun TvPlayBtn(playing: Boolean, focusMe: FocusRequester, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier.size(96.dp)
            .focusRequester(focusMe)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusRing(focused, 48.dp, 1.08f)
            .background(Color.White, CircleShape)
    ) {
        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null,
            tint = Color.Black, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun TvZapBtn(symbol: String, name: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocusRing(focused, 32.dp, 1.08f)
            .clip(CircleShape)
            .background(Color(0x9E222230))
            .tvClickableNoRipple(onClick)
            .size(72.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(symbol, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
    if (name.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(110.dp))
    }
}

@Composable
private fun TvTextBtn(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.height(56.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusRing(focused, 99.dp, 1.07f)
            .clip(RoundedCornerShape(99.dp))
            .background(Color(0x9E222230))
            .tvClickableNoRipple(onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TvPillBtn(
    label: String,
    primary: Boolean,
    focusMe: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.height(44.dp)
            .then(if (focusMe != null) Modifier.focusRequester(focusMe) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusRing(focused, 99.dp, 1.07f)
            .clip(RoundedCornerShape(99.dp))
            .background(if (primary) Color.White else Color(0x9E222230))
            .tvClickableNoRipple(onClick)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (primary) Color.Black else Color.White,
            fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TvPillRow(items: List<String>, onPick: (String) -> Unit, labelOf: (String) -> String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEach {
            var focused by remember { mutableStateOf(false) }
            Box(
                Modifier.height(44.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .tvFocusRing(focused, 99.dp, 1.07f)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.Transparent)
                    .border(1.dp, PLine, RoundedCornerShape(99.dp))
                    .tvClickableNoRipple { onPick(it) }
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(labelOf(it), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = if (focused) Color.White else PTx2)
            }
        }
    }
}

@Composable
private fun TvLivePill(playing: Boolean, onResume: () -> Unit) {
    if (playing) {
        Box(
            Modifier.clip(RoundedCornerShape(7.dp))
                .background(Color(0xFFFF453A))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("CANLI", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    } else {
        var focused by remember { mutableStateOf(false) }
        Box(
            Modifier
                .onFocusChanged { focused = it.isFocused }
                .tvFocusRing(focused, 7.dp, 1.07f)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White.copy(alpha = 0.2f))
                .tvClickableNoRipple(onResume)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("CANLIYA DÖN", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
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

/** Odaklanabilir sarma cubugu: odaktayken sag/sol ±10 sn sarar,
 *  basili tutunca KeyDown tekrarlariyla surekli sarar (Netflix tarzi). */
@Composable
private fun TvSeekBar(frac: Float, posText: String, durText: String, onScrub: (Long) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column {
        Box(
            Modifier.fillMaxWidth().height(34.dp)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent {
                    // Tek basim tek adim, basili tutma = art arda KeyDown = surekli sarma
                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (it.key) {
                        Key.DirectionLeft -> { onScrub(-10_000); true }
                        Key.DirectionRight -> { onScrub(10_000); true }
                        else -> false
                    }
                }
                .tvFocusRing(focused, 9.dp, 1.0f),
            contentAlignment = Alignment.CenterStart
        ) {
            TvThinProgress(frac)
            if (focused) {
                Box(
                    Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
        Row {
            Text(posText, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(durText, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF171722),
        dragHandle = {
            Box(Modifier.padding(10.dp).width(40.dp).height(5.dp)
                .clip(RoundedCornerShape(9.dp)).background(PLine))
        }
    ) {
        Column(Modifier.padding(horizontal = 28.dp).padding(bottom = 30.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun TvSheetRow(
    title: String,
    sub: String,
    on: Boolean = false,
    focusFirst: Boolean = false,
    trailing: String = "",
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    Row(
        Modifier.fillMaxWidth()
            .then(if (focusFirst) Modifier.focusRequester(fr) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusRing(focused, 12.dp, 1.02f)
            .clip(RoundedCornerShape(12.dp))
            .tvClickableNoRipple(onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (sub.isNotBlank()) {
                Text(sub, fontSize = 13.sp, color = PTx2)
            }
        }
        if (trailing.isNotBlank()) {
            Text(trailing, color = Color(0xFFFF453A), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(10.dp))
        }
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .then(
                    if (on) Modifier.background(Color.White)
                    else Modifier.border(2.dp, PLine, CircleShape)
                )
        )
    }
    HorizontalDivider(color = PLine)
    if (focusFirst) {
        LaunchedEffect(Unit) {
            try { fr.requestFocus() } catch (_: Exception) { }
        }
    }
}

/** TV ses/ altyazi sayfasi (odak ilk satirda baslar). */
@Composable
private fun TvTrackSheet(exo: Player, tick: Int, subSize: Int = 1, onSubSize: ((Int) -> Unit)? = null, onClose: () -> Unit) {
    androidx.compose.runtime.key(tick) {
        data class Opt(val gi: Int, val ti: Int, val label: String, val selected: Boolean)
        val audio = mutableListOf<Opt>()
        val text = mutableListOf<Opt>()
        exo.currentTracks.groups.forEachIndexed { gi, g ->
            val isAudio = g.type == C.TRACK_TYPE_AUDIO
            val isText = g.type == C.TRACK_TYPE_TEXT
            if (!isAudio && !isText) return@forEachIndexed
            for (ti in 0 until g.length) {
                if (!g.isTrackSupported(ti)) continue
                val f = g.getTrackFormat(ti)
                val label = if (isAudio) {
                    (f.language ?: f.label ?: "Ses ${audio.size + 1}") +
                        (if (f.bitrate > 0) " • ${f.bitrate / 1000}k" else "")
                } else {
                    (f.language ?: f.label ?: "Altyazı ${text.size + 1}")
                }
                val opt = Opt(gi, ti, label, g.isTrackSelected(ti))
                if (isAudio) audio.add(opt) else text.add(opt)
            }
        }
        val params = remember(tick) { exo.trackSelectionParameters }
        var firstFocusDone by remember { mutableStateOf(false) }
        TvSheet(title = "Ses ve altyazı", onClose = onClose) {
            Text("Ses", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
            if (audio.isEmpty()) Text("Ses parçası bulunamadı", fontSize = 14.sp, color = PTx2)
            audio.forEachIndexed { i, o ->
                TvSheetRow(o.label, "", o.selected, focusFirst = i == 0 && !firstFocusDone) {
                    firstFocusDone = true
                    val group = exo.currentTracks.groups[o.gi].mediaTrackGroup
                    exo.trackSelectionParameters = params.buildUpon()
                        .setOverrideForType(TrackSelectionOverride(group, listOf(o.ti)))
                        .build()
                    onClose()
                }
            }
            Text("Altyazı", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            TvSheetRow("Kapalı", "", text.none { it.selected }) {
                exo.trackSelectionParameters = params.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                onClose()
            }
            text.forEach { o ->
                TvSheetRow(o.label, "", o.selected) {
                    val group = exo.currentTracks.groups[o.gi].mediaTrackGroup
                    exo.trackSelectionParameters = params.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(group, listOf(o.ti)))
                        .build()
                    onClose()
                }
            }
            if (onSubSize != null) {
            Text("Altyazı boyutu", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Küçük", "Orta", "Büyük").forEachIndexed { i, n ->
                    var focused by remember { mutableStateOf(false) }
                    Box(
                        Modifier.height(52.dp)
                            .onFocusChanged { focused = it.isFocused }
                            .tvFocusRing(focused, 99.dp, 1.07f)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (subSize == i) Color.White else Color.Transparent)
                            .border(1.dp, PLine, RoundedCornerShape(99.dp))
                            .tvClickableNoRipple { onSubSize(i) }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(n, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            color = if (subSize == i) Color.Black else PTx2)
                    }
                }
            }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** TV kanal paneli (sagdan kayar, ilk odakta mevcut kanal). */
@Composable
private fun TvChannelPanel(
    channels: List<StalkerChannel>,
    currentId: String?,
    onPick: (Int) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }
    val listFr = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color(0x80000000))) {
        Column(
            Modifier.fillMaxHeight().width(420.dp).align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(28.dp, 0.dp, 0.dp, 28.dp))
                .background(Color(0xFF171722))
                .padding(18.dp, 20.dp)
        ) {
            Text("Kanallar", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(6.dp, 0.dp, 6.dp, 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(channels.take(100).size) { pos ->
                    val ch = channels[pos]
                    val idx = channels.indexOfFirst { it.id == ch.id }
                    var focused by remember { mutableStateOf(false) }
                    if (pos == 0) {
                        LaunchedEffect(Unit) {
                            try { listFr.requestFocus() } catch (_: Exception) { }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .then(if (pos == 0) Modifier.focusRequester(listFr) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .tvFocusRing(focused, 16.dp, 1.03f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (ch.id == currentId) Color.White.copy(alpha = 0.1f)
                                else Color.Transparent
                            )
                            .tvClickableNoRipple { onPick(idx) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (ch.logo.isNotBlank()) {
                            AsyncImage(model = ch.logo, contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                        } else {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                .background(tileBrush(ch.id)),
                                contentAlignment = Alignment.Center) {
                                Text(initialsOf(ch.name), color = Color.White, fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ch.name, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(ch.genre, fontSize = 13.sp, color = PTx2,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    // panele girince ilk satir odakta
    LaunchedEffect(Unit) {
        try { listFr.requestFocus() } catch (_: Exception) { }
    }
}

/** TV bolum paneli (odak mevcut bolumde baslar). */
@Composable
private fun TvEpisodePanel(
    title: String,
    episodes: List<com.bayram.xqtvapp.data.EpisodeEntry>,
    current: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }
    val curFr = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color(0x80000000))) {
        Column(
            Modifier.fillMaxHeight().width(420.dp).align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(28.dp, 0.dp, 0.dp, 28.dp))
                .background(Color(0xFF171722))
                .padding(18.dp, 20.dp)
        ) {
            Text(title.ifBlank { "Bölümler" }, fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(6.dp, 0.dp, 6.dp, 12.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(episodes.size) { i ->
                    val ep = episodes[i]
                    var focused by remember { mutableStateOf(false) }
                    if (i == current) {
                        LaunchedEffect(Unit) {
                            try { curFr.requestFocus() } catch (_: Exception) { }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .then(if (i == current) Modifier.focusRequester(curFr) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .tvFocusRing(focused, 16.dp, 1.03f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (i == current) Color.White.copy(alpha = 0.1f)
                                else Color.Transparent
                            )
                            .tvClickableNoRipple { onPick(i) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(110.dp).height(62.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(tileBrush(ep.id)),
                            contentAlignment = Alignment.Center) {
                            Text("▶", color = Color.White, fontSize = 17.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("S${ep.season} B${ep.episode}",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PTx2)
                            Text(ep.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (i == current) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White,
                                modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
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
            Text("Yayın açılamadı", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(Modifier.height(10.dp))
            Text(msg, color = Color.Gray, fontSize = 17.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvPillBtn("Tekrar dene", primary = true, onClick = onRetry, focusMe = fr)
                TvPillBtn("Geri dön", primary = false, onClick = onBack)
            }
        }
    }
    LaunchedEffect(Unit) {
        try { fr.requestFocus() } catch (_: Exception) { }
    }
}

@Composable
private fun TvEndBox2(
    top: String,
    title: String,
    sub: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onBack: () -> Unit
) {
    val fr = remember { FocusRequester() }
    var firstFocused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0xEE000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(top, color = Color.Gray, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(sub, color = Color.Gray, fontSize = 16.sp)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                var focused by remember { mutableStateOf(false) }
                Box(
                    Modifier.height(60.dp)
                        .focusRequester(fr)
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) firstFocused = true
                        }
                        .tvFocusRing(focused, 99.dp, 1.07f)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White)
                        .tvClickableNoRipple(onPrimary)
                        .padding(horizontal = 34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(primaryLabel, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                }
                TvPillBtn("Çık", primary = false, onClick = onBack)
            }
        }
    }
    LaunchedEffect(Unit) {
        try { fr.requestFocus() } catch (_: Exception) { }
    }
}
