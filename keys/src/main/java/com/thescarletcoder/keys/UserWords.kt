package com.thescarletcoder.keys

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.UserManager
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
        sealer = KeystoreSealer,
        unlocked = { context.getSystemService(UserManager::class.java).isUserUnlocked },
    )

    fun isKnown(word: String) = store.isKnown(word)
    fun boost(word: String) = store.boost(word)

    /** Learned words (used twice or more), for suggestions. */
    val learned: Collection<String> get() = store.learned
    val count: Int get() = store.count

    /** A list that couldn't be read on this device was set aside and a new one started. */
    val hasSetAside: Boolean get() = store.hasSetAside

    /** What Settings says about the list, if anything. */
    val notice: WordStore.Notice get() = store.notice

    fun record(word: String) = store.record(word)
    fun load() = store.load()
    fun save() = store.save()

    /** "Forget learned words": true only when every copy is gone. */
    fun clear(): Boolean = store.clear()

    /** What Settings shows: the count and the notice, if any. */
    data class Status(val count: Int, val notice: WordStore.Notice)

    /**
     * For Settings: the file and Keystore work on a background thread, the answer on the main thread. The thread
     * belongs to the store, not the screen, so a Forget started before Settings closes still finishes (Forget 4).
     */
    fun loadAsync(done: (Status) -> Unit) = io.execute {
        store.load()
        val s = Status(store.count, store.notice)
        main.post { done(s) }
    }

    fun forgetAsync(done: (Boolean, Status) -> Unit) = io.execute {
        val ok = store.clear()
        val s = Status(store.count, store.notice)
        main.post { done(ok, s) }
    }

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /**
     * AES-GCM with a Keystore key; the 12-byte IV goes first. Keys come in generations ([ALIAS] is the first, then
     * "_g2", "_g3"...): sealing uses the newest, opening tries each from the newest down, so a file sealed before a
     * rotation still opens until the old key is retired (A-D1).
     */
    private object KeystoreSealer : WordStore.Sealer {
        private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        private fun generation(alias: String): Int = when {
            alias == ALIAS -> 1
            alias.startsWith("$ALIAS_PREFIX$GEN") -> alias.removePrefix("$ALIAS_PREFIX$GEN").toIntOrNull() ?: 0
            else -> 0
        }

        private fun aliasFor(gen: Int) = if (gen == 1) ALIAS else "$ALIAS_PREFIX$GEN$gen"

        /** Whether more than one key existed at the last listing (so saves skip a Keystore call when there's one). */
        @Volatile private var several = true

        /** Existing key aliases of this store, newest first. */
        private fun aliases(ks: KeyStore): List<String> =
            ks.aliases().toList().filter { generation(it) > 0 }.sortedByDescending { generation(it) }.also { several = it.size > 1 }

        override fun hasOldKeys(): Boolean = several

        override fun seal(plain: ByteArray): ByteArray {
            val ks = store()
            val alias = aliases(ks).firstOrNull() ?: ALIAS
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key(ks, alias))
            return c.iv + c.doFinal(plain)
        }

        override fun open(sealed: ByteArray): ByteArray {
            if (sealed.size < 12 + 16) throw WordStore.Unreadable()
            val ks = store()
            // No key at all: a new one, so the file (sealed with a lost key) is found unreadable and set aside.
            val all = aliases(ks).ifEmpty { listOf(ALIAS.also { key(ks, it) }) }
            var passing: Exception? = null
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
                    if (passing == null) passing = e
                }
            }
            throw passing ?: WordStore.Unreadable()
        }

        override fun rotate(): Boolean {
            val ks = store()
            val next = (aliases(ks).firstOrNull()?.let { generation(it) } ?: 0) + 1
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
                    .build()
            )
            return g.generateKey()
        }
    }

    companion object {
        private const val ALIAS_PREFIX = "keys_user_words_"
        /** The first key, as in 0.2.1. Later generations: keys_user_words_g2, _g3... */
        private const val ALIAS = "${ALIAS_PREFIX}v1"
        private const val GEN = "g"
        @Volatile private var instance: UserWords? = null

        fun get(context: Context): UserWords = instance ?: synchronized(this) {
            instance ?: UserWords(context.applicationContext).also { instance = it }
        }
    }
}
