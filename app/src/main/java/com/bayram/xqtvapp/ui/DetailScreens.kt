package com.bayram.xqtvapp.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bayram.xqtvapp.Session
import com.bayram.xqtvapp.data.EpisodeEntry
import com.bayram.xqtvapp.data.FavoritesStore
import com.bayram.xqtvapp.data.SeriesEntry
import com.bayram.xqtvapp.data.StalkerChannel
import com.bayram.xqtvapp.data.StalkerClient
import com.bayram.xqtvapp.data.VodDetail
import com.bayram.xqtvapp.data.XtreamClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ==================== FİLM ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    movie: StalkerChannel,
    session: Session,
    onBack: () -> Unit,
    onSelect: (StalkerChannel) -> Unit,
    onPlay: (String, Long, Map<String, String>) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { onBack() }
    val favs by FavoritesStore.favsFlow(ctx).collectAsState(initial = emptySet())
    val liked by FavoritesStore.watchedFlow(ctx).collectAsState(initial = emptySet())
    val resume by FavoritesStore.entryFlow(ctx, movie.id).collectAsState(initial = null)
    var detail by remember { mutableStateOf<VodDetail?>(null) }
    var busy by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val prefs by FavoritesStore.trackPrefsFlow(ctx).collectAsState(initial = Pair("auto", "off"))
    val listState = rememberLazyListState()
    val showMini by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    LaunchedEffect(movie.id) {
        detail = null
        if (session.xServer.isNotEmpty() && movie.id.startsWith("vod_")) {
            try {
                detail = XtreamClient(session.xServer, session.xUser, session.xPass)
                    .vodInfo(movie.id.removePrefix("vod_"))
            } catch (_: Exception) { }
        }
    }

    val cover = detail?.cover?.ifBlank { movie.logo } ?: movie.logo
    val similar = remember(movie.id, session) {
        session.movies.filter { it.id != movie.id && it.genre == movie.genre }.take(8)
            .ifEmpty { session.movies.filter { it.id != movie.id }.take(8) }
    }
    val cast = remember(detail) {
        (detail?.cast ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(10)
    }

    fun playNow(fromMs: Long = 0L) {
        scope.launch {
            busy = true
            try {
                if (session.stalkerUrl.isNotEmpty()) {
                    val sc = StalkerClient(session.stalkerUrl, session.stalkerMac)
                    val url = sc.createLink(movie.cmd)
                    if (url.isNotBlank()) onPlay(url, fromMs, sc.streamHeaders())
                } else if (movie.cmd.isNotBlank()) onPlay(movie.cmd, fromMs, emptyMap())
            } finally { busy = false }
        }
    }

    Box(Modifier.fillMaxSize().background(PBg)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(520.dp)) {
                    if (cover.isNotBlank()) AsyncImage(model = cover, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Box(Modifier.fillMaxSize().background(tileBrush(movie.id)))
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, PBg), startY = 500f, endY = 1400f)
                    ))
                    Row(Modifier.fillMaxWidth().padding(16.dp, 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        GlassIconButton(Icons.Filled.ArrowBack, onBack)
                        GlassIconButton(Icons.Filled.Share) {
                            val i = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Portio'da izliyorum: ${movie.name}")
                            }
                            ctx.startActivity(Intent.createChooser(i, null))
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(top = 370.dp).padding(horizontal = 20.dp)) {
                    GlassTag(if (resume?.hasValid() == true) "Kaldığın yerden" else "Yeni eklendi")
                    Spacer(Modifier.height(12.dp))
                    Text(movie.name, fontSize = 52.sp, lineHeight = 50.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = (-2.5).sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (detail?.rating?.isNotBlank() == true) {
                            Text("★ ${detail!!.rating}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        if (detail?.year?.isNotBlank() == true) MetaSmall(detail!!.year)
                        if (detail?.duration?.isNotBlank() == true) MetaSmall(detail!!.duration)
                        MetaSmall(movie.genre)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 14.dp)) {
                        BadgeChip(movie.cmd.substringAfterLast(".").uppercase().takeIf { it.length <= 5 } ?: "HD")
                        BadgeChip("HD")
                        if (prefs.second != "off") BadgeChip("Altyazı")
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { playNow(if (resume?.hasValid() == true) resume!!.posMs else 0L) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(99.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = !busy
                    ) {
                        Text("▶", color = Color.Black, fontSize = 18.sp)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            if (busy) "Açılıyor..."
                            else if (resume?.hasValid() == true) "Kaldığın yerden devam et"
                            else "Oynat",
                            color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp
                        )
                    }
                    if (resume?.hasValid() == true) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${fmtMs(resume!!.posMs)} izledin · ${remainingText(resume!!.posMs, resume!!.durMs)}",
                                color = PTx2, fontSize = 13.sp, modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        ProgressLine(
                            if (resume!!.durMs > 0) resume!!.posMs.toFloat() / resume!!.durMs else 0f,
                            color = Color.White
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActButton("Listem", favs.contains(movie.id),
                            if (favs.contains(movie.id)) Icons.Filled.Check else Icons.Filled.FavoriteBorder) {
                            scope.launch { FavoritesStore.toggle(ctx, movie.id) }
                        }
                        ActButton("Beğen", liked.contains("like_" + movie.id), Icons.Filled.ThumbUp) {
                            scope.launch { FavoritesStore.toggle(ctx, "like_" + movie.id) }
                        }
                        ActButton("Paylaş", false, Icons.Filled.Share) {
                            val i = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Portio'da izliyorum: ${movie.name}")
                            }
                            ctx.startActivity(Intent.createChooser(i, null))
                        }
                    }
                    val plot = detail?.plot?.ifBlank { null }
                    if (!plot.isNullOrBlank()) {
                        Spacer(Modifier.height(20.dp))
                        Text(plot, fontSize = 16.sp, lineHeight = 24.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Daha az" else "Daha fazla", color = PTx2,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (cast.isNotEmpty()) {
                        Spacer(Modifier.height(30.dp))
                        Text("Oyuncular", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(cast, key = { it }) { name ->
                                Column(Modifier.width(76.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(76.dp).clip(
                                        CircleShape)
                                        .background(tileBrush(name)),
                                        contentAlignment = Alignment.Center) {
                                        Text(initialsOf(name), color = Color.White,
                                            fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(name, fontSize = 13.sp, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                    Text("Bilgiler", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    Spacer(Modifier.height(4.dp))
                    InfoTable(
                        listOfNotNull(
                            detail?.director?.takeIf { it.isNotBlank() }?.let { "Yönetmen" to it },
                            "Tür" to movie.genre,
                            detail?.duration?.takeIf { it.isNotBlank() }?.let { "Süre" to it },
                            "Kaynak" to session.sourceName.ifBlank { session.label },
                            detail?.year?.takeIf { it.isNotBlank() }?.let { "Yıl" to it }
                        )
                    )
                    if (similar.isNotEmpty()) {
                        Spacer(Modifier.height(30.dp))
                        Text("Benzer filmler", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(similar, key = { it.id }) { m ->
                                PosterCard128(m.name, m.logo) { onSelect(m) }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                }
            }
        }

        if (showMini) {
            MiniBar(title = movie.name, onBack = onBack,
                onPlay = { playNow(if (resume?.hasValid() == true) resume!!.posMs else 0L) })
        }
    }
}

fun avSummary(audio: String, sub: String): String {
    val a = when (audio) {
        "tr" -> "Türkçe dublaj"
        "en" -> "Orijinal dil (İngilizce)"
        else -> "Otomatik ses"
    }
    val s = when (sub) {
        "tr" -> "Türkçe"
        "en" -> "İngilizce"
        "auto" -> "Otomatik"
        else -> "kapalı"
    }
    return "$a · Altyazı $s"
}

// ==================== DİZİ ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    entry: SeriesEntry,
    session: Session,
    onBack: () -> Unit,
    onSelect: (SeriesEntry) -> Unit,
    onPlayEpisode: (String, String, List<EpisodeEntry>, Int, Long) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { onBack() }
    val favs by FavoritesStore.favsFlow(ctx).collectAsState(initial = emptySet())
    val liked by FavoritesStore.watchedFlow(ctx).collectAsState(initial = emptySet())
    val watched by FavoritesStore.watchedFlow(ctx).collectAsState(initial = emptySet())
    var full by remember { mutableStateOf<SeriesEntry?>(if (entry.episodes.isNotEmpty()) entry else null) }
    var season by remember { mutableIntStateOf(-1) }
    var loading by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val showMini by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    LaunchedEffect(entry.id) {
        full = if (entry.episodes.isNotEmpty()) entry else null
        if (full == null && session.xServer.isNotEmpty()) {
            loading = true
            try {
                full = XtreamClient(session.xServer, session.xUser, session.xPass)
                    .seriesEpisodes(entry.id) ?: entry
                season = full?.episodes?.firstOrNull()?.season ?: -1
            } finally { loading = false }
        } else season = entry.episodes.firstOrNull()?.season ?: -1
    }

    val seasons = remember(full) { full?.episodes?.map { it.season }?.distinct()?.sorted() ?: emptyList() }
    val allEps = remember(full) { full?.episodes ?: emptyList() }
    val seriesCast = remember(full) {
        (full?.cast ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(10)
    }
    val similarSeries = remember(entry.id, session) {
        session.series.filter { it.id != entry.id && it.category == entry.category }.take(8)
            .ifEmpty { session.series.filter { it.id != entry.id }.take(8) }
    }
    val eps = remember(full, season) { allEps.filter { season == -1 || it.season == season } }
    val watchedCount = remember(allEps, watched) {
        allEps.count { watched.contains("series_" + it.id) }
    }
    val resumeMap by FavoritesStore.resumeFlow(ctx).collectAsState(initial = emptyMap())
    // siradaki: kaldigi yer varsa o bolum, yoksa izlenmemis ilk, yoksa ilk
    val nextIdx = remember(allEps, watched, resumeMap) {
        val withPos = allEps.indexOfFirst { ep ->
            (resumeMap["series_" + ep.id]?.hasValid() == true)
        }
        if (withPos >= 0) withPos
        else allEps.indexOfFirst { !watched.contains("series_" + it.id) }.takeIf { it >= 0 } ?: 0
    }.takeIf { allEps.isNotEmpty() } ?: -1
    val resumeEp = allEps.getOrNull(nextIdx)
    val resumeInfo = resumeEp?.let { resumeMap["series_" + it.id] }?.takeIf { it.hasValid() }

    Box(Modifier.fillMaxSize().background(PBg)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(520.dp)) {
                    if (entry.cover.isNotBlank()) AsyncImage(model = entry.cover,
                        contentDescription = null, modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop)
                    else Box(Modifier.fillMaxSize().background(tileBrush(entry.id)))
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, PBg), startY = 500f, endY = 1400f)
                    ))
                    Row(Modifier.fillMaxWidth().padding(16.dp, 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        GlassIconButton(Icons.Filled.ArrowBack, onBack)
                        GlassIconButton(Icons.Filled.Share) {
                            val i = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Portio'da izliyorum: ${entry.name}")
                            }
                            ctx.startActivity(Intent.createChooser(i, null))
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(top = 370.dp).padding(horizontal = 20.dp)) {
                    GlassTag(if (watchedCount > 0) "İzlemeye devam et" else "Yeni sezon")
                    Spacer(Modifier.height(12.dp))
                    Text(entry.name, fontSize = 52.sp, lineHeight = 50.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = (-2.5).sp)
                    Spacer(Modifier.height(12.dp))
                    Text("${seasons.size} sezon • ${allEps.size} bölüm • ${entry.category}",
                        color = PTx2, fontSize = 15.sp)
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = {
                            if (allEps.isEmpty()) return@Button
                            val idx = if (nextIdx >= 0) nextIdx else 0
                            val ep = allEps[idx]
                            scope.launch {
                                val info = FavoritesStore.resumeFlow(ctx).first()[("series_" + ep.id)]
                                val ms = if (info?.hasValid() == true) info.posMs else 0L
                                onPlayEpisode(entry.name, entry.cover, allEps, idx, ms)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(99.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = !loading && allEps.isNotEmpty()
                    ) {
                        Text("▶", color = Color.Black, fontSize = 18.sp)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            if (loading) "Yükleniyor..."
                            else if (resumeEp != null && (resumeInfo?.hasValid() == true))
                                "S${resumeEp.season} B${resumeEp.episode} · Devam et"
                            else if (resumeEp != null) "S${resumeEp.season} B${resumeEp.episode} · Oynat"
                            else "Oynat",
                            color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp
                        )
                    }
                    if (resumeInfo?.hasValid() == true) {
                        Spacer(Modifier.height(12.dp))
                        ProgressLine(
                            if (resumeInfo.durMs > 0) resumeInfo.posMs.toFloat() / resumeInfo.durMs else 0f,
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${fmtMs(resumeInfo.posMs)} izledin · ${remainingText(resumeInfo.posMs, resumeInfo.durMs)}",
                            color = PTx2, fontSize = 13.sp
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActButton("Listem", favs.contains(entry.id),
                            if (favs.contains(entry.id)) Icons.Filled.Check else Icons.Filled.FavoriteBorder) {
                            scope.launch { FavoritesStore.toggle(ctx, entry.id) }
                        }
                        ActButton("Beğen", liked.contains("like_" + entry.id), Icons.Filled.ThumbUp) {
                            scope.launch { FavoritesStore.toggle(ctx, "like_" + entry.id) }
                        }
                        ActButton("Paylaş", false, Icons.Filled.Share) {
                            val i = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Portio'da izliyorum: ${entry.name}")
                            }
                            ctx.startActivity(Intent.createChooser(i, null))
                        }
                    }
                    val plot = allEps.firstOrNull { it.plot.isNotBlank() }?.plot
                    if (!plot.isNullOrBlank()) {
                        Spacer(Modifier.height(20.dp))
                        Text(plot, fontSize = 16.sp, lineHeight = 24.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Daha az" else "Daha fazla", color = PTx2,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Bölümler", fontWeight = FontWeight.Bold, fontSize = 21.sp,
                            modifier = Modifier.weight(1f))
                        Text("$watchedCount/${allEps.size} izlendi", color = PTx2, fontSize = 14.sp)
                    }
                    if (seasons.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasons) { s ->
                                FilterChip(
                                    selected = season == s,
                                    onClick = { season = s },
                                    label = { Text("S$s") },
                                    shape = RoundedCornerShape(99.dp)
                                )
                            }
                        }
                    }
                }
            }
            items(eps, key = { it.id }) { ep ->
                val idx = allEps.indexOfFirst { it.id == ep.id }
                val wid = "series_" + ep.id
                val isW = watched.contains(wid)
                val ri = resumeEp?.id == ep.id
                val prog = if (ri && resumeInfo != null && resumeInfo.durMs > 0)
                    resumeInfo.posMs.toFloat() / resumeInfo.durMs else 0f
                Card(
                    modifier = Modifier.fillMaxWidth().height(112.dp)
                        .padding(horizontal = 20.dp, vertical = 5.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF15151F))
                ) {
                Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(128.dp).height(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(tileBrush(ep.id))
                            .clickableNoRipple {
                                scope.launch {
                                    val info = FavoritesStore.resumeFlow(ctx).first()[wid]
                                    val ms = if (info?.hasValid() == true) info.posMs else 0L
                                    onPlayEpisode(entry.name, entry.cover, allEps, idx, ms)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (ep.cover.isNotBlank() && ep.cover != entry.cover) {
                            AsyncImage(model = ep.cover, contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Text("▶", color = Color.White, fontSize = 22.sp)
                        if (prog > 0.02f) {
                            Box(Modifier.align(Alignment.BottomCenter).padding(8.dp, 6.dp)) {
                                ProgressLine(prog)
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${ep.episode}. ${ep.title}", fontWeight = FontWeight.Bold,
                            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (ep.durationSecs > 0) "${ep.durationSecs / 60} dk" else ep.url.substringAfterLast(".").uppercase()) +
                                    (if (isW) " · İzlendi" else ""),
                            color = PTx2, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 3.dp, bottom = 4.dp)
                        )
                        if (ep.plot.isNotBlank()) {
                            Text(ep.plot, color = PTx2, fontSize = 13.sp, lineHeight = 18.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(if (isW) POk else Color.Transparent)
                            .then(if (isW) Modifier else Modifier.border(1.5.dp, PLine, CircleShape))
                            .clickableNoRipple {
                                scope.launch { FavoritesStore.toggleWatched(ctx, wid) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Check, null,
                            tint = if (isW) Color.White else Color.Transparent,
                            modifier = Modifier.size(15.dp))
                    }
                }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (seriesCast.isNotEmpty()) {
                        Spacer(Modifier.height(26.dp))
                        Text("Oyuncular", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(seriesCast, key = { it }) { name ->
                                Column(Modifier.width(76.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(76.dp).clip(CircleShape)
                                        .background(tileBrush(name)),
                                        contentAlignment = Alignment.Center) {
                                        Text(initialsOf(name), color = Color.White,
                                            fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(name, fontSize = 13.sp, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                    Text("Bilgiler", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    Spacer(Modifier.height(4.dp))
                    InfoTable(
                        listOfNotNull(
                            "Kategori" to entry.category,
                            "${seasons.size} sezon" to "${allEps.size} bölüm",
                            "Kaynak" to session.sourceName.ifBlank { session.label }
                        )
                    )
                    if (similarSeries.isNotEmpty()) {
                        Spacer(Modifier.height(26.dp))
                        Text("Benzer diziler", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            if (similarSeries.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(similarSeries, key = { it.id }) { s ->
                            PosterCard128(s.name, s.cover) { onSelect(s) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }

        if (showMini) {
            MiniBar(title = entry.name, onBack = onBack, onPlay = {
                val idx = if (nextIdx >= 0) nextIdx else 0
                if (allEps.isNotEmpty()) onPlayEpisode(entry.name, entry.cover, allEps, idx, 0L)
            })
        }
    }
}


// ==================== ORTAK PARÇALAR ====================

@Composable
fun GlassIconButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).clip(CircleShape)
            .background(Color(0x7F1E1E2C))
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
}

// yukarida tanimli ikonlar material3'te farkli pakette olabilir; basit vektorler:
@Composable
fun MetaSmall(text: String) {
    Text(text, color = PTx2, fontSize = 15.sp)
}

@Composable
fun GlassTag(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
        modifier = Modifier.clip(RoundedCornerShape(99.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(11.dp, 5.dp))
}

@Composable
fun BadgeChip(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PTx2,
        modifier = Modifier.clip(RoundedCornerShape(7.dp))
            .background(Color.Transparent)
            .padding(9.dp, 4.dp))
}

@Composable
fun ActButton(label: String, on: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp).clickableNoRipple(onClick)) {
        Box(Modifier.size(50.dp).clip(CircleShape)
            .background(if (on) Color.White else PGlass),
            contentAlignment = Alignment.Center) {
            Icon(icon, null,
                tint = if (on) Color.Black else Color.White,
                modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(if (on && label == "Listem") "Listemde" else label,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun InfoTable(rows: List<Pair<String, String>>) {
    Column {
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 13.dp)) {
                Text(k, color = PTx2, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(v, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
            }
            HorizontalDivider(color = PLine)
        }
    }
}

@Composable
fun MiniBar(title: String, onBack: () -> Unit, onPlay: () -> Unit) {
    Row(Modifier.fillMaxWidth()
        .background(PGlass)
        .padding(16.dp, 10.dp, 16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(Icons.Filled.ArrowBack, onBack)
        Spacer(Modifier.width(12.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Button(
            onClick = onPlay,
            shape = RoundedCornerShape(99.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
            modifier = Modifier.height(36.dp)
        ) { Text("Oynat", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvSheet(audio: String, sub: String, onPick: (String, String) -> Unit, onClose: () -> Unit) {
    var a by remember { mutableStateOf(audio) }
    var s by remember { mutableStateOf(sub) }
    ModalBottomSheet(onDismissRequest = onClose,
        containerColor = PBg2,
        dragHandle = {
            Box(Modifier.padding(10.dp).width(40.dp).height(5.dp)
                .clip(RoundedCornerShape(9.dp)).background(PLine))
        }) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 24.dp)) {
            Text("Ses ve altyazı", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("Ses", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PTx2,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            AvOption("Otomatik ses", a == "auto") { a = "auto" }
            AvOption("Türkçe dublaj", a == "tr") { a = "tr" }
            AvOption("Orijinal dil (İngilizce)", a == "en") { a = "en" }
            Text("Altyazı", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PTx2,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            AvOption("Kapalı", s == "off") { s = "off" }
            AvOption("Otomatik", s == "auto") { s = "auto" }
            AvOption("Türkçe", s == "tr") { s = "tr" }
            AvOption("İngilizce", s == "en") { s = "en" }
            Button(
                onClick = { onPick(a, s); onClose() },
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
                shape = RoundedCornerShape(99.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) { Text("Tamam", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun AvOption(label: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Box(Modifier.size(20.dp).clip(CircleShape)
            .background(if (on) Color.White else Color.Transparent)
            .then(if (on) Modifier else Modifier)) {
            if (!on) {
                Box(Modifier.fillMaxSize().padding(0.dp)) { }
            }
        }
        if (!on) {
            Box(Modifier.size(20.dp).clip(CircleShape)) {
                Box(Modifier.fillMaxSize().padding(2.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)) { }
            }
        }
    }
    HorizontalDivider(color = PLine)
}

@Composable
fun PosterCard128(title: String, logo: String, onClick: () -> Unit) {
    Column(Modifier.width(128.dp).clickableNoRipple(onClick),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(16.dp))
            .background(tileBrush(title))) {
            if (logo.isNotBlank()) {
                AsyncImage(model = logo, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xBF000000)))
            ))
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
    }
}
