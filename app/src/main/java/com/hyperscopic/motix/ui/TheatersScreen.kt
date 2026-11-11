package com.hyperscopic.motix.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyperscopic.motix.MoTixViewModel
import com.hyperscopic.motix.R
import com.hyperscopic.motix.TheaterShowings
import com.hyperscopic.motix.data.Film
import com.hyperscopic.motix.data.Market
import com.hyperscopic.motix.data.Showing
import com.hyperscopic.motix.data.Theater
import com.hyperscopic.motix.data.rememberImage

/** "See it nearby": the theaters nearest the car with their next showings, on the film's still. Pick a time to book. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TheatersScreen(vm: MoTixViewModel, film: Film) {
    val compact = LocalCompact.current
    val fmt = LocalFmt.current
    val rows = vm.nearby(film)
    Box(Modifier.fillMaxSize()) {
        StillBackground(film)
        Column(Modifier.fillMaxSize()) {
            Header(vm, stringResource(R.string.all_films), onBack = { vm.back() })
            if (compact) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Poster(film, Modifier.width(132.dp), radius = 12.dp)
                    Spacer(Modifier.width(24.dp))
                    FilmBlurb(vm, film)
                }
                Spacer(Modifier.height(20.dp))
                TheaterGrid(vm, rows, columns = 1, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp))
            } else {
                Row(Modifier.fillMaxSize().padding(start = 56.dp, end = 48.dp)) {
                    // The poster at full column width.
                    Column(Modifier.width(380.dp).fillMaxHeight().padding(top = 8.dp, end = 36.dp)) {
                        Poster(film, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(22.dp))
                        FilmBlurb(vm, film)
                    }
                    TheaterGrid(vm, rows, columns = 2, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        // The chosen showing: a panel from the right in the full window, a sheet from the bottom in the half window.
        val selected = vm.showing
        var last by remember { mutableStateOf(selected) }
        if (selected != null) last = selected
        if (compact) {
            AnimatedVisibility(selected != null, Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                last?.let { s -> vm.theater(s.theaterId)?.let { ShowPanel(vm, film, it, s, sheet = true) } }
            }
        } else {
            AnimatedVisibility(selected != null, Modifier.align(Alignment.CenterEnd).padding(top = Dimens.Header), enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
                last?.let { s -> vm.theater(s.theaterId)?.let { ShowPanel(vm, film, it, s, sheet = false) } }
            }
        }
    }
}

@Composable
private fun FilmBlurb(vm: MoTixViewModel, film: Film) {
    val fmt = LocalFmt.current
    Column {
        Text(stringResource(R.string.see_it_nearby), style = Type.LabelM, color = MT.TextSoft, fontWeight = FontWeight.SemiBold)
        Text(film.title(fmt.korean), style = Type.TitleM, color = MT.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        film.secondTitle(fmt.korean)?.let { Text(it, style = Type.LabelM, color = MT.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        film.runtimeMinutes?.let { Text(fmt.runtime(it), style = Type.LabelM, color = MT.TextDim) }
        Spacer(Modifier.height(14.dp))
        Text(
            vm.area?.let { stringResource(R.string.theaters_near_place, it.name) } ?: stringResource(R.string.theaters_near_you),
            style = Type.LabelM, color = MT.TextSoft, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        // Places content shown without a Google map carries the Google Maps attribution.
        Text(stringResource(R.string.google_maps), style = Type.LabelS, color = MT.TextDim)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TheaterGrid(vm: MoTixViewModel, rows: List<TheaterShowings>, columns: Int, modifier: Modifier) {
    val fmt = LocalFmt.current
    if (rows.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.TopStart) {
            when {
                vm.theaters == null && vm.theatersFailed -> Column {
                    NoteLine(stringResource(R.string.theaters_unavailable))
                    SecondaryButton(stringResource(R.string.try_again)) { vm.retry() }
                }
                vm.theaters == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = MT.Accent, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(20.dp))
                    Text(stringResource(R.string.loading_theaters), style = Type.BodyS, color = MT.TextSoft)
                }
                else -> NoteLine(stringResource(R.string.no_theaters))
            }
        }
        return
    }
    LazyVerticalGrid(
        GridCells.Fixed(columns), modifier, contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(rows, key = { it.theater.id }) { (theater, metres, list) ->
            Column(Modifier.fillMaxWidth().glassCard().padding(start = 26.dp, end = 22.dp, top = 18.dp, bottom = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(theater.name(fmt.korean), style = Type.BodyL, color = MT.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    metres?.let { Spacer(Modifier.width(14.dp)); Text(fmt.distance(it), style = Type.LabelM, color = MT.TextSoft) }
                }
                Text(theater.address(fmt.korean), style = Type.LabelS, color = MT.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(14.dp))
                val first = list.first().startsAt
                if (first.toLocalDate() != vm.now.atZone(first.zone).toLocalDate()) {
                    Text(stringResource(R.string.tomorrow), style = Type.LabelS, color = MT.TextSoft, modifier = Modifier.padding(bottom = 8.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxLines = 2) {
                    list.take(6).forEach { s ->
                        val on = s.id == vm.showing?.id
                        Box(
                            Modifier.heightIn(min = 72.dp).tap(RoundedCornerShape(16.dp)) { vm.showing = s }
                                .background(if (on) MT.Accent else MT.Raised).padding(horizontal = 22.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(fmt.time(s.startsAt), style = Type.BodyM, color = if (on) MT.OnAccent else MT.Text, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** The chosen showing: the film's still, the time, the theater, the prices, and the two things to do about it. */
@Composable
private fun ShowPanel(vm: MoTixViewModel, film: Film, theater: Theater, s: Showing, sheet: Boolean) {
    val fmt = LocalFmt.current
    val route by vm.route.collectAsStateWithLifecycle()
    val outer = if (sheet) Modifier.fillMaxWidth().background(MT.Surface).border(1.dp, MT.Line) else Modifier.width(600.dp).fillMaxHeight().background(MT.Surface).border(1.dp, MT.Line)
    Column(outer) {
        // The film's still across the top of the panel, the close button on it.
        Box(Modifier.fillMaxWidth().then(if (sheet) Modifier.height(200.dp) else Modifier.aspectRatio(16f / 9f))) {
            rememberImage(film.stillUrl ?: film.heroUrl)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to MT.Surface)))
            Box(Modifier.align(Alignment.TopEnd).padding(16.dp).size(76.dp).tap(CircleShape) { vm.showing = null }.background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, stringResource(R.string.close), tint = MT.Text, modifier = Modifier.size(38.dp))
            }
        }
        Column((if (sheet) Modifier else Modifier.weight(1f)).padding(start = 36.dp, end = 30.dp, bottom = 32.dp)) {
            Column(if (sheet) Modifier else Modifier.weight(1f)) {
                Text(film.title(fmt.korean), style = Type.TitleM, color = MT.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                film.secondTitle(fmt.korean)?.let { Text(it, style = Type.LabelM, color = MT.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(14.dp))
                Text(fmt.time(s.startsAt), style = Type.HeadlineL, color = MT.Text, fontWeight = FontWeight.Light)
                Text(fmt.day(s.startsAt), style = Type.BodyS, color = MT.TextSoft)
                Spacer(Modifier.height(18.dp))
                Text(theater.name(fmt.korean), style = Type.TitleS, color = MT.Text, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(theater.address(fmt.korean).takeIf { it.isNotBlank() }, vm.metresTo(theater)?.let { fmt.distance(it) }).joinToString(SEP),
                    style = Type.LabelM, color = MT.TextSoft, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                val prices = vm.partner.prices(vm.market ?: Market.KR)
                Text(
                    stringResource(R.string.adult) + " " + prices.format(prices.adult) + SEP +
                        stringResource(if (vm.market == Market.KR) R.string.youth else R.string.child) + " " + prices.format(prices.youth),
                    style = Type.LabelM, color = MT.TextSoft,
                )
                RouteStatus(vm, route, Modifier.padding(top = 14.dp), showButton = false)
            }
            if (sheet) Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PrimaryButton(stringResource(R.string.book_tickets), Modifier.weight(1f), painterResource(R.drawable.ic_ticket)) { vm.startBuying() }
                SecondaryButton(stringResource(R.string.get_route), Modifier.weight(1f), painterResource(R.drawable.ic_navigation)) { vm.routeTo(theater) }
            }
        }
    }
}
