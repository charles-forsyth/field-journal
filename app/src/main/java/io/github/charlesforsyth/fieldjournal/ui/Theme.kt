package io.github.charlesforsyth.fieldjournal.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.charlesforsyth.fieldjournal.R
import io.github.charlesforsyth.fieldjournal.core.DisplayMode
import io.github.charlesforsyth.fieldjournal.core.PaletteSpec

/** Compose view of [PaletteSpec]. Names follow field-guide roles. */
data class Palette(
    val mode: DisplayMode,
    val bg: Color, val card: Color, val chip: Color, val rule: Color,
    val primary: Color, val onPrimary: Color, val heading: Color, val secondary: Color,
    val muted: Color, val brass: Color, val text: Color, val dim: Color, val warn: Color, val halo: Color,
    val spec: PaletteSpec,
) {
    val dark get() = mode != DisplayMode.SUNLIGHT
    // Named text roles: the same names ContrastTest checks in PaletteSpec.uiTextPairs.
    val placeholder get() = Color(spec.placeholder)
    val tabInactive get() = Color(spec.tabInactive)
    val navInactive get() = Color(spec.navInactive)
    val meta get() = Color(spec.meta)
    val stampOnText get() = Color(spec.stampOnText)
    val stampOnFill get() = Color(spec.stampOnFill)
    val stampOffFill get() = Color(spec.stampOffFill)
    val stampOffText get() = Color(spec.stampOffText)
    val curveLabel get() = Color(spec.curveLabel)
}

fun paletteFor(m: DisplayMode): Palette {
    val s = PaletteSpec.forMode(m)
    return Palette(m, Color(s.bg), Color(s.card), Color(s.chip), Color(s.rule), Color(s.primary), Color(s.onPrimary),
        Color(s.heading), Color(s.secondary), Color(s.muted), Color(s.brass), Color(s.text), Color(s.dim), Color(s.warn), Color(s.halo), s)
}

val LocalPal = staticCompositionLocalOf { paletteFor(DisplayMode.SUNLIGHT) }

/** Bundled OFL faces (res/font, subset to Latin-1 + punctuation). */
object Fonts {
    val serif = FontFamily(
        Font(R.font.eb_garamond_regular, FontWeight.Normal),
        Font(R.font.eb_garamond_medium, FontWeight.Medium),
        Font(R.font.eb_garamond_italic, FontWeight.Normal, FontStyle.Italic),
    )
    val sans = FontFamily(
        Font(R.font.source_sans_regular, FontWeight.Normal),
        Font(R.font.source_sans_semibold, FontWeight.SemiBold),
    )
    val mono = FontFamily(
        Font(R.font.plex_mono_regular, FontWeight.Normal),
        Font(R.font.plex_mono_medium, FontWeight.Medium),
    )
}

/** Type scale. "tag" = stamped gear label (mono caps, tracked). "meta" = serif small caps. */
object Type {
    val brand = TextStyle(fontFamily = Fonts.serif, fontWeight = FontWeight.Medium, fontSize = 21.sp, letterSpacing = 0.2.sp)
    val title = TextStyle(fontFamily = Fonts.serif, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 24.sp)
    val titleSmall = TextStyle(fontFamily = Fonts.serif, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 21.sp)
    val italic = TextStyle(fontFamily = Fonts.serif, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp)
    val body = TextStyle(fontFamily = Fonts.sans, fontSize = 15.sp, lineHeight = 21.sp)
    val bodySmall = TextStyle(fontFamily = Fonts.sans, fontSize = 13.sp, lineHeight = 18.sp)
    val button = TextStyle(fontFamily = Fonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.6.sp)
    val tag = TextStyle(fontFamily = Fonts.mono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.6.sp)
    val reading = TextStyle(fontFamily = Fonts.mono, fontSize = 17.sp)
    val readingSmall = TextStyle(fontFamily = Fonts.mono, fontSize = 11.sp, letterSpacing = 0.3.sp)
    val meta = TextStyle(fontFamily = Fonts.mono, fontSize = 10.5.sp, letterSpacing = 0.8.sp, lineHeight = 15.sp)
}

@Composable
fun FieldTheme(p: Palette, content: @Composable () -> Unit) {
    val scheme = (if (p.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.primary, onPrimary = p.onPrimary, secondary = p.secondary, onSecondary = p.bg, tertiary = p.brass,
        background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text, surfaceVariant = p.card, onSurfaceVariant = p.dim,
        outline = p.rule, outlineVariant = p.rule, error = p.warn, onError = p.bg, primaryContainer = p.chip, onPrimaryContainer = p.text,
        secondaryContainer = p.chip, onSecondaryContainer = p.text, surfaceTint = p.bg,
        surfaceContainerLowest = p.bg, surfaceContainerLow = p.bg, surfaceContainer = p.bg, surfaceContainerHigh = p.card, surfaceContainerHighest = p.card,
        inverseSurface = p.heading, inverseOnSurface = p.bg,
    )
    val typo = Typography(
        bodyLarge = Type.body, bodyMedium = Type.body.copy(fontSize = 14.sp), bodySmall = Type.bodySmall,
        labelLarge = Type.button, labelMedium = Type.button.copy(fontSize = 12.sp), labelSmall = Type.tag,
        titleLarge = Type.title, titleMedium = Type.titleSmall, titleSmall = Type.titleSmall.copy(fontSize = 15.sp),
    )
    val shapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(8.dp), extraLarge = RoundedCornerShape(10.dp))
    MaterialTheme(colorScheme = scheme, typography = typo, shapes = shapes, content = content)
}
