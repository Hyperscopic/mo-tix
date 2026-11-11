package com.hyperscopic.motix.ui

import android.content.res.Resources
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperscopic.motix.R
import com.hyperscopic.motix.data.Seat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Mo-Tix on Pleos Connect. Dark and image-led (the posters and stills carry the colour), on the Pleos design guide:
 * the system typeface Asta Sans, the guide's type scale, its primary blue as the one accent, and touch targets sized
 * for a car (the guide asks for buttons of at least 12.5 mm).
 */
object MT {
    val Bg = Color(0xFF0B0B0D)
    val Surface = Color(0xFF17171B)
    val Raised = Color(0xFF26262D)
    val Line = Color(0xFF393941)
    val Text = Color(0xFFF4F4F6)
    val TextSoft = Color(0xFFBDBDC6)
    val TextDim = Color(0xFF93939E)

    /** The Pleos Connect primary. */
    val Accent = Color(0xFF596BFF)
    val OnAccent = Color.White

    /** Panels and cards over a still: the surface, slightly see-through. */
    val Glass = Color(0xE617171B)

    /** The white ticket card (the QR code needs a light ground to scan). */
    val Ticket = Color(0xFFF7F7F9)
    val TicketInk = Color(0xFF111114)
    val TicketSoft = Color(0xFF45454E)

    /** The app tile gradient. */
    val Brand = listOf(Color(0xFFFF6B3D), Color(0xFFC21E56))
}

/** Asta Sans, the Pleos Connect system typeface (SIL Open Font License 1.1; one variable font, weights 300 to 800). */
@OptIn(ExperimentalTextApi::class)
private fun asta(weight: Int) = Font(R.font.asta_sans, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val AstaSans = FontFamily(asta(300), asta(400), asta(500), asta(600), asta(700), asta(800))

/** The Pleos Connect type scale (design guide, Typography): size / line height in sp, at 160 dpi. */
object Type {
    private fun style(size: Int, line: Int, weight: FontWeight) =
        TextStyle(fontFamily = AstaSans, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

    val HeadlineL = style(60, 80, FontWeight.Bold)
    val HeadlineM = style(56, 72, FontWeight.Bold)
    val HeadlineS = style(48, 64, FontWeight.Bold)
    val TitleL = style(40, 52, FontWeight.Bold)
    val TitleM = style(36, 44, FontWeight.Bold)
    val TitleS = style(32, 40, FontWeight.Bold)
    val BodyL = style(30, 38, FontWeight.Normal)
    val BodyM = style(28, 36, FontWeight.Normal)
    val BodyS = style(26, 34, FontWeight.Normal)
    val LabelM = style(24, 28, FontWeight.Normal)
    val LabelS = style(20, 24, FontWeight.Normal)
}

object Dimens {
    /** Buttons: the guide's large size, comfortably over the 12.5 mm minimum on every Connect-S display. */
    val Button = 88.dp
    val ButtonRadius = 22.dp
    val Header = 112.dp
    val CardRadius = 22.dp
}

/** Dates, times, distances and seats, in the UI language and the market's units. */
class Fmt(val korean: Boolean, private val metric: Boolean, private val res: Resources) {
    private val locale: Locale = if (korean) Locale.KOREAN else Locale.US
    private val timeFormat = DateTimeFormatter.ofPattern(if (korean) "a h:mm" else "h:mm a", locale)
    private val dayFormat = DateTimeFormatter.ofPattern(if (korean) "M월 d일 (E)" else "EEE, MMM d", locale)

    fun time(t: ZonedDateTime): String = t.format(timeFormat)
    fun day(t: ZonedDateTime): String = t.format(dayFormat)

    fun runtime(minutes: Int): String =
        if (minutes < 60) res.getString(R.string.runtime_min, minutes) else res.getString(R.string.runtime_h_min, minutes / 60, minutes % 60)

    /** A drive time from the car navigation, in seconds. */
    fun duration(seconds: Int): String = runtime(maxOf(1, (seconds + 30) / 60))

    fun distance(metres: Double): String = when {
        !metric -> res.getString(R.string.distance_mi, String.format(Locale.US, "%.1f", metres / 1609.344))
        metres < 950 -> res.getString(R.string.distance_m, (metres / 10).toInt() * 10)
        else -> res.getString(R.string.distance_km, String.format(Locale.US, "%.1f", metres / 1000))
    }

    /** "Row G, seats 8, 9" when the seats share a row, else "G8, H3". */
    fun seats(seats: List<Seat>): String {
        if (seats.isEmpty()) return res.getString(R.string.not_picked)
        val sorted = seats.sortedWith(compareBy({ it.row }, { it.index }))
        if (sorted.any { it.row != sorted.first().row }) return sorted.joinToString(", ") { it.label }
        val numbers = sorted.joinToString(", ") { it.number.toString() }
        return res.getString(if (sorted.size == 1) R.string.seat_row_one else R.string.seat_row_many, sorted.first().rowLetter.toString(), numbers)
    }

    fun tickets(count: Int): String = res.getQuantityString(R.plurals.ticket_count, count, count)
}

/** Between the parts of a line. */
const val SEP = "  ·  "

val LocalFmt = staticCompositionLocalOf<Fmt> { error("No Fmt") }

/** True in the half window (1:1, portrait); false in the full app area (2:0, landscape). */
val LocalCompact = staticCompositionLocalOf { false }

@Composable
fun MoTixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = MT.Accent, onPrimary = MT.OnAccent, background = MT.Bg, surface = MT.Surface, onSurface = MT.Text, onBackground = MT.Text),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MT.Text,
            LocalTextStyle provides Type.BodyM.copy(color = MT.Text),
            content = content,
        )
    }
}
