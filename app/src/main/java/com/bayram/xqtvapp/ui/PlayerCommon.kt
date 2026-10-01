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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    /** Hata aninda sunucunun gercek HTTP cevabini olc (tani icin). Or: "HTTP 403". */
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
}

@Composable
fun TrackDialog(exo: ExoPlayer, tick: Int, onClose: () -> Unit) {
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
