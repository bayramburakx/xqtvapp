package com.bayram.xqtvapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Portio tasarim jetonlari ----------
val PBg = Color(0xFF0B0B12)
val PBg2 = Color(0xFF15151F)
val PTx = Color(0xFFF5F5F7)
val PTx2 = Color(0xFF9A9AA8)
val PGlass = Color(0x9E20202C)
val PLine = Color(0x1FFFFFFF)
val POk = Color(0xFF30D158)
val PLive = Color(0xFFFF453A)
val PAcc1 = Color(0xFF8A6BFF)
val PAcc2 = Color(0xFFE84A8F)

val PortioGradient = Brush.linearGradient(listOf(PAcc1, PAcc2))

private val TileGrads = listOf(
    Pair(Color(0xFF6A4CFF), Color(0xFF1D1659)),
    Pair(Color(0xFFFF7A45), Color(0xFF6B1D2E)),
    Pair(Color(0xFF18C6B0), Color(0xFF0B3B4F)),
    Pair(Color(0xFFF2B84B), Color(0xFF7A2C10)),
    Pair(Color(0xFFE84A8F), Color(0xFF3B1458)),
    Pair(Color(0xFF4AA3FF), Color(0xFF112A63))
)

fun tileBrush(key: String): Brush {
    val i = (key.hashCode() and Int.MAX_VALUE) % TileGrads.size
    val (a, b) = TileGrads[i]
    return Brush.linearGradient(listOf(a, b))
}

fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

/** Tasarimdaki ince ilerleme cizgisi. */
@Composable
fun ProgressLine(progress: Float, color: Color = Color.White, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(9.dp)),
        color = color,
        trackColor = Color.White.copy(alpha = 0.22f)
    )
}

/** Bolum basligi + sag link. */
@Composable
fun SectionHead(title: String, count: Int = 0, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 21.sp,
            letterSpacing = (-0.3).sp, modifier = Modifier.weight(1f))
        if (count > 0) Text("$count", color = PTx2, fontSize = 13.sp,
            modifier = Modifier.padding(end = 8.dp))
        if (action != null) {
            Text(action, color = PTx2, fontSize = 14.sp,
                modifier = Modifier.clickableNoRipple { onAction?.invoke() })
        }
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(
        androidx.compose.foundation.clickable(
            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
            indication = null,
            onClick = onClick
        )
    )

/** "38 dk kaldi" tarzi kalan sure metni. */
fun remainingText(posMs: Long, durMs: Long): String {
    if (durMs <= 0) return ""
    val left = durMs - posMs
    if (left <= 0) return ""
    val m = left / 60000
    return if (m < 60) "$m dk kaldı" else "${m / 60} sa ${m % 60} dk kaldı"
}

@Composable
fun MetaText(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, modifier = modifier,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

