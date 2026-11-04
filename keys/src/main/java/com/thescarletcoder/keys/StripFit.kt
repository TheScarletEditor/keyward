package com.thescarletcoder.keys

/**
 * How the suggestion strip shares its width between its three words, so a long word is never cut where it matters.
 * Plain arithmetic on measured widths, no Android types, so the JVM tests cover it.
 *
 * 1. Words that fit in a third of the strip each: three equal slots, as before.
 * 2. Otherwise a long word takes room from the short ones next to it.
 * 3. Still too long: its text shrinks, down to [MIN_SCALE] of the usual size.
 * 4. Still too long: it's shortened in the middle, so its start and its end both show ("accommo…ation").
 * The word offered with "+ Save" is never shortened: if it can't be shown whole, it's offered as a plain word, without
 * "+ Save", so nothing is saved that couldn't be read first.
 */
object StripFit {
    const val MIN_SCALE = 0.72f
    /** The least a word next to a long "+ Save" word keeps, as a share of the strip. */
    private const val FLOOR = 0.2f

    /**
     * [edges]: the slots' left edges and the strip's right edge (4 values). [scale]: each word's text size against the
     * usual one. [whole]: the word fits in its slot at that size. [saveSlot]: where "+ Save" still shows, or -1.
     */
    class Fit(val edges: FloatArray, val scale: FloatArray, val whole: BooleanArray, val saveSlot: Int)

    /**
     * [natural]: each word's width at the usual text size, null for an empty slot. [pad]: the room each word keeps
     * around it. [chip]: the room "+ Save" takes beside its word.
     */
    fun fit(total: Float, natural: List<Float?>, saveSlot: Int, pad: Float, chip: Float): Fit {
        val save = if (natural.getOrNull(saveSlot) != null) saveSlot else -1
        val f = place(total, natural, save, pad, chip)
        return if (save < 0 || f.whole[save]) f else place(total, natural, -1, pad, chip)
    }

    private fun place(total: Float, natural: List<Float?>, save: Int, pad: Float, chip: Float): Fit {
        val extra = FloatArray(3) { pad + if (it == save) chip else 0f }
        val present = (0 until 3).filter { natural.getOrNull(it) != null }
        val need = FloatArray(3) { i -> natural.getOrNull(i)?.let { it + extra[i] } ?: 0f }
        val least = FloatArray(3) { i -> natural.getOrNull(i)?.let { it * MIN_SCALE + extra[i] } ?: 0f }
        val third = total / 3
        val widths = FloatArray(3)
        val sumNeed = present.sumOf { need[it].toDouble() }.toFloat()
        val sumLeast = present.sumOf { least[it].toDouble() }.toFloat()
        when {
            present.all { need[it] <= third } -> widths.fill(third)
            sumNeed <= total -> {
                // Everything fits at the usual size: each word its room, the rest shared out evenly.
                val share = (total - sumNeed) / present.size
                for (i in present) widths[i] = need[i] + share
            }
            sumLeast <= total -> {
                // Fits with smaller text: each word gives up room in proportion to how much it can shrink.
                val give = sumNeed - sumLeast
                for (i in present) widths[i] = least[i] + (total - sumLeast) * (need[i] - least[i]) / give
            }
            save >= 0 -> {
                // The "+ Save" word first, whole if it can be; the others share what's left and are shortened.
                val others = present.filter { it != save }
                widths[save] = minOf(least[save], total - FLOOR * total * others.size)
                val rest = total - widths[save]
                val sumOthers = others.sumOf { least[it].toDouble() }.toFloat()
                for (i in others) widths[i] = rest * least[i] / sumOthers
            }
            else -> for (i in present) widths[i] = total * least[i] / sumLeast
        }
        val scale = FloatArray(3) { 1f }
        val whole = BooleanArray(3) { true }
        for (i in present) {
            val n = natural[i]!!
            scale[i] = if (n <= 0f) 1f else ((widths[i] - extra[i]) / n).coerceIn(MIN_SCALE, 1f)
            whole[i] = n * scale[i] + extra[i] <= widths[i] + 0.5f
        }
        val edges = FloatArray(4)
        for (i in 0 until 3) edges[i + 1] = edges[i] + widths[i]
        return Fit(edges, scale, whole, save)
    }

    /** The slot under [x]; empty slots have no width, so they're never hit. */
    fun slotAt(edges: FloatArray, x: Float): Int {
        for (i in 0 until 3) if (x < edges[i + 1] && edges[i + 1] > edges[i]) return i
        return (2 downTo 0).first { edges[it + 1] > edges[it] || it == 0 }
    }
}
