package com.bayram.xqtvapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.unit.Dp
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
import com.bayram.xqtvapp.Session
import com.bayram.xqtvapp.data.EpgCache
import com.bayram.xqtvapp.data.EpgXml
import com.bayram.xqtvapp.data.EpgEntry
import com.bayram.xqtvapp.data.FavoritesStore
import com.bayram.xqtvapp.data.StalkerChannel
import com.bayram.xqtvapp.data.StalkerClient
import com.bayram.xqtvapp.data.XtreamClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LiveRed = Color(0xFFFF453A)
private val LiveGold = Color(0xFFFFD60A)
private val LiveResizeNames = listOf("Sığdır", "Doldur", "Zoom")
private val LiveResizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
)

private fun epgNow(list: List<EpgEntry>, now: Long = System.currentTimeMillis() / 1000): EpgEntry? =
    list.firstOrNull { it.startEpoch <= now && now < it.endEpoch }

private fun epgNext(list: List<EpgEntry>, now: Long = System.currentTimeMillis() / 1000): EpgEntry? =
    list.firstOrNull { it.startEpoch >= now }

/**
 * Canli TV oynatici: zap, OSD, mini EPG, yan kanal paneli, program sayfasi,
 * gercek kalite, ses/altyazi, goruntu orani, sessiz otomatik format.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivePlayerScreen(req: PlayReq, onBack: () -> Unit) {
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
    var autoNote by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var retryKey by remember { mutableIntStateOf(0) }
    var resizeIdx by remember { mutableIntStateOf(0) }
    var muted by remember { mutableStateOf(false) }
    var qualityH by remember { mutableIntStateOf(-1) } // -1 otomatik
    var showUi by remember { mutableStateOf(true) }
    var uiTick by remember { mutableIntStateOf(0) }
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

    // EPG: gecerli kanal icin gunluk akis (kaynak + internet XMLTV yedegi)
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
    val nowEpg = remember(dayEpg) { epgNow(dayEpg) }
    val nextEpg = remember(dayEpg) { epgNext(dayEpg) }

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
                            autoNote = true
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

    LaunchedEffect(showUi, uiTick, playing) {
        if (showUi && playing) {
            delay(4000)
            showUi = false
        }
    }
    LaunchedEffect(autoNote) {
        if (autoNote) {
            delay(4000)
            autoNote = false
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

    fun poke() { showUi = true; uiTick++ }

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
    }

    fun zapTo(idx: Int) {
        val list = req.zap
        if (list.isEmpty() || zapping) return
        val i = ((idx % list.size) + list.size) % list.size
        val target = list[i]
        zapping = true
        buffering = true
        errorMsg = null
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (exo != null) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).also {
                        it.player = exo
                        it.useController = false
                        it.resizeMode = LiveResizeModes[resizeIdx]
                    }
                },
                update = {
                    it.player = exo
                    it.resizeMode = LiveResizeModes[resizeIdx]
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        // dokunma + dikey kaydirma tek overlay uzerinde (video ustu)
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { poke() })
                }
                .pointerInput(req) {
                    var acc = 0f
                    detectVerticalDragGestures(
                        onDragStart = { acc = 0f },
                        onDragEnd = {
                            if (acc < -70) zapTo(currentIdx + 1)
                            else if (acc > 70) zapTo(currentIdx - 1)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            acc += dragAmount
                            change.consume()
                        }
                    )
                }
        )

        if (buffering && errorMsg == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 4.dp,
                    modifier = Modifier.size(54.dp))
            }
        }

        // filigran: kanal adi
        if (!showUi) {
            Box(Modifier.align(Alignment.TopEnd).padding(18.dp, 18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0x59000000))
                .padding(9.dp, 5.dp)) {
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        // OSD: zap bildirimi
        osd?.let {
            Column(Modifier.align(Alignment.TopStart).padding(22.dp, 90.dp)) {
                Text(it, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold)
                Text(nowEpg?.title ?: (currentCh?.genre ?: ""),
                    color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (showUi) {
            // ust bar
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                    .padding(16.dp, 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCircleButton(Icons.Filled.ArrowBack, onBack)
                Spacer(Modifier.width(12.dp))
                if ((currentCh?.logo?.isNotBlank() == true)) {
                    AsyncImage(model = currentCh!!.logo, contentDescription = null,
                        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit)
                    Spacer(Modifier.width(11.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(meta, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GlassCircleButton(
                    Icons.Filled.Star, { scope.launch { FavoritesStore.toggle(ctx, chId) } },
                    tint = if (isFav) LiveGold else Color.White
                )
                Spacer(Modifier.width(8.dp))
                GlassCircleButton(
                    if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    {
                        muted = !muted
                        exo?.volume = if (muted) 0f else 1f
                        poke()
                    }
                )
            }

            // orta: onceki / oynat / sonraki
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(34.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (req.zap.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickableNoRipple { zapTo(currentIdx - 1); poke() }) {
                        GlassCircleButton(Icons.Filled.KeyboardArrowUp, { zapTo(currentIdx - 1); poke() }, size = 56.dp)
                        Text(req.zap.getOrNull((currentIdx - 1 + req.zap.size) % req.zap.size)?.name ?: "",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(70.dp))
                    }
                }
                IconButton(
                    onClick = { if (playing) exo?.pause() else exo?.play(); poke() },
                    modifier = Modifier.size(84.dp).background(Color.White, CircleShape)
                ) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null,
                        tint = Color.Black, modifier = Modifier.size(32.dp))
                }
                if (req.zap.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickableNoRipple { zapTo(currentIdx + 1); poke() }) {
                        GlassCircleButton(Icons.Filled.KeyboardArrowDown, { zapTo(currentIdx + 1); poke() }, size = 56.dp)
                        Text(req.zap.getOrNull((currentIdx + 1) % req.zap.size)?.name ?: "",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(70.dp))
                    }
                }
            }

            // alt bar
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                    .padding(22.dp, 10.dp, 22.dp, 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playing) {
                        LivePill("CANLI") { }
                    } else {
                        LivePill("CANLIYA DÖN", dim = true) {
                            exo?.play()
                            poke()
                            scope.launch {
                                toast = "Canlı yayına dönüldü"
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    val left = nowEpg?.let { (it.endEpoch - System.currentTimeMillis() / 1000) / 60 }
                    Text(
                        (if (left != null && left > 0) "$left dk kaldı" else "") +
                                (nextEpg?.let { " · Sırada: ${it.title}" } ?: ""),
                        color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(nowEpg?.title ?: title, color = Color.White,
                    fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                if (nowEpg != null) {
                    ProgressLine(nowEpg.progress(), color = Color.White)
                    Spacer(Modifier.height(7.dp))
                    Row {
                        Text(nowEpg.range().substringBefore("–").trim(),
                            color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text(nowEpg.range().substringAfter("–").trim(),
                            color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                } else {
                    Spacer(Modifier.height(10.dp))
                }
                LazyRowPills(
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
                            "Görüntü" -> LiveResizeNames[resizeIdx]
                            else -> it
                        }
                    }
                )
            }
        }

        toast?.let {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color(0xD91E1E2C))
                .padding(18.dp, 10.dp)) {
                Text(it, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                        colors = ButtonDefaults.buttonColors(containerColor = LiveRed),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Tekrar dene", color = Color.White) }
                    TextButton(onClick = onBack) { Text("Geri dön", color = Color.Gray) }
                }
            }
        }
    }

    // yan kanal paneli
    AnimatedVisibility(
        visible = showChannels,
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it }
    ) {
        Box(Modifier.fillMaxSize().background(Color(0x80000000))
            .clickableNoRipple { showChannels = false }) {
            Column(
                Modifier.fillMaxHeight().width(340.dp).align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(28.dp, 0.dp, 0.dp, 28.dp))
                    .background(Color(0xFF171722))
                    .padding(14.dp, 16.dp)
            ) {
                Text("Kanallar", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(6.dp, 0.dp, 6.dp, 12.dp))
                var panelQ by remember(req) { mutableStateOf("") }
                OutlinedTextField(
                    value = panelQ, onValueChange = { panelQ = it },
                    placeholder = { Text("Kanal ara...", fontSize = 13.sp) },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                val panelList = remember(req, panelQ) {
                    (if (panelQ.isBlank()) req.zap
                    else req.zap.filter { it.name.contains(panelQ, true) }).take(100)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(panelList, key = { it.id }) { ch ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (ch.id == currentCh?.id) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                                .clickableNoRipple {
                                    showChannels = false
                                    val i = req.zap.indexOfFirst { it.id == ch.id }
                                    if (i >= 0) zapTo(i)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (ch.logo.isNotBlank()) {
                                AsyncImage(model = ch.logo, contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                            } else {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                    .background(tileBrush(ch.id)),
                                    contentAlignment = Alignment.Center) {
                                    Text(initialsOf(ch.name), color = Color.White, fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ch.name, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(ch.genre, fontSize = 12.sp, color = PTx2,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    // program sayfasi
    if (showProgram) {
        ModalBottomSheetCompat(
            title = "${currentCh?.name ?: ""} · Program",
            onClose = { showProgram = false }
        ) {
            val now = System.currentTimeMillis() / 1000
            val rows = dayEpg.filter { it.endEpoch > now - 3600 }.take(12)
            if (rows.isEmpty()) {
                Text("Program bilgisi yok.", color = PTx2, fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 12.dp))
            }
            rows.forEach { p ->
                val isNow = p.startEpoch <= now && now < p.endEpoch
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("${p.range()}",
                            fontSize = 12.sp, color = PTx2)
                    }
                    if (isNow) {
                        Text("ŞİMDİ", color = LiveRed, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                HorizontalDivider(color = PLine)
            }
        }
    }

    if (showTracks && exo != null) {
        TrackDialog(exo = exo, tick = trackTick, onClose = { showTracks = false })
    }

    if (showQuality && exo != null) {
        val heights = remember(exo, trackTick) { PlayerBackend.videoHeights(exo) }
        ModalBottomSheetCompat(title = "Kalite", onClose = { showQuality = false }) {
            QualityRow("Otomatik (önerilen)", qualityH == -1) {
                qualityH = -1
                PlayerBackend.setQuality(exo, null)
                showQuality = false
            }
            heights.forEach { h ->
                QualityRow("${h}p", qualityH == h) {
                    qualityH = h
                    PlayerBackend.setQuality(exo, h)
                    showQuality = false
                }
            }
            if (heights.isEmpty()) {
                Text("Tek kalite mevcut.", color = PTx2, fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }

    if (showMore) {
        ModalBottomSheetCompat(title = "Görüntü oranı", onClose = { showMore = false }) {
            LiveResizeNames.forEachIndexed { i, n ->
                QualityRow(n, resizeIdx == i) {
                    resizeIdx = i
                    showMore = false
                }
            }
            QualityRow(
                if (sleepMin > 0) "Uyku: $sleepMin dk (kapat)" else "Uyku zamanlayıcı",
                false
            ) {
                showMore = false
                showTimer = true
            }
        }
    }

    if (showTimer) {
        SleepDialog(
            sleepMin = sleepMin,
            onPick = { sleepMin = it; showTimer = false },
            onClose = { showTimer = false }
        )
    }
}

@Composable
private fun GlassCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    tint: Color = Color.White
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size).clip(CircleShape)
            .background(Color(0x9E222230))
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun LivePill(text: String, dim: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(7.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (dim) Color.White.copy(alpha = 0.2f) else LiveRed
        ),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun LazyRowPills(items: List<String>, onPick: (String) -> Unit, labelOf: (String) -> String) {
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

@Composable
private fun QualityRow(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Box(Modifier.size(20.dp).clip(CircleShape)
            .then(if (on) Modifier.background(Color.White)
            else Modifier.border(2.dp, PLine, CircleShape)))
    }
    HorizontalDivider(color = PLine)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModalBottomSheetCompat(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF171722),
        dragHandle = {
            Box(Modifier.padding(10.dp).width(40.dp).height(5.dp)
                .clip(RoundedCornerShape(9.dp)).background(PLine))
        }
    ) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 24.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 4.dp))
            content()
        }
    }
}
