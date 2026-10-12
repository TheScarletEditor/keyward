package com.thescarletcoder.keys

import java.io.File

/**
 * Learned words and their file: the format and every rule from docs/KEYS-0.2.2-PLAN.md "What can go wrong" (learned
 * words 1 to 7). Plain Kotlin, so the JVM tests run it with a fake [Sealer]; [UserWords] adds the Android Keystore.
 *
 * Files in [dir]: `words.enc` (the list), `words.tmp` (a save in progress), `words.unreadable` (a list that couldn't be
 * read, set aside and never overwritten, Q2; a second one becomes `words.unreadable-2`, and so on). Inside the encryption: one format byte, then "word<TAB>count" lines.
 * Files from 0.2.1 have no format byte (format 0).
 */
class WordStore(
    dirProvider: () -> File,
    private val sealer: Sealer,
    private val unlocked: () -> Boolean = { true },
    private val move: (File, File) -> Boolean = { from, to -> from.renameTo(to) },
    private val delete: (File) -> Boolean = { it.delete() },
) {
    /** Plain directory, for tests. */
    constructor(
        dir: File, sealer: Sealer, unlocked: () -> Boolean = { true },
        move: (File, File) -> Boolean = { from, to -> from.renameTo(to) }, delete: (File) -> Boolean = { it.delete() },
    ) : this({ dir }, sealer, unlocked, move, delete)

    /** Found on first use, which is always a file operation off the main thread (asking Android for it can touch the disk). */
    private val dir by lazy(dirProvider)

    /** Encryption for the file. [open] throws [Unreadable] when the data can never be read, anything else when it may be passing. */
    interface Sealer {
        fun seal(plain: ByteArray): ByteArray
        fun open(sealed: ByteArray): ByteArray
        /** Replace the key with a new one. Only called when no readable file depends on it. */
        fun reset() {}
    }

    /** The file can't be read, now or later: wrong or lost key, damaged or cut-short file. */
    class Unreadable(cause: Throwable? = null) : Exception(cause)

    private enum class State { NOT_LOADED, LOADED, REFUSED }

    /** What Settings tells the user about the list (Q2, Q12). */
    enum class Notice { NONE, SET_ASIDE, NEWER, NOT_LEARNING }

    /** In the order words were last used (oldest first), so trimming drops the oldest of the least used. */
    private val counts = LinkedHashMap<String, Int>()
    private var dirty = false
    private var state = State.NOT_LOADED
    /** Why the store refused: a newer format, or an unreadable file that couldn't be moved aside. */
    private var refusedNewer = false
    /** Passing errors in a row while unlocked; at [MAX_PASSING] the file is treated as unreadable (Q13). */
    private var passingFailures = 0
    /** Saves that failed in a row; at [MAX_PASSING] the key itself is taken as broken and replaced. */
    private var saveFailures = 0
    /** The key is replaced at most once per run. */
    private var keyReplaced = false

    private val file get() = File(dir, "words.enc")
    private val tmp get() = File(dir, "words.tmp")
    private fun setAsideFiles() = dir.listFiles { f -> f.name == "words.unreadable" || f.name.startsWith("words.unreadable-") }.orEmpty().toList()

    /** A word counts as "known" once used twice. */
    @Synchronized fun isKnown(word: String) = (counts[word.lowercase()] ?: 0) >= 2

    @Synchronized fun boost(word: String) = ((counts[word.lowercase()] ?: 0) * 8).coerceAtMost(80)

    /** Learned words (used twice or more), for suggestions. */
    val learned: Collection<String> @Synchronized get() = counts.filterValues { it >= 2 }.keys.toList()

    val count: Int @Synchronized get() = counts.size

    /** A list that couldn't be read was set aside (Settings says so, Q2). */
    val hasSetAside: Boolean get() = setAsideFiles().isNotEmpty()

    val notice: Notice @Synchronized get() = when {
        state == State.REFUSED && refusedNewer -> Notice.NEWER
        state == State.REFUSED -> Notice.NOT_LEARNING
        hasSetAside -> Notice.SET_ASIDE
        else -> Notice.NONE
    }

    /** True once the file was read (or there was none): until then nothing is saved over it. */
    val isLoaded: Boolean @Synchronized get() = state == State.LOADED

    @Synchronized
    fun record(word: String) {
        if (state == State.REFUSED) return
        val w = word.lowercase()
        if (w.length < 2 || w.length > MAX_LETTERS || w.any { !it.isLetter() && it != '\'' }) return
        // Moved to the end: most recently used. The list may pass the cap until the next save trims it (off the typing path).
        counts[w] = (counts.remove(w) ?: 0) + 1
        dirty = true
    }

    /**
     * Read the file. A passing error (locked device, busy key store) leaves everything as it was, to try again with the
     * next field (2). An unreadable file is set aside and a new list starts (1, 3). A newer format is left alone and
     * nothing is learned or saved this run (7). Words recorded before the file could be read are kept and added to it.
     */
    @Synchronized
    fun load() {
        if (state != State.NOT_LOADED || !unlocked()) return
        // A leftover save in progress is never read (4).
        if (tmp.exists()) tmp.delete()
        if (!file.exists()) { state = State.LOADED; return }
        val body = try {
            sealer.open(file.readBytes())
        } catch (e: Unreadable) {
            setAsideFile()
            return
        } catch (e: Exception) {
            // Passing: stay NOT_LOADED, nothing saved, the next field retries; after a few in a row it isn't passing.
            if (++passingFailures >= MAX_PASSING) setAsideFile()
            return
        }
        passingFailures = 0
        val format = formatOf(body)
        if (format < 0) { setAsideFile(); return }
        if (format > FORMAT) { state = State.REFUSED; refusedNewer = true; counts.clear(); dirty = false; return }
        val text = String(body, if (format == 0) 0 else 1, body.size - (if (format == 0) 0 else 1), Charsets.UTF_8)
        // The file's words first (they're older), then words typed before it could be read, on top.
        val typed = LinkedHashMap(counts)
        counts.clear()
        text.lineSequence().forEach { line ->
            val i = line.lastIndexOf('\t')
            val n = if (i > 0) line.substring(i + 1).toIntOrNull() else null
            if (i > 0 && n != null && n > 0) {
                val w = line.substring(0, i)
                counts[w] = (counts[w] ?: 0) + n
            } // a bad count line is skipped, never read as 0 (3)
        }
        typed.forEach { (w, n) -> counts[w] = (counts.remove(w) ?: 0) + n }
        trim()
        state = State.LOADED
    }

    /** Write the list: a temp file, then a checked rename over the old one (4). Only after the old file was read (2). */
    @Synchronized
    fun save() {
        if (!dirty || state != State.LOADED || !unlocked()) return
        trim()
        val body = byteArrayOf(FORMAT.toByte()) + counts.entries.joinToString("\n") { "${it.key}\t${it.value}" }.toByteArray(Charsets.UTF_8)
        if (write(body)) { saveFailures = 0; return }
        // A key that fails every time is broken. Everything in the file is in memory (the file was read), so a new key
        // loses nothing, provided the list is written with it before this call ends: the old file can't be read after.
        if (++saveFailures >= MAX_PASSING && !keyReplaced) {
            saveFailures = 0
            keyReplaced = true
            if (runCatching { sealer.reset() }.isSuccess) write(body)
        }
    }

    /** Temp file, synced, then a checked rename. True when the new list is in place; dirty is cleared then. */
    private fun write(body: ByteArray): Boolean = try {
        // On the disk before the rename, so a power cut can't leave an empty list in its place.
        java.io.FileOutputStream(tmp).use { out -> out.write(sealer.seal(body)); out.fd.sync() }
        if (move(tmp, file)) { dirty = false; true } else { tmp.delete(); false }
    } catch (e: Exception) {
        tmp.delete() // stays dirty: the next save tries again
        false
    }

    /**
     * "Forget learned words": every copy on disk, then memory, all under the same lock, so a keyboard save can't run in
     * between and write old words back (Forget 2). True only when every copy is gone (Forget 1). The list itself goes
     * last, so a failed delete always leaves it on disk, with the words and count as they were (L3-1).
     */
    @Synchronized
    fun clear(): Boolean {
        val gone = (listOf(tmp) + setAsideFiles() + file).all { !it.exists() || delete(it) }
        if (!gone) { if (state == State.LOADED) dirty = true; return false }
        counts.clear()
        dirty = false
        state = State.LOADED; refusedNewer = false; passingFailures = 0
        return true
    }

    private fun setAsideFile() {
        // Never overwrite a list set aside earlier: the next free name.
        val target = generateSequence(1) { it + 1 }.map { n -> File(dir, if (n == 1) "words.unreadable" else "words.unreadable-$n") }
            .first { !it.exists() }
        // If it can't be moved, nothing is learned or saved this run, so the file is never written over.
        state = if (move(file, target)) State.LOADED else State.REFUSED
        refusedNewer = false
    }

    /** Down to [CAP] words: the least used go first, and among those the longest unused (6). */
    private fun trim() {
        if (counts.size <= CAP) return
        val order = counts.keys.withIndex().associate { it.value to it.index }
        counts.entries.sortedWith(compareBy({ it.value }, { order[it.key] })).take(counts.size - CAP).map { it.key }.forEach { counts.remove(it) }
    }

    companion object {
        /** The format this build writes. 0 was 0.2.1's (no format byte). */
        const val FORMAT = 1
        /** Most words kept; the least used go first (6). */
        const val CAP = 5000
        const val MAX_LETTERS = 30
        /** Passing errors in a row (while unlocked) before the file counts as unreadable (Q13), or the key as broken. */
        const val MAX_PASSING = 3

        /**
         * 0.2.1 wrote text straight away (a word starts with a letter or apostrophe, a non-ASCII letter's first UTF-8
         * byte is negative); later formats start with a byte from 1 to 31. -1 (never written by any build) for 0.
         */
        fun formatOf(body: ByteArray): Int = when {
            body.isEmpty() || body[0] >= 32 || body[0] < 0 -> 0
            body[0].toInt() == 0 -> -1
            else -> body[0].toInt()
        }
    }
}
