package com.bayram.xqtvapp.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bayram.xqtvapp.ui.PBg
import com.bayram.xqtvapp.ui.PGlass
import com.bayram.xqtvapp.ui.PLine
import com.bayram.xqtvapp.ui.PTx2
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun TvAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = PBg, surface = PBg, surfaceVariant = Color(0xFF15151F),
            primary = Color.White, onBackground = Color.White, onSurface = Color.White
        )
    ) {
        Surface(modifier = Modifier.fillMaxWidth(), color = PBg) { content() }
    }
}

/** Kumanda odagi halkasi: odaklaninca buyur + beyaz cerceve (tasarimdaki gibi). */
@Composable
fun Modifier.tvFocusRing(focused: Boolean, corner: Dp = 18.dp, scale: Float = 1.06f): Modifier {
    val s by animateFloatAsState(if (focused) scale else 1f, label = "tvScale")
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .then(if (focused) Modifier.border(4.dp, Color.White, RoundedCornerShape(corner)) else Modifier)
}

fun Modifier.tvClickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

/** Odaklanabilir kart: D-pad ile gezilir, odakta halka + buyume gosterir. */
@Composable
fun TvFocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    scale: Float = 1.07f,
    onFocused: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused; onFocused(it.isFocused) }
            .tvFocusRing(focused, corner, scale)
            .clip(RoundedCornerShape(corner))
            .tvClickableNoRipple(onClick),
        content = content
    )
}

/** Kategori cip'i: secili = beyaz, odakta halka. */
@Composable
fun TvChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusMe: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .then(if (focusMe != null) Modifier.focusRequester(focusMe) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusRing(focused, 99.dp, 1.08f)
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) Color.White else PGlass)
            .border(1.dp, PLine, RoundedCornerShape(99.dp))
            .tvClickableNoRipple(onClick)
            .padding(horizontal = 22.dp, vertical = 0.dp)
            .height(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.Black else PTx2, maxLines = 1
        )
    }
}

/** Buyuk hap buton (Oynat / Listem tarzi). */
@Composable
fun TvButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusMe: FocusRequester? = null,
    leading: @Composable (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .then(if (focusMe != null) Modifier.focusRequester(focusMe) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusRing(focused, 99.dp, 1.07f)
            .clip(RoundedCornerShape(99.dp))
            .background(if (primary) Color.White else PGlass)
            .border(1.dp, if (primary) Color.White else PLine, RoundedCornerShape(99.dp))
            .tvClickableNoRipple(onClick)
            .padding(horizontal = 34.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        leading?.invoke()
        if (leading != null) Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = if (primary) Color.Black else Color.White
        )
    }
}

/** Satir basligi (tasarimdaki .rt). */
@Composable
fun TvSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp, color = Color.White,
        modifier = modifier.padding(top = 24.dp, bottom = 14.dp)
    )
}

/** Ust bar saati (HH:mm, 30 sn'de bir guncellenir). */
@Composable
fun TvClock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    Text(
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)),
        fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
        modifier = modifier
    )
}

/** Yetiskin kategori filtresi (telefon + TV ortak kural). */
fun isAdultLabel(name: String): Boolean {
    val t = name.lowercase()
    return t.contains("adult") || t.contains("xxx") || t.contains("porn") ||
        t.contains("erotik") || t.contains("erotic") || t.contains("18+") || t.contains("+18")
}

/** Sadece yildiz: favori isareti (odakta halka). */
@Composable
fun TvStarBtn(filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocusRing(focused, 32.dp, 1.1f)
            .clip(CircleShape)
            .background(Color(0x9E222230))
            .tvClickableNoRipple(onClick)
            .size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "★", fontSize = 26.sp, fontWeight = FontWeight.Bold,
            color = if (filled) Color(0xFFFFD60A) else Color.White
        )
    }
}
@Composable
fun TvHint(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        listOf("↑↓←→" to "Gezin", "OK" to "Seç", "BACK" to "Geri").forEach { (k, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .border(1.dp, PLine, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) { Text(k, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PTx2) }
                Spacer(Modifier.width(8.dp))
                Text(v, fontSize = 18.sp, color = PTx2)
            }
        }
    }
}
