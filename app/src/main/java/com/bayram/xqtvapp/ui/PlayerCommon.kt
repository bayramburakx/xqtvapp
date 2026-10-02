package com.bayram.xqtvapp.ui

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun fmtMs(ms: Long): String {
    if (ms < 0) return "00:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

private data class TrackOpt(val groupIdx: Int, val trackIdx: Int, val label: String, val selected: Boolean)

/**
 * Guclendirilmis backend:
 * - Ozel HTTP kaynagi: MAG/Xtream uyumlu UA, 15 sn timeout, capraz protokol yonlendirme
 * - Bant genisligi olcer (uyarlamali HLS secimi icin)
 * - Canli yayin icin dusuk gecikmeli tampon; VOD icin cift tampon
 */
object PlayerBackend {
    private const val UA = "Mozilla/5.0 (Linux; Android 13; XqTV) AppleWebKit/537.36 Chrome/120 Safari/537.36"

    fun build(context: Context, url: String, isLive: Boolean, headers: Map<String, String> = emptyMap()): ExoPlayer {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(UA)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        if (headers.isNotEmpty()) httpFactory.setDefaultRequestProperties(headers)

        val loadControl = if (isLive) {
            DefaultLoadControl.Builder()
                .setAllocator(DefaultAllocator(true, 16 * 1024))
                .setBufferDurationsMs(5_000, 15_000, 1_000, 2_000)
                .setTargetBufferBytes(-1)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setAllocator(DefaultAllocator(true, 16 * 1024))
                .setBufferDurationsMs(20_000, 60_000, 2_000, 5_000)
                .build()
        }

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
            }
    }

    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun probeStatus(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            try {
                val rb = Request.Builder().url(url).head()
                    .header("User-Agent", UA)
                headers.forEach { (k, v) -> rb.header(k, v) }
                probeClient.newCall(rb.build()).execute().use { resp ->
                    "HTTP ${resp.code}"
                }
            } catch (e: Exception) {
                "ulaşılamadı (${e.message?.take(60)})"
            }
        }

    /**
     * Detay sayfasindaki "Ses ve altyazi" tercihlerini ilk acilista uygula.
     * audio: "auto" | dil kodu ("tr", "en"...); sub: "off" | "auto" | dil kodu.
     */
    fun applyTrackPrefs(exo: ExoPlayer, audioPref: String, subPref: String) {
        try {
            if (audioPref.isNotBlank() && audioPref != "auto") {
                exo.currentTracks.groups.forEach { g ->
                    if (g.type != C.TRACK_TYPE_AUDIO) return@forEach
                    for (ti in 0 until g.length) {
                        if (!g.isTrackSupported(ti)) continue
                        val lang = g.getTrackFormat(ti).language ?: ""
                        if (lang.startsWith(audioPref, ignoreCase = true)) {
                            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                .setOverrideForType(
                                    TrackSelectionOverride(g.mediaTrackGroup, listOf(ti))
                                ).build()
                            return
                        }
                    }
                }
            }
            when (subPref) {
                "off" -> exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                "auto" -> { /* varsayilan */ }
                else -> {
                    exo.currentTracks.groups.forEach { g ->
                        if (g.type != C.TRACK_TYPE_TEXT) return@forEach
                        for (ti in 0 until g.length) {
                            if (!g.isTrackSupported(ti)) continue
                            val lang = g.getTrackFormat(ti).language ?: ""
                            if (lang.startsWith(subPref, ignoreCase = true)) {
                                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(
                                        TrackSelectionOverride(g.mediaTrackGroup, listOf(ti))
                                    ).build()
                                return
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) { }
    }

    /** Gercek video yukseklikleri (kalite listesi icin), buyukten kucuge. */
    fun videoHeights(exo: ExoPlayer): List<Int> {
        return try {
            exo.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_VIDEO }
                .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).height } }
                .filter { it > 0 }
                .distinct()
                .sortedDescending()
        } catch (_: Exception) { emptyList() }
    }

    /** height=null -> otomatik. */
    fun setQuality(exo: ExoPlayer, height: Int?) {
        try {
            var b = exo.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            if (height != null) {
                exo.currentTracks.groups.forEach { g ->
                    if (g.type != C.TRACK_TYPE_VIDEO) return@forEach
                    for (ti in 0 until g.length) {
                        if (!g.isTrackSupported(ti)) continue
                        if (g.getTrackFormat(ti).height == height) {
                            b = b.setOverrideForType(
                                TrackSelectionOverride(g.mediaTrackGroup, listOf(ti))
                            )
                            exo.trackSelectionParameters = b.build()
                            return
                        }
                    }
                }
            }
            exo.trackSelectionParameters = b.build()
        } catch (_: Exception) { }
    }

    val SUB_SIZES_SP = listOf(15f, 19f, 24f)
}

@Composable
fun TrackDialog(
    exo: ExoPlayer,
    tick: Int,
    onClose: () -> Unit,
    subSize: Int = 1,
    onSubSize: ((Int) -> Unit)? = null
) {    key(tick) {
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
                    Text("Ses", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 13.sp)
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
                    Text("Altyazı", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 13.sp)
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
                    if (onSubSize != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Altyazı boyutu", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)) {
                            listOf("Küçük", "Orta", "Büyük").forEachIndexed { i, n ->
                                FilterChip(
                                    selected = subSize == i,
                                    onClick = { onSubSize(i) },
                                    label = { Text(n) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onClose) { Text("Kapat") } }
        )
    }
}

@Composable
fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MoreRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Text(value, color = androidx.compose.ui.graphics.Color.Gray, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

@Composable
fun SleepDialog(sleepMin: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Uyku zamanlayıcı") },
        text = {
            Column {
                listOf(0, 10, 20, 30, 60).forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(m) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = sleepMin == m, onClick = { onPick(m) })
                        Spacer(Modifier.width(8.dp))
                        Text(if (m == 0) "Kapalı" else "$m dakika sonra duraklat")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Kapat") } }
    )
}

// ---------- paylasilan player sayfalari ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetShell(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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

@Composable
fun SheetOption(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(20.dp).clip(CircleShape)
                .then(if (on) Modifier.background(Color.White)
                else Modifier.border(2.dp, PLine, CircleShape))
        )
    }
    HorizontalDivider(color = PLine)
}

@Composable
fun EpisodeSidePanel(
    title: String,
    episodes: List<com.bayram.xqtvapp.data.EpisodeEntry>,
    current: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it }
    ) {
        Box(Modifier.fillMaxSize().background(Color(0x80000000))
            .clickableNoRipple(onClose)) {
            Column(
                Modifier.fillMaxHeight().width(340.dp).align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(28.dp, 0.dp, 0.dp, 28.dp))
                    .background(Color(0xFF171722))
                    .padding(14.dp, 16.dp)
            ) {
                Text(title.ifBlank { "Bölümler" }, fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(6.dp, 0.dp, 6.dp, 12.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(episodes.size) { i ->
                        val ep = episodes[i]
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (i == current) Color.White.copy(alpha = 0.1f)
                                    else Color.Transparent
                                )
                                .clickableNoRipple { onPick(i) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(104.dp).height(58.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(tileBrush(ep.id)),
                                contentAlignment = Alignment.Center) {
                                Text("▶", color = Color.White, fontSize = 16.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("S${ep.season} B${ep.episode}",
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PTx2)
                                Text(ep.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (i == current) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.White,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
