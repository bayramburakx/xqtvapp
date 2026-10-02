package com.bayram.xqtvapp.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bayram.xqtvapp.CatChips
import com.bayram.xqtvapp.Session
import com.bayram.xqtvapp.data.EpgCache
import com.bayram.xqtvapp.data.EpgXml
import com.bayram.xqtvapp.data.EpgEntry
import com.bayram.xqtvapp.data.FavoritesStore
import com.bayram.xqtvapp.data.StalkerChannel
import com.bayram.xqtvapp.data.StalkerClient
import com.bayram.xqtvapp.data.XtreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LiveGold = Color(0xFFFFD60A)

/**
 * Canli TV sayfasi: Kanallar / Program rehberi sekmeleri,
 * favori yildizi, EPG satirlari, mini oynatici cubugu.
 */
@Composable
fun TvTab(
    session: Session,
    onSearch: (String) -> Unit,
    onPlayChannel: (StalkerChannel, String, String?, Map<String, String>, List<StalkerChannel>, Int) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val favs by FavoritesStore.favsFlow(ctx).collectAsState(initial = emptySet())
    var view by remember { mutableStateOf("list") } // list | epg
    var cat by remember { mutableStateOf("Tümü") }
    var busy by remember { mutableStateOf<String?>(null) }
    var lastCh by remember { mutableStateOf<StalkerChannel?>(null) }

    val cats = remember(session) {
        listOf("Tümü", "Favoriler") + session.channels.map { it.genre }.distinct().sorted()
    }
    val list = remember(session, cat, favs) {
        session.channels.filter {
            when (cat) {
                "Tümü" -> true
                "Favoriler" -> favs.contains(it.id)
                else -> it.genre == cat
            }
        }
    }

    // Kanal satirlari: sadece kaynak EPG. Liste kaydirirken arka planda
    // XML indirip isitma yapmaz; internet yedegi yalnizca rehberde.
    var nowMap by remember { mutableStateOf<Map<String, EpgEntry?>>(emptyMap()) }
    var epgDone by remember { mutableStateOf(false) }
    LaunchedEffect(list, session.sourceId) {
        epgDone = false
        nowMap = emptyMap()
        try {
            val sem = kotlinx.coroutines.sync.Semaphore(3)
            coroutineScope {
                list.take(12).map { ch ->
                    async(Dispatchers.IO) {
                        sem.acquire()
                        try {
                            val e = try {
                                EpgXml.lookupNow(ctx, session, ch, includeInternet = false)
                            } catch (_: Exception) { null }
                            ch.id to e
                        } finally { sem.release() }
                    }
                }.awaitAll().forEach { (id, e) ->
                    if (e != null) nowMap = nowMap + (id to e)
                }
            }
        } catch (_: Exception) { }
        epgDone = true
    }

    fun open(ch: StalkerChannel, idx: Int) {
        scope.launch {
            busy = ch.id
            try {
                if (session.stalkerUrl.isNotEmpty()) {
                    val sc = StalkerClient(session.stalkerUrl, session.stalkerMac)
                    val url = sc.createLink(ch.cmd)
                    if (url.isNotBlank()) {
                        lastCh = ch
                        onPlayChannel(ch, url, null, sc.streamHeaders(), list, idx)
                    }
                } else {
                    val headers = if (session.xServer.isNotBlank())
                        mapOf("Referer" to session.xServer.trimEnd('/') + "/") else emptyMap()
                    val alt = if (ch.cmd.endsWith(".m3u8")) ch.cmd.dropLast(5) + ".ts" else null
                    lastCh = ch
                    onPlayChannel(ch, ch.cmd, alt, headers, list, idx)
                }
            } finally { busy = null }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B12))) {
        Row(Modifier.fillMaxWidth().padding(20.dp, 18.dp, 20.dp, 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Canlı TV", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.5).sp)
                Text("${list.size} kanal", color = PTx2, fontSize = 14.sp)
            }
            Box(Modifier.size(38.dp).clip(CircleShape).background(PGlass)
                .clickableNoRipple({ onSearch("live") }), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Search, "Ara", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // Kanallar / Rehber segmesi
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(99.dp)).background(PGlass).padding(4.dp)
        ) {
            listOf("Kanallar" to "list", "Program rehberi" to "epg").forEach { (t, v) ->
                Box(
                    Modifier.weight(1f).height(38.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (view == v) Color.White else Color.Transparent)
                        .clickableNoRipple { view = v },
                    contentAlignment = Alignment.Center
                ) {
                    Text(t, color = if (view == v) Color.Black else PTx2,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        CatChips(cats, cat) { cat = it }

        if (view == "list") {
            if (list.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (cat == "Favoriler") "Burada henüz kanal yok.\nKanalların yıldızına dokunarak favorilerine ekle."
                        else "Bu kategoride kanal yok.",
                        color = PTx2, fontSize = 15.sp, lineHeight = 22.sp
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(list, key = { _, it -> it.id }) { idx, ch ->
                        LiveRow(
                            ch = ch,
                            number = idx + 1,
                            epg = nowMap[ch.id],
                            isFav = favs.contains(ch.id),
                            busy = busy == ch.id,
                            onFav = { scope.launch { FavoritesStore.toggle(ctx, ch.id) } },
                            onClick = { open(ch, idx) }
                        )
                    }
                }
            }
        } else {
            EpgGuide(session, list, onPlay = { ch ->
                open(ch, list.indexOfFirst { it.id == ch.id })
            }, modifier = Modifier.weight(1f))
        }

        // mini oynatici cubugu
        lastCh?.let { lc ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                    .padding(bottom = 104.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x9E20202C))
                    .padding(10.dp, 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (lc.logo.isNotBlank()) {
                    AsyncImage(model = lc.logo, contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)))
                } else {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                        .background(tileBrush(lc.id)),
                        contentAlignment = Alignment.Center) {
                        Text(initialsOf(lc.name), color = Color.White, fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(lc.name, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(nowMap[lc.id]?.title ?: lc.genre, fontSize = 12.sp, color = PTx2,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(
                    onClick = { open(lc, list.indexOfFirst { it.id == lc.id }.takeIf { it >= 0 } ?: 0) },
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White)
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.Black,
                        modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun LiveRow(
    ch: StalkerChannel,
    number: Int,
    epg: EpgEntry?,
    isFav: Boolean,
    busy: Boolean,
    onFav: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF15151F)),
        border = androidx.compose.foundation.BorderStroke(1.dp, PLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                if (ch.logo.isNotBlank()) {
                    AsyncImage(model = ch.logo, contentDescription = null,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)),
                        contentScale = ContentScale.Fit)
                } else {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(15.dp))
                        .background(tileBrush(ch.id)),
                        contentAlignment = Alignment.Center) {
                        Text(initialsOf(ch.name), color = Color.White, fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold)
                    }
                }
                Box(Modifier.align(Alignment.BottomCenter).offset(y = 6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFF0B0B12))
                    .padding(6.dp, 1.dp)) {
                    Text("$number", color = PTx2, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ch.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = PTx2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    LiveBadgeMini()
                }
                Text(epg?.title ?: ch.genre, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp, bottom = 7.dp))
                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                } else if (epg != null) {
                    ProgressLine(epg.progress(), color = PLive)
                    val next = rememberNextHint(epg)
                    Text(next, fontSize = 12.sp, color = PTx2,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 7.dp))
                }
            }
            IconButton(onClick = onFav, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Star, "Favori",
                    tint = if (isFav) LiveGold else PTx2,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun rememberNextHint(epg: EpgEntry): String {
    return "Sırada · " + try {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epg.endEpoch * 1000))
    } catch (_: Exception) { "" }
}

@Composable
private fun LiveBadgeMini() {
    var blink by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1600)
            blink = !blink
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape)
            .background(PLive.copy(alpha = if (blink) 1f else 0.25f)))
        Spacer(Modifier.width(4.dp))
        Text("CANLI", color = PLive, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ==================== PROGRAM REHBERİ ====================

@Composable
private fun EpgGuide(
    session: Session,
    list: List<StalkerChannel>,
    onPlay: (StalkerChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    var days by remember { mutableStateOf<Map<String, List<EpgEntry>>>(emptyMap()) }
    var guideDone by remember { mutableStateOf(false) }
    // Rehber kullanici istegiyle acilir: internet yedegi burada serbest (max 8 kanal, 3 paralel)
    LaunchedEffect(list, session.sourceId) {
        guideDone = false
        days = emptyMap()
        try {
            val sem = kotlinx.coroutines.sync.Semaphore(3)
            coroutineScope {
                list.take(8).map { ch ->
                    async(Dispatchers.IO) {
                        sem.acquire()
                        try {
                            val l = try {
                                EpgXml.lookupDay(ctx, session, ch, includeInternet = true)
                            } catch (_: Exception) { emptyList() }
                            ch.id to l
                        } finally { sem.release() }
                    }
                }.awaitAll().forEach { (id, l) ->
                    if (l.isNotEmpty()) days = days + (id to l)
                }
            }
        } catch (_: Exception) { }
        guideDone = true
    }

    val now = System.currentTimeMillis() / 1000
    val winStart = (now - 1800) / 3600 * 3600
    val winEnd = winStart + 6 * 3600
    val pxPerSec = 0.55f
    val hScroll = rememberScrollState()
    val rows = remember(list) { list.take(8) }

    Column(modifier.verticalScroll(rememberScrollState())) {
        // saat basligi (logo sutunuyla hizali 64dp bosluk)
        Row {
            Spacer(Modifier.width(64.dp))
            Row(Modifier.horizontalScroll(hScroll)) {
                Box(Modifier.width((((winEnd - winStart) * pxPerSec).dp)).height(34.dp)) {
                    var hh = winStart
                    while (hh < winEnd) {
                        Box(
                            Modifier.offset(x = ((hh - winStart) * pxPerSec).dp)
                                .padding(start = 8.dp)
                        ) {
                            Text(
                                SimpleDateFormat("HH:mm", Locale.getDefault())
                                    .format(Date(hh * 1000)),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PTx2
                            )
                        }
                        hh += 3600
                    }
                }
            }
        }
        rows.forEach { ch ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(64.dp).height(64.dp), contentAlignment = Alignment.Center) {
                    if (ch.logo.isNotBlank()) {
                        AsyncImage(model = ch.logo, contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
                    } else {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(tileBrush(ch.id)),
                            contentAlignment = Alignment.Center) {
                            Text(initialsOf(ch.name), color = Color.White, fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
                    Box(Modifier.width(((winEnd - winStart) * pxPerSec).dp).height(64.dp)) {
                        val progs = (days[ch.id] ?: emptyList())
                            .filter { it.endEpoch > winStart && it.startEpoch < winEnd }
                        if (progs.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                Text(if (!guideDone) "Yükleniyor..." else "Program bilgisi yok",
                                    color = PTx2, fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        progs.forEach { pr ->
                            val left = ((pr.startEpoch.coerceAtLeast(winStart) - winStart) * pxPerSec).dp
                            val durS = (pr.endEpoch.coerceAtMost(winEnd) - pr.startEpoch.coerceAtLeast(winStart)).coerceAtLeast(0)
                            val w = ((durS * pxPerSec) - 6).coerceAtLeast(40f).dp
                            val isNow = pr.startEpoch <= now && now < pr.endEpoch
                            Box(
                                Modifier.offset(x = left).width(w)
                                    .padding(vertical = 6.dp).fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isNow) Color.White else Color(0xFF15151F))
                                    .clickableNoRipple {
                                        if (isNow) onPlay(ch)
                                        else Toast.makeText(ctx, "Henüz başlamadı", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(10.dp, 0.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Column {
                                    Text(pr.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        color = if (isNow) Color.Black else Color.White,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(pr.range(), fontSize = 11.sp,
                                        color = if (isNow) Color.Black.copy(alpha = 0.7f) else PTx2,
                                        maxLines = 1)
                                }
                            }
                        }
                        // simdi cizgisi
                        val nowX = ((now - winStart) * pxPerSec).dp
                        Box(Modifier.offset(x = nowX).width(2.dp).fillMaxHeight()
                            .background(PLive))
                    }
                }
            }
            HorizontalDivider(color = PLine)
        }
        Spacer(Modifier.height(16.dp))
    }
}
