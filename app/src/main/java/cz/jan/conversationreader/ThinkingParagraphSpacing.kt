package cz.jan.conversationreader

import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LineHeightSpan
import io.noties.markwon.core.spans.CodeBlockSpan

class ThinkingParagraphGap(private val pixels: Int) : LineHeightSpan {
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int, fm: Paint.FontMetricsInt) {
        fm.descent += pixels
        fm.bottom += pixels
    }
}

/** Adds visual spacing without changing searchable text or character-based reading anchors. */
fun thinkingParagraphSpacing(text: Spanned, gapPixels: Int): SpannableStringBuilder = SpannableStringBuilder(text).apply {
    if (gapPixels > 0) {
        var index = 1
        while (index < text.length) {
            if (text[index] == '\n' && text[index - 1] == '\n' && text.getSpans(index, index + 1, CodeBlockSpan::class.java).isEmpty()) {
                setSpan(ThinkingParagraphGap(gapPixels), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            index++
        }
    }
}
