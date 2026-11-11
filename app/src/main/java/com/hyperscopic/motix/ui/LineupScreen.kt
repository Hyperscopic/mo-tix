package com.hyperscopic.motix.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperscopic.motix.MoTixViewModel
import com.hyperscopic.motix.R
import com.hyperscopic.motix.data.Film
import com.hyperscopic.motix.data.FilmList
import com.hyperscopic.motix.data.rememberImage

/**
 * The lineup: what is in theaters now where the car is. The selected film leads (what it is, where it is next on
 * nearby, the trailer and the theaters one tap away); the posters of everything else run underneath.
 */
@Composable
fun LineupScreen(vm: MoTixViewModel, list: FilmList) {
    val compact = LocalCompact.current
    val selected = vm.film ?: list.films.first()
    Box(Modifier.fillMaxSize().background(MT.Bg)) {
        if (!compact) {
            // An ambient wash from the selected film: blurred, so a small still never shows soft.
            Crossfade(selected.heroUrl, Modifier.fillMaxWidth().fillMaxHeight(0.7f), animationSpec = tween(400), label = "wash") { url ->
                rememberImage(url)?.let { Image(it, null, Modifier.fillMaxSize().blur(64.dp).alpha(0.6f), contentScale = ContentScale.Crop) }
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to MT.Bg.copy(0.7f), 0.6f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to MT.Bg.copy(0.5f), 0.4f to MT.Bg.copy(0.3f), 0.64f to MT.Bg)))
        }
        Column(if (compact) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier.fillMaxSize()) {
            Header(vm) {
                if (vm.booking != null) {
                    SecondaryButton(stringResource(R.string.my_ticket), Modifier.height(72.dp), painterResource(R.drawable.ic_ticket)) { vm.push(com.hyperscopic.motix.Screen.TICKET) }
                    Spacer(Modifier.width(16.dp))
                }
                HeaderIcon(rememberVectorPainter(Icons.Filled.Info), stringResource(R.string.sources)) { vm.creditsOpen = true }
            }
            if (compact) {
                CompactHero(vm, selected)
            } else {
                Crossfade(selected, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 56.dp), animationSpec = tween(300), label = "hero") { f -> WideHero(vm, f) }
            }
            // The row title: "오늘 밤 상영작   성수동 근처 상영작 8편"
            Row(Modifier.fillMaxWidth().padding(start = if (compact) 28.dp else 56.dp, end = if (compact) 28.dp else 48.dp, top = if (compact) 28.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val evening = vm.now.atZone(list.market.zone).hour >= 16
                Text(
                    stringResource(if (evening) R.string.whats_on_tonight else R.string.whats_on_today),
                    style = if (compact) Type.TitleS else Type.TitleM, color = MT.Text, modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.width(20.dp))
                Text(
                    vm.area?.let { stringResource(R.string.films_near, list.films.size, it.name) } ?: stringResource(R.string.films_count, list.films.size),
                    style = Type.LabelM, color = MT.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).alignByBaseline(),
                )
            }
            val posterWidth = if (compact) 168.dp else 220.dp
            LazyRow(
                state = rememberLazyListState(),
                contentPadding = PaddingValues(start = if (compact) 24.dp else 52.dp, end = if (compact) 24.dp else 48.dp, top = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 22.dp),
            ) {
                items(list.films, key = { it.id }) { film ->
                    // The first tap puts the film up top; tapping the film already there goes to its theaters.
                    PosterCard(film, film.id == selected.id, posterWidth) { if (film.id == selected.id) vm.openTheaters() else vm.select(film) }
                }
            }
            // The Google Search suggestions strip: hidden in this build (see SHOW_SEARCH_SUGGESTIONS).
            if (SHOW_SEARCH_SUGGESTIONS) {
                SearchSuggestions(vm, list.search, Modifier.fillMaxWidth().padding(horizontal = if (compact) 20.dp else 48.dp).padding(bottom = 12.dp))
            }
        }
    }
}

/** The selected film in the full window: text on the left, its trailer still on the right. */
@Composable
private fun WideHero(vm: MoTixViewModel, f: Film) {
    val fmt = LocalFmt.current
    val still = rememberImage(f.heroUrl)
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.now_showing), style = Type.LabelS, color = MT.TextSoft, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
            // A long title steps down a size and takes a second line rather than losing its end.
            val title = f.title(fmt.korean)
            Text(title, style = if (title.length > 24) Type.HeadlineS else Type.HeadlineL, color = MT.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            f.secondTitle(fmt.korean)?.let { Text(it, style = Type.TitleS, color = MT.TextSoft, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            f.metaLine().takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = Type.LabelM, color = MT.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            f.synopsis(fmt.korean).takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, style = Type.BodyS, color = MT.TextSoft, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 920.dp))
            }
            NearbyLine(vm, f)
            Spacer(Modifier.height(26.dp))
            HeroButtons(vm, f)
        }
        if (still != null) {
            Spacer(Modifier.width(56.dp))
            // The still at its own shape (a 16:9 trailer thumbnail keeps its edges and its title card).
            Box(
                Modifier.width(560.dp).aspectRatio((still.width.toFloat() / still.height).coerceIn(1.33f, 1.9f))
                    .tap(RoundedCornerShape(24.dp)) { if (f.trailer != null) vm.openTrailer() else vm.openTheaters() },
                contentAlignment = Alignment.Center,
            ) {
                Image(still, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (f.trailer != null) PlayBadge()
            }
        } else if (f.posterUrl != null) {
            // No trailer and no still: the poster stands in.
            Spacer(Modifier.width(56.dp))
            Poster(f, Modifier.height(430.dp).tap(RoundedCornerShape(16.dp)) { vm.openTheaters() })
        }
    }
}

/** The selected film in the half window: its still across the top, the text and the two actions under it. */
@Composable
private fun CompactHero(vm: MoTixViewModel, f: Film) {
    val fmt = LocalFmt.current
    val still = rememberImage(f.heroUrl)
    Box(
        Modifier.fillMaxWidth().height(300.dp).background(MT.Surface).tap(RoundedCornerShape(0.dp)) { if (f.trailer != null) vm.openTrailer() else vm.openTheaters() },
        contentAlignment = Alignment.Center,
    ) {
        if (still != null) Image(still, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to MT.Bg)))
        if (f.trailer != null) PlayBadge()
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.now_showing), style = Type.LabelS, color = MT.TextSoft, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
        val title = f.title(fmt.korean)
        Text(title, style = if (title.length > 22) Type.TitleL else Type.HeadlineS, color = MT.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        f.secondTitle(fmt.korean)?.let { Text(it, style = Type.BodyM, color = MT.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        f.metaLine().takeIf { it.isNotBlank() }?.let { Text(it, style = Type.LabelM, color = MT.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        NearbyLine(vm, f)
        Spacer(Modifier.height(22.dp))
        HeroButtons(vm, f, fill = true)
    }
}

@Composable
private fun PlayBadge() {
    Box(Modifier.size(104.dp).clip(CircleShape).background(Color.Black.copy(0.55f)).border(3.dp, Color.White.copy(0.85f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.PlayArrow, stringResource(R.string.watch_trailer), tint = Color.White, modifier = Modifier.size(60.dp))
    }
}

/** "가장 빠른 상영: 메가박스 코엑스 오후 7:20, 3.6km  ·  극장 8곳" */
@Composable
private fun NearbyLine(vm: MoTixViewModel, f: Film) {
    val fmt = LocalFmt.current
    val near = vm.nearby(f)
    val soonest = near.minByOrNull { it.showings.first().startsAt.toInstant() } ?: return
    val first = soonest.showings.first().startsAt
    val tomorrow = first.toLocalDate() != vm.now.atZone(first.zone).toLocalDate()
    val line = stringResource(
        if (tomorrow) R.string.next_nearby_tomorrow else R.string.next_nearby,
        fmt.time(first), soonest.theater.name(fmt.korean), soonest.metres?.let { fmt.distance(it) }.orEmpty(),
    ).trimEnd(',', ' ') + SEP + pluralStringResource(R.plurals.theater_count, near.size, near.size)
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.LocationOn, null, tint = MT.Accent, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(10.dp))
        Text(line, style = if (LocalCompact.current) Type.LabelM else Type.BodyS, color = MT.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HeroButtons(vm: MoTixViewModel, f: Film, fill: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val each = if (fill) Modifier.weight(1f) else Modifier
        val pin = rememberVectorPainter(Icons.Filled.LocationOn)
        if (f.trailer != null) {
            PrimaryButton(stringResource(R.string.watch_trailer), each, rememberVectorPainter(Icons.Filled.PlayArrow)) { vm.openTrailer() }
            SecondaryButton(stringResource(R.string.see_it_nearby), each, pin) { vm.openTheaters() }
        } else {
            PrimaryButton(stringResource(R.string.see_it_nearby), each, pin) { vm.openTheaters() }
        }
    }
}

@Composable
private fun PosterCard(film: Film, selected: Boolean, width: Dp, onClick: () -> Unit) {
    val fmt = LocalFmt.current
    Column(Modifier.width(width).tap(RoundedCornerShape(20.dp), onClick = onClick).padding(4.dp)) {
        Box {
            Poster(film, Modifier.fillMaxWidth())
            if (selected) Box(Modifier.matchParentSize().border(4.dp, MT.Accent, RoundedCornerShape(16.dp)))
        }
        Spacer(Modifier.height(10.dp))
        Text(film.title(fmt.korean), style = Type.LabelM, color = MT.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(film.genres(fmt.korean).firstOrNull(), film.runtimeMinutes?.let { fmt.runtime(it) }).joinToString(" · "),
            style = Type.LabelS, color = MT.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
