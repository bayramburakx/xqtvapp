package com.bayram.xqtvapp.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
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
import com.bayram.xqtvapp.data.CountryGroup
import com.bayram.xqtvapp.data.COUNTRIES
import com.bayram.xqtvapp.data.EpisodeEntry
import com.bayram.xqtvapp.data.FavoritesStore
import com.bayram.xqtvapp.data.SeriesEntry
import com.bayram.xqtvapp.data.StalkerChannel
import com.bayram.xqtvapp.data.StalkerClient
import com.bayram.xqtvapp.data.VodDetail
import com.bayram.xqtvapp.data.XtreamClient
import com.bayram.xqtvapp.data.countryDef
import com.bayram.xqtvapp.data.groupByCountry
import com.bayram.xqtvapp.ui.PGlass
import com.bayram.xqtvapp.ui.PLine
import com.bayram.xqtvapp.ui.PTx2
import com.bayram.xqtvapp.ui.initialsOf
import com.bayram.xqtvapp.ui.tileBrush
import kotlinx.coroutines.launch

// ==================== AYARLAR (iki panel) ====================

private val TvSections = listOf(
    "Kaynaklar", "Oynatıcı", "Canlı TV", "Görünüm", "Ebeveyn kontrolü", "Hakkında"
)

@Composable
fun TvSettingsTab(
    session: Session,
    vis: Session,
    onRefresh: () -> Unit,
    onSourceSwitch: () -> Unit,
    onLogout: () -> Unit,
    onOpenFilter: () -> Unit
) {
    var sec by remember { mutableIntStateOf(0) }
    val menuFr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { menuFr.requestFocus() } catch (_: Exception) { }
    }
    Row(
        Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 10.dp)
    ) {
        // Sol menu
        Column(Modifier.width(260.dp)) {
            TvSections.forEachIndexed { i, s ->
                var focused by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (i == 0) Modifier.focusRequester(menuFr) else Modifier)
                        .onFocusChanged { focused = it.isFocused }
                        .tvFocusRing(focused, 18.dp, 1.04f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (sec == i) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                        .tvClickableNoRipple { sec = i }
                        .padding(horizontal = 20.dp)
                        .height(52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        s, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        color = if (sec == i || focused) Color.White else PTx2
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.width(30.dp))
        // Sag panel
        Box(Modifier.weight(1f)) {
            when (TvSections[sec]) {
                "Kaynaklar" -> TvSrcPanel(session, vis, onRefresh, onSourceSwitch, onLogout, onOpenFilter)
                "Oynatıcı" -> TvPlayerPanel()
                "Canlı TV" -> TvLivePanel()
                "Görünüm" -> TvLookPanel()
                "Ebeveyn kontrolü" -> TvLockPanel()
                "Hakkında" -> TvAboutPanel()
            }
        }
    }
}

@Composable
private fun TvSettingRow(
    title: String,
    value: String = "",
    check: Boolean? = null,
    onCheck: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .tvFocusRing(focused, 18.dp, 1.02f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF15151F))
            .border(1.dp, PLine, RoundedCornerShape(18.dp))
            .tvClickableNoRipple {
                if (check != null && onCheck != null) onCheck(!check) else onClick()
            }
            .padding(horizontal = 24.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 17.sp, color = Color.White, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) {
            Text(value, color = PTx2, fontSize = 15.sp)
        }
        if (check != null && onCheck != null) {
            Spacer(Modifier.width(12.dp))
            Switch(checked = check, onCheckedChange = null)
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun TvSrcPanel(
    session: Session,
    vis: Session,
    onRefresh: () -> Unit,
    onSourceSwitch: () -> Unit,
    onLogout: () -> Unit,
    onOpenFilter: () -> Unit
) {
    val ctx = LocalContext.current
    val hidden by FavoritesStore.hiddenCatsFlow(ctx).collectAsState(initial = emptySet())
    var confirmDelete by remember { mutableStateOf(false) }
    val typeLabel = when {
        session.stalkerUrl.isNotBlank() -> "Portal"
        session.xServer.isNotBlank() -> "Xtream Codes"
        else -> "M3U"
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        // Kaynak karti
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF7A5CFF), Color(0xFF2A1C7A), Color(0xFF150F3D))
                    )
                )
                .padding(28.dp)
        ) {
            Column {
                Text("Bağlı · $typeLabel", color = Color(0xFF7DFFA0),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(session.sourceName.ifBlank { session.label },
                    fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${vis.channels.size} kanal · ${vis.movies.size} film · ${vis.series.size} dizi",
                    fontSize = 15.sp, color = Color.White.copy(alpha = 0.75f)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        TvSettingRow(
            title = "Ülke ve kategori filtresi",
            value = "Seçili ülkeler  ›",
            onClick = onOpenFilter
        )
        TvSettingRow(title = "Listeyi yenile", value = "", onClick = onRefresh)
        TvSettingRow(title = "Kaynağı değiştir", value = "", onClick = onSourceSwitch)
        if (!confirmDelete) {
            TvSettingRow(title = "Kaynağı sil", value = "", onClick = { confirmDelete = true })
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    TvSettingRow(title = "Vazgeç", value = "", onClick = { confirmDelete = false })
                }
                Box(Modifier.weight(1f)) {
                    TvSettingRow(title = "Evet, sil", value = "", onClick = onLogout)
                }
            }
        }
        if (hidden.isNotEmpty()) {
            Text(
                "${hidden.size} kategori gizli — filtre ekranından açabilirsin.",
                color = PTx2, fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun TvPlayerPanel() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val autoplay by FavoritesStore.autoplayFlow(ctx).collectAsState(initial = true)
    val tvResume by FavoritesStore.tvResumeFlow(ctx).collectAsState(initial = true)
    val prefs by FavoritesStore.trackPrefsFlow(ctx).collectAsState(initial = Pair("auto", "off"))

    Column(Modifier.verticalScroll(rememberScrollState())) {
        TvSettingRow(title = "Varsayılan kalite", value = "Otomatik", onClick = {
            tvToast(ctx, "Otomatik kalite kullanılıyor")
        })
        TvSettingRow(
            title = "Ses dili",
            value = when (prefs.first) {
                "tr" -> "Türkçe"
                "en" -> "İngilizce"
                else -> "Otomatik"
            },
            onClick = {
                val next = when (prefs.first) {
                    "auto" -> "tr"
                    "tr" -> "en"
                    else -> "auto"
                }
                scope.launch { FavoritesStore.setTrackPrefs(ctx, next, prefs.second) }
            }
        )
        TvSettingRow(
            title = "Altyazı dili",
            value = when (prefs.second) {
                "tr" -> "Türkçe"
                "en" -> "İngilizce"
                "auto" -> "Otomatik"
                else -> "Kapalı"
            },
            onClick = {
                val next = when (prefs.second) {
                    "off" -> "auto"
                    "auto" -> "tr"
                    "tr" -> "en"
                    else -> "off"
                }
                scope.launch { FavoritesStore.setTrackPrefs(ctx, prefs.first, next) }
            }
        )
        TvSettingRow(
            title = "Sonraki bölümü otomatik oynat", check = autoplay,
            onCheck = { scope.launch { FavoritesStore.setAutoplay(ctx, it) } },
            onClick = {}
        )
        TvSettingRow(
            title = "Kaldığım yerden devam et", check = tvResume,
            onCheck = { scope.launch { FavoritesStore.setTvResume(ctx, it) } },
            onClick = {}
        )
    }
}

@Composable
private fun TvLivePanel() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playLast by FavoritesStore.tvPlayLastFlow(ctx).collectAsState(initial = true)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TvSettingRow(
            title = "Açılışta son kanalı oynat", check = playLast,
            onCheck = { scope.launch { FavoritesStore.setTvPlayLast(ctx, it) } },
            onClick = {}
        )
        TvSettingRow(title = "Rehber güncelleme", value = "Her gün", onClick = {
            tvToast(ctx, "Rehber her açılışta tazelenir")
        })
        TvSettingRow(title = "Kanal sıralaması", value = "Kanal numarası", onClick = {
            tvToast(ctx, "Kanal numarasına göre sıralanır")
        })
    }
}

@Composable
private fun TvLookPanel() {
    val ctx = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TvSettingRow(title = "Tema", value = "Koyu", onClick = {
            tvToast(ctx, "Portio TV koyu tema kullanır")
        })
        TvSettingRow(title = "Uygulama dili", value = "Türkçe", onClick = {
            tvToast(ctx, "Uygulama dili: Türkçe")
        })
    }
}

@Composable
private fun TvLockPanel() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hideAdult by FavoritesStore.hideAdultFlow(ctx).collectAsState(initial = false)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TvSettingRow(
            title = "Yetişkin kategorilerini gizle", check = hideAdult,
            onCheck = { scope.launch { FavoritesStore.setHideAdult(ctx, it) } },
            onClick = {}
        )
        Text(
            "Açıkken yetişkin içerikli kategoriler tüm sekmelerde gizlenir.",
            color = PTx2, fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun TvAboutPanel() {
    val ctx = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TvSettingRow(title = "Sürüm", value = "2.9.2 TV", onClick = {
            tvToast(ctx, "Portio TV 2.9.2")
        })
        TvSettingRow(title = "Lisanslar ve gizlilik", value = "", onClick = {
            tvToast(ctx, "Portio TV · Tüm yayınların tek yerde")
        })
    }
}

// ==================== DETAY ====================

@Composable
fun TvMovieDetail(
    movie: StalkerChannel,
    session: Session,
    onBack: () -> Unit,
    onSelect: (StalkerChannel) -> Unit,
    onPlay: (String, Long, Map<String, String>) -> Unit
) {
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val favs by FavoritesStore.favsFlow(ctx).collectAsState(initial = emptySet())
    val resume by FavoritesStore.entryFlow(ctx, movie.id).collectAsState(initial = null)
    val prefs by FavoritesStore.trackPrefsFlow(ctx).collectAsState(initial = Pair("auto", "off"))
    var detail by remember { mutableStateOf<VodDetail?>(null) }
    var busy by remember { mutableStateOf(false) }
    val playFr = remember { FocusRequester() }

    LaunchedEffect(movie.id) {
        detail = null
        if (session.xServer.isNotEmpty() && movie.id.startsWith("vod_")) {
            try {
                detail = XtreamClient(session.xServer, session.xUser, session.xPass)
                    .vodInfo(movie.id.removePrefix("vod_"))
            } catch (_: Exception) { }
        }
    }
    LaunchedEffect(Unit) {
        try { playFr.requestFocus() } catch (_: Exception) { }
    }

    val similar = remember(movie.id, session) {
        session.movies.filter { it.id != movie.id && it.genre == movie.genre }.take(8)
            .ifEmpty { session.movies.filter { it.id != movie.id }.take(8) }
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

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B12))) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 50.dp)
        ) {
            item {
                val tag = if (resume?.hasValid() == true) "Kaldığın yerden" else "Film"
                Box(
                    Modifier.clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text("$tag · ${movie.genre}", color = Color.White,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(14.dp))
                Text(movie.name, fontSize = 56.sp, lineHeight = 58.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp,
                    color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (detail?.rating?.isNotBlank() == true) {
                        Text("★ ${detail!!.rating}", color = Color.White,
                            fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    if (detail?.year?.isNotBlank() == true) {
                        Text(detail!!.year, color = PTx2, fontSize = 17.sp)
                    }
                    if (detail?.duration?.isNotBlank() == true) {
                        Text(detail!!.duration, color = PTx2, fontSize = 17.sp)
                    }
                }
                val plot = detail?.plot?.ifBlank { null }
                if (!plot.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(plot, fontSize = 17.sp, lineHeight = 25.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvButton(
                        if (busy) "Açılıyor..." else if (resume?.hasValid() == true) "▶  Devam et" else "▶  Oynat",
                        primary = true, focusMe = playFr,
                        onClick = {
                            playNow(if (resume?.hasValid() == true) resume!!.posMs else 0L)
                        }
                    )
                    TvButton(
                        if (favs.contains(movie.id)) "✓ Listemde" else "+ Listem",
                        primary = false,
                        onClick = { scope.launch { FavoritesStore.toggle(ctx, movie.id) } }
                    )
                }
            }
            if (similar.isNotEmpty()) {
                item { TvSectionTitle("Benzer filmler") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(similar, key = { it.id }) { m ->
                            TvPosterCard(m.name, m.logo) { onSelect(m) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvSeriesDetail(
    entry: SeriesEntry,
    session: Session,
    onBack: () -> Unit,
    onSelect: (SeriesEntry) -> Unit,
    onPlayEpisode: (String, String, List<EpisodeEntry>, Int, Long) -> Unit
) {
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val favs by FavoritesStore.favsFlow(ctx).collectAsState(initial = emptySet())
    val watched by FavoritesStore.watchedFlow(ctx).collectAsState(initial = emptySet())
    val resumeMap by FavoritesStore.resumeFlow(ctx).collectAsState(initial = emptyMap())
    var full by remember { mutableStateOf<SeriesEntry?>(if (entry.episodes.isNotEmpty()) entry else null) }
    var season by remember { mutableIntStateOf(-1) }
    var loading by remember { mutableStateOf(false) }
    val playFr = remember { FocusRequester() }

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
    LaunchedEffect(Unit) {
        try { playFr.requestFocus() } catch (_: Exception) { }
    }

    val seasons = remember(full) { full?.episodes?.map { it.season }?.distinct()?.sorted() ?: emptyList() }
    val allEps = remember(full) { full?.episodes ?: emptyList() }
    val eps = remember(full, season) { allEps.filter { season == -1 || it.season == season } }
    val nextIdx = remember(allEps, watched, resumeMap) {
        val withPos = allEps.indexOfFirst { ep ->
            (resumeMap["series_" + ep.id]?.hasValid() == true)
        }
        if (withPos >= 0) withPos
        else allEps.indexOfFirst { !watched.contains("series_" + it.id) }.takeIf { it >= 0 } ?: 0
    }.takeIf { allEps.isNotEmpty() } ?: -1

    val similar = remember(entry.id, session) {
        session.series.filter { it.id != entry.id && it.category == entry.category }.take(8)
            .ifEmpty { session.series.filter { it.id != entry.id }.take(8) }
    }

    fun playIdx(idx: Int) {
        val ep = allEps.getOrNull(idx) ?: return
        scope.launch {
            val info = resumeMap["series_" + ep.id]
            val ms = if (info?.hasValid() == true) info.posMs else 0L
            onPlayEpisode(entry.name, entry.cover, allEps, idx, ms)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B12))) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 50.dp)
        ) {
            item {
                Box(
                    Modifier.clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text("Dizi · ${entry.category}", color = Color.White,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(14.dp))
                Text(entry.name, fontSize = 56.sp, lineHeight = 58.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp,
                    color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Text("${seasons.size} sezon • ${allEps.size} bölüm",
                    color = PTx2, fontSize = 17.sp)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvButton(
                        if (loading) "Yükleniyor..." else "▶  Oynat",
                        primary = true, focusMe = playFr,
                        onClick = { if (allEps.isNotEmpty()) playIdx(if (nextIdx >= 0) nextIdx else 0) }
                    )
                    TvButton(
                        if (favs.contains(entry.id)) "✓ Listemde" else "+ Listem",
                        primary = false,
                        onClick = { scope.launch { FavoritesStore.toggle(ctx, entry.id) } }
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bölümler", fontWeight = FontWeight.Bold, fontSize = 24.sp,
                        modifier = Modifier.weight(1f))
                }
                if (seasons.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(seasons) { s ->
                            TvChip("Sezon $s", season == s, onClick = { season = s })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            items(eps, key = { it.id }) { ep ->
                val idx = allEps.indexOfFirst { it.id == ep.id }
                val isW = watched.contains("series_" + ep.id)
                var focused by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                        .tvFocusRing(focused, 16.dp, 1.02f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF15151F))
                        .tvClickableNoRipple { playIdx(idx) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(120.dp, 68.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(tileBrush(ep.id)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▶", color = Color.White, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("S${ep.season} B${ep.episode} · ${ep.title}",
                            fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (ep.durationSecs > 0) "${ep.durationSecs / 60} dk" else "") +
                                (if (isW) " · İzlendi" else ""),
                            color = PTx2, fontSize = 14.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (similar.isNotEmpty()) {
                item { TvSectionTitle("Benzer diziler") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(similar, key = { it.id }) { s ->
                            TvPosterCard(s.name, s.cover) { onSelect(s) }
                        }
                    }
                }
            }
        }
    }
}

// ==================== ULKE FILTRESI ====================

@Composable
fun TvCountryFilter(session: Session, onClose: () -> Unit) {
    BackHandler { onClose() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hidden by FavoritesStore.hiddenCatsFlow(ctx).collectAsState(initial = emptySet())
    val overrides by FavoritesStore.catCountryFlow(ctx).collectAsState(initial = emptyMap())
    val applyFr = remember { FocusRequester() }

    val kinds = remember(session) {
        listOf(
            "live" to session.channels.groupBy { it.genre }.map { (k, v) -> k to v.size },
            "movie" to session.movies.groupBy { it.genre }.map { (k, v) -> k to v.size },
            "series" to session.series.groupBy { it.category }.map { (k, v) -> k to v.size }
        )
    }
    val groupsAll = remember(kinds, overrides) {
        kinds.map { (kind, cats) -> groupByCountry(kind, cats, overrides) }
    }
    val allKeys = remember(kinds) {
        kinds.flatMap { (kind, cats) -> cats.map { (n, _) -> "$kind:$n" } }.toSet()
    }
    // Ulkeler: 3 turun birlesimi (bayrak + toplam kategori)
    val countries = remember(groupsAll) {
        val order = mutableListOf<String>()
        val counts = mutableMapOf<String, Int>()
        val keys = mutableMapOf<String, MutableSet<String>>()
        groupsAll.forEachIndexed { i, gl ->
            gl.forEach { cg ->
                if (!order.contains(cg.code)) order.add(cg.code)
                counts[cg.code] = (counts[cg.code] ?: 0) + cg.subs.size
                keys.getOrPut(cg.code) { mutableSetOf() }.addAll(
                    kinds[i].first.let { kind ->
                        cg.subs.flatMap { it.keys }
                    }
                )
            }
        }
        order.map { Triple(it, counts[it] ?: 0, keys[it] ?: emptySet<String>()) }
    }
    val selected = remember(countries, hidden) {
        countries.filter { (_, _, keys) -> keys.any { !hidden.contains(it) } }.map { it.first }.toSet()
    }

    fun setCountry(code: String, show: Boolean) {
        scope.launch {
            val keys = countries.firstOrNull { it.first == code }?.third ?: emptySet()
            FavoritesStore.replaceHiddenCats(
                ctx, if (show) hidden - keys else hidden + keys
            )
        }
    }

    LaunchedEffect(Unit) {
        try { applyFr.requestFocus() } catch (_: Exception) { }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B12))) {
        Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 36.dp)) {
            Text("Ülke ve kategoriler", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-2).sp, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text("Sadece izlemek istediğin ülkeleri seç, gerisi gizlenir.",
                fontSize = 17.sp, color = PTx2)
            Spacer(Modifier.height(20.dp))
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(countries, key = { it.first }) { (code, count, _) ->
                    val def = countryDef(code)
                    val on = selected.contains(code)
                    var focused by remember { mutableStateOf(false) }
                    Column(
                        Modifier.fillMaxWidth().height(150.dp)
                            .onFocusChanged { focused = it.isFocused }
                            .tvFocusRing(focused, 22.dp, 1.05f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                if (on) Color(0xFF30D158).copy(alpha = 0.14f)
                                else Color(0xFF15151F)
                            )
                            .border(
                                1.dp,
                                if (on) Color(0xFF30D158) else PLine,
                                RoundedCornerShape(22.dp)
                            )
                            .tvClickableNoRipple { setCountry(code, !on) }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(def.flag, fontSize = 42.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(def.trName, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$count kategori", fontSize = 13.sp, color = PTx2)
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(bottom = 30.dp)
            ) {
                TvButton("Uygula", primary = true, focusMe = applyFr, onClick = {
                    if (selected.isEmpty()) tvToast(ctx, "En az bir ülke seç")
                    else {
                        tvToast(ctx, "Filtre uygulandı · ${selected.size} ülke")
                        onClose()
                    }
                })
                TvButton("Sadece ABD", primary = false, onClick = {
                    scope.launch {
                        val keep = groupsAll.flatMap { gl ->
                            gl.firstOrNull { it.code == "US" }?.subs?.flatMap { it.keys } ?: emptyList()
                        }.toSet()
                        FavoritesStore.replaceHiddenCats(ctx, allKeys - keep)
                    }
                })
                TvButton("Tümünü seç", primary = false, onClick = {
                    scope.launch { FavoritesStore.replaceHiddenCats(ctx, emptySet()) }
                })
            }
        }
    }
}
