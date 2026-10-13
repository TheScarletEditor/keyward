package com.thescarletcoder.keys

import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.system.Os
import android.system.OsConstants
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.Executors

/**
 * Words the user actually uses, so the keyboard stops "correcting" them and ranks them higher.
 * Only words, with a use count: never sentences, never where they were typed.
 * Encrypted at rest with an Android Keystore key (AES-GCM). Only available after the device is unlocked.
 * The service never records in password fields or fields that ask for no personalized learning.
 * One instance per process ([get]), so "Forget learned words" in Settings also empties the keyboard's copy in memory
 * (with two copies, the keyboard's next save wrote every forgotten word back).
 * The file rules (set aside, never overwrite, retry, cap) live in [WordStore]; this adds the Keystore.
 */
class UserWords private constructor(context: Context) {
    private val store = WordStore(
        dirProvider = { context.noBackupFilesDir },
        syncDir = { d ->
            val fd = Os.open(d.path, OsConstants.O_RDONLY, 0)
            try { Os.fsync(fd) } finally { Os.close(fd) }
        },
        sealer = KeystoreSealer,
        // Unlocked since boot and unlocked now: the key refuses while the phone is locked (Q33), so nothing is tried then.
        unlocked = {
            context.getSystemService(UserManager::class.java).isUserUnlocked &&
                !context.getSystemService(KeyguardManager::class.java).isDeviceLocked
        },
    )

    fun isKnown(word: String) = store.isKnown(word)
    val canKeep: Boolean get() = store.canKeep
    fun isSaved(word: String) = store.isSaved(word)
    /** Saved words only, for fields where learning is off (S7-4). */
    val saved: Collection<String> get() = store.savedWords
    fun boost(word: String) = store.boost(word)

    /** Learned and saved words, for suggestions. */
    val learned: Collection<String> get() = store.learned
    fun record(word: String) = store.record(word)
    /** Save a word the user chose to keep (Q28). */
    fun keep(word: String) = store.keep(word)

    /**
     * Read and save on the store's own thread (process-long, never the screen's or the keyboard's suggestion thread),
     * so a save's encryption and disk sync never delay the next field's suggestions.
     */
    fun loadInBackground() = io.execute { store.load() }
    fun saveInBackground() = io.execute { store.save() }
    /** The phone locked or the screen went off: save, then drop the words from memory (Q33). */
    fun unloadInBackground() = io.execute { store.save(); store.unload() }

    /** What Settings shows: the count, every word held (listed or not, for Forget all), and the notice, if any. */
    data class Status(val count: Int, val notice: WordStore.Notice, val held: Int = count, val canKeep: Boolean = true)

    /**
     * For Settings: the file and Keystore work on a background thread, the answer on the main thread. The thread
     * belongs to the store, not the screen, so a Forget started before Settings closes still finishes (Forget 4).
     */
    fun loadAsync(done: (Status) -> Unit) = io.execute {
        store.load()
        val s = Status(store.count, store.notice, store.size, store.canKeep)
        main.post { done(s) }
    }

    /** For the Your words page: the list, read off the main thread. */
    fun entriesAsync(done: (List<WordStore.Entry>, Status) -> Unit) = io.execute {
        store.load()
        val e = store.entries()
        val s = Status(store.count, store.notice, store.size, store.canKeep)
        main.post { done(e, s) }
    }

    /** Remove one word and save at once, so the keyboard's copy and the file agree (Q30). */
    fun removeAsync(word: String, done: (Status) -> Unit) = io.execute {
        // Read first: a remove before the file is read would come back with the next load.
        store.load()
        store.remove(word)
        store.save()
        val s = Status(store.count, store.notice, store.size, store.canKeep)
        main.post { done(s) }
    }

    /** Save a word typed on the Your words page. False when it can't be a word or nothing is kept. */
    fun keepAsync(word: String, done: (Boolean, Status) -> Unit) = io.execute {
        store.load()
        val ok = store.keep(word)
        if (ok) store.save()
        val s = Status(store.count, store.notice, store.size, store.canKeep)
        main.post { done(ok, s) }
    }

    fun forgetAsync(done: (Boolean, Status) -> Unit) = io.execute {
        val ok = store.clear()
        val s = Status(store.count, store.notice, store.size, store.canKeep)
        main.post { done(ok, s) }
    }

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /**
     * AES-GCM with a Keystore key; the 12-byte IV goes first. Keys come in generations ([ALIAS] is the first, then
     * "_g2", "_g3"...): sealing uses the newest, opening tries each from the newest down, so a file sealed before a
     * rotation still opens until the old key is retired (A-D1).
     * From 0.2.2 keys are made with unlockedDeviceRequired (Q33): they refuse to work while the phone is locked.
     * Those are "_l1", "_l2"... and rank above every older generation, so the first save moves a list onto one.
     */
    private object KeystoreSealer : WordStore.Sealer {
        private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        private fun generation(alias: String): Int = when {
            alias == ALIAS -> 1
            alias.startsWith(GEN_PREFIX) -> alias.removePrefix(GEN_PREFIX).toIntOrNull()?.takeIf { it < LOCKED } ?: 0
            alias.startsWith(LOCKED_PREFIX) -> alias.removePrefix(LOCKED_PREFIX).toIntOrNull()?.takeIf { it in 1 until LOCKED }?.plus(LOCKED) ?: 0
            else -> 0
        }

        private fun aliasFor(gen: Int) = when {
            gen > LOCKED -> "$LOCKED_PREFIX${gen - LOCKED}"
            gen == 1 -> ALIAS
            else -> "$GEN_PREFIX$gen"
        }

        /** Whether more than one key existed at the last listing (so saves skip a Keystore call when there's one). */
        @Volatile private var several = true

        /** Existing key aliases of this store, newest first. */
        private fun aliases(ks: KeyStore): List<String> =
            ks.aliases().toList().filter { generation(it) > 0 }.sortedByDescending { generation(it) }.also { several = it.size > 1 }

        override fun hasOldKeys(): Boolean = several

        override fun needsUpgrade(): Boolean = aliases(store()).firstOrNull()?.let { generation(it) <= LOCKED } ?: false

        override fun seal(plain: ByteArray): ByteArray {
            val ks = store()
            val alias = aliases(ks).firstOrNull() ?: aliasFor(LOCKED + 1)
            return try {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, key(ks, alias))
                c.iv + c.doFinal(plain)
            } catch (e: Exception) {
                // A locked-only key refusing is the phone being locked to it, not a broken key (Q33).
                if (generation(alias) > LOCKED) throw WordStore.Locked(e) else throw e
            }
        }

        override fun open(sealed: ByteArray): ByteArray {
            if (sealed.size < 12 + 16) throw WordStore.Unreadable()
            val ks = store()
            // No key at all: a new one, so the file (sealed with a lost key) is found unreadable and set aside.
            val all = aliases(ks).ifEmpty { listOf(aliasFor(LOCKED + 1).also { key(ks, it) }) }
            var passing: Exception? = null
            var locked: Exception? = null
            for (alias in all) {
                try {
                    val c = Cipher.getInstance("AES/GCM/NoPadding")
                    c.init(Cipher.DECRYPT_MODE, key(ks, alias), GCMParameterSpec(128, sealed, 0, 12))
                    return c.doFinal(sealed, 12, sealed.size - 12)
                } catch (e: AEADBadTagException) {
                    // not this key (lost, replaced, or a damaged file): try the next
                } catch (e: KeyPermanentlyInvalidatedException) {
                    // this key can never be used again: try the next
                } catch (e: Exception) {
                    // Keystore errors (busy, not ready, even a damaged key) all look alike here: passing, and
                    // WordStore gives up after a few in a row (Q13).
                    // A locked-only key refusing is the phone being locked to it, not a fault (Q33).
                    if (generation(alias) > LOCKED) { if (locked == null) locked = e } else if (passing == null) passing = e
                }
            }
            locked?.let { throw WordStore.Locked(it) }
            throw passing ?: WordStore.Unreadable()
        }

        override fun rotate(): Boolean {
            val ks = store()
            // Every new key is the locked kind (Q33), so a rotation from an older one starts at the first of those.
            val next = maxOf((aliases(ks).firstOrNull()?.let { generation(it) } ?: 0) + 1, LOCKED + 1)
            key(ks, aliasFor(next))
            return true
        }

        override fun retireOld() {
            val ks = store()
            aliases(ks).drop(1).forEach { ks.deleteEntry(it) }
            several = false
        }

        private fun key(ks: KeyStore, alias: String): SecretKey {
            (ks.getKey(alias, null) as? SecretKey)?.let { return it }
            val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            g.init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUnlockedDeviceRequired(generation(alias) > LOCKED)
                    .build()
            )
            return g.generateKey()
        }
    }

    companion object {
        private const val ALIAS_PREFIX = "keys_user_words_"
        /** The first key, as in 0.2.1. Later generations: keys_user_words_g2, _g3... */
        private const val ALIAS = "${ALIAS_PREFIX}v1"
        private const val GEN_PREFIX = "${ALIAS_PREFIX}g"
        /** Keys that refuse to work while the phone is locked (Q33): "_l1", "_l2"..., ranked above every "_g" key. */
        private const val LOCKED_PREFIX = "${ALIAS_PREFIX}l"
        private const val LOCKED = 1_000_000
        @Volatile private var instance: UserWords? = null

        fun get(context: Context): UserWords = instance ?: synchronized(this) {
            instance ?: UserWords(context.applicationContext).also { instance = it }
        }
    }
}
