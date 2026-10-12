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
        /**
         * Make a new key and use it for every seal from now on; older keys stay, so [open] can still read a file sealed
         * with one of them (A-D1). True when the new key exists.
         */
        fun rotate(): Boolean = false
        /** Delete every key but the newest. Only called once the list is saved with the newest. */
        fun retireOld() {}
        /** More than one key exists (a rotation in this run or an earlier one), so [retireOld] has work to do. */
        fun hasOldKeys(): Boolean = false
    }

    /** The file can't be read, now or later: wrong or lost key, damaged or cut-short file. */
    class Unreadable(cause: Throwable? = null) : Exception(cause)

    /** REFUSED_NEWER: a list from a newer format; REFUSED: an unreadable list that couldn't be moved aside. */
    private enum class State { NOT_LOADED, LOADED, REFUSED, REFUSED_NEWER }

    /** What Settings tells the user about the list (Q2, Q12). */
    enum class Notice { NONE, SET_ASIDE, NEWER, NOT_LEARNING }

    /*
     * Two locks (R10): `this` guards memory and is held only briefly, so the keyboard's main thread (record, isKnown,
     * boost, learned) never waits on the disk or the Keystore; [io] guards the files and the Keystore. Always [io]
     * first, then `this`, never the other way. load, save and clear all hold [io], so a save can't run between Forget's
     * deletes and write old words back (Forget 2).
     */
    private val io = Any()

    /** In the order words were last used (oldest first), so trimming drops the oldest of the least used. */
    private val counts = LinkedHashMap<String, Int>()
    private var dirty = false
    /** Bumped on every change to [counts]: a save clears [dirty] only if nothing changed while it was writing. */
    private var generation = 0L
    @Volatile private var state = State.NOT_LOADED
    private val refused get() = state == State.REFUSED || state == State.REFUSED_NEWER
    /** Passing errors in a row while unlocked; at [MAX_PASSING] the file is treated as unreadable (Q13). Under [io]. */
    private var passingFailures = 0
    /** Saves whose seal failed in a row; at [MAX_PASSING] the key is taken as broken and rotated. Under [io]. */
    private var saveFailures = 0
    /** The key is replaced at most once per run. Under [io]. */
    private var keyReplaced = false

    private val file get() = File(dir, "words.enc")
    private val tmp get() = File(dir, "words.tmp")
    /** Null when the folder couldn't be listed (an I/O error): Forget must not report success then (A-D3). */
    private fun setAsideFiles() =
        dir.listFiles { f -> f.name == "words.unreadable" || f.name.startsWith("words.unreadable-") }?.toList()

    /** A word counts as "known" once used twice. */
    @Synchronized fun isKnown(word: String) = (counts[word.lowercase()] ?: 0) >= 2

    @Synchronized fun boost(word: String) = ((counts[word.lowercase()] ?: 0) * 8).coerceAtMost(80)

    private var learnedCache: List<String> = emptyList()
    private var learnedAt = -1L

    /** Learned words (used twice or more), for suggestions. Rebuilt only when the list changed, not on every key. */
    val learned: Collection<String> @Synchronized get() {
        if (learnedAt != generation) { learnedCache = counts.filterValues { it >= 2 }.keys.toList(); learnedAt = generation }
        return learnedCache
    }

    val count: Int @Synchronized get() = counts.size

    /** A list that couldn't be read was set aside (Settings says so, Q2). Touches the disk: not on the main thread. */
    val hasSetAside: Boolean get() = !setAsideFiles().isNullOrEmpty()

    /** Touches the disk: not on the main thread. */
    val notice: Notice get() = synchronized(io) {
        when {
            state == State.REFUSED_NEWER -> Notice.NEWER
            state == State.REFUSED -> Notice.NOT_LEARNING
            hasSetAside -> Notice.SET_ASIDE
            else -> Notice.NONE
        }
    }

    /** True once the file was read (or there was none): until then nothing is saved over it. */
    internal val isLoaded: Boolean get() = state == State.LOADED

    @Synchronized
    fun record(word: String) {
        if (refused) return
        val w = word.lowercase()
        if (!learnable(w)) return
        // Moved to the end: most recently used. The list may pass the cap until the next save trims it (off the typing path).
        counts[w] = ((counts.remove(w) ?: 0) + 1).coerceAtMost(MAX_COUNT)
        generation++
        dirty = true
    }

    /**
     * Read the file. A passing error (locked device, busy key store) leaves everything as it was, to try again with the
     * next field (2). An unreadable file is set aside and a new list starts (1, 3). A newer format is left alone and
     * nothing is learned or saved this run (7). Words recorded before the file could be read are kept and added to it.
     * The file is read and opened under [io] only; the words are merged under the memory lock.
     */
    fun load() = synchronized(io) {
        if (state != State.NOT_LOADED || !unlocked()) return
        // A leftover save in progress is never read (4).
        tmp.delete()
        if (!file.exists()) { synchronized(this) { state = State.LOADED }; return }
        // Far larger than 5,000 words could ever be (about 200 KB): not ours, set aside unread (R7).
        if (file.length() > MAX_FILE_BYTES) { setAsideFile(); return }
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
        if (format > FORMAT) {
            synchronized(this) { state = State.REFUSED_NEWER; counts.clear(); generation++; dirty = false }
            return
        }
        val skip = if (format == 0) 0 else 1
        val text = String(body, skip, body.size - skip, Charsets.UTF_8)
        val fromFile = LinkedHashMap<String, Int>()
        text.lineSequence().forEach { line ->
            val i = line.lastIndexOf('\t')
            val n = if (i > 0) line.substring(i + 1).toIntOrNull() else null
            // The same rule as typing (A-D2): a word an older or newer build wrote with stray characters (a 0.2.1 build
            // reading this format keeps the format byte in the first word) is cleaned or skipped, never suggested.
            val w = if (i > 0) line.substring(0, i).filter { it >= ' ' }.lowercase() else ""
            if (n != null && n > 0 && learnable(w)) fromFile[w] = ((fromFile[w] ?: 0) + n).coerceAtMost(MAX_COUNT)
            // a bad count line is skipped, never read as 0 (3)
        }
        synchronized(this) {
            // The file's words first (they're older), then words typed before it could be read, on top.
            val typed = LinkedHashMap(counts)
            counts.clear()
            counts.putAll(fromFile)
            typed.forEach { (w, n) -> counts[w] = ((counts.remove(w) ?: 0) + n).coerceAtMost(MAX_COUNT) }
            trim()
            generation++
            state = State.LOADED
        }
    }

    /**
     * Write the list: a temp file, then a checked rename over the old one (4). Only after the old file was read (2).
     * The list is copied under the memory lock; sealing and writing hold only [io], so typing never waits on them.
     */
    fun save() = synchronized(io) {
        if (!unlocked()) return
        // Only a copy under the memory lock; the text and bytes are built after it's released.
        val words: Array<String>
        val uses: IntArray
        val gen: Long
        synchronized(this) {
            if (!dirty || state != State.LOADED) return
            trim()
            words = counts.keys.toTypedArray(); uses = counts.values.toIntArray(); gen = generation
        }
        val body = java.io.ByteArrayOutputStream(words.size * 12 + 1).apply {
            write(FORMAT)
            words.forEachIndexed { i, w -> if (i > 0) write('\n'.code); write("$w\t${uses[i]}".toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        when (write(body)) {
            Write.OK -> { saved(gen); return }
            // The seal worked, so the key is fine: a full disk or a failed rename says nothing about it (R6).
            Write.DISK_FAILED -> { saveFailures = 0; return }
            Write.SEAL_FAILED -> {}
        }
        // A key that fails to seal every time is broken. Safe rotation (A-D1, Q2): a new key next to the old one, the
        // list sealed with it, and the old key deleted only once that file is in place. Reading tries every key, so a
        // failure at any step strands nothing.
        if (++saveFailures >= MAX_PASSING && !keyReplaced) {
            saveFailures = 0
            keyReplaced = true
            if (runCatching { sealer.rotate() }.getOrDefault(false) && write(body) == Write.OK) saved(gen)
        }
    }

    /** The list as of [gen] is on disk: clean unless it changed meanwhile; older keys go (they hold nothing now). */
    private fun saved(gen: Long) {
        saveFailures = 0
        synchronized(this) { if (generation == gen) dirty = false }
        if (runCatching { sealer.hasOldKeys() }.getOrDefault(false)) runCatching { sealer.retireOld() }
    }

    private enum class Write { OK, SEAL_FAILED, DISK_FAILED }

    /** Sealed, then a temp file, synced, then a checked rename. Under [io]. */
    private fun write(body: ByteArray): Write {
        val sealed = try { sealer.seal(body) } catch (e: Exception) { return Write.SEAL_FAILED }
        return try {
            // On the disk before the rename, so a power cut can't leave an empty list in its place.
            java.io.FileOutputStream(tmp).use { out -> out.write(sealed); out.fd.sync() }
            if (move(tmp, file)) Write.OK else { tmp.delete(); Write.DISK_FAILED }
        } catch (e: Exception) {
            tmp.delete() // stays dirty: the next save tries again
            Write.DISK_FAILED
        }
    }

    /**
     * "Forget learned words": every copy on disk, then memory, under [io] the whole time, so a keyboard save can't run in
     * between and write old words back (Forget 2). True only when every copy is gone (Forget 1). The list itself goes
     * last, so a failed delete always leaves it on disk, with the words and count as they were (L3-1).
     */
    fun clear(): Boolean = synchronized(io) {
        val aside = setAsideFiles()
        val gone = aside != null && (listOf(tmp) + aside + file).all { !it.exists() || delete(it) }
        synchronized(this) {
            if (!gone) { if (state == State.LOADED) dirty = true; return false }
            counts.clear()
            generation++
            dirty = false
            state = State.LOADED
        }
        passingFailures = 0
        true
    }

    private fun learnable(w: String) = w.length in 2..MAX_LETTERS && w.all(Words::isWordChar)

    private fun setAsideFile() {
        // Never overwrite a list set aside earlier: the next free name.
        val target = generateSequence(1) { it + 1 }.map { n -> File(dir, if (n == 1) "words.unreadable" else "words.unreadable-$n") }
            .first { !it.exists() }
        // If it can't be moved, nothing is learned or saved this run, so the file is never written over.
        val moved = move(file, target)
        synchronized(this) { state = if (moved) State.LOADED else State.REFUSED }
    }

    /**
     * Down to [CAP] words: the least used go first, and among those the longest unused (6). Usually one or a few over,
     * so a pass for the lowest count and a removal in last-used order, no sort.
     */
    private fun trim() {
        while (counts.size > CAP) {
            val low = counts.values.min()
            val iter = counts.entries.iterator()
            while (counts.size > CAP && iter.hasNext()) if (iter.next().value == low) iter.remove()
            generation++ // the cached learned list rebuilds
        }
    }

    companion object {
        /** The format this build writes. 0 was 0.2.1's (no format byte). */
        const val FORMAT = 1
        /** Most words kept; the least used go first (6). */
        const val CAP = 5000
        const val MAX_LETTERS = 30
        /** A use count never goes past this (no overflow from a crafted file or years of use, R8). */
        const val MAX_COUNT = 1_000_000
        /** A list file larger than this isn't ours (5,000 words is about 200 KB): set aside unread (R7). */
        const val MAX_FILE_BYTES = 1L shl 20
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
