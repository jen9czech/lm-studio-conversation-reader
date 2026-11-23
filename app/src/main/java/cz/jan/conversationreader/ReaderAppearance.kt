package cz.jan.conversationreader

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription

internal val BrandPurple = Color(0xFF6760CF)
internal val DayLink = Color(0xFF554DB5)
internal val Night = darkColorScheme(
    primary = Color(0xFFC9C3FF), onPrimary = Color(0xFF302866),
    primaryContainer = Color(0xFF38335F), onPrimaryContainer = Color(0xFFE7DEFF),
    secondary = Color(0xFFC9C2DF), secondaryContainer = Color(0xFF464057),
    background = Color(0xFF141218), onBackground = Color(0xFFE7E1EB),
    surface = Color(0xFF141218), onSurface = Color(0xFFE7E1EB),
    surfaceContainer = Color(0xFF211E29), surfaceContainerHigh = Color(0xFF2C2739),
    surfaceVariant = Color(0xFF302D3B), onSurfaceVariant = Color(0xFFCAC4D2),
    outline = Color(0xFF948D9D)
)
internal val Day = lightColorScheme(
    primary = BrandPurple, onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DFFF), onPrimaryContainer = Color(0xFF282147),
    secondary = Color(0xFF625B74), secondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFFFAF8FF), onBackground = Color(0xFF1C1922),
    surface = Color(0xFFFAF8FF), onSurface = Color(0xFF1C1922),
    surfaceContainer = Color(0xFFF0EDF7), surfaceContainerHigh = Color(0xFFE8E3F3),
    surfaceVariant = Color(0xFFE9E3F0), onSurfaceVariant = Color(0xFF494452),
    outline = Color(0xFF797281)
)

@Composable
internal fun readerLinkColor() = if (MaterialTheme.colorScheme.surface.luminance() < .3f) Night.primary else DayLink

@Composable
internal fun SavedStar(saved: Boolean) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .3f
    Icon(androidx.compose.ui.res.painterResource(if (saved) R.drawable.ic_star else R.drawable.ic_star_outline), contentDescription = null,
        modifier = androidx.compose.ui.Modifier.size(24.dp),
        tint = if (saved) { if (dark) Color(0xFFFFD54F) else Color(0xFF9B7000) } else MaterialTheme.colorScheme.onSurfaceVariant)
}

private val nativeFonts = java.util.concurrent.ConcurrentHashMap<String, Typeface>()
internal fun readerTypeface(context: Context, font: String): Typeface = nativeFonts.getOrPut(font) {
    ResourcesCompat.getFont(context.applicationContext, when (font) { "Serif" -> R.font.reader_serif; "Monospaced" -> R.font.reader_mono; else -> return@getOrPut Typeface.DEFAULT })!!
}

private fun bundledFamily(regular: Int, bold: Int, italic: Int, boldItalic: Int) = FontFamily(
    Font(regular, FontWeight.Normal, FontStyle.Normal), Font(bold, FontWeight.Bold, FontStyle.Normal),
    Font(italic, FontWeight.Normal, FontStyle.Italic), Font(boldItalic, FontWeight.Bold, FontStyle.Italic)
)
private val serifFamily = bundledFamily(R.font.liberation_serif_regular, R.font.liberation_serif_bold, R.font.liberation_serif_italic, R.font.liberation_serif_bolditalic)
private val monoFamily = bundledFamily(R.font.courier_prime_regular, R.font.courier_prime_bold, R.font.courier_prime_italic, R.font.courier_prime_bolditalic)
internal fun readerFontName(font: String): String = when (font) { "Serif" -> "Liberation Serif"; "Monospaced" -> "Courier Prime"; else -> "Sans-serif" }

internal fun readerFontFamily(font: String): FontFamily = when (font) { "Serif" -> serifFamily; "Monospaced" -> monoFamily; else -> FontFamily.Default }

/** Check the actual view after binding; renderer/selection changes can invalidate cached font flags. */
internal fun applyReaderTypography(view: TextView, sizeSp: Float, style: ReaderStyle, italic: Boolean = false) {
    val pixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, view.resources.displayMetrics)
    if (view.textSize != pixels) view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    if (view.lineSpacingMultiplier != style.lineSpacing) view.setLineSpacing(0f, style.lineSpacing)
    val base = readerTypeface(view.context, style.font)
    val typeface = if (italic) Typeface.create(base, Typeface.ITALIC) else base
    if (view.typeface != typeface) view.typeface = typeface
}

@Composable
internal fun BookmarkButton(bookmarked: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = androidx.compose.ui.Modifier.semantics {
        contentDescription = if (bookmarked) "Remove bookmark" else "Add bookmark"
        stateDescription = if (bookmarked) "Bookmarked" else "Not bookmarked"
    }) { SavedStar(bookmarked) }
}

internal fun readerInterfaceTypography(scale: Float): Typography {
    val base = Typography()
    fun androidx.compose.ui.text.TextStyle.scaled() = copy(
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
        letterSpacing = letterSpacing * scale
    )
    return Typography(
        displayLarge = base.displayLarge.scaled(),
        displayMedium = base.displayMedium.scaled(),
        displaySmall = base.displaySmall.scaled(),
        headlineLarge = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall = base.headlineSmall.scaled(),
        titleLarge = base.titleLarge.scaled(),
        titleMedium = base.titleMedium.scaled(),
        titleSmall = base.titleSmall.scaled(),
        bodyLarge = base.bodyLarge.scaled(),
        bodyMedium = base.bodyMedium.scaled(),
        bodySmall = base.bodySmall.scaled(),
        labelLarge = base.labelLarge.scaled(),
        labelMedium = base.labelMedium.scaled(),
        labelSmall = base.labelSmall.scaled()
    )
}
