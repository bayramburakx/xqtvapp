package com.bayram.xqtvapp.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bayram.xqtvapp.Session
import com.bayram.xqtvapp.data.EpgEntry
import com.bayram.xqtvapp.data.EpgXml
import com.bayram.xqtvapp.data.FavoritesStore
import com.bayram.xqtvapp.data.SeriesEntry
import com.bayram.xqtvapp.data.StalkerChannel
import com.bayram.xqtvapp.data.StalkerClient
import com.bayram.xqtvapp.ui.PGlass
import com.bayram.xqtvapp.ui.PLine
import com.bayram.xqtvapp.ui.PLive
import com.bayram.xqtvapp.ui.PTx2
import com.bayram.xqtvapp.ui.ProgressLine
import com.bayram.xqtvapp.ui.initialsOf
import com.bayram.xqtvapp.ui.remainingText
import com.bayram.xqtvapp.ui.tileBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class TvTab(val title: String) {
    HOME("Ana Sayfa"),
    LIVE("Canlı TV"),
    MOVIES("Filmler"),
    SERIES("Diziler"),
    SETTINGS("Ayarlar")
}

private fun tabIcon(t: TvTab) = when (t) {
    TvTab.HOME -> Icons.Filled.Home
    TvTab.LIVE -> Icons.Filled.Tv
    TvTab.MOVIES -> Icons.Filled.Movie
    TvTab.SERIES -> Icons.Filled.PlayArrow
    TvTab.SETTINGS -> Icons.Filled.Settings
}

@Composable
fun TvHomeScreen(
    session: Session,
    onSourceSwitch: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onPlayChannel: (StalkerChannel, String, String?, Map<String, String>, List<StalkerChannel>, Int) -> Unit,
    onOpenMovie: (StalkerChannel) -> Unit,
    onOpenSeries: (SeriesEntry) -> Unit,
    onOpenFilter: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hidden by FavoritesStore.hiddenCatsFlow(ctx).collectAsState(initial = emptySet())
    val hideAdult by FavoritesStore.hideAdultFlow(ctx).collectAsState(initial = false)
    val tvPlayLast by FavoritesStore.tvPlayLastFlow(ctx).collectAsState(initial = true)
    val recents by FavoritesStore.recentFlow(ctx).collectAsState(initial = emptyList())
    val resumeMap by FavoritesStore.resumeFlow(ctx).collectAsState(initial = emptyMap())

    val vis = remember(session, hidden, hideAdult) {
        session.copy(
            channels = session.channels.filter {
                !hidden.contains("live:${it.genre}") && !(hideAdult && isAdultLabel(it.genre))
            },
            movies = session.movies.filter {
                !hidden.contains("movie:${it.genre}") && !(hideAdult && isAdultLabel(it.genre))
            },
            series = session.series.filter {
                !hidden.contains("series:${it.category}") && !(hideAdult && isAdultLabel(it.category))
            }
        )
    }
    var tab by remember { mutableIntStateOf(0) }
    val tabFr = remember { FocusRequester() }
    var playLastDone by remember { mutableStateOf(false) }

    // Acilista son kanali oynat (bir kez)
    LaunchedEffect(vis, tvPlayLast, playLastDone) {
        if (tvPlayLast && !playLastDone && recents.isNotEmpty()) {
            playLastDone = true
            val last = recents.firstOrNull { resumeMap[it.id]?.kind == "live" }
            val ch = last?.let { l -> vis.channels.find { it.id == l.id } }
            if (ch != null) openTvLive(scope, vis, ch, vis.channels.indexOf(ch), onPlayChannel)
        }
    }
    LaunchedEffect(Unit) {
        try { tabFr.requestFocus() } catch (_: Exception) { }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B12))) {
    Column(Modifier.fillMaxSize()) {
        // Ust bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 40.dp).padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Portio", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp, color = Color.White,
                modifier = Modifier.width(110.dp)
            )
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center
            ) {
                TvTab.entries.forEachIndexed { i, t ->
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .then(if (i == 0) Modifier.focusRequester(tabFr) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .tvFocusRing(focused, 99.dp, 1.08f)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (tab == i) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                            .tvClickableNoRipple { tab = i }
                            .padding(horizontal = 18.dp)
                            .height(40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(tabIcon(t), null,
                            tint = if (tab == i || focused) Color.White else PTx2,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            t.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            color = if (tab == i || focused) Color.White else PTx2
                        )
                    }
                    if (i < TvTab.entries.size - 1) Spacer(Modifier.width(6.dp))
                }
            }
            Row(
                Modifier.clip(RoundedCornerShape(99.dp)).background(PGlass)
                    .padding(horizontal = 14.dp).height(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF30D158)))
                Spacer(Modifier.width(6.dp))
                Text(
                    (session.sourceName.ifBlank { session.label }).take(18),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            TvClock()
        }
        Box(Modifier.weight(1f)) {
            when (TvTab.entries[tab]) {
                TvTab.HOME -> TvDiscoverTab(vis, onPlayChannel, onOpenMovie, onOpenSeries)
                TvTab.LIVE -> TvLiveTab(vis, onPlayChannel)
                TvTab.MOVIES -> TvMediaGridTab(
                    title = "Filmler", unit = "film",
                    cats = vis.movies.map { it.genre }.distinct().sorted(),
                    items = vis.movies.associate { it.id to (it.name to it.logo) },
                    itemsByCat = { c -> vis.movies.filter { it.genre == c }.map { it.id } },
                    onOpen = { id -> vis.movies.find { it.id == id }?.let(onOpenMovie) }
                )
                TvTab.SERIES -> TvMediaGridTab(
                    title = "Diziler", unit = "dizi",
                    cats = vis.series.map { it.category }.distinct().sorted(),
                    items = vis.series.associate { it.id to (it.name to it.cover) },
                    itemsByCat = { c -> vis.series.filter { it.category == c }.map { it.id } },
                    onOpen = { id -> vis.series.find { it.id == id }?.let(onOpenSeries) }
                )
                TvTab.SETTINGS -> TvSettingsTab(
                    session = session, vis = vis,
                    onRefresh = onRefresh, onSourceSwitch = onSourceSwitch,
                    onLogout = onLogout, onOpenFilter = onOpenFilter
                )
            }
        }
    } // Column
    TvHint(
        Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 14.dp)
    )
    } // dis Box
}

/** Kumandayla canli acilis (Stalker link cozumu dahil). */
fun openTvLive(
    scope: kotlinx.coroutines.CoroutineScope,
    session: Session,
    ch: StalkerChannel,
    idx: Int,
    onPlayChannel: (StalkerChannel, String, String?, Map<String, String>, List<StalkerChannel>, Int) -> Unit
) {
    scope.launch {
        try {
            if (session.stalkerUrl.isNotEmpty()) {
                val sc = StalkerClient(session.stalkerUrl, session.stalkerMac)
                val url = sc.createLink(ch.cmd)
                if (url.isNotBlank()) onPlayChannel(ch, url, null, sc.streamHeaders(), session.channels, idx)
            } else {
                val headers = if (session.xServer.isNotBlank())
                    mapOf("Referer" to session.xServer.trimEnd('/') + "/") else emptyMap()
                val alt = if (ch.cmd.endsWith(".m3u8")) ch.cmd.dropLast(5) + ".ts" else null
                onPlayChannel(ch, ch.cmd, alt, headers, session.channels, idx)
            }
        } catch (_: Exception) { }
    }
}

// ==================== KESFET ====================

@Composable
private fun TvDiscoverTab(
    vis: Session,
    onPlayChannel: (StalkerChannel, String, String?, Map<String, String>, List<StalkerChannel>, Int) -> Unit,
    onOpenMovie: (StalkerChannel) -> Unit,
    onOpenSeries: (SeriesEntry) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val recents by FavoritesStore.recentFlow(ctx).collectAsState(initial = emptyList())
    val resumeMap by FavoritesStore.resumeFlow(ctx).collectAsState(initial = emptyMap())
    var epg by remember { mutableStateOf<Map<String, EpgEntry>>(emptyMap()) }

    // Canli ray EPG: sadece kaynak (isinma yok)
    LaunchedEffect(vis.sourceId) {
        try {
            val sem = kotlinx.coroutines.sync.Semaphore(3)
            coroutineScope {
                vis.channels.take(12).map { ch ->
                    async(Dispatchers.IO) {
                        sem.acquire()
                        try {
                            val e = try {
                                EpgXml.lookupNow(ctx, vis, ch, includeInternet = false)
                            } catch (_: Exception) { null }
                            ch.id to e
                        } finally { sem.release() }
                    }
                }.awaitAll().forEach { (id, e) ->
                    if (e != null) epg = epg + (id to e)
                }
            }
        } catch (_: Exception) { }
    }

    val slides = remember(vis) {
        val out = mutableListOf<TvHeroSlide>()
        vis.movies.take(2).forEach { m ->
            out.add(TvHeroSlide("Yeni eklendi", m.name, "Film • ${m.genre}", m.logo,
                { onOpenMovie(m) }, m.id))
        }
        vis.series.take(2).forEach { s ->
            out.add(TvHeroSlide("Dizi", s.name,
                "${s.category} • ${s.episodes.size} bölüm", s.cover,
                { onOpenSeries(s) }, s.id))
        }
        vis.channels.take(1).forEach { c ->
            out.add(TvHeroSlide("Canlı", c.name, c.genre, c.logo, {
                openTvLive(scope, vis, c, vis.channels.indexOf(c), onPlayChannel)
            }, c.id))
        }
        out
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 50.dp)
    ) {
        if (slides.isNotEmpty()) {
            item {
                TvHeroSlider(slides = slides)
            }
        }
        if (recents.isNotEmpty()) {
            item { TvSectionTitle("İzlemeye devam et") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(recents.take(8), key = { it.id }) { r ->
                        val ri = resumeMap[r.id]
                        val prog = if (ri != null && ri.durMs > 0) ri.posMs.toFloat() / ri.durMs else 0f
                        TvContinueCard(r.name, r.logo, prog) {
                            val ch = vis.channels.find { m -> m.id == r.id }
                                ?: vis.movies.find { m -> m.id == r.id }
                            if (ch != null) {
                                if (vis.movies.any { m -> m.id == ch.id }) onOpenMovie(ch)
                                else openTvLive(scope, vis, ch, vis.channels.indexOf(ch), onPlayChannel)
                            }
                        }
                    }
                }
            }
        }
        if (vis.channels.isNotEmpty()) {
            item { TvSectionTitle("Şimdi canlı") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(vis.channels.take(12), key = { it.id }) { ch ->
                        TvLiveCard(ch, epg[ch.id]) {
                            openTvLive(scope, vis, ch, vis.channels.indexOf(ch), onPlayChannel)
                        }
                    }
                }
            }
        }
        if (vis.movies.isNotEmpty()) {
            item { TvSectionTitle("Yeni filmler") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(vis.movies.take(12), key = { it.id }) { m ->
                        TvPosterCard(m.name, m.logo) {
                            onOpenMovie(m)
                        }
                    }
                }
            }
        }
        if (vis.series.isNotEmpty()) {
            item { TvSectionTitle("Popüler diziler") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(vis.series.take(12), key = { it.id }) { s ->
                        TvPosterCard(s.name, s.cover) {
                            onOpenSeries(s)
                        }
                    }
                }
            }
        }
    }
}

private data class TvHeroSlide(
    val badge: String,
    val title: String,
    val meta: String,
    val art: String,
    val play: () -> Unit,
    val favId: String?
)

/** Devasa hero slider: fon posteri + otomatik donme + nokta gostergesi. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvHeroSlider(slides: List<TvHeroSlide>) {
    if (slides.isEmpty()) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val favs by FavoritesStore.favsFlow(ctx).collectAsState(initial = emptySet())
    val pager = rememberPagerState(pageCount = { slides.size })
    LaunchedEffect(pager, slides.size) {
        while (slides.size > 1) {
            try {
                delay(6000)
                pager.animateScrollToPage((pager.currentPage + 1) % slides.size)
            } catch (_: Exception) { break }
        }
    }
    Box(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { i ->
            val s = slides[i % slides.size]
            val playFr = remember { FocusRequester() }
            Box(
                Modifier.fillMaxWidth().height(400.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(tileBrush(s.title))
            ) {
                if (s.art.isNotBlank()) {
                    AsyncImage(model = s.art, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xE605050C)))
                ))
                Box(Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(Color(0xB305050C), Color.Transparent),
                        startX = 0f, endX = 900f
                    )
                ))
                Column(
                    Modifier.align(Alignment.BottomStart).padding(36.dp)
                        .fillMaxWidth(0.72f)
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(99.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Text(s.badge, color = Color.White,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(s.title, fontSize = 54.sp, lineHeight = 56.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp,
                        color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text(s.meta, fontSize = 17.sp, color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TvButton("▶  Oynat", primary = true, focusMe = playFr, onClick = s.play)
                        TvButton(
                            if (s.favId != null && favs.contains(s.favId)) "✓ Listemde" else "+ Listem",
                            primary = false,
                            onClick = {
                                s.favId?.let { id ->
                                    scope.launch { FavoritesStore.toggle(ctx, id) }
                                }
                            }
                        )
                    }
                }
            }
            // Sayfa degisince Oynat odakta kalsin
            LaunchedEffect(pager.currentPage) {
                if (i == pager.currentPage) {
                    try { playFr.requestFocus() } catch (_: Exception) { }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(slides.size) { i ->
                Box(
                    Modifier
                        .then(
                            if (i == pager.currentPage) Modifier.size(28.dp, 8.dp)
                            else Modifier.size(8.dp)
                        )
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (i == pager.currentPage) Color.White
                            else Color.White.copy(alpha = 0.4f)
                        )
                )
            }
        }
    }
}

@Composable
private fun TvContinueCard(title: String, art: String, progress: Float, onFocus: (Boolean) -> Unit, onClick: () -> Unit) {
    TvFocusCard(onClick = onClick, onFocused = onFocus,
        modifier = Modifier.size(210.dp, 118.dp), corner = 16.dp) {
        Box(Modifier.fillMaxSize().background(tileBrush(title))) {
            if (art.isNotBlank()) {
                AsyncImage(model = art, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))
            ))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (progress > 0.02f) {
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(progress)
                }
            }
        }
    }
}

@Composable
private fun TvLiveCard(ch: StalkerChannel, epg: EpgEntry?, onFocus: (Boolean) -> Unit, onClick: () -> Unit) {
    TvFocusCard(onClick = onClick, onFocused = onFocus,
        modifier = Modifier.size(210.dp, 118.dp), corner = 16.dp) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF15151F)).padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ch.logo.isNotBlank()) {
                    AsyncImage(model = ch.logo, contentDescription = null,
                        modifier = Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit)
                } else {
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))
                        .background(tileBrush(ch.id)),
                        contentAlignment = Alignment.Center) {
                        Text(initialsOf(ch.name), color = Color.White,
                            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(ch.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("CANLI", color = PLive, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(epg?.title ?: ch.genre, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(epg?.range() ?: "", fontSize = 11.sp, color = PTx2,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
            if (epg != null) ProgressLine(epg.progress(), color = PLive)
        }
    }
}

@Composable
fun TvPosterCard(title: String, art: String, onFocus: (Boolean) -> Unit = {}, onClick: () -> Unit) {
    TvFocusCard(onClick = onClick, onFocused = onFocus,
        modifier = Modifier.size(115.dp, 172.dp), corner = 14.dp) {
        Box(Modifier.fillMaxSize().background(tileBrush(title))) {
            if (art.isNotBlank()) {
                AsyncImage(model = art, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))
            ))
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
    }
}

// ==================== CANLI TV ====================

@Composable
private fun TvLiveTab(
    vis: Session,
    onPlayChannel: (StalkerChannel, String, String?, Map<String, String>, List<StalkerChannel>, Int) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val favs by FavoritesStore.favsFlow(ctx).collectAsState(initial = emptySet())
    var cat by remember { mutableStateOf("Tümü") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var dayEpg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    var nowMap by remember { mutableStateOf<Map<String, EpgEntry>>(emptyMap()) }

    val cats = remember(vis) {
        listOf("Tümü", "Favoriler") + vis.channels.map { it.genre }.distinct().sorted()
    }
    val list = remember(vis, cat, favs) {
        vis.channels.filter {
            when (cat) {
                "Tümü" -> true
                "Favoriler" -> favs.contains(it.id)
                else -> it.genre == cat
            }
        }
    }
    val selected = list.find { it.id == selectedId } ?: list.firstOrNull()

    LaunchedEffect(list, vis.sourceId) {
        nowMap = emptyMap()
        try {
            val sem = kotlinx.coroutines.sync.Semaphore(3)
            coroutineScope {
                list.take(12).map { ch ->
                    async(Dispatchers.IO) {
                        sem.acquire()
                        try {
                            val e = try {
                                EpgXml.lookupNow(ctx, vis, ch, includeInternet = false)
                            } catch (_: Exception) { null }
                            ch.id to e
                        } finally { sem.release() }
                    }
                }.awaitAll().forEach { (id, e) ->
                    if (e != null) nowMap = nowMap + (id to e)
                }
            }
        } catch (_: Exception) { }
    }
    // Odaklanilan kanal icin gunluk akis (tek kanal, internet serbest)
    LaunchedEffect(selected?.id) {
        dayEpg = emptyList()
        val ch = selected ?: return@LaunchedEffect
        try {
            dayEpg = EpgXml.lookupDay(ctx, vis, ch, includeInternet = true)
        } catch (_: Exception) { }
    }
    val nowEpg = remember(dayEpg) {
        val n = System.currentTimeMillis() / 1000
        dayEpg.firstOrNull { it.startEpoch <= n && n < it.endEpoch }
    }
    val nextEpg = remember(dayEpg) {
        val n = System.currentTimeMillis() / 1000
        dayEpg.firstOrNull { it.startEpoch >= n }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Canlı TV", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp, modifier = Modifier.weight(1f))
            Text("${list.size} kanal", color = PTx2, fontSize = 14.sp)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(cats) { c ->
                TvChip(c, c == cat, onClick = { cat = c })
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Kanal listesi
            LazyColumn(
                Modifier.weight(0.45f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                itemsIndexed(list, key = { _, it -> it.id }) { idx, ch ->
                    val epg = nowMap[ch.id]
                    var focused by remember { mutableStateOf(false) }
                    LaunchedEffect(focused) {
                        if (focused) selectedId = ch.id
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused }
                            .tvFocusRing(focused, 18.dp, 1.03f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF15151F))
                            .border(1.dp, PLine, RoundedCornerShape(18.dp))
                            .tvClickableNoRipple {
                                openTvLive(scope, vis, ch, idx, onPlayChannel)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${idx + 1}", color = PTx2, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.width(30.dp))
                        if (ch.logo.isNotBlank()) {
                            AsyncImage(model = ch.logo, contentDescription = null,
                                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit)
                        } else {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                                .background(tileBrush(ch.id)),
                                contentAlignment = Alignment.Center) {
                                Text(initialsOf(ch.name), color = Color.White,
                                    fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ch.name, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(epg?.title ?: ch.genre, fontSize = 13.sp, color = PTx2,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (favs.contains(ch.id)) {
                            Icon(Icons.Filled.Star, "Favori", tint = Color(0xFFFFD60A),
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            // Onizleme paneli
            if (selected != null) {
                val ch = selected
                Column(
                    Modifier.weight(0.55f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(tileBrush(ch.id))
                        .padding(28.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("CANLI · Kanal", color = Color(0xFFFF8A80),
                            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(nowEpg?.title ?: ch.name, fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text("${ch.name} · ${nowEpg?.range() ?: ch.genre}",
                        fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(12.dp))
                    if (nowEpg != null) {
                        ProgressLine(nowEpg.progress(), color = Color.White)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (nextEpg != null) {
                        Text("Sırada: ${nextEpg.title}", fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(16.dp))
                    } else Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        TvButton("▶  Oynat", primary = true, onClick = {
                            openTvLive(scope, vis, ch, list.indexOf(ch), onPlayChannel)
                        })
                        TvStarBtn(
                            filled = favs.contains(ch.id),
                            onClick = { scope.launch { FavoritesStore.toggle(ctx, ch.id) } }
                        )
                    }
                }
            }
        }
    }
}

// ==================== FILM / DIZI IZGARASI ====================

@Composable
private fun TvMediaGridTab(
    title: String,
    unit: String,
    cats: List<String>,
    items: Map<String, Pair<String, String>>,
    itemsByCat: (String) -> List<String>,
    onOpen: (String) -> Unit
) {
    var cat by remember { mutableStateOf("Tümü") }
    val allCats = remember(cats) { listOf("Tümü") + cats }
    val ids = remember(cat) {
        if (cat == "Tümü") items.keys.toList() else itemsByCat(cat)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp, modifier = Modifier.weight(1f))
            Text("${ids.size} $unit", color = PTx2, fontSize = 14.sp)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(allCats) { c ->
                TvChip(c, c == cat, onClick = { cat = c })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (ids.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Bu kategoride içerik yok.", color = PTx2, fontSize = 18.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 50.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(ids, key = { it }) { id ->
                    val (name, art) = items[id] ?: ("" to "")
                    TvFocusCard(onClick = { onOpen(id) },
                        modifier = Modifier.fillMaxWidth(),
                        corner = 14.dp) {
                        Column {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(tileBrush(id))
                            ) {
                                if (art.isNotBlank()) {
                                    AsyncImage(model = art, contentDescription = null,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentScale = ContentScale.Crop)
                                }
                                Box(Modifier.fillMaxWidth().background(
                                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))
                                ))
                                Text(name, color = Color.White, fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
