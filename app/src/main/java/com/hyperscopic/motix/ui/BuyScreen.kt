package com.hyperscopic.motix.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyperscopic.motix.Cart
import com.hyperscopic.motix.MoTixViewModel
import com.hyperscopic.motix.R
import com.hyperscopic.motix.data.Film
import com.hyperscopic.motix.data.Market
import com.hyperscopic.motix.data.Prices
import com.hyperscopic.motix.data.Seat
import com.hyperscopic.motix.data.SeatPlan
import com.hyperscopic.motix.data.Showing
import com.hyperscopic.motix.data.Theater
import kotlinx.coroutines.launch

/**
 * Buying tickets in the car: how many, which seats, pay with the saved card, then the code for the cinema.
 * Parked only (the root swaps this screen out the moment the car leaves Park).
 */
@Composable
fun BuyScreen(vm: MoTixViewModel, film: Film) {
    val compact = LocalCompact.current
    val s = vm.showing
    val theater = s?.let { vm.theater(it.theaterId) }
    val back = when (vm.buyStep) {
        0 -> stringResource(R.string.back_to_times)
        1 -> stringResource(R.string.back_to_tickets)
        2 -> stringResource(R.string.back_to_seats)
        else -> stringResource(R.string.done)
    }
    Box(Modifier.fillMaxSize()) {
        StillBackground(film, dim = 0.45f)
        Column(Modifier.fillMaxSize()) {
            Header(vm, back, onBack = { vm.back() })
            if (s == null || theater == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NoteLine(stringResource(R.string.pick_time_first)) }
                return@Column
            }
            val prices = vm.partner.prices(vm.market ?: Market.KR)
            val steps: @Composable (Modifier) -> Unit = { m ->
                AnimatedContent(vm.buyStep, m, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "buy") { step ->
                    when (step) {
                        0 -> TicketsStep(vm, prices)
                        1 -> SeatsStep(vm, s)
                        2 -> PayStep(vm, prices)
                        else -> BookedStep(vm)
                    }
                }
            }
            if (compact) {
                Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, bottom = 28.dp)) {
                    if (vm.buyStep < 3) {
                        CompactSummary(vm, film, theater, s, prices)
                        Spacer(Modifier.height(24.dp))
                    }
                    steps(Modifier.weight(1f).fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, bottom = 36.dp)) {
                    OrderSummary(vm, film, theater, s, prices, Modifier.width(360.dp).fillMaxHeight())
                    Spacer(Modifier.width(48.dp))
                    steps(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun ticketsText(cart: Cart, market: Market?): String = listOfNotNull(
    cart.adults.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.adults_count, it, it) },
    cart.youths.takeIf { it > 0 }?.let { if (market == Market.KR) stringResource(R.string.youth_count, it) else pluralStringResource(R.plurals.child_count, it, it) },
).joinToString(", ").ifEmpty { stringResource(R.string.none_yet) }

/** The full window: the poster, big, at the top of the column; the order under it. */
@Composable
private fun OrderSummary(vm: MoTixViewModel, film: Film, theater: Theater, s: Showing, prices: Prices, modifier: Modifier) {
    val fmt = LocalFmt.current
    Column(modifier) {
        Poster(film, Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).fillMaxWidth().glassCard().padding(horizontal = 24.dp, vertical = 16.dp)) {
            SummaryLine(stringResource(R.string.label_when), "${fmt.day(s.startsAt)}, ${fmt.time(s.startsAt)}")
            SummaryLine(stringResource(R.string.label_where), theater.name(fmt.korean))
            SummaryLine(stringResource(R.string.label_tickets), ticketsText(vm.cart, vm.market))
            SummaryLine(stringResource(R.string.label_seats), fmt.seats(vm.cart.seats))
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MT.Line))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.total), style = Type.LabelM, color = MT.TextSoft, modifier = Modifier.weight(1f))
                Text(prices.format(prices.total(vm.cart.adults, vm.cart.youths)), style = Type.TitleM, color = MT.Text)
            }
        }
    }
}

/** The half window: the same order as one strip. */
@Composable
private fun CompactSummary(vm: MoTixViewModel, film: Film, theater: Theater, s: Showing, prices: Prices) {
    val fmt = LocalFmt.current
    Row(Modifier.fillMaxWidth().glassCard().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Poster(film, Modifier.width(104.dp), radius = 10.dp)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(film.title(fmt.korean), style = Type.BodyM, color = MT.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${fmt.day(s.startsAt)}, ${fmt.time(s.startsAt)}", style = Type.LabelM, color = MT.TextSoft, maxLines = 1)
            Text(theater.name(fmt.korean), style = Type.LabelM, color = MT.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${ticketsText(vm.cart, vm.market)}${SEP}${fmt.seats(vm.cart.seats)}", style = Type.LabelS, color = MT.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Text(prices.format(prices.total(vm.cart.adults, vm.cart.youths)), style = Type.TitleS, color = MT.Text)
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = Type.LabelS, color = MT.TextDim, modifier = Modifier.width(72.dp).padding(top = 3.dp))
        Text(value, style = Type.LabelM, color = MT.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StepTitle(title: String, sub: String? = null) {
    Text(title, style = Type.HeadlineS, color = MT.Text, fontWeight = FontWeight.Light)
    sub?.let { Text(it, style = Type.LabelM, color = MT.TextSoft) }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun TicketsStep(vm: MoTixViewModel, prices: Prices) {
    val adult = stringResource(R.string.adult)
    val youth = stringResource(if (vm.market == Market.KR) R.string.youth else R.string.child)
    Column(Modifier.fillMaxSize()) {
        StepTitle(stringResource(R.string.how_many))
        TicketRow(adult, prices.format(prices.adult), vm.cart.adults) { vm.cart = vm.cart.copy(adults = it, seats = emptyList()) }
        Spacer(Modifier.height(18.dp))
        TicketRow(youth, prices.format(prices.youth), vm.cart.youths) { vm.cart = vm.cart.copy(youths = it, seats = emptyList()) }
        Spacer(Modifier.weight(1f))
        Row {
            Spacer(Modifier.weight(1f))
            PrimaryButton(stringResource(R.string.choose_seats), Modifier.width(380.dp), enabled = vm.cart.tickets > 0) { vm.buyStep = 1 }
        }
    }
}

@Composable
private fun TicketRow(label: String, price: String, count: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().glassCard().padding(horizontal = 32.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.TitleS, color = MT.Text, fontWeight = FontWeight.Medium)
            Text(price, style = Type.LabelM, color = MT.TextSoft)
        }
        CountButton("−", stringResource(R.string.remove_one, label), enabled = count > 0) { onChange(count - 1) }
        Text("$count", style = Type.TitleL, color = MT.Text, textAlign = TextAlign.Center, modifier = Modifier.width(96.dp))
        CountButton("+", stringResource(R.string.add_one, label), enabled = count < 8) { onChange(count + 1) }
    }
}

@Composable
private fun CountButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(88.dp).semantics { contentDescription = description }.tap(CircleShape, enabled = enabled, onClick = onClick)
            .background(if (enabled) MT.Raised else MT.Surface).border(1.5.dp, MT.Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = Type.TitleL, color = if (enabled) MT.Text else MT.TextDim, fontWeight = FontWeight.Normal)
    }
}

@Composable
private fun SeatsStep(vm: MoTixViewModel, s: Showing) {
    val need = vm.cart.tickets
    val plan = remember(s.id) { vm.partner.seatPlan(s) }
    Column(Modifier.fillMaxSize()) {
        StepTitle(pluralStringResource(R.plurals.pick_seats, need, need), stringResource(R.string.seats_hint))
        SeatPicker(plan, vm.cart.seats, Modifier.weight(1f).fillMaxWidth().glassCard().padding(vertical = 14.dp)) { seat ->
            val picked = vm.cart.seats
            vm.cart = vm.cart.copy(
                seats = when {
                    seat in picked -> picked - seat
                    picked.size < need -> picked + seat
                    else -> picked.drop(1) + seat // a full set: the oldest pick gives way
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.picked_of, vm.cart.seats.size, need), style = Type.BodyS, color = MT.TextSoft, modifier = Modifier.weight(1f))
            PrimaryButton(stringResource(R.string.action_continue), Modifier.width(380.dp), enabled = vm.cart.seats.size == need) { vm.buyStep = 2 }
        }
    }
}

/** The seat map: rows from the screen back (A at the front), aisles between sections, every seat a touch target. */
@Composable
private fun SeatPicker(plan: SeatPlan, picked: List<Seat>, modifier: Modifier, onTap: (Seat) -> Unit) {
    val starts = plan.sections.runningFold(0) { a, b -> a + b }.dropLast(1)
    val perRow = plan.sections.sum()
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val aisle = 28.dp
        val label = 36.dp
        // The biggest seat that fits both ways.
        val byWidth = (maxWidth - label * 2 - aisle * (plan.sections.size - 1)) / perRow
        val byHeight = (maxHeight - 70.dp) / plan.rows
        val cell: Dp = minOf(byWidth, byHeight).coerceIn(34.dp, 68.dp)
        val seatCd = stringResource(R.string.seat_cd, "%s")
        val takenCd = stringResource(R.string.seat_cd_taken, "%s")
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.width(cell * perRow * 0.8f).height(30.dp)) {
                drawArc(Color.White.copy(0.55f), 200f, 140f, false, Offset(0f, 8f), Size(size.width, size.height * 3f), style = Stroke(6f))
            }
            Text(stringResource(R.string.screen_label), style = Type.LabelS, color = MT.TextDim, letterSpacing = 4.sp)
            Spacer(Modifier.height(10.dp))
            for (r in 0 until plan.rows) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${'A' + r}", style = Type.LabelS, color = MT.TextDim, textAlign = TextAlign.Center, modifier = Modifier.width(label))
                    for ((i, start) in starts.withIndex()) {
                        if (i > 0) Spacer(Modifier.width(aisle))
                        for (index in start until start + plan.sections[i]) {
                            val seat = Seat(r, index)
                            val taken = seat in plan.taken
                            val on = seat in picked
                            Box(
                                Modifier.size(cell).padding(3.dp).semantics { contentDescription = (if (taken) takenCd else seatCd).format(seat.label) }
                                    .then(if (taken) Modifier.clip(RoundedCornerShape(10.dp)) else Modifier.tap(RoundedCornerShape(10.dp)) { onTap(seat) })
                                    .background(when { on -> MT.Accent; taken -> Color.White.copy(0.07f); else -> Color.White.copy(0.36f) }),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) Text("${seat.number}", style = Type.LabelS, color = MT.OnAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text("${'A' + r}", style = Type.LabelS, color = MT.TextDim, textAlign = TextAlign.Center, modifier = Modifier.width(label))
                }
            }
        }
    }
}

@Composable
private fun PayStep(vm: MoTixViewModel, prices: Prices) {
    val fmt = LocalFmt.current
    var paying by remember { mutableStateOf(false) }
    val cart = vm.cart
    val total = prices.total(cart.adults, cart.youths)
    // Leaving this step (the car leaves Park, or Back) cancels the payment with it.
    if (paying) LaunchedEffect(Unit) { if (!vm.pay()) paying = false }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StepTitle(stringResource(R.string.review_pay))
        Column(Modifier.fillMaxWidth().glassCard().padding(horizontal = 32.dp, vertical = 24.dp)) {
            if (cart.adults > 0) PayLine(stringResource(R.string.count_times, stringResource(R.string.adult), cart.adults), prices.format(cart.adults * prices.adult))
            if (cart.youths > 0) PayLine(stringResource(R.string.count_times, stringResource(if (vm.market == Market.KR) R.string.youth else R.string.child), cart.youths), prices.format(cart.youths * prices.youth))
            PayLine(stringResource(R.string.label_seats), fmt.seats(cart.seats))
            PayLine(stringResource(R.string.booking_fee), prices.format(0))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MT.Line))
            Spacer(Modifier.height(12.dp))
            PayLine(stringResource(R.string.total), prices.format(total), strong = true)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().glassCard().padding(horizontal = 32.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 76.dp, height = 50.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2B3A55)))
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.saved_card), style = Type.BodyM, color = MT.Text)
                Text(stringResource(R.string.pay_note), style = Type.LabelM, color = MT.TextSoft)
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth().height(Dimens.Button), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (paying) {
                CircularProgressIndicator(color = MT.Accent, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(22.dp))
                Text(stringResource(R.string.paying), style = Type.BodyM, color = MT.TextSoft)
            } else {
                PrimaryButton(stringResource(R.string.pay_amount, prices.format(total)), Modifier.width(420.dp)) { paying = true }
            }
        }
    }
}

@Composable
private fun PayLine(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = if (strong) Type.BodyM else Type.BodyS, color = if (strong) MT.Text else MT.TextSoft, modifier = Modifier.weight(1f))
        Text(value, style = if (strong) Type.TitleM else Type.BodyS, color = MT.Text)
    }
}

/** Booked: the code for the cinema, and one tap to the route. */
@Composable
private fun BookedStep(vm: MoTixViewModel) {
    val compact = LocalCompact.current
    val fmt = LocalFmt.current
    val b = vm.booking ?: return
    val route by vm.route.collectAsStateWithLifecycle()
    val words: @Composable () -> Unit = {
        Text(stringResource(R.string.booked_title), style = Type.HeadlineM, color = MT.Text, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(if (b.market == Market.KR) R.string.booked_collect else R.string.booked_scan, b.theaterName(fmt.korean)),
            style = Type.BodyL, color = MT.TextSoft,
        )
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.booking_ref, b.ref), style = Type.LabelM, color = MT.TextDim)
        RouteStatus(vm, route, Modifier.padding(top = 14.dp), showButton = false)
    }
    val buttons: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PrimaryButton(stringResource(R.string.get_route), icon = painterResource(R.drawable.ic_navigation)) { vm.routeToBooking() }
            SecondaryButton(stringResource(R.string.done)) { vm.home() }
        }
    }
    if (compact) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth()) { words() }
            Spacer(Modifier.height(24.dp))
            CollectCard(b, Modifier.width(460.dp), qr = 250.dp)
            Spacer(Modifier.height(24.dp))
            buttons()
        }
    } else {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 48.dp)) {
                words()
                Spacer(Modifier.height(40.dp))
                buttons()
            }
            CollectCard(b, Modifier.width(500.dp), qr = 330.dp)
        }
    }
}
