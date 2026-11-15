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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription

internal val BrandPurple = Color(0xFF6760CF)
internal val Night = darkColorScheme(
    primary = Color(0xFFC9C3FF), onPrimary = Color(0xFF302866),
    primaryContainer = Color(0xFF38335F), onPrimaryContainer = Color(0xFFE7DEFF),
    secondary = Color(0xFFC9C2DF), secondaryContainer = Color(0xFF464057),
    background = Color(0xFF141318), onBackground = Color(0xFFE7E1EB),
    surface = Color(0xFF19171E), onSurface = Color(0xFFE7E1EB),
    surfaceVariant = Color(0xFF302D3B), onSurfaceVariant = Color(0xFFCAC4D2),
    outline = Color(0xFF948D9D)
)
internal val Day = lightColorScheme(
    primary = BrandPurple, onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DFFF), onPrimaryContainer = Color(0xFF282147),
    secondary = Color(0xFF625B74), secondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFFFCF9FF), onBackground = Color(0xFF1C1922),
    surface = Color(0xFFFCF9FF), onSurface = Color(0xFF1C1922),
    surfaceVariant = Color(0xFFE9E3F0), onSurfaceVariant = Color(0xFF494452),
    outline = Color(0xFF797281)
)

@Composable
internal fun readerLinkColor() = if (MaterialTheme.colorScheme.surface.luminance() < .3f) Color(0xFFC9C3FF) else BrandPurple

@Composable
internal fun SavedStar(saved: Boolean) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .3f
    Text(if (saved) "★" else "☆", fontSize = 26.sp,
        color = if (saved) { if (dark) Color(0xFFFFD54F) else Color(0xFF9B7000) } else MaterialTheme.colorScheme.onSurfaceVariant)
}

private val nativeFonts = java.util.concurrent.ConcurrentHashMap<String, Typeface>()
internal fun readerTypeface(context: Context, font: String): Typeface = nativeFonts.getOrPut(font) {
    ResourcesCompat.getFont(context.applicationContext, when (font) { "Serif" -> R.font.reader_serif; "Monospaced" -> R.font.reader_mono; else -> R.font.reader_sans })!!
}

private fun bundledFamily(regular: Int, bold: Int, italic: Int, boldItalic: Int) = FontFamily(
    Font(regular, FontWeight.Normal, FontStyle.Normal), Font(bold, FontWeight.Bold, FontStyle.Normal),
    Font(italic, FontWeight.Normal, FontStyle.Italic), Font(boldItalic, FontWeight.Bold, FontStyle.Italic)
)
private val sansFamily = bundledFamily(R.font.liberation_sans_regular, R.font.liberation_sans_bold, R.font.liberation_sans_italic, R.font.liberation_sans_bolditalic)
private val serifFamily = bundledFamily(R.font.liberation_serif_regular, R.font.liberation_serif_bold, R.font.liberation_serif_italic, R.font.liberation_serif_bolditalic)
private val monoFamily = bundledFamily(R.font.courier_prime_regular, R.font.courier_prime_bold, R.font.courier_prime_italic, R.font.courier_prime_bolditalic)
internal fun readerFontName(font: String): String = when (font) { "Serif" -> "Liberation Serif"; "Monospaced" -> "Courier Prime"; else -> "Liberation Sans" }

internal fun readerFontFamily(font: String): FontFamily = when (font) { "Serif" -> serifFamily; "Monospaced" -> monoFamily; else -> sansFamily }

/** Check the actual view after binding; renderer/selection changes can invalidate cached font flags. */
internal fun applyReaderTypography(view: TextView, sizeSp: Float, style: ReaderStyle) {
    val pixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, view.resources.displayMetrics)
    if (view.textSize != pixels) view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    if (view.lineSpacingMultiplier != style.lineSpacing) view.setLineSpacing(0f, style.lineSpacing)
    val typeface = readerTypeface(view.context, style.font)
    if (view.typeface != typeface) view.typeface = typeface
}

@Composable
internal fun BookmarkButton(bookmarked: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = androidx.compose.ui.Modifier.semantics {
        contentDescription = if (bookmarked) "Remove bookmark" else "Add bookmark"
        stateDescription = if (bookmarked) "Bookmarked" else "Not bookmarked"
    }) { SavedStar(bookmarked) }
}
