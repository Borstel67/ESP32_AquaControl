package de.borstel67.aquacontrol

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

// Farben und Maße wie die Wärmepumpen-App (APK_Luxtronik, web/index.src.html):
// dunkles Theme = data/WifiManager.css, helles Theme = „STA Light Theme“.
class AquaPalette(
    val isLight: Boolean,
    val Bg: Color, val Card: Color, val Ring: Color, val Muted: Color, val Text: Color, val Head: Color,
    val Label: Color, val Accent: Color, val Accent2: Color, val Ok: Color, val Bad: Color, val Mid: Color,
    val Weak: Color, val Tile: Color, val TileRing: Color, val Input: Color, val InputRing: Color,
    val InputFocus: Color, val BtnBg: Color, val BtnRing: Color, val BtnText: Color, val Sel: Color,
    val SelRing: Color, val Hero1: Color, val Hero2: Color, val HeroRing: Color, val BannerBg: Color,
    val BannerRing: Color, val BannerText: Color, val PillText: Color,
) {
    val Gradient = Brush.linearGradient(listOf(Accent2, Accent))
}

val DarkPalette = AquaPalette(
    isLight = false,
    Bg = Color(0xFF0B1220), Card = Color(0xFF121A2B), Ring = Color(0xFF1B2A4A), Muted = Color(0xFF9FB0CF),
    Text = Color(0xFFE6EFFF), Head = Color(0xFFD9E6FF), Label = Color(0xFFCBD6EF), Accent = Color(0xFF4CC9F0),
    Accent2 = Color(0xFF4361EE), Ok = Color(0xFF2EC27E), Bad = Color(0xFFFF6B6B), Mid = Color(0xFFF4A261),
    Weak = Color(0xFFFFD166), Tile = Color(0xFF0F1730), TileRing = Color(0xFF1C2C50), Input = Color(0xFF0E162B),
    InputRing = Color(0xFF1E2B4A), InputFocus = Color(0xFF385A9B), BtnBg = Color(0xFF0F1830),
    BtnRing = Color(0xFF24406C), BtnText = Color(0xFFE7F0FF), Sel = Color(0xFF0F1C3A), SelRing = Color(0xFF3760B9),
    Hero1 = Color(0xFF16224A), Hero2 = Color(0xFF0F1C3A), HeroRing = Color(0xFF2D5AA8),
    BannerBg = Color(0xFF1A0F14), BannerRing = Color(0xFF402029), BannerText = Color(0xFFFFD6D6),
    PillText = Color(0xFFCFE2FF),
)

val LightPalette = AquaPalette(
    isLight = true,
    Bg = Color(0xFFF3F6FB), Card = Color(0xFFFFFFFF), Ring = Color(0xFFE5E7EB), Muted = Color(0xFF4B5563),
    Text = Color(0xFF111827), Head = Color(0xFF111827), Label = Color(0xFF374151), Accent = Color(0xFF2563EB),
    Accent2 = Color(0xFF4361EE), Ok = Color(0xFF16A34A), Bad = Color(0xFFDC2626), Mid = Color(0xFFD97706),
    Weak = Color(0xFFCA8A04), Tile = Color(0xFFF8FAFC), TileRing = Color(0xFFE2E8F0), Input = Color(0xFFFFFFFF),
    InputRing = Color(0xFFD1D5DB), InputFocus = Color(0xFF2563EB), BtnBg = Color(0xFFFFFFFF),
    BtnRing = Color(0xFFD1D5DB), BtnText = Color(0xFF111827), Sel = Color(0xFFEFF6FF), SelRing = Color(0xFF2563EB),
    Hero1 = Color(0xFFEEF4FF), Hero2 = Color(0xFFFFFFFF), HeroRing = Color(0xFFC7D7FE),
    BannerBg = Color(0xFFFEF2F2), BannerRing = Color(0xFFFECACA), BannerText = Color(0xFF991B1B),
    PillText = Color(0xFF1E3A8A),
)

/** Aktive Palette; als Compose-State, damit ein Wechsel hell/dunkel alles neu zeichnet. */
private var activePalette by mutableStateOf(DarkPalette)
val AquaColors: AquaPalette get() = activePalette

private val CardShape = RoundedCornerShape(14.dp)
private val TileShape = RoundedCornerShape(12.dp)
private val InputShape = RoundedCornerShape(10.dp)

/** [mode]: "auto" (System), "light" oder "dark" – wie die Einstellung „Darstellung“ der Wärmepumpen-App. */
@Composable
fun AquaTheme(mode: String = "auto", content: @Composable () -> Unit) {
    val dark = mode == "dark" || (mode != "light" && isSystemInDarkTheme())
    val c = if (dark) DarkPalette else LightPalette
    activePalette = c
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.Accent, onPrimary = Color.White,
        primaryContainer = c.Accent2, onPrimaryContainer = Color.White,
        secondary = c.Accent2, onSecondary = Color.White,
        secondaryContainer = c.Sel, onSecondaryContainer = c.Text,
        background = c.Bg, onBackground = c.Text,
        surface = c.Card, onSurface = c.Text,
        surfaceVariant = c.Tile, onSurfaceVariant = c.Label,
        surfaceContainer = c.Card, surfaceContainerHigh = c.Tile, surfaceContainerHighest = c.Ring,
        surfaceContainerLow = c.Card, surfaceContainerLowest = c.Card,
        outline = c.InputRing, outlineVariant = c.Ring,
        error = c.Bad,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Helles Theme: leichter Schatten unter Karten und Kopfzeile (wie .card/.hdr in Luxtronik). */
private fun Modifier.lightShadow(shape: androidx.compose.ui.graphics.Shape): Modifier =
    if (AquaColors.isLight) shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.10f))
    else this

// ── Karten und Abschnitte (.card / .sec / h2) ────────────────────────────────

@Composable
fun AquaCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .lightShadow(CardShape)
            .clip(CardShape)
            .background(AquaColors.Card)
            .border(1.dp, AquaColors.Ring, CardShape),
        content = content,
    )
}

/**
 * Abschnitt innerhalb einer [AquaCard]: Kopfzeile in Akzentfarbe mit Linie darunter
 * (.card .sec der Wärmepumpen-App). [divider] = Trennlinie darüber.
 */
@Composable
fun Sec(
    title: String?,
    divider: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (divider) HorizontalDivider(color = AquaColors.Ring)
    if (title != null) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, Modifier.weight(1f, fill = false), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AquaColors.Accent)
            trailing?.invoke()
        }
        HorizontalDivider(color = AquaColors.Ring)
    }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/** Überschrift über einer Kartengruppe (h2). */
@Composable
fun GroupTitle(text: String, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AquaColors.Head)
        trailing?.invoke()
    }
}

/** Fehlerhinweis-Karte (.banner) mit optionalen Aktionen. */
@Composable
fun Banner(title: String, text: String, actions: (@Composable RowScope.() -> Unit)? = null) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AquaColors.BannerBg)
            .border(1.dp, AquaColors.BannerRing, shape)
            .padding(12.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AquaColors.BannerText)
        Text(text, fontSize = 15.sp, color = AquaColors.BannerText, modifier = Modifier.padding(top = 2.dp))
        if (actions != null) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

/**
 * Hervorgehobene Kachel (.hero): großer Hauptwert links, Zustand rechts,
 * Verlaufsstreifen oben; optional mit Sparkline darunter.
 */
@Composable
fun Hero(
    label: String,
    value: String,
    unit: String,
    stateLabel: String,
    state: String,
    error: Boolean = false,
    history: List<Double?>? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(AquaColors.Hero1, AquaColors.Hero2)))
            .border(1.dp, AquaColors.HeroRing, shape)
            .drawBehind {
                drawRect(
                    Brush.horizontalGradient(listOf(AquaColors.Accent2, AquaColors.Accent)),
                    size = Size(size.width, 3.dp.toPx()),
                )
            }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text(label, fontSize = 12.sp, color = AquaColors.Muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        value, fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.Light,
                        color = if (error) AquaColors.Bad else AquaColors.Text,
                    )
                    if (unit.isNotEmpty()) Text(unit, fontSize = 22.sp, color = AquaColors.Text, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(stateLabel, fontSize = 12.sp, color = AquaColors.Muted)
                Text(state, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AquaColors.Accent, textAlign = TextAlign.End)
            }
        }
        if (history != null) Spark(history)
    }
}

@Composable
fun Section(
    title: String?,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = AquaCard { Sec(title, trailing = trailing, content = content) }

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color = AquaColors.Muted) {
    Text(text, modifier, fontSize = 12.sp, color = color, lineHeight = 16.sp)
}

/** Rückmeldung unter einem Formular (.hint.ok / .hint.err). */
@Composable
fun Msg(text: String?, ok: Boolean) {
    if (!text.isNullOrBlank()) Hint(text, color = if (ok) AquaColors.Ok else AquaColors.Bad)
}

// ── Kopfzeile (.header) ──────────────────────────────────────────────────────

/** Verbindungszustand für den Punkt in der Kopfzeile (.dot ok/busy/err). */
enum class LinkState { None, Ok, Busy, Err }

@Composable
fun AppHeader(title: String, subtitle: String, icon: ImageVector, link: LinkState, onRefresh: (() -> Unit)?) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .lightShadow(shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(AquaColors.Card, AquaColors.Card.copy(alpha = 0.85f))))
            .border(1.dp, AquaColors.Ring, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Logo(icon)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AquaColors.Text, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(9.dp).clip(CircleShape).background(
                        when (link) {
                            LinkState.Ok -> AquaColors.Ok
                            LinkState.Busy -> AquaColors.Weak
                            LinkState.Err -> AquaColors.Bad
                            LinkState.None -> AquaColors.Muted
                        },
                    ),
                )
                Text(subtitle, fontSize = 12.sp, color = AquaColors.Muted, maxLines = 1)
            }
        }
        if (onRefresh != null) {
            val btn = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .size(36.dp)
                    .clip(btn)
                    .background(AquaColors.BtnBg)
                    .border(1.dp, AquaColors.Ring, btn)
                    .clickable(onClick = onRefresh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Refresh, "Aktualisieren", Modifier.size(18.dp), tint = AquaColors.BtnText)
            }
        }
    }
}

/** Untere Tab-Leiste (nav.tabs): aktiver Tab in Akzentfarbe mit Verlaufsstrich oben. */
@Composable
fun AquaTabBar(items: List<Pair<String, ImageVector>>, selected: Int, onSelect: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(AquaColors.Card.copy(alpha = 0.9f), AquaColors.Card))),
    ) {
        HorizontalDivider(color = AquaColors.Ring)
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            items.forEachIndexed { i, (label, icon) ->
                val on = i == selected
                val col = if (on) AquaColors.Accent else AquaColors.Muted
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(i) }
                        .drawBehind {
                            if (on) {
                                val w = size.width / 2
                                drawRoundRect(
                                    Brush.horizontalGradient(listOf(AquaColors.Accent2, AquaColors.Accent), startX = w / 2, endX = w * 1.5f),
                                    topLeft = Offset(w / 2, 0f),
                                    size = Size(w, 2.dp.toPx()),
                                    cornerRadius = CornerRadius(2.dp.toPx()),
                                )
                            }
                        }
                        .padding(top = 8.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(icon, null, Modifier.size(22.dp), tint = col)
                    Text(label, fontSize = 11.5.sp, color = col, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

/** Kurze Meldung unten (.toast). */
@Composable
fun AquaToast(text: String) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text,
        Modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .lightShadow(shape)
            .clip(shape)
            .background(AquaColors.Card)
            .border(1.dp, AquaColors.HeroRing, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        fontSize = 14.sp, color = AquaColors.Text,
    )
}

@Composable
private fun Logo(icon: ImageVector) {
    val pulse by rememberInfiniteTransition(label = "pulse")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearOutSlowInEasing)), label = "p")
    Box(
        Modifier
            .size(28.dp)
            .drawBehind {
                // wie @keyframes pulse: Ring wächst auf 12 px und blendet bei 70 % aus
                val k = (pulse / 0.7f).coerceAtMost(1f)
                val spread = k * 12.dp.toPx()
                drawRoundRect(
                    AquaColors.Accent.copy(alpha = 0.35f * (1f - k)),
                    topLeft = Offset(-spread, -spread),
                    size = Size(size.width + 2 * spread, size.height + 2 * spread),
                    cornerRadius = CornerRadius(8.dp.toPx() + spread),
                )
            }
            .clip(RoundedCornerShape(8.dp))
            .background(AquaColors.Gradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
    }
}

// ── Kacheln, Chips, Verlauf (.tiles / .chip / .spark) ────────────────────────

/** Zwei Kacheln nebeneinander, gleich hoch. */
@Composable
fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun RowScope.Tile(
    label: String,
    value: String,
    sub: String? = null,
    error: Boolean = false,
    history: List<Double?>? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(TileShape)
            .background(AquaColors.Tile)
            .border(1.dp, AquaColors.TileRing, TileShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, fontSize = 12.sp, color = AquaColors.Muted, maxLines = 1)
        if (content != null) content()
        else {
            // Einheit kleiner und gedämpft (.tile .v small)
            val m = Regex("""^(-?\d+(?:[.,]\d+)?)\s+(.+)$""").matchEntire(value)
            val num = m?.groupValues?.get(1) ?: value
            val unit = m?.groupValues?.get(2) ?: ""
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    num, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    color = if (error) AquaColors.Bad else AquaColors.Text,
                )
                if (unit.isNotEmpty()) Text(
                    unit, fontSize = 13.sp, color = AquaColors.Muted,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                )
            }
        }
        if (!sub.isNullOrBlank()) Text(sub, fontSize = 12.sp, color = AquaColors.Muted, modifier = Modifier.padding(top = 2.dp))
        if (history != null) Spark(history)
    }
}

const val HISTORY_POINTS = 150

/** Verlaufslinie über min…max der gesammelten Werte; fehlende Werte unterbrechen die Linie. */
@Composable
fun Spark(values: List<Double?>) {
    Canvas(Modifier.fillMaxWidth().padding(top = 6.dp).height(34.dp)) {
        val pts = values.filterNotNull()
        if (pts.size < 2) return@Canvas
        var lo = pts.min()
        var hi = pts.max()
        if (hi - lo < 1e-9) { lo -= 0.5; hi += 0.5 }
        val path = Path()
        var pen = false
        values.forEachIndexed { i, v ->
            if (v == null) { pen = false; return@forEachIndexed }
            val x = i / (HISTORY_POINTS - 1f) * size.width
            val y = (30f - ((v - lo) / (hi - lo)).toFloat() * 26f) / 34f * size.height
            if (pen) path.lineTo(x, y) else path.moveTo(x, y)
            pen = true
        }
        drawPath(path, AquaColors.Accent, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun Swatch(color: Color?, size: Int = 18) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color ?: Color.Transparent)
            .border(1.dp, AquaColors.Text.copy(alpha = 0.25f), RoundedCornerShape(6.dp)),
    )
}

@Composable
fun Chip(text: String, on: Boolean, color: Color? = null) {
    val shape = CircleShape
    Row(
        Modifier
            .clip(shape)
            .background(AquaColors.Tile)
            .border(1.dp, if (on) AquaColors.HeroRing else AquaColors.TileRing, shape)
            .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (color != null) Swatch(color)
        Text(text, fontSize = 13.sp, color = AquaColors.Text)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun StatusDot(on: Boolean) {
    Box(Modifier.size(9.dp).clip(CircleShape).background(if (on) AquaColors.Ok else AquaColors.Bad))
}

// ── Eingabe (.input / .btn / .row) ───────────────────────────────────────────

@Composable
private fun InputBox(modifier: Modifier = Modifier, focused: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(InputShape)
            .background(AquaColors.Input)
            .border(1.dp, if (focused) AquaColors.InputFocus else AquaColors.InputRing, InputShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
        content = content,
    )
}

@Composable
fun FieldLabel(text: String) {
    Text(text, fontSize = 14.sp, color = AquaColors.Label, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
fun AquaField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    suffix: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label != null) FieldLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value, onChange,
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                singleLine = true,
                textStyle = TextStyle(color = AquaColors.Text, fontSize = 16.sp),
                cursorBrush = SolidColor(AquaColors.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                decorationBox = { inner ->
                    InputBox(focused = focused) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, fontSize = 16.sp, color = AquaColors.Muted.copy(alpha = 0.6f))
                        }
                        inner()
                    }
                },
            )
            if (suffix != null) Text(suffix, fontSize = 14.sp, color = AquaColors.Muted)
        }
    }
}

@Composable
fun AquaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    ghost: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = InputShape
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.6f)
            .heightIn(min = 44.dp)
            .clip(shape)
            .let {
                when {
                    primary -> it.background(AquaColors.Gradient)
                    ghost -> it.border(1.dp, AquaColors.BtnRing, shape)
                    else -> it.background(AquaColors.BtnBg).border(1.dp, AquaColors.BtnRing, shape)
                }
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontSize = 15.sp, textAlign = TextAlign.Center,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            color = if (primary) Color.White else AquaColors.BtnText,
        )
    }
}

/** Betriebsart-Auswahl (UHR / EIN / AUS): aktive Taste im Verlauf. */
@Composable
fun ModeRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, l ->
            AquaButton(l, { onSelect(i) }, Modifier.weight(1f), primary = selected == i)
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, color = AquaColors.Label)
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AquaColors.Accent2, checkedThumbColor = Color.White,
                checkedBorderColor = AquaColors.Accent2,
                uncheckedTrackColor = AquaColors.Input, uncheckedBorderColor = AquaColors.InputRing,
                uncheckedThumbColor = AquaColors.Muted,
            ),
        )
    }
}

@Composable
fun <T> AquaDropdown(
    selected: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label != null) FieldLabel(label)
        Box {
            InputBox(Modifier.clickable { open = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selected, Modifier.weight(1f), fontSize = 16.sp, color = AquaColors.Text)
                    Icon(Icons.Filled.ArrowDropDown, null, tint = AquaColors.Muted)
                }
            }
            DropdownMenu(open, { open = false }, Modifier.background(AquaColors.Card)) {
                options.forEach { (v, t) ->
                    DropdownMenuItem(text = { Text(t, color = AquaColors.Text) }, onClick = { onSelect(v); open = false })
                }
            }
        }
    }
}

// ── Dialoge: Farbe und Uhrzeit (ersetzen <input type=color|time>) ────────────

/** Bottom-Sheet (.scrim/.sheet): von unten, oben abgerundet; Tippen daneben schließt. */
@Composable
fun AquaSheet(onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss,
            ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(AquaColors.Card)
                    .border(1.dp, AquaColors.Ring, shape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (title != null) Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AquaColors.Text)
                content()
            }
        }
    }
}

/** Aktionen am Sheet-Ende (.actions): sekundär „Abbrechen“, primär rechts. */
@Composable
fun SheetActions(onCancel: () -> Unit, okText: String = "Übernehmen", danger: Boolean = false, onOk: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
        AquaButton("Abbrechen", onCancel)
        if (danger) {
            val shape = InputShape
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(AquaColors.Bad)
                    .clickable(onClick = onOk)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(okText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
        } else AquaButton(okText, onOk, primary = true)
    }
}

/** Sicherheitsabfrage als Bottom-Sheet. */
@Composable
fun ConfirmSheet(title: String, text: String, okText: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    AquaSheet(onDismiss, title) {
        Text(text, fontSize = 14.sp, color = AquaColors.Muted)
        SheetActions(onDismiss, okText, danger = true, onOk = onOk)
    }
}

internal fun parseHex(hex: String): Color? = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()

private fun toHex(c: Color) =
    String.format(Locale.US, "#%02x%02x%02x", (c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())

/** Farbwahl als Feld mit Vorschau; Tippen öffnet RGB-Regler. Wert im Format #rrggbb. */
@Composable
fun ColorField(label: String, hex: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        FieldLabel(label)
        Box(
            Modifier
                .width(64.dp).height(40.dp)
                .clip(InputShape)
                .background(AquaColors.Input)
                .border(1.dp, AquaColors.InputRing, InputShape)
                .padding(3.dp)
                .clickable { open = true },
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(7.dp)).background(parseHex(hex) ?: Color.Transparent))
        }
    }
    if (open) ColorPickerDialog(parseHex(hex) ?: Color.Black, { open = false }) { onChange(it); open = false }
}

@Composable
private fun ColorPickerDialog(initial: Color, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var r by remember { mutableFloatStateOf(initial.red * 255) }
    var g by remember { mutableFloatStateOf(initial.green * 255) }
    var b by remember { mutableFloatStateOf(initial.blue * 255) }
    val current = Color(r.roundToInt(), g.roundToInt(), b.roundToInt())
    AquaSheet(onDismiss, "Farbe wählen") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Swatch(current, 44)
            Text(toHex(current), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = AquaColors.Text)
        }
        ChannelSlider("R", r, Color(0xFFFF5A5A)) { r = it }
        ChannelSlider("G", g, Color(0xFF4ADE80)) { g = it }
        ChannelSlider("B", b, Color(0xFF60A5FA)) { b = it }
        SheetActions(onDismiss) { onPick(toHex(current)) }
    }
}

@Composable
private fun ChannelSlider(name: String, value: Float, color: Color, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, color = AquaColors.Label, modifier = Modifier.width(16.dp))
        Slider(
            value, onChange, Modifier.weight(1f), valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = AquaColors.Ring),
        )
        Text(value.roundToInt().toString(), color = AquaColors.Muted, fontSize = 12.sp, modifier = Modifier.width(28.dp))
    }
}

/** Uhrzeit HH:MM als Feld; Tippen öffnet den Zeitwähler. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    InputBox(modifier.clickable { open = true }) {
        Text(value.ifEmpty { "--:--" }, fontSize = 16.sp, color = AquaColors.Text)
    }
    if (open) {
        val parts = value.split(":")
        val state = rememberTimePickerState(parts.getOrNull(0)?.toIntOrNull() ?: 0, parts.getOrNull(1)?.toIntOrNull() ?: 0, true)
        AquaSheet({ open = false }, "Uhrzeit") {
            TimePicker(
                state, Modifier.align(Alignment.CenterHorizontally),
                colors = TimePickerDefaults.colors(
                    clockDialColor = AquaColors.Tile,
                    selectorColor = AquaColors.Accent2,
                    timeSelectorSelectedContainerColor = AquaColors.Sel,
                    timeSelectorUnselectedContainerColor = AquaColors.Tile,
                    timeSelectorSelectedContentColor = AquaColors.Accent,
                    timeSelectorUnselectedContentColor = AquaColors.Text,
                    clockDialUnselectedContentColor = AquaColors.Text,
                ),
            )
            SheetActions({ open = false }) {
                onChange(String.format(Locale.US, "%02d:%02d", state.hour, state.minute))
                open = false
            }
        }
    }
}

// ── JSON/Format-Helfer ───────────────────────────────────────────────────────

/** Zahl aus JSON; null/fehlend (Sensor nicht verfügbar) → null. */
internal fun JSONObject.dbl(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

internal fun fmt(v: Double?, decimals: Int, unit: String = ""): String =
    if (v == null) "–" else String.format(Locale.GERMANY, "%.${decimals}f", v) + if (unit.isEmpty()) "" else " $unit"
