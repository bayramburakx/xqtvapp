package com.bayram.xqtvapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.bayram.xqtvapp.PlayReq
import kotlinx.coroutines.delay

private val LiveRed = Color(0xFFE50914)
private val LiveResizeNames = listOf("Sığdır", "Doldur", "Zoom")
private val LiveResizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
)

/**
 * Canli TV oynatici: sade arayuz, format otomatik secilir (HLS olmazsa sessizce TS'ye gecer,
 * kullaniciya sorulmaz), seek yok, CANLI rozeti.
 */
@Composable
fun LivePlayerScreen(req: PlayReq, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var currentUrl by remember { mutableStateOf(req.url) }
    var triedAlt by remember { mutableStateOf(false) }
    var autoNote by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var retryKey by remember { mutableIntStateOf(0) }
    var resizeIdx by remember { mutableIntStateOf(0) }
    var showUi by remember { mutableStateOf(true) }
    var uiTick by remember { mutableIntStateOf(0) }
    var showTracks by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var sleepMin by remember { mutableIntStateOf(0) }
    var trackTick by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val exo = remember(currentUrl, retryKey) {
        errorMsg = null; buffering = true; playing = true
        try {
            PlayerBackend.build(ctx, currentUrl, isLive = true).apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        buffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) errorMsg = null
                    }
                    override fun onIsPlayingChanged(v: Boolean) { playing = v }
                    override fun onPlayerError(error: PlaybackException) {
                        // OTOMATIK FORMAT SECIMI: once diger formati sessizce dene
                        if (req.altUrl != null && !triedAlt) {
                            triedAlt = true
                            autoNote = true
                            currentUrl = if (currentUrl == req.url) req.altUrl else req.url
                            retryKey++
                        } else {
                            buffering = false; playing = false
                            errorMsg = "Yayın açılamadı (${error.errorCodeName})"
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
    LaunchedEffect(sleepMin) {
        if (sleepMin > 0) {
            delay(sleepMin * 60_000L)
            exo?.pause()
            sleepMin = 0
        }
    }

    val interaction = remember { MutableInteractionSource() }
    fun poke() { showUi = true; uiTick++ }

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
                        it.resizeMode = LiveResizeModes[resizeIdx]
                    }
                },
                update = { it.resizeMode = LiveResizeModes[resizeIdx] },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (buffering && errorMsg == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = LiveRed, strokeWidth = 5.dp, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Canlı yayın bağlanıyor...", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        if (showUi) {
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
                    Text(req.title, color = Color.White, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                    Text("● CANLI", color = LiveRed, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                TextButton(onClick = { showTracks = true; trackTick++; poke() }) {
                    Text("Ses", color = Color.White, fontSize = 12.sp)
                }
                TextButton(onClick = { showMore = true; poke() }) {
                    Text("•••", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        if (showUi && !buffering && errorMsg == null && exo != null) {
            Box(Modifier.align(Alignment.Center)) {
                IconButton(
                    onClick = { if (playing) exo.pause() else exo.play(); poke() },
                    modifier = Modifier.size(78.dp).background(Color(0x66000000), CircleShape)
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null,
                        tint = Color.White, modifier = Modifier.size(52.dp)
                    )
                }
            }
        }

        if (showUi) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                    .padding(16.dp, 10.dp, 16.dp, 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("● CANLI YAYIN", color = LiveRed, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = {
                        resizeIdx = (resizeIdx + 1) % 3
                        poke()
                    }) { Text(LiveResizeNames[resizeIdx], color = Color.White, fontSize = 12.sp) }
                }
                if (autoNote) {
                    Text("En uygun yayın formatı otomatik seçildi", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }

        if (errorMsg != null) {
            Box(Modifier.fillMaxSize().background(Color(0xEE000000)), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Yayın açılamadı", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(errorMsg!!, color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("İnternetini ve kanalın durumunu kontrol et.", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { triedAlt = false; retryKey++ },
                        colors = ButtonDefaults.buttonColors(containerColor = LiveRed),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Tekrar dene", color = Color.White) }
                    TextButton(onClick = onBack) { Text("Geri dön", color = Color.Gray) }
                }
            }
        }
    }

    if (showTracks && exo != null) {
        TrackDialog(exo = exo, tick = trackTick, onClose = { showTracks = false })
    }

    if (showMore && exo != null) {
        AlertDialog(
            onDismissRequest = { showMore = false },
            title = { Text("Canlı yayın ayarları", fontSize = 16.sp) },
            text = {
                Column {
                    MoreRow("Görüntü", LiveResizeNames[resizeIdx]) {
                        resizeIdx = (resizeIdx + 1) % 3
                    }
                    MoreRow("Uyku", if (sleepMin > 0) "$sleepMin dk" else "Kapalı") {
                        showMore = false
                        showTimer = true
                    }
                    MoreRow("Yayın formatı", "Otomatik") { }
                }
            },
            confirmButton = { TextButton(onClick = { showMore = false }) { Text("Kapat") } }
        )
    }

    if (showTimer) {
        SleepDialog(
            sleepMin = sleepMin,
            onPick = { sleepMin = it; showTimer = false },
            onClose = { showTimer = false }
        )
    }
}
