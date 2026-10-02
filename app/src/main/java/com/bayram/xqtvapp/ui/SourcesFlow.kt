package com.bayram.xqtvapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bayram.xqtvapp.Session
import com.bayram.xqtvapp.data.ContentCache
import com.bayram.xqtvapp.data.SourceEntry
import com.bayram.xqtvapp.data.SourceStore
import com.bayram.xqtvapp.data.loadSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ==================== AÇILIŞ ====================

@Composable
fun SplashScreen(onDone: () -> Unit) {
    var gone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2200)
        if (!gone) {
            gone = true
            onDone()
        }
    }
    Box(
        Modifier.fillMaxSize().background(PBg)
            .clickableNoRipple { if (!gone) { gone = true; onDone() } },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxWidth(1.3f).aspectRatio(1f).align(Alignment.TopCenter)
                .offset(y = (-60).dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x8C6A4CFF), Color(0x2EE84A8F), Color.Transparent)
                    )
                )
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(28.dp))
                    .background(PortioGradient),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(26.dp))
            Text("Portio", fontSize = 54.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-2.5).sp)
            Text("Tüm yayınların tek yerde.", color = PTx2, fontSize = 16.sp)
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)
                .width(120.dp).height(3.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = 0.12f))
        )
    }
}

// ==================== KAYNAK LİSTESİ ====================

@Composable
fun SourcesScreen(
    sources: List<SourceEntry>,
    activeId: String?,
    counts: Map<String, Triple<Int, Int, Int>>,
    onPick: (SourceEntry) -> Unit,
    onAdd: () -> Unit,
    onBack: (() -> Unit)?
) {
    if (onBack != null) BackHandler { onBack() }
    Column(Modifier.fillMaxSize().background(PBg).padding(20.dp, 34.dp, 20.dp, 22.dp)) {
        Text("Kaynağını seç", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1.5).sp)
        Text("İzlemek istediğin listeyi seç ya da yeni bir kaynak ekle.",
            color = PTx2, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
        if (sources.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Henüz kaynak yok.\nAşağıdan ilk kaynağını ekle.", color = PTx2,
                    fontSize = 15.sp, lineHeight = 22.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sources, key = { it.id }) { s ->
                    val c = counts[s.cacheKey()]
                    val total = (c?.first ?: 0) + (c?.second ?: 0) + (c?.third ?: 0)
                    val active = s.id == activeId
                    SourceCard(
                        name = s.name,
                        sub = "${s.describe()}${if (total > 0) " · ${"%,d".format(total)} içerik" else ""}",
                        tile = when (s.type) {
                            "xtream" -> "XT"
                            "stalker" -> "PT"
                            else -> "M3U"
                        },
                        active = active,
                        onClick = { onPick(s) }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(99.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) { Text("Kaynak ekle", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun SourceCard(name: String, sub: String, tile: String, active: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PGlass),
        border = androidx.compose.foundation.BorderStroke(1.dp, PLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(15.dp)).background(tileBrush(tile)),
                contentAlignment = Alignment.Center
            ) { Text(tile, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, color = PTx2, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (active) POk else PTx2))
                Spacer(Modifier.width(5.dp))
                Text(if (active) "Aktif" else "Kayıtlı",
                    color = if (active) POk else PTx2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ==================== KAYNAK EKLE ====================

@Composable
fun AddSourceScreen(
    initialMac: String,
    onDone: (SourceEntry) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var tab by remember { mutableIntStateOf(1) } // 0 m3u, 1 xtream, 2 portal
    var name by remember { mutableStateOf("") }
    var m3u by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var portal by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf(initialMac) }
    var error by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().background(PBg).padding(20.dp, 20.dp, 20.dp, 22.dp)) {
        item {
            Row {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(99.dp)) {
                    Text("‹ Geri")
                }
            }
            Text("Kaynak ekle", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 12.dp))
            Text("Kaynak türünü seç ve bilgilerini gir.", color = PTx2, fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(99.dp))
                    .background(PGlass).padding(4.dp)
            ) {
                listOf("M3U", "Xtream", "Portal").forEachIndexed { i, t ->
                    Box(
                        Modifier.weight(1f).height(40.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (tab == i) Color.White else Color.Transparent)
                            .clickableNoRipple { tab = i; error = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(t, color = if (tab == i) Color.Black else PTx2,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        when (tab) {
            0 -> item {
                LabeledInput("Liste adı", "Örn. Spor Listesi", name) { name = it }
                Spacer(Modifier.height(14.dp))
                LabeledInput("M3U bağlantısı", "https://…/liste.m3u", m3u) { m3u = it }
                Spacer(Modifier.height(6.dp))
                Text("Yerel bir dosya kullanmak için bağlantı yerine dosya seçebilirsin.",
                    color = PTx2, fontSize = 13.sp)
            }
            1 -> item {
                LabeledInput("Liste adı", "Örn. Ev Xtream", name) { name = it }
                Spacer(Modifier.height(14.dp))
                LabeledInput("Sunucu adresi", "http://sunucu.com:8080", server) { server = it }
                Spacer(Modifier.height(14.dp))
                LabeledInput("Kullanıcı adı", "", user) { user = it }
                Spacer(Modifier.height(14.dp))
                LabeledInput("Şifre", "", pass, secret = true) { pass = it }
            }
            else -> item {
                LabeledInput("Liste adı", "Örn. Yedek Portal", name) { name = it }
                Spacer(Modifier.height(14.dp))
                LabeledInput("Portal adresi", "http://portal.com/c/", portal) { portal = it }
                Spacer(Modifier.height(14.dp))
                LabeledInput("MAC adresi", "00:1A:79:XX:XX:XX", mac) { mac = it.uppercase() }
                Spacer(Modifier.height(6.dp))
                Text("Cihazının MAC adresi otomatik oluşturulur, istersen değiştirebilirsin.",
                    color = PTx2, fontSize = 13.sp)
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            if (error.isNotEmpty()) {
                Text(error, color = Color(0xFFFF9A9A), fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp))
            }
            val ok = when (tab) {
                0 -> m3u.isNotBlank()
                1 -> server.isNotBlank() && user.isNotBlank()
                else -> portal.isNotBlank() && mac.isNotBlank()
            }
            Button(
                onClick = {
                    val id = "src_${System.currentTimeMillis()}"
                    val e = when (tab) {
                        0 -> SourceEntry(id, name.ifBlank { "M3U Listesi" }, "m3u", url = m3u.trim())
                        1 -> SourceEntry(id, name.ifBlank { "Xtream" }, "xtream",
                            url = server.trim(), user = user.trim(), pass = pass)
                        else -> SourceEntry(id, name.ifBlank { "Portal" }, "stalker",
                            url = portal.trim(), user = mac.trim())
                    }
                    if (tab == 1 && pass.isBlank()) {
                        error = "Şifreni de yazmayı unutma."
                    } else onDone(e)
                },
                enabled = ok,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(99.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) { Text("Bağlan", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun LabeledInput(label: String, hint: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    Column {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PTx2,
            modifier = Modifier.padding(start = 4.dp, bottom = 7.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange,
            placeholder = { Text(hint, color = PTx2) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = PBg2, focusedContainerColor = PBg2,
                unfocusedBorderColor = PLine, focusedBorderColor = PAcc1
            )
        )
    }
}

fun randomMac(): String {
    val r = Random(System.currentTimeMillis())
    return "00:1A:79:%02X:%02X:%02X".format(r.nextInt(256), r.nextInt(256), r.nextInt(256))
}

// ==================== YÜKLEME HALKASI ====================

@Composable
fun LoadingRingScreen(
    src: SourceEntry,
    forceRefresh: Boolean,
    onDone: (Session, Boolean, Int) -> Unit,
    onCancel: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pct by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf("Giriş yapılıyor") }
    var chN by remember { mutableIntStateOf(0) }
    var mvN by remember { mutableIntStateOf(0) }
    var srN by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    BackHandler { job?.cancel(); onCancel() }

    fun phaseOf(p: String): String = when (p) {
        "connect" -> "Bağlantı kuruluyor"
        "channels" -> "Kanallar alınıyor"
        "movies" -> "Filmler ve diziler alınıyor"
        "series" -> "Diziler alınıyor"
        else -> "Hazırlanıyor"
    }

    LaunchedEffect(src.id, forceRefresh) {
        job = scope.launch {
            try {
                val res = loadSource(ctx, src, forceRefresh) { p, ch, mv, sr ->
                    phase = phaseOf(p)
                    chN = ch; mvN = mv; srN = sr
                    pct = when (p) {
                        "connect" -> 12
                        "channels" -> 45
                        "movies" -> 72
                        "series" -> 90
                        else -> 100
                    }
                }
                pct = 100
                phase = "Hazır"
                SourceStore.setLastId(ctx, src.id)
                delay(350)
                onDone(res.session, res.fromCache, res.added)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Bağlanamadı"
            }
        }
    }

    Box(Modifier.fillMaxSize().background(PBg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { pct / 100f },
                    modifier = Modifier.size(150.dp),
                    strokeWidth = 7.dp,
                    trackColor = Color.White.copy(alpha = 0.1f),
                    color = PAcc1
                )
                Text("$pct%", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(30.dp))
            Text(phase, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("${src.name} bağlanıyor", color = PTx2, fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                Counter("Kanal", chN)
                Counter("Film", mvN)
                Counter("Dizi", srN)
            }
            if (error != null) {
                Spacer(Modifier.height(18.dp))
                Text(error!!, color = Color(0xFFFF9A9A), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(99.dp)) {
                    Text("Geri dön")
                }
            }
        }
    }
}

@Composable
private fun Counter(label: String, n: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%,d".format(n), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = PTx2, fontSize = 13.sp)
    }
}
