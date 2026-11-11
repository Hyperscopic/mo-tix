package com.hyperscopic.motix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyperscopic.motix.MoTixViewModel
import com.hyperscopic.motix.R
import com.hyperscopic.motix.data.Booking
import com.hyperscopic.motix.data.Market
import com.hyperscopic.motix.data.QrCode
import com.hyperscopic.motix.data.rememberImage
import com.hyperscopic.motix.platform.RouteState

/** A QR code, dark on white with its quiet zone. */
@Composable
fun QrImage(payload: String, size: Dp, modifier: Modifier = Modifier) {
    val modules = remember(payload) { runCatching { QrCode.encode(payload) }.getOrNull() } ?: return
    Canvas(modifier.size(size)) {
        val quiet = 2
        val cell = this.size.minDimension / (modules.size + quiet * 2)
        drawRect(Color.White, size = this.size)
        for (y in modules.indices) for (x in modules.indices) {
            if (modules[y][x]) drawRect(MT.TicketInk, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/** The white ticket card on the booked screen: the film, the time, the code, the seats. */
@Composable
fun CollectCard(b: Booking, modifier: Modifier = Modifier, qr: Dp = 300.dp) {
    val fmt = LocalFmt.current
    Column(modifier.clip(RoundedCornerShape(32.dp)).background(MT.Ticket).padding(horizontal = 32.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(if (b.market == Market.KR) R.string.collect_label else R.string.scan_label),
            style = Type.LabelS, color = MT.TicketSoft, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(b.filmTitle(fmt.korean), style = Type.TitleS, color = MT.TicketInk, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${fmt.day(b.startsAt)}${SEP}${fmt.time(b.startsAt)}", style = Type.LabelM, color = MT.TicketSoft)
        Spacer(Modifier.height(18.dp))
        QrImage(b.qrPayload, qr)
        Spacer(Modifier.height(16.dp))
        Text(fmt.seats(b.seats), style = Type.BodyM, color = MT.TicketInk, fontWeight = FontWeight.SemiBold)
        Text("${b.theaterName(fmt.korean)}${SEP}${b.ref}", style = Type.LabelS, color = MT.TicketSoft, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The ticket: the poster, the time, the seats and the code for the cinema. This is what Mo-Tix shows in the half
 * window beside the navigation, and all it shows while the car is moving ([static]: no controls, nothing that moves).
 */
@Composable
fun TicketScreen(vm: MoTixViewModel, b: Booking, static: Boolean) {
    val compact = LocalCompact.current
    val fmt = LocalFmt.current
    val route by vm.route.collectAsStateWithLifecycle()
    val poster = rememberImage(b.posterUrl)
    val details: @Composable () -> Unit = {
        Text(b.filmTitle(fmt.korean), style = if (compact) Type.TitleL else Type.HeadlineM, color = MT.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
        b.filmSecondTitle(fmt.korean)?.let { Text(it, style = Type.BodyS, color = MT.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.height(if (compact) 16.dp else 22.dp))
        Text(fmt.time(b.startsAt), style = if (compact) Type.HeadlineM else Type.HeadlineL.copy(fontSize = 76.sp, lineHeight = 88.sp), color = MT.Text, fontWeight = FontWeight.Light)
        Text(fmt.day(b.startsAt), style = Type.BodyM, color = MT.TextSoft)
        Spacer(Modifier.height(if (compact) 16.dp else 22.dp))
        Text(b.theaterName(fmt.korean), style = Type.TitleS, color = MT.Text, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${fmt.tickets(b.tickets)}${SEP}${fmt.seats(b.seats)}", style = Type.BodyS, color = MT.TextSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val code: @Composable (Modifier, Dp) -> Unit = { m, size ->
        // The white collection card: the code to show at the cinema, and the booking reference.
        Row(m.clip(RoundedCornerShape(28.dp)).background(MT.Ticket).padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            QrImage(b.qrPayload, size)
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (b.market == Market.KR) R.string.collect_label else R.string.scan_label), style = Type.LabelS, color = MT.TicketSoft, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                Text(b.theaterName(fmt.korean), style = Type.BodyL, color = MT.TicketInk, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text(b.ref, style = Type.LabelM, color = MT.TicketSoft)
            }
        }
    }
    val actions: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (route is RouteState.Guiding || route is RouteState.Requesting) {
                PrimaryButton(stringResource(R.string.show_navigation), Modifier.weight(1f), painterResource(R.drawable.ic_navigation)) { vm.showNavigation() }
            } else {
                PrimaryButton(stringResource(R.string.get_route), Modifier.weight(1f), painterResource(R.drawable.ic_navigation)) { vm.routeToBooking() }
            }
            SecondaryButton(stringResource(R.string.all_films), Modifier.weight(1f)) { vm.home() }
        }
    }
    Box(Modifier.fillMaxSize().background(MT.Bg)) {
        Column(Modifier.fillMaxSize()) {
            Header(vm)
            if (compact) {
                Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, bottom = 28.dp)) {
                    Row {
                        Box(Modifier.width(300.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(18.dp)).background(MT.Raised)) {
                            if (poster != null) Image(poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Spacer(Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) { details() }
                    }
                    Spacer(Modifier.weight(1f))
                    RouteStatus(vm, route, Modifier.padding(bottom = 16.dp), showButton = false)
                    code(Modifier.fillMaxWidth(), 250.dp)
                    if (!static) {
                        Spacer(Modifier.height(20.dp))
                        actions()
                    }
                }
            } else {
                Row(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, bottom = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(24.dp)).background(MT.Raised)) {
                        if (poster != null) Image(poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.width(56.dp))
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        details()
                        Spacer(Modifier.weight(1f))
                        RouteStatus(vm, route, Modifier.padding(bottom = 18.dp), showButton = false)
                        code(Modifier.fillMaxWidth(), 280.dp)
                        if (!static) {
                            Spacer(Modifier.height(24.dp))
                            actions()
                        }
                    }
                }
            }
        }
    }
}

/** Where the "Get route" request stands, in one line. */
@Composable
fun RouteStatus(vm: MoTixViewModel, route: RouteState, modifier: Modifier = Modifier, showButton: Boolean = true) {
    val fmt = LocalFmt.current
    val text = when (route) {
        RouteState.Idle -> return
        is RouteState.Requesting -> stringResource(R.string.route_requesting)
        is RouteState.Guiding -> listOfNotNull(
            stringResource(R.string.route_guiding, route.destination),
            route.durationS?.let { fmt.duration(it) },
            route.distanceM?.let { fmt.distance(it.toDouble()) },
        ).joinToString(SEP)
        is RouteState.Failed -> when (route.reason) {
            RouteState.Reason.UNAVAILABLE -> stringResource(R.string.route_unavailable)
            RouteState.Reason.TOO_CLOSE -> stringResource(R.string.route_too_close, route.destination)
            RouteState.Reason.TOO_FAR -> stringResource(R.string.route_too_far, route.destination)
            RouteState.Reason.LOW_BATTERY -> stringResource(R.string.route_low_battery)
            RouteState.Reason.SEARCH_FAILED -> stringResource(R.string.route_failed)
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (route is RouteState.Requesting) {
            CircularProgressIndicator(color = MT.Accent, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
        } else {
            Icon(painterResource(R.drawable.ic_navigation), null, tint = if (route is RouteState.Failed) MT.TextDim else MT.Accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = Type.LabelM, color = MT.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (showButton && route is RouteState.Guiding) {
            Spacer(Modifier.width(18.dp))
            SecondaryButton(stringResource(R.string.show_navigation), Modifier.height(72.dp)) { vm.showNavigation() }
        }
    }
}

/** The car is moving and there is no ticket to show: nothing to look at, nothing to touch. */
@Composable
fun DrivingNotice(vm: MoTixViewModel) {
    Box(Modifier.fillMaxSize().background(MT.Bg)) {
        Column(Modifier.fillMaxSize()) {
            Header(vm)
            Column(Modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(144.dp).clip(RoundedCornerShape(36.dp)).background(Brush.verticalGradient(MT.Brand)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_ticket), null, tint = Color.White, modifier = Modifier.size(84.dp))
                }
                Spacer(Modifier.height(36.dp))
                Text(stringResource(R.string.driving_title), style = Type.HeadlineS, color = MT.Text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.driving_body), style = Type.BodyL, color = MT.TextSoft, textAlign = TextAlign.Center)
            }
        }
    }
}
