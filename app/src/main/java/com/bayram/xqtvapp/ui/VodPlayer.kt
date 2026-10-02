package com.bayram.xqtvapp.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.util.TypedValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.bayram.xqtvapp.PlayReq
import com.bayram.xqtvapp.data.EpisodeEntry
import com.bayram.xqtvapp.data.FavoritesStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SUB_SIZES = listOf("Küçük", "Orta", "Büyük")
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val RESIZE_NAMES = listOf("Sığdır", "Doldur", "Zoom")
private val RESIZE_MODES = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
)

/**
 * Film/dizi oynatici: cift dokunus +-10sn, ses/parlaklik surukleme,
 * kilit modu, uyku yok, devam-toast, bolum paneli, gercek kalite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VodPlayerScreen(req: PlayReq, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val errScope = rememberCoroutineScope()
    val activity = ctx as? Activity
    val audioMan = remember {
        ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

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
    var locked by remember { mutableStateOf(false) }
    var qualityH by remember { mutableIntStateOf(-1) }
    var subSizeIdx by remember { mutableIntStateOf(1) }
    var showUi by remember { mutableStateOf(true) }
    var uiTick by remember { mutableIntStateOf(0) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var showTracks by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showAspect by remember { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    var trackTick by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(-1) }
    var ripple by remember { mutableStateOf<String?>(null) }
    var hud by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var resumeToast by remember { mutableStateOf(req.startMs > 10_000L) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val autoplay by FavoritesStore.autoplayFlow(ctx).collectAsState(initial = true)
    val saveScope = rememberCoroutineScope()
    var resumeKey by remember(req) { mutableStateOf(req.resumeId) }
    val trackPrefs by FavoritesStore.trackPrefsFlow(ctx).collectAsState(initial = Pair("auto", "off"))
    val subSizePref by FavoritesStore.subSizeFlow(ctx).collectAsState(initial = 1)
    var prefsApplied by remember(req) { mutableStateOf(false) }
    var tapJob by remember { mutableStateOf<Job?>(null) }

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
    val nextEp: EpisodeEntry? = if (hasSeries) req.episodes.getOrNull(currentIdx + 1) else null
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
                            if (req.startMs > 10_000L && currentPosition < 5_000L) {
                                seekTo(req.startMs)
                            }
                        }
                        if (state == Player.STATE_ENDED) {
                            playing = false; ended = true
                            saveScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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
                setPlaybackSpeed(SPEEDS[speedIdx])
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

    val saveScope2 = saveScope
    fun saveNow() {
        val p = exo?.currentPosition ?: 0L
        val d = exo?.duration ?: 0L
        if (resumeKey.isNotEmpty() && d > 0 && p > 5_000L && p < (d * 0.98).toLong()) {
            saveScope2.launch(kotlinx.coroutines.Dispatchers.IO) {
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
    LaunchedEffect(showUi, uiTick, playing) {
        if (showUi && playing && !ended && !locked) {
            delay(4000)
            showUi = false
        }
    }
    LaunchedEffect(ripple) {
        if (ripple != null) {
            delay(550)
            ripple = null
        }
    }
    LaunchedEffect(hud) {
        if (hud != null) {
            delay(900)
            hud = null
        }
    }
    LaunchedEffect(resumeToast) {
        if (resumeToast) {
            delay(5000)
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

    fun poke() {
        if (locked) {
            showUi = true; uiTick++
            return
        }
        showUi = true; uiTick++
    }
    fun togglePlay() {
        if (playing) exo?.pause() else exo?.play()
        poke()
    }
    fun seekBy(ms: Long) {
        exo?.seekTo(((exo?.currentPosition ?: 0L) + ms).coerceAtLeast(0L))
        poke()
    }
    fun playEpisode(idx: Int) {
        val ep = req.episodes.getOrNull(idx) ?: return
        currentIdx = idx
        currentUrl = ep.url
        resumeKey = "series_" + ep.id
        retryKey++
    }
    fun setVolume(v: Int) {
        audioMan?.let {
            val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            it.setStreamVolume(AudioManager.STREAM_MUSIC, (v / 100f * max).toInt().coerceIn(0, max), 0)
        }
        muted = v == 0
    }
    fun getVolume(): Int {
        audioMan?.let {
            val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max <= 0) return 70
            return (it.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max)
        }
        return 70
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // mektup kutusu alanlari saf siyah (arka plan deseni yok)

        if (exo != null) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).also {
                        it.player = exo
                        it.useController = false
                        it.resizeMode = RESIZE_MODES[resizeIdx]
                        playerView = it
                    }
                },
                update = {
                    it.player = exo
                    it.resizeMode = RESIZE_MODES[resizeIdx]
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // dokunma alani: tek tik + cift tik + dikey surukleme
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(locked) {
                        detectTapGestures(
                            onDoubleTap = { off ->
                                tapJob?.cancel()
                                tapJob = null
                                if (locked) {
                                    showUi = true; uiTick++
                                    return@detectTapGestures
                                }
                                when {
                                    off.x < w.toPx() * 0.4f -> {
                                        seekBy(-10_000); ripple = "L"
                                    }
                                    off.x > w.toPx() * 0.6f -> {
                                        seekBy(10_000); ripple = "R"
                                    }
                                    else -> togglePlay()
                                }
                                poke()
                            },
                            onTap = {
                                tapJob?.cancel()
                                tapJob = scope.launch {
                                    delay(300)
                                    if (locked) {
                                        showUi = true; uiTick++
                                    } else if (showUi) {
                                        if (playing) showUi = false
                                    } else {
                                        poke()
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(locked) {
                        var accY = 0f
                        var startX = 0f
                        var startVol = 70
                        var startBri = 0.7f
                        var active = false
                        detectVerticalDragGestures(
                            onDragStart = { off ->
                                startX = off.x
                                startVol = getVolume()
                                startBri = activity?.window?.attributes?.screenBrightness
                                    ?.takeIf { it >= 0 } ?: 0.7f
                                accY = 0f
                                active = false
                            },
                            onDragEnd = { active = false },
                            onVerticalDrag = { _, dragAmount ->
                                if (locked) return@detectVerticalDragGestures
                                accY += dragAmount
                                if (!active && kotlin.math.abs(accY) < 14) return@detectVerticalDragGestures
                                active = true
                                // asagi surukleme degeri dusurur (dragAmount>0 asagi)
                                val d = -accY / 400f * 100
                                if (startX < w.toPx() / 2f) {
                                    val b = (startBri + d / 100f).coerceIn(0.15f, 1f)
                                    activity?.window?.let {
                                        val lp = it.attributes
                                        lp.screenBrightness = b
                                        it.attributes = lp
                                    }
                                    hud = Pair("bri", (b * 100).toInt())
                                } else {
                                    val v = (startVol + d).toInt().coerceIn(0, 100)
                                    setVolume(v)
                                    hud = Pair("vol", v)
                                }
                            }
                        )
                    }
            )
        }

        if (buffering && errorMsg == null && !ended) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 4.dp,
                    modifier = Modifier.size(54.dp))
            }
        }

        // +-10 dalga efekti
        ripple?.let { side ->
            Box(
                Modifier.align(if (side == "L") Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 36.dp).size(120.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (side == "L") "−10 sn" else "+10 sn",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        // orta HUD (ses/parlaklik)
        hud?.let { (kind, v) ->
            Box(Modifier.align(Alignment.Center)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0x9E20202C))
                .padding(20.dp, 14.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(180.dp)) {
                    Icon(
                        if (kind == "vol") Icons.Filled.VolumeUp else Icons.Filled.VolumeUp,
                        null, tint = Color.White, modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { v / 100f },
                        modifier = Modifier.fillMaxWidth().height(5.dp)
                            .clip(RoundedCornerShape(9.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }
        }

        // devam toast'i
        AnimatedVisibility(
            visible = resumeToast && errorMsg == null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(99.dp))
                    .background(Color(0xDE1E1E2C))
                    .padding(18.dp, 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${fmtMs(req.startMs)}’den devam ediliyor",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        exo?.seekTo(0L)
                        resumeToast = false
                        poke()
                    },
                    shape = RoundedCornerShape(99.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                ) { Text("Baştan başla", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }

        if (showUi && !ended && !locked) {
            // ust bar
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                    .padding(16.dp, 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCircleButton2(Icons.Filled.ArrowBack) { goBack() }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(displayTitle, color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (hasSeries) Text(req.seriesTitle, color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp, maxLines = 1)
                }
                GlassCircleButton2(Icons.Filled.Lock) {
                    locked = true
                    showUi = false
                }
                Spacer(Modifier.width(8.dp))
                GlassCircleButton2(if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp) {
                    muted = !muted
                    exo?.volume = if (muted) 0f else 1f
                    poke()
                }
            }

            // orta
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkipButton("-10", { seekBy(-10_000); ripple = "L" })
                IconButton(
                    onClick = { togglePlay() },
                    modifier = Modifier.size(86.dp).background(Color.White, CircleShape)
                ) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null,
                        tint = Color.Black, modifier = Modifier.size(34.dp))
                }
                SkipButton("+10", { seekBy(10_000); ripple = "R" })
            }

            // alt bar
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE0000000))))
                    .padding(22.dp, 10.dp, 22.dp, 18.dp)
            ) {
                Slider(
                    value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                    onValueChange = { f -> exo?.seekTo((f * dur).toLong()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White, activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )
                Row {
                    Text(fmtMs(pos), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(fmtMs(dur), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                PillRow(
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
                            "Hız" -> "${SPEEDS[speedIdx]}x"
                            "Kalite" -> if (qualityH > 0) "${qualityH}p" else "Otomatik"
                            "Görüntü" -> RESIZE_NAMES[resizeIdx]
                            else -> it
                        }
                    }
                )
            }
        }

        // kilit acma hapi
        if (locked) {
            Button(
                onClick = {
                    locked = false
                    poke()
                },
                shape = RoundedCornerShape(99.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x9E20202C)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp).height(48.dp)
            ) { Text("Kilidi aç", color = Color.White, fontWeight = FontWeight.SemiBold) }
        }

        // sonraki bolum
        if (ended && errorMsg == null) {
            val nx = nextEp
            if (nx != null) {
                Box(Modifier.fillMaxSize().background(Color(0xEE000000)),
                    contentAlignment = Alignment.Center) {
                    Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Sıradaki bölüm", color = Color.Gray, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("S${nx.season} • B${nx.episode} — ${nx.title}",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        if (countdown > 0) Text("$countdown sn içinde başlıyor...",
                            color = Color.Gray, fontSize = 13.sp)
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
                            onClick = { countdown = -1; goBack() },
                            shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                        ) { Text("Çık", color = Color.White) }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xEE000000)),
                    contentAlignment = Alignment.Center) {
                    Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Bitti", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { retryKey++ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Baştan izle", color = Color.Black) }
                        TextButton(onClick = { goBack() }) { Text("Geri dön", color = Color.Gray) }
                    }
                }
            }
        }

        if (errorMsg != null) {
            Box(Modifier.fillMaxSize().background(Color(0xEE000000)), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Yayın açılamadı", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(errorMsg!!, color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { retryKey++ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Tekrar dene", color = Color.Black) }
                    TextButton(onClick = { goBack() }) { Text("Geri dön", color = Color.Gray) }
                }
            }
        }
    }

    if (showEpisodes && hasSeries) {
        EpisodeSidePanel(
            title = req.seriesTitle,
            episodes = req.episodes,
            current = currentIdx,
            onPick = { playEpisode(it); showEpisodes = false },
            onClose = { showEpisodes = false }
        )
    }

    if (showTracks && exo != null) {
        TrackDialog(
            exo = exo, tick = trackTick, onClose = { showTracks = false },
            subSize = subSizeIdx,
            onSubSize = { i ->
                subSizeIdx = i
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    FavoritesStore.setSubSize(ctx, i)
                }
                playerView?.subtitleView?.setFixedTextSize(
                    TypedValue.COMPLEX_UNIT_SP, PlayerBackend.SUB_SIZES_SP[i.coerceIn(0, 2)]
                )
            }
        )
    }

    if (showSpeed && exo != null) {
        SheetShell(title = "Oynatma hızı", onClose = { showSpeed = false }) {
            SPEEDS.forEachIndexed { i, s ->
                SheetOption("${s}x" + if (s == 1f) " (normal)" else "", speedIdx == i) {
                    speedIdx = i
                    exo.setPlaybackSpeed(s)
                    showSpeed = false
                }
            }
        }
    }

    if (showQuality && exo != null) {
        val heights = remember(exo, trackTick, retryKey) { PlayerBackend.videoHeights(exo) }
        SheetShell(title = "Kalite", onClose = { showQuality = false }) {
            SheetOption("Otomatik (önerilen)", qualityH == -1) {
                qualityH = -1
                PlayerBackend.setQuality(exo, null)
                showQuality = false
            }
            heights.forEach { h ->
                SheetOption("${h}p", qualityH == h) {
                    qualityH = h
                    PlayerBackend.setQuality(exo, h)
                    showQuality = false
                }
            }
        }
    }

    if (showAspect) {
        SheetShell(title = "Görüntü oranı", onClose = { showAspect = false }) {
            RESIZE_NAMES.forEachIndexed { i, n ->
                SheetOption(n, resizeIdx == i) {
                    resizeIdx = i
                    showAspect = false
                }
            }
        }
    }
}

@Composable
private fun SkipButton(label: String, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center,
        modifier = Modifier.size(60.dp).clickableNoRipple(onClick)) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun GlassCircleButton2(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).clip(CircleShape)
            .background(Color(0x9E222230))
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun PillRow(items: List<String>, onPick: (String) -> Unit, labelOf: (String) -> String) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach {
            OutlinedButton(
                onClick = { onPick(it) },
                shape = RoundedCornerShape(99.dp),
                modifier = Modifier.height(40.dp)
            ) { Text(labelOf(it), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
