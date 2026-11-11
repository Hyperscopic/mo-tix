package com.hyperscopic.motix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyperscopic.motix.MoTixViewModel
import com.hyperscopic.motix.R
import com.hyperscopic.motix.Screen

/**
 * The whole app in whatever window Pleos gives it.
 *
 * Two things decide what shows before any screen does:
 *  - The window: under 1000 dp wide is the half window (1:1, portrait, about 841 x 1210 dp on Connect-S), anything
 *    wider the full app area (2:0, landscape, about 1682 x 1210 dp). Every screen lays out for both.
 *  - The car: the moment it is not parked, everything gives way to the static ticket card (or, with no ticket, a
 *    plain notice). No video, no payment, nothing that moves.
 */
@Composable
fun MoTixRoot(vm: MoTixViewModel, locationGranted: Boolean, locationDenied: Boolean, onAskLocation: () -> Unit) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val korean = configuration.locales[0].language == "ko"
    LaunchedEffect(korean) { vm.korean = korean }
    val driving by vm.driving.collectAsStateWithLifecycle()
    val market = vm.market
    val fmt = remember(korean, market, configuration) { Fmt(korean, market?.metric ?: korean, context.resources) }

    MoTixTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(MT.Bg)) {
            val compact = maxWidth < 1000.dp
            CompositionLocalProvider(LocalFmt provides fmt, LocalCompact provides compact) {
                val booking = vm.booking
                if (!driving.parked) {
                    // Driving: the ticket, or nothing.
                    if (booking != null) TicketScreen(vm, booking, static = true) else DrivingNotice(vm)
                    return@CompositionLocalProvider
                }
                BackHandler(enabled = vm.stack.size > 1 || vm.creditsOpen || vm.liveNeedsKeys) { vm.back() }
                val list = vm.filmList
                val film = vm.film
                when {
                    // Only live listings need the car position; the saved ones are placed where they were captured.
                    !locationGranted && !vm.cachedMode -> LocationIntro(vm, locationDenied, onAskLocation)
                    list == null || film == null -> FirstLoad(vm)
                    else -> when (vm.screen) {
                        Screen.LINEUP -> LineupScreen(vm, list)
                        Screen.TRAILER -> TrailerScreen(vm, film)
                        Screen.THEATERS -> TheatersScreen(vm, film)
                        Screen.BUY -> BuyScreen(vm, film)
                        Screen.TICKET -> if (booking != null) TicketScreen(vm, booking, static = false) else LineupScreen(vm, list)
                        Screen.SEARCH -> vm.searchUrl?.let { SearchPage(vm, it) } ?: LineupScreen(vm, list)
                    }
                }
                if (vm.creditsOpen) CreditsPanel(vm)
                if (vm.liveNeedsKeys) NoticePanel(stringResource(R.string.live_needs_keys)) { vm.liveNeedsKeys = false }
            }
        }
    }
}

/** A short message over the screen, with one way out. */
@Composable
private fun NoticePanel(text: String, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).tap(androidx.compose.ui.graphics.RectangleShape, onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(if (LocalCompact.current) 0.92f else 0.56f).glassCard(28.dp).tap(RoundedCornerShape(28.dp)) {}.padding(horizontal = 44.dp, vertical = 36.dp),
        ) {
            Text(text, style = Type.BodyL, color = MT.Text)
            Spacer(Modifier.height(28.dp))
            SecondaryButton(stringResource(R.string.close), Modifier.align(Alignment.End), onClick = onClose)
        }
    }
}

@Composable
private fun Centered(vm: MoTixViewModel, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(MT.Bg)) {
        Header(vm)
        Column(Modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content() }
    }
}

@Composable
private fun Mark() {
    Box(Modifier.size(144.dp).clip(RoundedCornerShape(36.dp)).background(Brush.verticalGradient(MT.Brand)), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_ticket), null, tint = Color.White, modifier = Modifier.size(84.dp))
    }
    Spacer(Modifier.height(36.dp))
}

/** The first launch: why Mo-Tix wants the car position, before Pleos asks. */
@Composable
private fun LocationIntro(vm: MoTixViewModel, denied: Boolean, onAsk: () -> Unit) {
    Centered(vm) {
        Mark()
        Text(stringResource(R.string.location_title), style = Type.HeadlineS, color = MT.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(if (denied) R.string.location_denied else R.string.location_body),
            style = Type.BodyL, color = MT.TextSoft, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 900.dp),
        )
        Spacer(Modifier.height(40.dp))
        PrimaryButton(stringResource(R.string.action_continue), Modifier.widthIn(min = 320.dp), onClick = onAsk)
    }
}

/** Nothing to show yet: the only time Mo-Tix shows a spinner. */
@Composable
private fun FirstLoad(vm: MoTixViewModel) {
    Centered(vm) {
        when {
            !vm.configured -> {
                Mark()
                Text(stringResource(R.string.no_keys), style = Type.BodyL, color = MT.TextSoft, textAlign = TextAlign.Center)
            }
            vm.filmsFailed -> {
                Mark()
                Text(stringResource(R.string.listings_unavailable), style = Type.HeadlineS, color = MT.Text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.listings_unavailable_note), style = Type.BodyL, color = MT.TextSoft, textAlign = TextAlign.Center)
                Spacer(Modifier.height(36.dp))
                PrimaryButton(stringResource(R.string.try_again), Modifier.widthIn(min = 320.dp)) { vm.retry() }
            }
            else -> {
                CircularProgressIndicator(color = MT.Accent, strokeWidth = 6.dp, modifier = Modifier.size(84.dp))
                Spacer(Modifier.height(36.dp))
                Text(
                    stringResource(if (vm.position == null) R.string.loading_position else R.string.loading_films),
                    style = Type.TitleL, color = MT.Text, textAlign = TextAlign.Center,
                )
                if (vm.position != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.loading_films_note), style = Type.BodyM, color = MT.TextSoft, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
