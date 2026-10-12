package com.thescarletcoder.keys

/**
 * What ships in the APK and the license each part comes under, for Settings' "Open source licenses".
 * [asset] is the file inside the APK: `licenses/` is copied from the repo's LICENSE and licenses folder at build time
 * (:keys:copyLicenses), the word list notice lives in src/main/assets. LicensesTest keeps this list, the build's copy
 * and the README's Credits in step.
 */
object Licenses {
    data class Part(val name: String, val license: String, val asset: String)

    val parts = listOf(
        Part("Scarlet Keys", "GNU General Public License 3.0", "licenses/LICENSE"),
        Part("Jetpack Compose, AndroidX, the Kotlin standard library and kotlinx.coroutines", "Apache License 2.0", "licenses/Apache-2.0.txt"),
        Part("English word list, from the Android Open Source Project", "Apache License 2.0", "DICTIONARY-NOTICE.txt"),
        Part("Inter font", "SIL Open Font License 1.1", "licenses/Inter-OFL.txt"),
        Part("Fraunces font", "SIL Open Font License 1.1", "licenses/Fraunces-OFL.txt"),
    )

    /** Texts already read, so a rotation shows the page at once and its scroll position holds (L1b-1). */
    val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * License files are wrapped at about 76 characters for a terminal; on a phone each line would wrap again.
     * Joins the lines of each paragraph (blank lines separate paragraphs) so the text flows. A heading line (letters,
     * none lowercase, like "PREAMBLE") at the start of a paragraph keeps its own line, so the last line of an all-caps
     * paragraph ("SUCH DAMAGES.") stays with it; divider lines of dashes are dropped.
     */
    fun reflow(text: String): String {
        val paragraphs = ArrayList<String>()
        val current = StringBuilder()
        fun flush() { if (current.isNotEmpty()) { paragraphs += current.toString(); current.clear() } }
        text.replace("\r\n", "\n").split("\n").forEach { raw ->
            val line = raw.trim().replace(Regex("[ \t]{2,}"), " ")
            when {
                line.isEmpty() -> flush()
                line.all { it == '-' } -> flush()
                current.isEmpty() && isHeading(line) -> paragraphs += line
                else -> { if (current.isNotEmpty()) current.append(' '); current.append(line) }
            }
        }
        flush()
        return paragraphs.joinToString("\n\n")
    }

    /**
     * True for "PREAMBLE", "PERMISSION & CONDITIONS" or "SIL OPEN FONT LICENSE Version 1.1": short, and its first two
     * words with letters have no lowercase ("1." and "(c)" alone are not headings).
     */
    private fun isHeading(line: String): Boolean {
        if (line.length > 60) return false
        val words = line.split(' ').filter { w -> w.any { it.isLetter() } }
        return words.isNotEmpty() && words.take(2).all { w -> w.none { it.isLowerCase() } }
    }
}
