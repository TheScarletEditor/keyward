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

    /** Stand-in for AES-GCM: a marker byte, then the bytes flipped. A wrong marker is "can never be read". */
    private class FakeSealer : WordStore.Sealer {
        var passingErrors = 0
        var brokenKey = false
        var resets = 0
        override fun reset() { resets++; brokenKey = false }
        override fun seal(plain: ByteArray): ByteArray {
            if (brokenKey) throw IllegalStateException("key unusable")
            return byteArrayOf(MARK) + plain.map { (it.toInt() xor 0x5A).toByte() }
        }
        override fun open(sealed: ByteArray): ByteArray {
            if (passingErrors > 0) { passingErrors--; throw IllegalStateException("key store busy") }
            if (sealed.isEmpty() || sealed[0] != MARK) throw WordStore.Unreadable()
            return sealed.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }
        companion object { const val MARK: Byte = 7 }
    }

    private val sealer = FakeSealer()
    private val dir get() = tmp.root
    private val enc get() = File(dir, "words.enc")
    private fun store(move: (File, File) -> Boolean = { a, b -> a.renameTo(b) }) = WordStore(dir, sealer, move = move)
    private fun writeList(body: ByteArray) = enc.writeBytes(sealer.seal(body))
    private fun format1(text: String) = byteArrayOf(WordStore.FORMAT.toByte()) + text.toByteArray()

    @Test fun roundTrip() {
        val s = store(); s.load()
        repeat(2) { s.record("zorbex") }; s.record("quillet"); s.save()
        val t = store(); t.load()
        assertTrue(t.isKnown("zorbex")); assertFalse(t.isKnown("quillet")); assertEquals(2, t.count)
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
        s.record("quillet"); s.save()
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
        writeList(byteArrayOf((WordStore.FORMAT + 1).toByte()) + "zorbex\t2".toByteArray())
        val newer = store(); newer.load()
        assertEquals(WordStore.Notice.NEWER, newer.notice)
        assertTrue(newer.clear()); assertEquals(WordStore.Notice.NONE, newer.notice)
        newer.record("quillet"); newer.save(); assertTrue(enc.exists())
        enc.writeBytes(byteArrayOf(1, 2))
        val stuck = store(move = { _, _ -> false }); stuck.load()
        assertEquals(WordStore.Notice.NOT_LEARNING, stuck.notice)
        assertTrue(stuck.clear()); assertEquals(WordStore.Notice.NONE, stuck.notice)
    }

    @Test fun aKeyThatFailsEverySaveIsReplacedAndNothingIsLost() {
        val s = store(); s.load()
        repeat(2) { s.record("zorbex") }
        sealer.brokenKey = true
        repeat(WordStore.MAX_PASSING) { s.save() }
        assertEquals(1, sealer.resets)
        // Written with the new key in the same call: nothing waits for a later save.
        val t = store(); t.load(); assertTrue(t.isKnown("zorbex"))
    }

    @Test fun fileWordsAreOlderThanWordsTypedBeforeTheLoad() {
        writeList(format1("old\t1"))
        val s = store(); s.record("fresh"); s.load()
        for (i in 0 until WordStore.CAP - 1) s.record(name(i)) // fills the list, all used once
        s.save() // one over the cap: the oldest word used once goes, which is the file's "old"
        assertFalse(s.isKnown("old")); assertEquals(WordStore.CAP, s.count)
        val t = store(); t.load(); assertEquals(WordStore.CAP, t.count)
    }

    @Test fun failedRenameKeepsDirtyAndRemovesTemp() {
        var allow = false
        val s = store(move = { a, b -> allow && a.renameTo(b) }); s.load()
        s.record("zorbex"); s.save()
        assertFalse(File(dir, "words.tmp").exists()); assertFalse(enc.exists())
        allow = true; s.save() // still dirty, so it tries again
        val t = store(); t.load(); assertEquals(1, t.count)
    }

    @Test fun leftoverTempIsIgnored() {
        writeList(format1("zorbex\t2"))
        File(dir, "words.tmp").writeBytes(sealer.seal(format1("intruder\t9")))
        val s = store(); s.load()
        assertFalse(File(dir, "words.tmp").exists())
        assertTrue(s.isKnown("zorbex")); assertFalse(s.isKnown("intruder"))
    }

    @Test fun badCountLineIsSkipped() {
        writeList(format1("zorbex\t2\nquillet\tlots\nflimber\t-4\n\tnoword"))
        val s = store(); s.load()
        assertEquals(1, s.count); assertTrue(s.isKnown("zorbex"))
    }

    @Test fun readsA021File() {
        writeList("zorbex\t2\nquillet\t1".toByteArray()) // 0.2.1: no format byte
        val s = store(); s.load()
        assertEquals(2, s.count); assertTrue(s.isKnown("zorbex"))
        s.record("quillet"); s.save()
        assertEquals("saved in the current format", WordStore.FORMAT, WordStore.formatOf(sealer.open(enc.readBytes())))
    }

    @Test fun reads021FilesStartingWithAccentOrApostrophe() {
        writeList("éclair\t2\nzorbex\t1".toByteArray())
        assertTrue(store().apply { load() }.isKnown("éclair"))
        writeList("'tis\t2".toByteArray())
        assertTrue(store().apply { load() }.isKnown("'tis"))
    }

    @Test fun wordsTypedBeforeAnUnreadableFileAreKept() {
        enc.writeBytes(byteArrayOf(1, 2, 3))
        val s = store()
        repeat(2) { s.record("zorbex") } // typed before the file was read
        s.load(); s.save()
        assertTrue(s.hasSetAside)
        val t = store(); t.load(); assertTrue(t.isKnown("zorbex"))
    }

    @Test fun zeroFormatByteIsUnreadable() {
        writeList(byteArrayOf(0) + "zorbex\t2".toByteArray())
        val s = store(); s.load()
        assertTrue(s.hasSetAside); assertEquals(0, s.count)
    }

    @Test fun newerFormatIsRefusedWithoutWriting() {
        writeList(byteArrayOf((WordStore.FORMAT + 1).toByte()) + "zorbex\t2".toByteArray())
        val before = enc.readBytes()
        val s = store(); s.load()
        s.record("quillet"); s.record("quillet"); s.save()
        assertArrayEquals(before, enc.readBytes())
        assertEquals(0, s.count)
    }

    @Test fun capAtFiveThousand() {
        val s = store(); s.load()
        repeat(3) { s.record("keepme") }
        for (i in 0 until WordStore.CAP + 50) s.record(name(i))
        s.save() // trims, off the typing path
        assertEquals(WordStore.CAP, s.count)
        assertTrue("the most used word stays", s.isKnown("keepme"))
        // A new word still gets learned when the list is full of words used once: the oldest go first.
        repeat(2) { s.record("zorbex") }
        s.save()
        assertTrue(s.isKnown("zorbex"))
        assertEquals(WordStore.CAP, s.count)
        val t = store(); t.load(); assertEquals(WordStore.CAP, t.count); assertTrue(t.isKnown("zorbex"))
    }

    @Test fun forgetThenSaveWritesNothingBack() {
        val s = store(); s.load()
        repeat(2) { s.record("zorbex") }; s.save()
        File(dir, "words.unreadable").writeBytes(byteArrayOf(1))
        assertTrue(s.clear())
        s.save() // the keyboard saving right after
        assertFalse(enc.exists()); assertFalse(s.hasSetAside)
        val t = store(); t.load(); assertEquals(0, t.count)
    }

    /** Letters-only made-up words: a, b, ..., z, ba, bb, ... */
    private fun name(i: Int): String {
        var n = i; val sb = StringBuilder()
        do { sb.append('a' + n % 26); n /= 26 } while (n > 0)
        return "w" + sb.toString()
    }
}
