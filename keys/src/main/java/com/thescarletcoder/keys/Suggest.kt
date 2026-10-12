package com.thescarletcoder.keys

import android.content.Context
import kotlin.math.abs
import kotlin.math.min

/**
 * Offline dictionary (assets/en.bin, built from tools/dict/en.dict: AOSP LatinIME, Apache 2.0; and tools/dict/extra.dict, newer names) with a keyboard-aware corrector.
 * Everything runs on the tablet. Nothing typed is stored here; learned words live in [UserWords].
 */
class Dictionary private constructor(
    private val lower: Array<String>,
    private val display: Array<String>,
    private val freq: IntArray,
    private val offensive: BooleanArray,
) {
    /** Indices by word length (letters only, apostrophes ignored) for the corrector. */
    private val byLength: Array<IntArray> = run {
        val buckets = Array(26) { ArrayList<Int>() }
        lower.forEachIndexed { i, w -> buckets[letters(w).length.coerceAtMost(25)].add(i) }
        Array(26) { buckets[it].toIntArray() }
    }

    val size get() = lower.size

    fun isKnown(word: String): Boolean = find(word.lowercase()) >= 0

    private fun find(w: String): Int {
        var lo = 0; var hi = lower.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = lower[mid].compareTo(w)
            if (c == 0) return mid
            if (c < 0) lo = mid + 1 else hi = mid - 1
        }
        return -1
    }

    private fun lowerBound(prefix: String): Int {
        var lo = 0; var hi = lower.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (lower[mid] < prefix) lo = mid + 1 else hi = mid }
        return lo
    }

    data class Candidate(val word: String, val score: Float, val distance: Float, val freq: Int)

    /**
     * Ranked candidates for what's being typed: completions of the prefix plus close misspellings.
     * [userBoost] lets learned words rank higher; [userWords] are learned words not in the dictionary.
     */
    fun candidates(typed: String, userBoost: (String) -> Int, userWords: Collection<String>): List<Candidate> {
        val t = typed.lowercase()
        if (t.isEmpty()) return emptyList()
        val out = HashMap<String, Candidate>()
        fun offer(i: Int, distance: Float, completion: Boolean) {
            if (offensive[i] && lower[i] != t) return
            val f = freq[i] + userBoost(lower[i])
            val score = f - distance * DISTANCE_WEIGHT - (if (completion) (lower[i].length - t.length) * 6f else 0f)
            val prev = out[lower[i]]
            if (prev == null || prev.score < score) out[lower[i]] = Candidate(display[i], score, distance, f)
        }

        // Completions of the prefix (best few by frequency).
        val start = lowerBound(t)
        var i = start
        var seen = 0
        while (i < lower.size && lower[i].startsWith(t) && seen < 4000) { offer(i, 0f, true); i++; seen++ }

        // Close misspellings, judged by keyboard distance.
        val tl = letters(t)
        if (tl.length >= 2) {
            val maxD = if (tl.length <= 4) 1.0f else 2.0f
            for (len in (tl.length - 1)..(tl.length + 1)) {
                if (len !in 1..25) continue
                for (idx in byLength[len]) {
                    val w = letters(lower[idx])
                    if (w.isEmpty() || !firstLetterOk(tl[0], w[0])) continue
                    var d = distance(tl, w, maxD)
                    if (d == 0f && lower[idx] != t) d = 0.1f // only an apostrophe differs: "dont" -> "don't"
                    if (d <= maxD) offer(idx, d, false)
                }
            }
        }
        // Learned words that aren't in the dictionary.
        userWords.forEach { u ->
            if (u.startsWith(t)) out[u] = Candidate(u, 200f + userBoost(u), 0f, 200)
            else if (abs(u.length - t.length) <= 1 && distance(tl, letters(u), 1f) <= 1f) out[u] = Candidate(u, 150f + userBoost(u), 1f, 150)
        }
        return out.values.sortedByDescending { it.score }
    }

    /**
     * Correction to apply when the word ends, or null to leave it alone. Gentle on purpose.
     * [ranked]: this word's [candidates] if the caller already has them (the keyboard does, for the strip), so the
     * dictionary is searched once per keystroke, not twice.
     * [name]: the user capitalised this word themselves mid-sentence, so it's taken as a name and left alone
     * (the "I" words still get fixed).
     */
    fun autocorrect(
        typed: String, known: (String) -> Boolean, userBoost: (String) -> Int, userWords: Collection<String>,
        ranked: List<Candidate>? = null, name: Boolean = false,
    ): String? {
        val t = typed.lowercase()
        // The word "I" and its contractions: always capital, before the dictionary (which knows "i" as a word, and
        // ranks "in" above "I'm" for "im"). KB-3, D-020.
        I_WORDS[t]?.let { fixed -> return if (typed == fixed || typed == fixed.uppercase()) null else fixed }
        if (name || t.length < 2 || known(t)) return null
        if (t.any { !it.isLetter() && it != '\'' }) return null
        val best = (ranked ?: candidates(t, userBoost, userWords)).firstOrNull { it.distance > 0f } ?: return null
        val limit = if (letters(t).length <= 4) 1.0f else 2.0f
        return if (best.distance <= limit && best.freq >= MIN_FIX_FREQ) best.word else null
    }

    companion object {
        /** How much one typing slip costs against word commonness (dictionary frequencies are 0 to 255, log scale). */
        private const val DISTANCE_WEIGHT = 150f
        /** Only autocorrect to reasonably common words. */
        private const val MIN_FIX_FREQ = 50

        /** Typed (lowercase) to fixed. "id" and "ill" are words of their own, so "I'd" and "I'll" need the apostrophe. */
        private val I_WORDS = mapOf(
            "i" to "I", "im" to "I'm", "i'm" to "I'm", "ive" to "I've", "i've" to "I've", "i'll" to "I'll", "i'd" to "I'd",
        )

        fun load(context: Context): Dictionary = loadBinary(context.assets.open("en.bin"))

        /**
         * en.bin (built from tools/dict/en.dict at compile time): "SKD1", count, then entries already in lookup order,
         * each u8 length + lowercase UTF-8, u8 length + display form (0 = same as lowercase), u8 frequency, u8 flags.
         * One buffered pass, no sorting.
         */
        fun loadBinary(input: java.io.InputStream): Dictionary = java.io.DataInputStream(java.io.BufferedInputStream(input, 1 shl 16)).use { d ->
            val magic = ByteArray(4); d.readFully(magic)
            require(String(magic, Charsets.US_ASCII) == "SKD1") { "not a Scarlet Keys dictionary" }
            val n = d.readInt()
            val lower = arrayOfNulls<String>(n); val display = arrayOfNulls<String>(n)
            val freq = IntArray(n); val offensive = BooleanArray(n)
            val buf = ByteArray(255)
            for (i in 0 until n) {
                val ll = d.readUnsignedByte(); d.readFully(buf, 0, ll)
                val l = String(buf, 0, ll, Charsets.UTF_8)
                lower[i] = l
                val dl = d.readUnsignedByte()
                display[i] = if (dl == 0) l else { d.readFully(buf, 0, dl); String(buf, 0, dl, Charsets.UTF_8) }
                freq[i] = d.readUnsignedByte()
                offensive[i] = d.readUnsignedByte() and 1 != 0
            }
            @Suppress("UNCHECKED_CAST")
            Dictionary(lower as Array<String>, display as Array<String>, freq, offensive)
        }

        /** The text source (tools/dict/en.dict and extra.dict, one after the other). Used by tests; the app ships the binary form. */
        fun load(input: java.io.InputStream): Dictionary {
            val l = ArrayList<String>(170_000); val d = ArrayList<String>(170_000)
            val f = ArrayList<Int>(170_000); val o = ArrayList<Boolean>(170_000)
            input.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size >= 2) {
                        d += parts[0]; l += parts[0].lowercase(); f += parts[1].toIntOrNull() ?: 0
                        o += parts.getOrNull(2) == "o"
                    }
                }
            }
            // Keep lowercase order for binary search (file is sorted case-insensitively).
            val order = l.indices.sortedWith(compareBy({ l[it] }, { -f[it] }))
            return Dictionary(
                Array(order.size) { l[order[it]] }, Array(order.size) { d[order[it]] },
                IntArray(order.size) { f[order[it]] }, BooleanArray(order.size) { o[order[it]] },
            )
        }

        private fun letters(w: String) = if (w.indexOf('\'') < 0) w else w.replace("'", "")

        // QWERTY neighbours: a slip onto an adjacent key is a cheap mistake.
        private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        private val pos: Map<Char, Pair<Int, Int>> = buildMap {
            rows.forEachIndexed { r, s -> s.forEachIndexed { c, ch -> put(ch, r to c) } }
        }
        private fun adjacent(a: Char, b: Char): Boolean {
            val p = pos[a] ?: return false; val q = pos[b] ?: return false
            return abs(p.first - q.first) <= 1 && abs(p.second - q.second) <= 1
        }
        private fun firstLetterOk(a: Char, b: Char) = a == b || adjacent(a, b)

        /** Weighted Damerau-Levenshtein (keyboard- and double-letter-aware) with an early exit once [max] is exceeded. */
        fun distance(a: String, b: String, max: Float): Float {
            val n = a.length; val m = b.length
            if (abs(n - m) > max) return max + 1
            var prev2 = FloatArray(m + 1)
            var prev = FloatArray(m + 1) { it.toFloat() }
            var cur = FloatArray(m + 1)
            for (i in 1..n) {
                cur[0] = i.toFloat()
                var rowMin = cur[0]
                for (j in 1..m) {
                    val sub = when {
                        a[i - 1] == b[j - 1] -> 0f
                        adjacent(a[i - 1], b[j - 1]) -> 0.6f
                        else -> 1f
                    }
                    // Missing or extra doubled letter ("helo", "tomorow", "untill") is a very common slip.
                    val del = if (i > 1 && a[i - 1] == a[i - 2]) 0.25f else 1f
                    val ins = if (j > 1 && b[j - 1] == b[j - 2]) 0.25f else 1f
                    var v = min(min(prev[j] + del, cur[j - 1] + ins), prev[j - 1] + sub)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = min(v, prev2[j - 2] + 0.8f)
                    cur[j] = v
                    if (v < rowMin) rowMin = v
                }
                if (rowMin > max) return max + 1
                val t = prev2; prev2 = prev; prev = cur; cur = t
            }
            return prev[m]
        }
    }
}
