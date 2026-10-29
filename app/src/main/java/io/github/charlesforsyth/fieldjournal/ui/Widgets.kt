package io.github.charlesforsyth.fieldjournal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.charlesforsyth.fieldjournal.core.DisplayMode
import kotlin.math.PI
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.animateScrollBy
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val Hair = 1.dp
private val Corner = RoundedCornerShape(4.dp)

/** True when the user's font scale is large enough that dense rows should reflow. */
@Composable fun largeFont() = LocalDensity.current.fontScale > 1.15f

/** Stamped gear-tag label: mono, upper case, tracked. */
@Composable
fun GearTag(text: String, modifier: Modifier = Modifier, color: Color = LocalPal.current.dim, size: Int = 10) {
    Text(text.uppercase(), modifier, style = Type.tag.copy(fontSize = size.sp), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Printed-guide section header: tag text followed by a hairline rule. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val p = LocalPal.current
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GearTag(text, color = p.secondary)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(Hair).background(p.rule))
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

@Composable
fun HairRule(modifier: Modifier = Modifier, color: Color = LocalPal.current.rule) {
    Box(modifier.fillMaxWidth().height(Hair).background(color))
}

@Composable
private fun ButtonFace(
    text: String, icon: ImageVector?, fg: Color, bg: Color, border: Color?, modifier: Modifier, enabled: Boolean, onClick: () -> Unit,
) {
    val p = LocalPal.current
    val c = if (enabled) fg else p.dim // full-strength dim keeps AA; disabled = no fill, rule border
    // 48 dp touch target around a ~32 dp visible face.
    Box(modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Row(
            Modifier.heightIn(min = 32.dp)
                .then(if (bg != Color.Transparent) Modifier.background(if (enabled) bg else p.chip, Corner) else Modifier)
                .then(if (border != null) Modifier.border(Hair, if (enabled) border else p.rule, Corner) else Modifier)
                .padding(horizontal = if (border == null && bg == Color.Transparent) 6.dp else 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = c); if (text.isNotEmpty()) Spacer(Modifier.width(7.dp)) }
            if (text.isNotEmpty()) Text(text, style = Type.button, color = c, maxLines = 1)
        }
    }
}

/** The single filled action on a screen. Small, saddle-brown, 4 dp corners. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPal.current
    // Night: no large lit fill (protects dark adaptation); the primary becomes a brighter hairline button.
    if (p.mode == DisplayMode.NIGHT_RED) ButtonFace(text, icon, p.primary, Color.Transparent, p.primary, modifier, enabled, onClick)
    else ButtonFace(text, icon, p.onPrimary, p.primary, null, modifier, enabled, onClick)
}

/** Hairline outlined button for secondary actions. */
@Composable
fun FieldButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, color: Color? = null, onClick: () -> Unit) {
    val p = LocalPal.current
    ButtonFace(text, icon, color ?: p.primary, Color.Transparent, (color ?: p.primary).copy(alpha = 0.7f), modifier, enabled, onClick)
}

/** Quiet text action (no border). */
@Composable
fun TextAction(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPal.current
    ButtonFace(text, icon, color ?: p.primary, Color.Transparent, null, modifier, enabled, onClick)
}

/** Line-icon button: 20 dp glyph, 48 dp touch area. */
@Composable
fun IconAction(icon: ImageVector, description: String, modifier: Modifier = Modifier, tint: Color? = null, size: Dp = 20.dp, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(modifier.size(48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = description, onClick = onClick)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size), tint = tint ?: p.primary)
    }
}

/** A text tab that is underlined when selected (category filters, map layers). */
@Composable
fun TabText(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    Column(modifier.heightIn(min = 48.dp).clickable(role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick).padding(horizontal = if (largeFont()) 4.dp else 8.dp)
        .semantics { this.selected = selected }, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text.uppercase(), style = Type.tag.copy(fontSize = 11.sp, letterSpacing = if (largeFont()) 0.4.sp else 1.2.sp), color = if (selected) p.heading else p.tabInactive,
            maxLines = 1, softWrap = false)
        Spacer(Modifier.height(5.dp))
        Box(Modifier.width(18.dp).height(1.5.dp).background(if (selected) p.brass else Color.Transparent))
    }
}

/** One reading on an instrument plate: stamped label above a mono value. */
@Composable
fun Reading(label: String, value: String, modifier: Modifier = Modifier, unit: String = "", valueColor: Color? = null) {
    val p = LocalPal.current
    Column(modifier) {
        GearTag(label, color = p.dim, size = 9)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = Type.reading, color = valueColor ?: p.heading, maxLines = 1, softWrap = false)
            if (unit.isNotEmpty()) Text(" $unit", style = Type.readingSmall, color = p.dim, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

/** Brass instrument plate: card colour, 1 dp brass border with an inner hairline inset like an engraved plate. */
@Composable
fun InstrumentPlate(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    Column(modifier.fillMaxWidth().background(p.card, RoundedCornerShape(5.dp)).border(Hair, p.brass.copy(alpha = 0.85f), RoundedCornerShape(5.dp))
        .padding(3.dp).border(Hair, p.brass.copy(alpha = 0.35f), RoundedCornerShape(3.dp)).padding(horizontal = 12.dp, vertical = 9.dp), content = content)
}

/** Spec-sheet row: label left, mono value right, hairline below. */
@Composable
fun SpecRow(label: String, value: String, valueColor: Color? = null, unit: String? = null, trailing: (@Composable () -> Unit)? = null) {
    val p = LocalPal.current
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            // Label takes the free space; the value is sized to its content (a short label never wraps beside a short value).
            Text(label, style = Type.bodySmall.copy(fontSize = 14.sp), color = p.text, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(value, style = Type.readingSmall.copy(fontSize = 12.sp), color = valueColor ?: p.heading, modifier = Modifier.widthIn(max = 220.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
            if (unit != null) Text(unit, style = Type.readingSmall.copy(fontSize = 11.sp), color = p.meta, modifier = Modifier.width(UnitCol).padding(start = 6.dp), maxLines = 1, softWrap = false)
            if (trailing != null) { Spacer(Modifier.width(6.dp)); Box(Modifier.width(TrailCol), contentAlignment = Alignment.CenterEnd) { trailing() } }
        }
        HairRule(color = p.rule.copy(alpha = 0.7f))
    }
}

/** Spec-sheet row whose value is editable in place (right-aligned mono, hairline under the value only). */
@Composable
fun SpecField(label: String, value: String, onChange: (String) -> Unit, unit: String = "", keyboard: KeyboardType = KeyboardType.Decimal, trailingSpace: Dp = 0.dp) {
    val p = LocalPal.current
    var focused by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.bodySmall.copy(fontSize = 14.sp), color = p.text, modifier = Modifier.weight(1f))
            Column(Modifier.width(72.dp)) {
                BasicTextField(value, onChange, Modifier.fillMaxWidth().padding(vertical = 6.dp).onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label },
                    textStyle = Type.readingSmall.copy(fontSize = 13.sp, color = p.heading, textAlign = androidx.compose.ui.text.style.TextAlign.End),
                    singleLine = true, cursorBrush = SolidColor(p.primary), keyboardOptions = KeyboardOptions(keyboardType = keyboard))
                Box(Modifier.fillMaxWidth().height(if (focused) 1.5.dp else Hair).background(if (focused) p.primary else p.dim.copy(alpha = 0.75f)))
            }
            Text(unit, style = Type.readingSmall.copy(fontSize = 11.sp), color = p.meta, modifier = Modifier.width(UnitCol).padding(start = 6.dp), maxLines = 1, softWrap = false)
            if (trailingSpace > 0.dp) Spacer(Modifier.width(trailingSpace))
        }
        HairRule(color = p.rule.copy(alpha = 0.7f))
    }
}

/** Underlined spec-sheet input: tag label above, hairline below. Much quieter than an outlined Material field. */
@Composable
fun LineField(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    placeholder: String = "", password: Boolean = false, singleLine: Boolean = true, keyboard: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = LocalPal.current
    var focused by remember { mutableStateOf(false) }
    Column(modifier.padding(vertical = 6.dp)) {
        if (label.isNotEmpty()) GearTag(label, color = if (focused) p.primary else p.dim, size = 9)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(value, onChange, Modifier.weight(1f).heightIn(min = 40.dp).padding(vertical = 8.dp)
                .onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label.ifEmpty { placeholder } },
                textStyle = Type.body.copy(color = p.text), singleLine = singleLine, maxLines = if (singleLine) 1 else 3,
                cursorBrush = SolidColor(p.primary),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = Type.italic.copy(fontSize = 15.sp), color = p.placeholder, maxLines = 1)
                        inner()
                    }
                })
            if (trailing != null) trailing()
        }
        // A real input baseline: ink-brown rule (not the faint khaki divider), thicker while focused.
        Box(Modifier.fillMaxWidth().height(if (focused) 1.5.dp else Hair).background(if (focused) p.primary else p.dim.copy(alpha = 0.75f)))
    }
}

/**
 * Two-position stamped switch: a small "OFF | ON" label plate. The chosen word is inked (filled), the other is left
 * debossed (meta text on paper). Still a real switch for accessibility (Role.Switch, toggleable state).
 */
@Composable
fun StampSwitch(checked: Boolean, onChange: (Boolean) -> Unit, description: String) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(3.dp)
    Box(Modifier.heightIn(min = 48.dp).toggleable(checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Row(Modifier.height(24.dp).border(Hair, p.dim, shape).clip(shape), verticalAlignment = Alignment.CenterVertically) {
            listOf("OFF" to !checked, "ON" to checked).forEachIndexed { i, (word, on) ->
                if (i == 1) Box(Modifier.width(Hair).fillMaxHeight().background(p.dim))
                // One language for both states: the selected word is inked (filled, paper-coloured text), the other is
                // debossed (plain meta text). ON inks in pine, OFF in the quieter dim ink.
                Box(Modifier.fillMaxHeight().widthIn(min = 36.dp).background(if (on) (if (word == "ON") p.stampOnFill else p.stampOffFill) else Color.Transparent)
                    .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(word, style = Type.tag.copy(fontSize = 9.5.sp, letterSpacing = 1.2.sp,
                        fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal),
                        color = if (on) (if (word == "ON") p.stampOnText else p.stampOffText) else p.meta, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null) {
    val p = LocalPal.current
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp, horizontal = 0.dp)) {
                Text(label, style = Type.bodySmall.copy(fontSize = 14.sp), color = p.text)
                if (sub != null) Text(sub, style = Type.bodySmall.copy(fontSize = 12.sp), color = p.dim)
            }
            Spacer(Modifier.width(10.dp))
            StampSwitch(checked, onChange, label)
        }
        HairRule(color = p.rule.copy(alpha = 0.7f))
    }
}

/** Width of the unit column in ledger rows, so every unit lines up under the one above. */
val UnitCol = 38.dp
/** Width of the action slot at the end of a settable row ("Set"), so value/unit columns line up across rows. */
val TrailCol = 64.dp

/**
 * Read-only ledger row (Settings > Readings): label (+ optional sub line) left, mono value right-aligned, unit in its own
 * fixed column. Used on a parchment plate, so all colours here are checked against the card colour.
 */
@Composable
fun LedgerRow(label: String, value: String, unit: String = "", sub: String? = null, valueColor: Color? = null, last: Boolean = false) {
    val p = LocalPal.current
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 38.dp).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = Type.bodySmall.copy(fontSize = 14.sp), color = p.text)
                if (sub != null) Text(sub, style = Type.bodySmall.copy(fontSize = 12.sp), color = p.meta)
            }
            Spacer(Modifier.width(8.dp))
            Text(value, style = Type.readingSmall.copy(fontSize = 13.sp), color = valueColor ?: p.heading, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text(unit, style = Type.readingSmall.copy(fontSize = 11.sp), color = p.meta, modifier = Modifier.width(UnitCol).padding(start = 6.dp), maxLines = 1, softWrap = false)
        }
        if (!last) HairRule(color = p.rule.copy(alpha = 0.7f))
    }
}

/** Parchment plate that holds read-only readings (visually distinct from the settable rows on paper). */
@Composable
fun ReadingsPlate(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(4.dp)).border(Hair, p.rule, RoundedCornerShape(4.dp))
        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), content = content)
}

/** Two-line Ray control: shows its state (unset / locked bearing) like a lockable instrument. */
@Composable
fun RayControl(name: String, lockedAz: Double?, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(4.dp)
    val locked = lockedAz != null
    val state = when { locked -> "%03.0f° %s".format(lockedAz, io.github.charlesforsyth.fieldjournal.core.GeoMath.cardinal8(lockedAz!!)); enabled -> "Unset"; else -> "Lock A first" }
    Box(modifier.heightIn(min = 48.dp).clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = if (locked) "Re-lock $name" else "Lock $name", onClick = onClick)
        .semantics { contentDescription = name; stateDescription = if (locked) "Locked at $state" else state }, contentAlignment = Alignment.Center) {
        Row(Modifier.height(38.dp).border(if (locked) 1.5.dp else Hair, if (locked) p.brass else if (enabled) p.dim else p.rule, shape)
            .background(if (locked) p.card else Color.Transparent, shape).padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            // State glyph: open ring = unset, filled ring with bar = locked.
            Canvas(Modifier.size(12.dp)) {
                val r = size.minDimension / 2 - 1.dp.toPx()
                if (locked) { drawCircle(p.brass, r); drawLine(p.card, Offset(center.x - r * 0.55f, center.y), Offset(center.x + r * 0.55f, center.y), 1.5.dp.toPx()) }
                else drawCircle(if (enabled) p.dim else p.rule, r, style = Stroke(1.dp.toPx()))
            }
            Spacer(Modifier.width(7.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(name.uppercase(), style = Type.tag.copy(fontSize = 9.sp, letterSpacing = 1.2.sp), color = if (enabled) p.heading else p.meta, maxLines = 1, softWrap = false)
                Text(state, style = Type.readingSmall.copy(fontSize = 10.5.sp, letterSpacing = 0.sp), color = if (locked) p.heading else p.meta, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Plate secondary row: equal cells, stamped label above, value with a smaller unit, hairline dividers between. */
@Composable
fun PlateCells(cells: List<io.github.charlesforsyth.fieldjournal.core.Readouts.PlateCell>) {
    val p = LocalPal.current
    val perRow = if (largeFont()) 2 else cells.size
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { i, c ->
                    if (i > 0) Box(Modifier.width(Hair).fillMaxHeight().background(p.brass.copy(alpha = 0.35f)))
                    Column(Modifier.weight(1f).padding(start = if (i == 0) 0.dp else 9.dp, end = 4.dp)) {
                        Text(c.label.uppercase(), style = Type.tag.copy(fontSize = 9.sp, letterSpacing = 1.2.sp), color = p.meta, maxLines = 1, softWrap = false)
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(c.value, style = Type.reading.copy(fontSize = 14.sp), color = p.heading, maxLines = 1, softWrap = false)
                            if (c.unit.isNotEmpty()) Text(" " + c.unit, style = Type.readingSmall.copy(fontSize = 10.sp), color = p.meta, maxLines = 1, softWrap = false,
                                modifier = Modifier.padding(bottom = 1.dp))
                        }
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Slim ruler slider: hairline track, graduated ticks, small brass needle. Used for ghost opacity,
 * clip length and the season scrubber. [steps] = number of discrete positions minus one (0 = continuous).
 */
@Composable
fun RulerSlider(
    value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier,
    steps: Int = 0, ticks: Int = 10, labels: List<String> = emptyList(), active: Boolean = true, description: String = "",
    curve: List<Float>? = null,
) {
    val p = LocalPal.current
    fun snap(v: Float): Float {
        val c = v.coerceIn(range.start, range.endInclusive)
        if (steps <= 0) return c
        val step = (range.endInclusive - range.start) / steps
        return range.start + ((c - range.start) / step).roundToInt() * step
    }
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Column(modifier.semantics {
        contentDescription = description
        progressBarRangeInfo = ProgressBarRangeInfo(value, range, steps)
        setProgress { t -> onChange(snap(t)); true }
    }) {
        Canvas(Modifier.fillMaxWidth().height(if (curve != null) 40.dp else 28.dp)
            .pointerInput(range, steps) { detectTapGestures { o -> onChange(snap(range.start + o.x / size.width * (range.endInclusive - range.start))) } }
            .pointerInput(range, steps) {
                detectDragGestures { ch, _ -> onChange(snap(range.start + ch.position.x / size.width * (range.endInclusive - range.start))) }
            }) {
            val pad = 6.dp.toPx(); val w = size.width - pad * 2; val base = size.height - 6.dp.toPx()
            val hair = 1.dp.toPx()
            curve?.let { c ->
                val path = Path(); val mx = (c.maxOrNull() ?: 1f).coerceAtLeast(1e-6f)
                c.forEachIndexed { i, v ->
                    val x = pad + i / (c.size - 1f) * w; val y = base - 4.dp.toPx() - v / mx * (size.height - 16.dp.toPx())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, p.muted.copy(alpha = 0.9f), style = Stroke(1.2f * hair))
            }
            drawLine(p.rule, Offset(pad, base), Offset(pad + w, base), hair)
            for (i in 0..ticks) {
                val x = pad + i / ticks.toFloat() * w
                val major = ticks <= 12 || i % 5 == 0
                drawLine(p.dim.copy(alpha = if (major) 0.8f else 0.45f), Offset(x, base), Offset(x, base - (if (major) 6.dp else 3.dp).toPx()), hair)
            }
            if (active) {
                val x = pad + frac * w
                drawLine(p.primary, Offset(pad, base), Offset(x, base), 1.5f * hair)
                drawLine(p.brass, Offset(x, base + 4.dp.toPx()), Offset(x, base - 16.dp.toPx().coerceAtMost(size.height - 4.dp.toPx())), 1.5f * hair)
                val tri = Path().apply { moveTo(x - 4.dp.toPx(), base + 5.dp.toPx()); lineTo(x + 4.dp.toPx(), base + 5.dp.toPx()); lineTo(x, base); close() }
                drawPath(tri, p.brass)
            }
        }
        if (labels.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 0.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, style = Type.tag.copy(fontSize = 9.sp, letterSpacing = 0.5.sp), color = p.dim) }
        }
    }
}

/**
 * v1.2: topographic contours only in the masthead band (never behind body text). Drawn once per size, clipped to the
 * band; any text inside the band sits on a [knockout] so no contour line ever crosses a glyph. None in Night mode.
 */
fun Modifier.topoBand(p: Palette): Modifier = if (p.mode == DisplayMode.NIGHT_RED) this else this.drawWithCache {
    val paths = ArrayList<Path>()
    val centres = listOf(Offset(size.width * 0.70f, size.height * 0.95f), Offset(size.width * 0.18f, -size.height * 0.35f))
    centres.forEachIndexed { ci, c ->
        for (k in 1..14) {
            val r0 = k * 15.dp.toPx(); val path = Path(); val n = 120
            for (i in 0..n) {
                val a = i / n.toFloat() * 2 * PI
                val wob = 1f + 0.10f * sin(3 * a + k * 0.7 + ci).toFloat() + 0.06f * cos(5 * a - k * 0.4).toFloat()
                val x = c.x + r0 * 1.5f * wob * cos(a).toFloat(); val y = c.y + r0 * 0.62f * wob * sin(a).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            paths += path
        }
    }
    val col = (if (p.dark) p.brass else p.primary).copy(alpha = if (p.dark) 0.16f else 0.13f)
    val w = 1.dp.toPx()
    onDrawBehind {
        clipRect { paths.forEachIndexed { i, path -> drawPath(path, col, style = Stroke(if (i % 5 == 4) 1.6f * w else w)) } }
    }
}

/** Paper-coloured plate with a feathered edge behind text that sits over [topoBand] contours. */
fun Modifier.knockout(p: Palette): Modifier = this.drawBehind {
    val f = 3.dp.toPx()
    for (i in 3 downTo 1) drawRoundRect(p.bg.copy(alpha = 0.4f), Offset(-f * i, -f * i / 2), androidx.compose.ui.geometry.Size(size.width + 2 * f * i, size.height + f * i),
        androidx.compose.ui.geometry.CornerRadius(f * (i + 1)))
    drawRect(p.bg)
}

/**
 * Horizontally scrolling tab row that reads as deliberately scrollable: a paper fade and a small chevron at whichever
 * edge has more content. The last tab can always scroll fully clear of the fade.
 */
@Composable
fun ScrollFadeRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val p = LocalPal.current
    val st = rememberScrollState()
    val scope = rememberCoroutineScope()
    Box(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(st).padding(end = 30.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        if (st.canScrollForward) Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            .clickable(onClickLabel = "Scroll for more") { scope.launch { st.animateScrollBy(240f) } }, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(36.dp).fillMaxHeight().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(p.bg.copy(alpha = 0f), p.bg))))
            Box(Modifier.fillMaxHeight().background(p.bg), contentAlignment = Alignment.Center) {
                Icon(LineIcons.chevronRight, "More filters", Modifier.size(16.dp), tint = p.tabInactive)
            }
        }
        if (st.canScrollBackward) Box(Modifier.align(Alignment.CenterStart).width(28.dp).fillMaxHeight()
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(p.bg, p.bg.copy(alpha = 0f)))))
    }
}

/** Overflow / action sheet with quiet rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.bg, contentColor = p.text, shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(32.dp, 3.dp).background(p.rule, CircleShape)) }, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 20.dp)) {
            Text(title, style = Type.titleSmall, color = p.heading, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
            HairRule()
            content()
        }
    }
}

@Composable
fun SheetRow(text: String, icon: ImageVector? = null, sub: String? = null, color: Color? = null, enabled: Boolean = true, chevron: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = enabled, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = if (enabled) (color ?: p.primary) else p.dim); Spacer(Modifier.width(16.dp)) }
            Column(Modifier.weight(1f)) {
                Text(text, style = Type.body, color = if (enabled) (color ?: p.text) else p.dim)
                if (sub != null) Text(sub, style = Type.bodySmall, color = p.meta)
            }
            if (chevron) Icon(LineIcons.chevronRight, null, Modifier.size(16.dp), tint = p.meta)
        }
        HairRule(color = p.rule.copy(alpha = 0.6f))
    }
}

/** Kept for readability of numeric blocks. */
@Composable
fun Mono(text: String, modifier: Modifier = Modifier, size: Int = 12, color: Color? = null) {
    Text(text, modifier = modifier, style = Type.readingSmall.copy(fontSize = size.sp), color = color ?: LocalPal.current.text)
}

/** Small plate used over the viewfinder (alignment score, voice). */
@Composable
fun OverlayTag(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val p = LocalPal.current
    Box(modifier.background(p.bg.copy(alpha = 0.82f), Corner).border(Hair, (color ?: p.brass).copy(alpha = 0.8f), Corner).padding(horizontal = 8.dp, vertical = 4.dp)) {
        GearTag(text, color = color ?: p.heading)
    }
}


/**
 * Field-data line: items separated by " · " only *between* items on the same line, so a wrap never starts or ends
 * a line with a separator and never splits a value from its unit (each item is unbreakable).
 */
@Composable
fun DotFlow(items: List<String>, modifier: Modifier = Modifier, style: TextStyle = Type.meta, color: Color = LocalPal.current.meta) {
    androidx.compose.ui.layout.Layout(modifier = modifier, content = {
        items.forEach { Text(it, style = style, color = color, maxLines = 1, softWrap = false) }
        items.forEach { _ -> Text(" · ", style = style, color = color, maxLines = 1, softWrap = false) }
    }) { ms, c ->
        val n = items.size
        val loose = c.copy(minWidth = 0, minHeight = 0)
        val pl = ms.map { it.measure(loose) }
        val maxW = if (c.hasBoundedWidth) c.maxWidth else Int.MAX_VALUE
        data class P(val i: Int, val x: Int, val y: Int)
        val placed = ArrayList<P>(); var x = 0; var y = 0; var lineH = 0; var widest = 0
        for (i in 0 until n) {
            val w = pl[i].width; val dot = pl[n + i].width
            if (x > 0 && x + dot + w > maxW) { y += lineH; x = 0; lineH = 0 }
            if (x > 0) { placed += P(n + i, x, y); x += dot }
            placed += P(i, x, y); x += w; lineH = maxOf(lineH, pl[i].height); widest = maxOf(widest, x)
        }
        layout(widest.coerceAtMost(maxW).coerceAtLeast(c.minWidth), (y + lineH).coerceAtLeast(c.minHeight)) {
            placed.forEach { pl[it.i].place(it.x, it.y) }
        }
    }
}
