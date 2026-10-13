package com.thescarletcoder.keys

import java.io.File

/**
 * Learned words and their file: the format and every rule from docs/KEYS-0.2.2-PLAN.md "What can go wrong" (learned
 * words 1 to 7). Plain Kotlin, so the JVM tests run it with a fake [Sealer]; [UserWords] adds the Android Keystore.
 *
 * Files in [dir]: `words.enc` (the list), `words.tmp` (a save in progress), `words.unreadable` (a list that couldn't be
 * read, set aside and never overwritten, Q2; a second one becomes `words.unreadable-2`, and so on).
 *
 * Inside the encryption (format 3, Q32): one format byte, then "~salt" (hex), then the learned and saved words A to Z as
 * "word<TAB>count", with "<TAB>s" after a word the user saved, then words used once or twice only as fingerprints,
 * "#fingerprint<TAB>count", in fingerprint order. A fingerprint is a salted hash: typing the word again finds it, but
 * the file can't be read back to the word, and nothing in the file says in what order anything was typed.
 * Older formats are read and rewritten as format 3 at the next save: 0.2.1's has no format byte (format 0), format 1
 * has no saved mark, format 2 kept every word as text in last-used order.
 *
 * Two kinds of word (Q29): learned, used [LEARNED] times or more; saved by the user ("+ Save", taking a fix back, or
 * the Your words page), kept until removed and never trimmed. Words used less are kept only as fingerprints.
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
        /** The newest key is an older kind (one that works while the phone is locked, Q33): rotate to a current one. */
        fun needsUpgrade(): Boolean = false
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

    /**
     * Learned and saved words with their uses, in the order last used in this run (oldest first; the file keeps no
     * order), so trimming drops the oldest of the least used.
     */
    private val counts = LinkedHashMap<String, Int>()
    /** Words the user saved: always known, never trimmed (Q29). Every one is also in [counts]. */
    private val saved = HashSet<String>()
    /** Words used once or twice, by fingerprint only (Q32), in the order last used in this run. */
    private val pending = LinkedHashMap<String, Int>()
    /** This list's salt for fingerprints; null until the file is read (and again after [unload]). */
    private var salt: ByteArray? = null
    /** Words typed or saved before the file could be read: memory only, merged in when it is (no salt before then). */
    private val early = LinkedHashMap<String, Int>()
    private val earlySaved = HashSet<String>()
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

    private fun listed(w: String, n: Int) = n >= LEARNED || w in saved

    /** A word counts as "known" once used [LEARNED] times, or saved. */
    @Synchronized fun isKnown(word: String) = word.lowercase().let { w -> listed(w, counts[w] ?: 0) }

    @Synchronized fun isSaved(word: String) = word.lowercase() in saved

    @Synchronized fun boost(word: String): Int {
        val w = word.lowercase()
        return if (w in saved) MAX_BOOST else ((counts[w] ?: 0) * 8).coerceAtMost(MAX_BOOST)
    }

    private var learnedCache: List<String> = emptyList()
    private var learnedAt = -1L

    /** Learned and saved words, for suggestions. Rebuilt only when the list changed, not on every key. */
    val learned: Collection<String> @Synchronized get() {
        if (learnedAt != generation) { learnedCache = counts.filter { (w, n) -> listed(w, n) }.keys.toList(); learnedAt = generation }
        return learnedCache
    }

    private var savedCache: List<String> = emptyList()
    private var savedAt = -1L

    /** Saved words only: what the keyboard uses where learning is off (S7-4). */
    val savedWords: Collection<String> @Synchronized get() {
        if (savedAt != generation) { savedCache = saved.toList(); savedAt = generation }
        return savedCache
    }

    /** Learned plus saved: what Settings counts (words used once or twice aren't counted, Q29). */
    val count: Int get() = learned.size

    /** Every word held, listed or not: Forget all reaches these too (S7-1). */
    val size: Int @Synchronized get() = counts.size + pending.size + early.size

    /** One row of the Your words page. */
    data class Entry(val word: String, val uses: Int, val saved: Boolean)

    /** Learned and saved words, alphabetically, for the Your words page. */
    @Synchronized fun entries(): List<Entry> =
        counts.filter { (w, n) -> listed(w, n) }.map { (w, n) -> Entry(w, n, w in saved) }.sortedBy { it.word }

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

    /** False while the list is refused (a newer format, or unreadable and not moved aside): nothing can be saved. */
    val canKeep: Boolean get() = !refused

    /** True once the file was read (or there was none): until then nothing is saved over it. */
    internal val isLoaded: Boolean get() = state == State.LOADED

    @Synchronized
    fun record(word: String) {
        if (refused) return
        val w = word.lowercase()
        if (!learnable(w)) return
        // Moved to the end: most recently used. The list may pass the cap until the next save trims it (off the typing path).
        if (state != State.LOADED) early[w] = ((early.remove(w) ?: 0) + 1).coerceAtMost(MAX_COUNT)
        else addUses(w, 1)
        generation++
        dirty = true
    }

    /** [n] more uses of [w]: a learned word counts on; a fingerprint becomes the word itself at [LEARNED] uses (Q32). */
    private fun addUses(w: String, n: Int) {
        val had = counts.remove(w)
        if (had != null) { counts[w] = (had + n).coerceAtMost(MAX_COUNT); return }
        val f = fingerprint(w)
        val uses = ((pending.remove(f) ?: 0) + n).coerceAtMost(MAX_COUNT)
        if (uses >= LEARNED) counts[w] = uses else pending[f] = uses
    }

    /** Save a word: known from now on and kept until removed (Q28, Q29). False when it can't be a word or nothing is kept. */
    @Synchronized
    fun keep(word: String): Boolean {
        if (refused) return false
        val w = word.lowercase()
        if (!learnable(w)) return false
        if (state != State.LOADED) earlySaved += w else keepLoaded(w)
        generation++
        dirty = true
        return true
    }

    private fun keepLoaded(w: String) {
        val uses = pending.remove(fingerprint(w)) ?: 0
        counts[w] = ((counts.remove(w) ?: 0) + uses).coerceIn(1, MAX_COUNT)
        saved += w
    }

    /** Take a word off the list, saved or learned; it's learned again only by being used [LEARNED] more times. */
    @Synchronized
    fun remove(word: String) {
        val w = word.lowercase()
        var had = counts.remove(w) != null
        had = saved.remove(w) || had
        had = early.remove(w) != null || had
        had = earlySaved.remove(w) || had
        if (state == State.LOADED) had = pending.remove(fingerprint(w)) != null || had
        if (!had) return
        generation++
        dirty = true
    }

    /** SHA-256 of this list's salt and the word, first 16 bytes, as hex. Under the memory lock, after the file is read. */
    private fun fingerprint(w: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(salt ?: error("no salt before the list is read"))
        val d = md.digest(w.toByteArray(Charsets.UTF_8))
        return buildString(32) { for (i in 0 until 16) { val b = d[i].toInt() and 0xFF; append(HEX[b ushr 4]); append(HEX[b and 15]) } }
    }

    /**
     * Drop the words from memory (the phone locked, Q33). Only when everything is on disk: unsaved words stay until
     * the next unlock saves them. They're read again, decrypted, at the next field after unlock.
     */
    fun unload(): Boolean = synchronized(io) {
        synchronized(this) {
            if (state != State.LOADED || dirty) return false
            counts.clear(); saved.clear(); pending.clear(); salt = null
            generation++
            state = State.NOT_LOADED
        }
        passingFailures = 0
        true
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
        if (!file.exists()) { startLoaded(emptyMap(), emptySet(), emptyMap(), newSalt(), rewrite = false); return }
        // Far larger than 5,000 words could ever be (about 200 KB): not ours, set aside unread (R7).
        if (file.length() > MAX_FILE_BYTES) { setAsideFile(); return }
        val body = try {
            sealer.open(file.readBytes())
        } catch (e: Unreadable) {
            setAsideFile()
            return
        } catch (e: Exception) {
            // Passing: stay NOT_LOADED, nothing saved, the next field retries; after a few in a row it isn't passing.
            // The phone locking mid-read isn't a failure of the file (the key refuses while locked, Q33).
            if (unlocked() && ++passingFailures >= MAX_PASSING) setAsideFile()
            return
        }
        passingFailures = 0
        val format = formatOf(body)
        if (format < 0) { setAsideFile(); return }
        if (format > FORMAT) {
            synchronized(this) {
                state = State.REFUSED_NEWER; counts.clear(); saved.clear(); pending.clear(); early.clear(); earlySaved.clear()
                generation++; dirty = false
            }
            return
        }
        val skip = if (format == 0) 0 else 1
        val text = String(body, skip, body.size - skip, Charsets.UTF_8)
        val fromFile = LinkedHashMap<String, Int>()
        val savedInFile = HashSet<String>()
        val prints = LinkedHashMap<String, Int>()
        var fileSalt: ByteArray? = null
        text.lineSequence().forEach { line ->
            if (format >= 3 && line.startsWith("~")) { fileSalt = unhex(line.substring(1)); return@forEach }
            if (format >= 3 && line.startsWith("#")) {
                val i = line.indexOf('\t')
                val f = if (i == 33) line.substring(1, i) else ""
                val n = if (i > 0) line.substring(i + 1).toIntOrNull() else null
                if (n != null && n > 0 && unhex(f) != null) prints[f] = ((prints[f] ?: 0) + n).coerceAtMost(MAX_COUNT)
                return@forEach
            }
            // Format 2 and later may add "<TAB>s" after the count; before that the count was the last field.
            val mark = format >= 2 && line.endsWith("\ts")
            val l = if (mark) line.substring(0, line.length - 2) else line
            val i = l.lastIndexOf('\t')
            val n = if (i > 0) l.substring(i + 1).toIntOrNull() else null
            // The same rule as typing (A-D2): a word an older or newer build wrote with stray characters (a 0.2.1 build
            // reading this format keeps the format byte in the first word) is cleaned or skipped, never suggested.
            val w = if (i > 0) l.substring(0, i).filter { it >= ' ' }.lowercase() else ""
            if (n != null && n > 0 && learnable(w)) {
                fromFile[w] = ((fromFile[w] ?: 0) + n).coerceAtMost(MAX_COUNT)
                if (mark) savedInFile += w
            }
            // a bad count line is skipped, never read as 0 (3)
        }
        // A list without a salt (any older format, or a damaged line) gets a new one; fingerprints made with another salt
        // could never match, so they go.
        val keptSalt = fileSalt?.takeIf { it.size == SALT_BYTES }
        if (keptSalt == null) prints.clear()
        startLoaded(fromFile, savedInFile, prints, keptSalt ?: newSalt(), rewrite = format < FORMAT || keptSalt == null)
        // The key itself refuses to work while the phone is locked (Q33); lists sealed with an older key move to one.
        if (runCatching { sealer.needsUpgrade() }.getOrDefault(false) && runCatching { sealer.rotate() }.getOrDefault(false)) {
            synchronized(this) { dirty = true }
        }
    }

    /**
     * Start a loaded list from the file's words: learned and saved words as text, words used less as fingerprints
     * (Q32; an older file's are turned into fingerprints here). Then the words typed before the file could be read,
     * on top (they're newer). [rewrite]: the file isn't in this format, so the next save writes it again.
     */
    private fun startLoaded(
        words: Map<String, Int>, savedWords: Set<String>, prints: Map<String, Int>, newSalt: ByteArray, rewrite: Boolean,
    ) = synchronized(this) {
        counts.clear(); saved.clear(); pending.clear()
        salt = newSalt
        saved += savedWords
        words.forEach { (w, n) -> if (n >= LEARNED || w in saved) counts[w] = n else addUses(w, n) }
        pending.putAll(prints.filterKeys { it !in pending }) // a word made into a fingerprint above wins over a stale copy
        val typedEarly = LinkedHashMap(early)
        val savedEarly = HashSet(earlySaved)
        early.clear(); earlySaved.clear()
        state = State.LOADED
        typedEarly.forEach { (w, n) -> addUses(w, n) }
        savedEarly.forEach { keepLoaded(it) }
        trim()
        generation++
        if (rewrite || typedEarly.isNotEmpty() || savedEarly.isNotEmpty()) dirty = true
    }

    /**
     * Write the list: a temp file, then a checked rename over the old one (4). Only after the old file was read (2).
     * The list is copied under the memory lock; sealing and writing hold only [io], so typing never waits on them.
     */
    fun save() = synchronized(io) {
        if (!unlocked()) return
        // Only a copy under the memory lock; the text and bytes are built after it's released.
        val lines = ArrayList<String>()
        val gen: Long
        synchronized(this) {
            if (!dirty || state != State.LOADED) return
            trim()
            gen = generation
            // A to Z and fingerprint order: the file says nothing about when anything was typed (Q32).
            lines += "~" + hex(salt ?: return)
            counts.keys.sorted().forEach { w -> lines += if (w in saved) "$w\t${counts[w]}\ts" else "$w\t${counts[w]}" }
            pending.keys.sorted().forEach { f -> lines += "#$f\t${pending[f]}" }
        }
        val body = java.io.ByteArrayOutputStream(lines.size * 16 + 1).apply {
            write(FORMAT)
            lines.forEachIndexed { i, l -> if (i > 0) write('\n'.code); write(l.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        when (write(body)) {
            Write.OK -> { saved(gen); return }
            // The seal worked, so the key is fine: a full disk or a failed rename says nothing about it (R6).
            Write.DISK_FAILED -> { saveFailures = 0; return }
            // The phone locked while saving: the key refuses until unlock (Q33), which says nothing about the key.
            Write.SEAL_FAILED -> if (!unlocked()) return
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
            saved.clear()
            pending.clear(); early.clear(); earlySaved.clear()
            // A new salt: nothing from before can match a new list's fingerprints.
            salt = newSalt()
            generation++
            dirty = false
            state = State.LOADED
        }
        passingFailures = 0
        true
    }

    private fun learnable(w: String) = Words.isWord(w)

    private fun setAsideFile() {
        // Never overwrite a list set aside earlier: the next free name.
        val target = generateSequence(1) { it + 1 }.map { n -> File(dir, if (n == 1) "words.unreadable" else "words.unreadable-$n") }
            .first { !it.exists() }
        // If it can't be moved, nothing is learned or saved this run, so the file is never written over.
        val moved = move(file, target)
        if (moved) startLoaded(emptyMap(), emptySet(), emptyMap(), newSalt(), rewrite = false)
        else synchronized(this) { state = State.REFUSED }
    }

    /**
     * Down to [CAP] words: the least used go first, and among those the longest unused (6). Saved words never go (Q29),
     * so the list stays over the cap only when nearly all of it is saved. Usually one or a few over, so a pass for the
     * lowest count and a removal in last-used order, no sort.
     */
    private fun trim() {
        // Fingerprints first: every one is used less than any learned word.
        while (counts.size + pending.size > CAP && pending.isNotEmpty()) {
            val low = pending.values.min()
            val iter = pending.entries.iterator()
            while (counts.size + pending.size > CAP && iter.hasNext()) if (iter.next().value == low) iter.remove()
        }
        while (counts.size > CAP) {
            val low = counts.entries.filter { it.key !in saved }.minOfOrNull { it.value } ?: return
            val iter = counts.entries.iterator()
            while (counts.size > CAP && iter.hasNext()) iter.next().let { if (it.value == low && it.key !in saved) iter.remove() }
            generation++ // the cached learned list rebuilds
        }
    }

    companion object {
        /** The format this build writes. 0 was 0.2.1's (no format byte); 1 had no saved mark; 2 kept every word as text. */
        const val FORMAT = 3
        private const val SALT_BYTES = 16
        private const val HEX = "0123456789abcdef"
        private val random = java.security.SecureRandom()
        private fun newSalt() = ByteArray(SALT_BYTES).also(random::nextBytes)
        private fun hex(b: ByteArray) = buildString(b.size * 2) { b.forEach { val v = it.toInt() and 0xFF; append(HEX[v ushr 4]); append(HEX[v and 15]) } }
        /** Null unless [s] is lowercase hex of whole bytes. */
        private fun unhex(s: String): ByteArray? {
            if (s.isEmpty() || s.length % 2 != 0 || s.any { it !in HEX }) return null
            return ByteArray(s.length / 2) { ((HEX.indexOf(s[2 * it]) shl 4) or HEX.indexOf(s[2 * it + 1])).toByte() }
        }
        /** Uses before a word is learned (Q29; was 2). */
        const val LEARNED = 3
        private const val MAX_BOOST = 80
        /** Most words kept; the least used go first (6). */
        const val CAP = 5000
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
