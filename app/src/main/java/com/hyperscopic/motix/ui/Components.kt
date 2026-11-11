package com.hyperscopic.motix.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperscopic.motix.MoTixViewModel
import com.hyperscopic.motix.R
import com.hyperscopic.motix.data.Film
import com.hyperscopic.motix.data.rememberImage

/** A touch target with a ripple, clipped to [shape]. */
fun Modifier.tap(shape: Shape = RoundedCornerShape(Dimens.ButtonRadius), enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    clip(shape).clickable(remember { MutableInteractionSource() }, ripple(color = Color.White), enabled = enabled, role = Role.Button, onClick = onClick)
}

@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, icon: Painter? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.height(Dimens.Button).tap(enabled = enabled, onClick = onClick).background(if (enabled) MT.Accent else MT.Raised).padding(horizontal = 36.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        val ink = if (enabled) MT.OnAccent else MT.TextDim
        icon?.let { Icon(it, null, tint = ink, modifier = Modifier.size(36.dp)); Spacer(Modifier.width(14.dp)) }
        Text(label, style = Type.BodyM, color = ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(label: String, modifier: Modifier = Modifier, icon: Painter? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Dimens.ButtonRadius)
    Row(
        modifier.height(Dimens.Button).tap(shape, onClick = onClick).background(Color.White.copy(0.10f)).border(1.5.dp, Color.White.copy(0.24f), shape).padding(horizontal = 34.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let { Icon(it, null, tint = MT.Text, modifier = Modifier.size(34.dp)); Spacer(Modifier.width(14.dp)) }
        Text(label, style = Type.BodyM, color = MT.Text, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Mo-" light, "Tix" bold. */
@Composable
fun Wordmark(size: Int = 40, color: Color = MT.Text) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append("Mo-") }
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("Tix") }
        },
        fontFamily = AstaSans, fontSize = size.sp, color = color, maxLines = 1,
    )
}

/**
 * The top of every screen: back (when there is somewhere to go back to), the wordmark, and where and when the listings
 * are for ("성수동 · 11월 12일 (목) · 오후 6:31").
 */
@Composable
fun Header(vm: MoTixViewModel, backLabel: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val compact = LocalCompact.current
    val fmt = LocalFmt.current
    Row(Modifier.fillMaxWidth().height(Dimens.Header).padding(horizontal = if (compact) 28.dp else 48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (backLabel != null && onBack != null) {
            Row(
                Modifier.height(72.dp).tap(RoundedCornerShape(36.dp), onClick = onBack).background(Color.White.copy(0.10f)).padding(start = 12.dp, end = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MT.Text, modifier = Modifier.size(44.dp))
                Text(backLabel, style = Type.BodyS, color = MT.Text, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            Spacer(Modifier.width(if (compact) 20.dp else 32.dp))
        }
        Wordmark()
        Spacer(Modifier.width(24.dp))
        val now = vm.now.atZone(vm.market?.zone ?: java.time.ZoneId.systemDefault())
        Text(
            listOfNotNull(vm.area?.name, fmt.day(now), fmt.time(now)).joinToString(SEP),
            style = Type.LabelM, color = MT.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** A round icon button in the header. */
@Composable
fun HeaderIcon(painter: Painter, description: String, onClick: () -> Unit) {
    Box(Modifier.size(72.dp).tap(CircleShape, onClick = onClick).background(Color.White.copy(0.10f)), contentAlignment = Alignment.Center) {
        Icon(painter, description, tint = MT.Text, modifier = Modifier.size(34.dp))
    }
}

/** A film poster (2:3), or a typographic stand-in while there is no artwork (never someone else's picture). */
@Composable
fun Poster(film: Film, modifier: Modifier = Modifier, radius: Dp = 16.dp) {
    val image = rememberImage(film.posterUrl)
    Box(modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(radius)).background(MT.Raised)) {
        if (image != null) Image(image, film.title(LocalFmt.current.korean), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else FallbackArt(film)
    }
}

@Composable
fun FallbackArt(film: Film, modifier: Modifier = Modifier) {
    val hue = (film.id.hashCode() and 0xFF) / 255f
    val top = Color.hsv(220f + 60f * hue, 0.25f, 0.24f)
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, MT.Surface))).padding(16.dp), contentAlignment = Alignment.Center) {
        Text(
            film.title(LocalFmt.current.korean), style = Type.BodyS, color = MT.Text, fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Mo-Tix screens sit on the film: its still full-bleed, darkened toward the text. Big pictures sell the night out. */
@Composable
fun StillBackground(film: Film?, modifier: Modifier = Modifier, dim: Float = 0f) {
    val image = rememberImage(film?.stillUrl ?: film?.heroUrl)
    Box(modifier.fillMaxSize().background(MT.Bg)) {
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.7f)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to MT.Bg.copy(0.9f), 0.42f to MT.Bg.copy(0.55f), 1f to MT.Bg.copy(0.35f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to MT.Bg.copy(0.85f), 0.16f to Color.Transparent, 0.62f to Color.Transparent, 1f to MT.Bg.copy(0.9f))))
        // Screens with fine detail on them (the seat map) sit on a darker picture.
        if (dim > 0f) Box(Modifier.fillMaxSize().background(MT.Bg.copy(dim)))
    }
}

/** A card over a still. */
fun Modifier.glassCard(radius: Dp = Dimens.CardRadius): Modifier =
    clip(RoundedCornerShape(radius)).background(MT.Glass).border(1.dp, MT.Line, RoundedCornerShape(radius))

/** A line of plain status in place of something that is not there (no trailer, no theaters). */
@Composable
fun NoteLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier.heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(MT.TextDim))
        Spacer(Modifier.width(16.dp))
        Text(text, style = Type.BodyS, color = MT.TextSoft)
    }
}

/** The genres, runtime and (US) rating: "액션 · 어드벤처   |   1시간 59분". */
@Composable
fun Film.metaLine(): String {
    val fmt = LocalFmt.current
    return listOfNotNull(
        genres(fmt.korean).take(3).joinToString(" · ").takeIf { it.isNotBlank() },
        runtimeMinutes?.let { fmt.runtime(it) },
        rating,
    ).joinToString("   |   ")
}
