package com.thescarletcoder.keys

import android.content.Context
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
        dir = context.noBackupFilesDir,
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

    /** AES-GCM with a Keystore key; the 12-byte IV goes first. */
    private object KeystoreSealer : WordStore.Sealer {
        override fun seal(plain: ByteArray): ByteArray {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            return c.iv + c.doFinal(plain)
        }

        override fun open(sealed: ByteArray): ByteArray {
            if (sealed.size < 12 + 16) throw WordStore.Unreadable()
            // Keystore errors (busy, not ready, even a damaged key) all look alike here: WordStore treats them as
            // passing and gives up after a few in a row (Q13).
            val k = key()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            try {
                c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, sealed, 0, 12))
                return c.doFinal(sealed, 12, sealed.size - 12)
            } catch (e: AEADBadTagException) {
                throw WordStore.Unreadable(e) // wrong key (lost or replaced) or a damaged file
            } catch (e: KeyPermanentlyInvalidatedException) {
                throw WordStore.Unreadable(e)
            }
            // Anything else (a busy key store, a device not quite unlocked) is passing: WordStore tries again later.
        }

        override fun reset() {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
        }

        private fun key(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            g.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            return g.generateKey()
        }
    }

    companion object {
        private const val ALIAS = "keys_user_words_v1"
        @Volatile private var instance: UserWords? = null

        fun get(context: Context): UserWords = instance ?: synchronized(this) {
            instance ?: UserWords(context.applicationContext).also { instance = it }
        }
    }
}
