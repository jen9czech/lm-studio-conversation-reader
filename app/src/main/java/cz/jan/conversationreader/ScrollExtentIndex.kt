package cz.jan.conversationreader

/** Prefix extents change only when item measurements/content change, never for each scrolled pixel. */
class ScrollExtentIndex {
    private var count = -1
    private var spacing = 0
    private var padding = 0
    private val measured = mutableMapOf<Int, Int>()
    private var prefix = longArrayOf(0)
    var total: Long = 0
        private set
    fun update(itemCount: Int, itemSpacing: Int, contentPadding: Int, measurements: List<Pair<Int, Int>>) {
        var dirty = false
        if (count != itemCount || spacing != itemSpacing || padding != contentPadding) {
            count = itemCount; spacing = itemSpacing; padding = contentPadding; measured.clear(); dirty = true
        }
        measurements.forEach { (index, size) -> if (index in 0 until count && measured[index] != size) { measured[index] = size; dirty = true } }
        if (!dirty) return
        val average = if (measured.isEmpty()) 96 else (measured.values.sumOf { it.toLong() } / measured.size).toInt().coerceAtLeast(1)
        prefix = LongArray(count + 1)
        repeat(count) { i -> prefix[i + 1] = prefix[i] + (measured[i] ?: average) + if (i < count - 1) spacing else 0 }
        total = prefix.last() + padding
    }
    fun position(index: Int, offset: Int) = prefix[index.coerceIn(prefix.indices)] + offset
    fun target(fraction: Float, viewport: Int): ScrollTarget {
        if (count <= 0 || fraction <= 0f) return ScrollTarget(0, 0)
        if (fraction >= 1f) return ScrollTarget(count - 1, Int.MAX_VALUE)
        val pixels = ((total - viewport).coerceAtLeast(0) * fraction).toLong()
        var low = 0; var high = count
        while (low < high) { val middle = (low + high + 1) / 2; if (prefix[middle] <= pixels) low = middle else high = middle - 1 }
        val index = low.coerceAtMost(count - 1)
        return ScrollTarget(index, (pixels - prefix[index]).coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
    }
}
