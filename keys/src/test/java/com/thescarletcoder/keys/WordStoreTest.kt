package com.thescarletcoder.keys

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The learned-words file rules (docs/KEYS-0.2.2-PLAN.md, "What can go wrong", learned words 1 to 7). Made-up words only. */
class WordStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    /**
     * Stand-in for AES-GCM: a marker byte, the key generation, then the bytes flipped. A wrong marker or a generation
     * that no longer exists is "can never be read".
     */
    private class FakeSealer : WordStore.Sealer {
        var passingErrors = 0
        val keys = mutableListOf(1) // generations that exist, newest last
        var brokenKey = false // the newest key can't seal
        var rotations = 0
        override fun rotate(): Boolean { rotations++; keys += keys.last() + 1; brokenKey = false; return true }
        override fun retireOld() { val k = keys.last(); keys.clear(); keys += k }
        override fun hasOldKeys() = keys.size > 1
        var upgrade = false // the newest key is an older kind
        override fun needsUpgrade() = upgrade && keys.size == 1
        override fun seal(plain: ByteArray): ByteArray {
            if (brokenKey) throw IllegalStateException("key unusable")
            return byteArrayOf(MARK, keys.last().toByte()) + plain.map { (it.toInt() xor 0x5A).toByte() }
        }
        override fun open(sealed: ByteArray): ByteArray {
            if (passingErrors > 0) { passingErrors--; throw IllegalStateException("key store busy") }
            if (sealed.size < 2 || sealed[0] != MARK || sealed[1].toInt() !in keys) throw WordStore.Unreadable()
            return sealed.drop(2).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }
        companion object { const val MARK: Byte = 7 }
    }

    private val sealer = FakeSealer()
    private val dir get() = tmp.root
    private val enc get() = File(dir, "words.enc")
    private fun store(move: (File, File) -> Boolean = { a, b -> a.renameTo(b) }, delete: (File) -> Boolean = { it.delete() }) =
        WordStore(dir, sealer, move = move, delete = delete)
    private fun writeList(body: ByteArray) = enc.writeBytes(sealer.seal(body))
    private fun format1(text: String) = byteArrayOf(1) + text.toByteArray()

    @Test fun roundTrip() {
        val s = store(); s.load()
        repeat(3) { s.record("zorbex") }; s.record("quillet"); s.save()
        val t = store(); t.load()
        assertTrue(t.isKnown("zorbex")); assertFalse(t.isKnown("quillet")); assertEquals(2, t.size)
    }

    @Test fun unreadableFileIsSetAsideAndNeverOverwritten() {
        val damaged = byteArrayOf(1, 2, 3, 4)
        enc.writeBytes(damaged)
        val s = store(); s.load()
        assertTrue(s.hasSetAside)
        assertArrayEquals(damaged, File(dir, "words.unreadable").readBytes())
        s.record("zorbex"); s.save()
        assertArrayEquals("set-aside file untouched by the next save", damaged, File(dir, "words.unreadable").readBytes())
        assertTrue(enc.exists())
    }

    @Test fun secondUnreadableFileGetsItsOwnName() {
        File(dir, "words.unreadable").writeBytes(byteArrayOf(9))
        enc.writeBytes(byteArrayOf(1, 2))
        store().load()
        assertArrayEquals(byteArrayOf(9), File(dir, "words.unreadable").readBytes())
        assertArrayEquals(byteArrayOf(1, 2), File(dir, "words.unreadable-2").readBytes())
    }

    @Test fun unreadableThatCantBeMovedIsNeverWrittenOver() {
        enc.writeBytes(byteArrayOf(1, 2))
        val s = store(move = { _, _ -> false }); s.load()
        s.record("zorbex"); s.record("zorbex"); s.save()
        assertArrayEquals(byteArrayOf(1, 2), enc.readBytes())
        assertFalse(s.isLoaded)
    }

    @Test fun passingErrorLeavesTheFileAndRetries() {
        writeList(format1("zorbex\t3"))
        val before = enc.readBytes()
        sealer.passingErrors = 1
        val s = store(); s.load()
        assertFalse(s.isLoaded)
        s.record("quillet"); s.save()
        assertArrayEquals("no save while the old file is unread", before, enc.readBytes())
        s.load() // the next field
        assertTrue(s.isLoaded)
        assertTrue("old words kept", s.isKnown("zorbex"))
        repeat(2) { s.record("quillet") }; s.save()
        val t = store(); t.load()
        assertTrue(t.isKnown("zorbex")); assertTrue("words from before the load are added", t.isKnown("quillet"))
    }

    @Test fun threePassingErrorsInARowSetTheFileAside() {
        writeList(format1("zorbex\t3"))
        val before = enc.readBytes()
        sealer.passingErrors = WordStore.MAX_PASSING
        val s = store()
        repeat(WordStore.MAX_PASSING - 1) { s.load(); assertFalse(s.hasSetAside) }
        s.load()
        assertTrue(s.isLoaded); assertEquals(WordStore.Notice.SET_ASIDE, s.notice)
        assertArrayEquals("moved, never deleted", before, File(dir, "words.unreadable").readBytes())
    }

    @Test fun noticesAndForgetStartsANewList() {
        assertEquals(WordStore.Notice.NONE, store().apply { load() }.notice)
        writeList(byteArrayOf((WordStore.FORMAT + 1).toByte()) + "zorbex\t3".toByteArray())
        val newer = store(); newer.load()
        assertEquals(WordStore.Notice.NEWER, newer.notice)
        assertTrue(newer.clear()); assertEquals(WordStore.Notice.NONE, newer.notice)
        newer.record("quillet"); newer.save(); assertTrue(enc.exists())
        enc.writeBytes(byteArrayOf(1, 2))
        val stuck = store(move = { _, _ -> false }); stuck.load()
        assertEquals(WordStore.Notice.NOT_LEARNING, stuck.notice)
        assertTrue(stuck.clear()); assertEquals(WordStore.Notice.NONE, stuck.notice)
    }

    @Test fun aKeyThatFailsEverySaveIsRotatedAndNothingIsLost() {
        val s = store(); s.load()
        repeat(3) { s.record("zorbex") }; s.save()
        s.record("quillet")
        sealer.brokenKey = true
        repeat(WordStore.MAX_PASSING) { s.save() }
        assertEquals(1, sealer.rotations)
        assertEquals("old key retired once the list is saved with the new one", listOf(2), sealer.keys)
        val t = store(); t.load(); assertTrue(t.isKnown("zorbex")); assertEquals(2, t.size)
    }

    @Test fun diskFailuresNeverReplaceTheKey() {
        val s = store(move = { _, _ -> false }); s.load()
        s.record("zorbex")
        repeat(WordStore.MAX_PASSING * 2) { s.save() }
        assertEquals(0, sealer.rotations)
    }

    @Test fun aRotationWhoseSaveFailsStrandsNothing() {
        var diskOk = true
        val s = store(move = { a, b -> diskOk && a.renameTo(b) }); s.load()
        repeat(3) { s.record("zorbex") }; s.save()
        sealer.brokenKey = true
        s.record("quillet")
        repeat(WordStore.MAX_PASSING - 1) { s.save() }
        diskOk = false
        s.save() // rotates, but the save with the new key fails on disk
        assertEquals(listOf(1, 2), sealer.keys)
        val t = store(); t.load(); assertTrue("the old file still opens with the old key", t.isKnown("zorbex"))
        diskOk = true
        s.save() // later save with the new key: then the old key goes
        assertEquals(listOf(2), sealer.keys)
        val u = store(); u.load(); assertTrue(u.isKnown("zorbex")); assertEquals(2, u.size)
    }

    @Test fun strayCharactersInTheFileAreCleaned() {
        writeList(format1("zorbex\t3\n\u0001quillet\t3\nbad word\t3\nx\t5"))
        val s = store(); s.load()
        assertTrue(s.isKnown("quillet")); assertEquals(2, s.size)
    }

    @Test fun fileWordsAreOlderThanWordsTypedBeforeTheLoad() {
        writeList(format1("old\t1"))
        val s = store(); s.record("fresh"); s.load()
        for (i in 0 until WordStore.CAP - 1) s.record(name(i)) // fills the list, all used once
        s.save() // one over the cap: the oldest word used once goes, which is the file's "old"
        assertFalse(s.isKnown("old")); assertEquals(WordStore.CAP, s.size)
        val t = store(); t.load(); assertEquals(WordStore.CAP, t.size)
    }

    @Test fun failedRenameKeepsDirtyAndRemovesTemp() {
        var allow = false
        val s = store(move = { a, b -> allow && a.renameTo(b) }); s.load()
        s.record("zorbex"); s.save()
        assertFalse(File(dir, "words.tmp").exists()); assertFalse(enc.exists())
        allow = true; s.save() // still dirty, so it tries again
        val t = store(); t.load(); assertEquals(1, t.size)
    }

    @Test fun leftoverTempIsIgnored() {
        writeList(format1("zorbex\t3"))
        File(dir, "words.tmp").writeBytes(sealer.seal(format1("intruder\t9")))
        val s = store(); s.load()
        assertFalse(File(dir, "words.tmp").exists())
        assertTrue(s.isKnown("zorbex")); assertFalse(s.isKnown("intruder"))
    }

    @Test fun badCountLineIsSkipped() {
        writeList(format1("zorbex\t3\nquillet\tlots\nflimber\t-4\n\tnoword"))
        val s = store(); s.load()
        assertEquals(1, s.size); assertTrue(s.isKnown("zorbex"))
    }

    @Test fun readsA021File() {
        writeList("zorbex\t3\nquillet\t1".toByteArray()) // 0.2.1: no format byte
        val s = store(); s.load()
        assertEquals(2, s.size); assertTrue(s.isKnown("zorbex"))
        s.record("quillet"); s.save()
        assertEquals("saved in the current format", WordStore.FORMAT, WordStore.formatOf(sealer.open(enc.readBytes())))
    }

    @Test fun reads021FilesStartingWithAccentOrApostrophe() {
        writeList("éclair\t3\nzorbex\t1".toByteArray())
        assertTrue(store().apply { load() }.isKnown("éclair"))
        writeList("'tis\t3".toByteArray())
        assertTrue(store().apply { load() }.isKnown("'tis"))
    }

    @Test fun wordsTypedBeforeAnUnreadableFileAreKept() {
        enc.writeBytes(byteArrayOf(1, 2, 3))
        val s = store()
        repeat(3) { s.record("zorbex") } // typed before the file was read
        s.load(); s.save()
        assertTrue(s.hasSetAside)
        val t = store(); t.load(); assertTrue(t.isKnown("zorbex"))
    }

    @Test fun zeroFormatByteIsUnreadable() {
        writeList(byteArrayOf(0) + "zorbex\t3".toByteArray())
        val s = store(); s.load()
        assertTrue(s.hasSetAside); assertEquals(0, s.size)
    }

    @Test fun newerFormatIsRefusedWithoutWriting() {
        writeList(byteArrayOf((WordStore.FORMAT + 1).toByte()) + "zorbex\t3".toByteArray())
        val before = enc.readBytes()
        val s = store(); s.load()
        s.record("quillet"); s.record("quillet"); s.save()
        assertArrayEquals(before, enc.readBytes())
        assertEquals(0, s.size)
    }

    @Test fun capAtFiveThousand() {
        val s = store(); s.load()
        repeat(3) { s.record("keepme") }
        for (i in 0 until WordStore.CAP + 50) s.record(name(i))
        s.save() // trims, off the typing path
        assertEquals(WordStore.CAP, s.size)
        assertTrue("the most used word stays", s.isKnown("keepme"))
        // A new word still gets learned when the list is full of words used once: the oldest go first.
        repeat(3) { s.record("zorbex") }
        s.save()
        assertTrue(s.isKnown("zorbex"))
        assertEquals(WordStore.CAP, s.size)
        val t = store(); t.load(); assertEquals(WordStore.CAP, t.size); assertTrue(t.isKnown("zorbex"))
    }

    @Test fun forgetThenSaveWritesNothingBack() {
        val s = store(); s.load()
        repeat(3) { s.record("zorbex") }; s.save()
        File(dir, "words.unreadable").writeBytes(byteArrayOf(1))
        assertTrue(s.clear())
        s.save() // the keyboard saving right after
        assertFalse(enc.exists()); assertFalse(s.hasSetAside)
        val t = store(); t.load(); assertEquals(0, t.size)
    }

    @Test fun forgetRemovesEveryCopy() {
        val s = store(); s.load()
        repeat(3) { s.record("zorbex") }; s.save()
        File(dir, "words.tmp").writeBytes(byteArrayOf(1))
        File(dir, "words.unreadable").writeBytes(byteArrayOf(2))
        File(dir, "words.unreadable-2").writeBytes(byteArrayOf(3))
        assertTrue(s.clear())
        assertEquals(emptyList<String>(), dir.list()!!.toList())
        assertEquals(0, s.size)
    }

    @Test fun failedDeleteReportsFailureAndKeepsTheWords() {
        val s = store(delete = { false }); s.load()
        repeat(3) { s.record("zorbex") }; s.record("quillet"); s.save()
        assertFalse(s.clear())
        assertEquals("count kept", 2, s.size)
        assertTrue(s.isKnown("zorbex"))
        val t = store(); t.load(); assertTrue("file kept", t.isKnown("zorbex"))
    }

    @Test fun aPartlyFailedForgetLeavesTheListOnDisk() {
        val s = store(delete = { f -> f.name != "words.unreadable" && f.delete() }); s.load()
        repeat(3) { s.record("zorbex") }; s.save()
        File(dir, "words.unreadable").writeBytes(byteArrayOf(1))
        assertFalse(s.clear())
        val t = store(); t.load(); assertTrue("the list is still on disk", t.isKnown("zorbex"))
    }

    @Test fun oversizedFileIsSetAsideUnread() {
        enc.writeBytes(ByteArray((WordStore.MAX_FILE_BYTES + 1).toInt()))
        val s = store(); s.load()
        assertTrue(s.hasSetAside); assertTrue(s.isLoaded)
    }

    @Test fun countsNeverOverflow() {
        writeList(format1("zorbex\t${Int.MAX_VALUE}"))
        val s = store(); s.record("zorbex"); s.load()
        assertTrue(s.isKnown("zorbex")); assertTrue(s.boost("zorbex") > 0)
    }

    @Test fun aSaveThatFailsOnDiskResetsTheSealCount() {
        var diskOk = true
        val s = store(move = { a, b -> diskOk && a.renameTo(b) }); s.load()
        s.record("zorbex")
        sealer.brokenKey = true
        repeat(WordStore.MAX_PASSING - 1) { s.save() } // two seal failures
        sealer.brokenKey = false; diskOk = false
        s.save() // seal works, disk fails: the key is fine
        sealer.brokenKey = true; diskOk = true
        s.save() // one seal failure in a row, not three
        assertEquals(0, sealer.rotations)
    }

    @Test fun wordsTypedDuringASaveStayUnsaved() {
        val s = store(move = { a, b -> a.renameTo(b) }); s.load()
        s.record("zorbex"); s.save()
        s.record("quillet") // after the save: still dirty, so the next save writes it
        s.save()
        val t = store(); t.load(); assertEquals(2, t.size)
    }

    @Test fun aWordIsLearnedAtThreeUses() {
        val s = store(); s.load()
        repeat(2) { s.record("zorbex") }
        assertFalse(s.isKnown("zorbex")); assertEquals("used twice: kept quietly, not counted", 0, s.count)
        s.record("zorbex")
        assertTrue(s.isKnown("zorbex")); assertEquals(1, s.count)
    }

    @Test fun savedWordsRoundTripInFormat2() {
        val s = store(); s.load()
        assertTrue(s.keep("Zorvex")); repeat(3) { s.record("quillet") }; s.record("flimber"); s.save()
        assertEquals(WordStore.FORMAT, WordStore.formatOf(sealer.open(enc.readBytes())))
        val t = store(); t.load()
        assertTrue(t.isKnown("zorvex")); assertTrue(t.isSaved("zorvex")); assertFalse(t.isSaved("quillet"))
        assertEquals(
            listOf(WordStore.Entry("quillet", 3, false), WordStore.Entry("zorvex", 1, true)),
            t.entries(),
        )
        assertEquals(2, t.count)
    }

    @Test fun aFormat1FileKeepsItsCountsAndNothingIsSaved() {
        writeList(format1("zorbex\t3\nquillet\t2"))
        val s = store(); s.load()
        assertTrue(s.isKnown("zorbex")); assertFalse(s.isKnown("quillet")); assertFalse(s.isSaved("zorbex"))
        assertEquals(2, s.size); assertEquals(1, s.count)
    }

    @Test fun aSavedMarkCountsOnlyFromFormat2() {
        writeList(format1("zorbex\t3\ts\nquillet\t3"))
        val s = store(); s.load()
        assertFalse("format 1 has no saved mark: the line is skipped", s.isKnown("zorbex")); assertTrue(s.isKnown("quillet"))
        writeList(byteArrayOf(2) + "zorbex\t1\ts\nflimber\ts".toByteArray())
        val t = store(); t.load()
        assertTrue(t.isSaved("zorbex")); assertFalse("no count: skipped", t.isKnown("flimber"))
    }

    @Test fun savedWordsAreListedApart() {
        val s = store(); s.load()
        s.keep("zorvex"); repeat(3) { s.record("quillet") }; s.record("flimber")
        assertEquals(listOf("zorvex"), s.savedWords.toList())
        assertEquals("every word held, for Forget all", 3, s.size)
    }

    @Test fun nothingIsKeptWhileTheListIsRefused() {
        writeList(byteArrayOf((WordStore.FORMAT + 1).toByte()) + "zorbex\t3".toByteArray())
        val s = store(); s.load()
        assertFalse(s.canKeep); assertFalse(s.keep("quillet"))
    }

    @Test fun savedWordsSurviveTheCap() {
        val s = store(); s.load()
        s.keep("zorvex")
        repeat(3) { s.record("keepme") }
        for (i in 0 until WordStore.CAP + 50) repeat(5) { s.record(name(i)) } // all used more than the saved word
        s.save()
        assertEquals(WordStore.CAP, s.size)
        assertTrue("a saved word is never trimmed", s.isKnown("zorvex"))
        val t = store(); t.load(); assertTrue(t.isSaved("zorvex"))
    }

    @Test fun removeTakesAWordOffAndItLearnsAgain() {
        val s = store(); s.load()
        s.keep("zorvex"); repeat(3) { s.record("quillet") }; s.save()
        s.remove("Zorvex"); s.remove("quillet"); s.save()
        val t = store(); t.load()
        assertFalse(t.isKnown("zorvex")); assertFalse(t.isKnown("quillet")); assertEquals(0, t.size)
        repeat(3) { t.record("quillet") }
        assertTrue("used three more times: learned again", t.isKnown("quillet"))
    }

    @Test fun keepRefusesWhatIsNotAWord() {
        val s = store(); s.load()
        assertFalse(s.keep("a")); assertFalse(s.keep("two words")); assertFalse(s.keep("x1"))
        assertEquals(0, s.size)
    }

    @Test fun forgetDropsSavedWords() {
        val s = store(); s.load()
        s.keep("zorvex"); s.save()
        assertTrue(s.clear())
        assertFalse(s.isKnown("zorvex"))
        val t = store(); t.load(); assertEquals(0, t.size)
    }

    private fun body() = String(sealer.open(enc.readBytes()), 1, sealer.open(enc.readBytes()).size - 1, Charsets.UTF_8)

    @Test fun format3KeepsNoReadableOneOffWordsAndNoOrder() {
        val s = store(); s.load()
        s.record("seedword"); s.record("seedword"); repeat(3) { s.record("zorbex") }; repeat(3) { s.record("apple") }; s.keep("quillet")
        s.save()
        val text = body()
        assertFalse("a word used twice is only a fingerprint", text.contains("seedword"))
        val words = text.lines().filter { !it.startsWith("~") && !it.startsWith("#") }.map { it.substringBefore('\t') }
        assertEquals("A to Z, not the order typed", listOf("apple", "quillet", "zorbex"), words)
        assertEquals(1, text.lines().count { it.startsWith("#") })
        // The fingerprint still finds the word: one more use learns it.
        val t = store(); t.load(); t.record("seedword")
        assertTrue(t.isKnown("seedword"))
    }

    @Test fun anOlderFileIsRewrittenWithFingerprints() {
        writeList(byteArrayOf(2) + "quillet\t2\nzorbex\t3\nzorvex\t1\ts".toByteArray())
        val s = store(); s.load()
        s.save() // nothing typed, but the file is rewritten in the new format
        assertEquals(WordStore.FORMAT, WordStore.formatOf(sealer.open(enc.readBytes())))
        val text = body()
        assertFalse(text.contains("quillet")); assertTrue(text.contains("zorbex")); assertTrue(text.contains("zorvex\t1\ts"))
        val t = store(); t.load(); t.record("quillet")
        assertTrue("2 old uses plus 1", t.isKnown("quillet")); assertTrue(t.isSaved("zorvex"))
    }

    @Test fun unloadDropsTheWordsUntilTheNextLoad() {
        val s = store(); s.load()
        repeat(3) { s.record("zorbex") }
        assertFalse("unsaved words stay in memory", s.unload())
        s.save()
        assertTrue(s.unload())
        assertFalse(s.isKnown("zorbex")); assertEquals(0, s.size)
        s.load()
        assertTrue(s.isKnown("zorbex"))
    }

    @Test fun nothingIsReadOrWrittenWhileLocked() {
        var unlocked = true
        val s = WordStore(dir, sealer, unlocked = { unlocked })
        s.load(); repeat(3) { s.record("zorbex") }; s.save()
        val before = enc.readBytes()
        s.save(); assertTrue(s.unload())
        unlocked = false
        s.load(); assertFalse("locked: not read", s.isKnown("zorbex"))
        s.record("quillet"); s.save()
        assertArrayEquals("locked: nothing written", before, enc.readBytes())
        unlocked = true
        s.load(); assertTrue(s.isKnown("zorbex"))
    }

    @Test fun anOlderKeyIsReplacedAtTheFirstSave() {
        val s = store(); s.load(); repeat(3) { s.record("zorbex") }; s.save()
        sealer.upgrade = true
        val t = store(); t.load()
        assertEquals(1, sealer.rotations)
        t.save()
        assertEquals("the old key is gone once the list is sealed with the new one", 1, sealer.keys.size)
        sealer.upgrade = false
        val u = store(); u.load(); assertTrue(u.isKnown("zorbex"))
    }

    @Test fun wordsTypedBeforeTheLoadAreFingerprintedToo() {
        writeList(byteArrayOf(3) + "~00112233445566778899aabbccddeeff\nzorbex\t3".toByteArray())
        val s = store(); s.record("seedword"); s.keep("quillet"); s.load(); s.save()
        val text = body()
        assertFalse(text.contains("seedword")); assertTrue(text.contains("quillet\t1\ts")); assertTrue(text.startsWith("~00112233445566778899aabbccddeeff"))
    }

    @Test fun forgetStartsANewSalt() {
        val s = store(); s.load(); s.record("seedword"); s.save()
        val salt = body().lines().first()
        assertTrue(s.clear()); s.record("seedword"); s.save()
        assertFalse("a new list never reuses the old salt", body().lines().first() == salt)
    }

    /** Letters-only made-up words: a, b, ..., z, ba, bb, ... */
    private fun name(i: Int): String {
        var n = i; val sb = StringBuilder()
        do { sb.append('a' + n % 26); n /= 26 } while (n > 0)
        return "w" + sb.toString()
    }
}
