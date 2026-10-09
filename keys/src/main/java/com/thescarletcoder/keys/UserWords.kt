package com.thescarletcoder.keys

import android.content.Context
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Words Sam actually uses, so the keyboard stops "correcting" them and ranks them higher.
 * Only words, with a use count: never sentences, never where they were typed.
 * Encrypted at rest with an Android Keystore key (AES-GCM). Only available after the tablet is unlocked.
 * The service never records in password fields or fields that ask for no personalized learning.
 */
class UserWords(private val context: Context) {
    private val counts = HashMap<String, Int>()
    private var dirty = false
    private var loaded = false

    /** A word counts as "known" once used twice. */
    @Synchronized
    fun isKnown(word: String) = (counts[word.lowercase()] ?: 0) >= 2

    @Synchronized
    fun boost(word: String) = ((counts[word.lowercase()] ?: 0) * 8).coerceAtMost(80)

    /** Learned words that aren't in the main dictionary (for suggestions). */
    val learned: Collection<String> @Synchronized get() = counts.filterValues { it >= 2 }.keys.toList()

    val count: Int @Synchronized get() = counts.size

    @Synchronized
    fun record(word: String) {
        val w = word.lowercase()
        if (w.length < 2 || w.length > 30 || w.any { !it.isLetter() && it != '\'' }) return
        counts[w] = (counts[w] ?: 0) + 1
        dirty = true
    }

    @Synchronized
    fun load() {
        if (loaded || !unlocked()) return
        loaded = true
        runCatching {
            val f = file()
            if (!f.exists()) return
            val blob = f.readBytes()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
            String(c.doFinal(blob, 12, blob.size - 12)).lineSequence().forEach { line ->
                val i = line.lastIndexOf('\t')
                if (i > 0) counts[line.substring(0, i)] = line.substring(i + 1).toIntOrNull() ?: 0
            }
        }
    }

    @Synchronized
    fun save() {
        if (!dirty || !unlocked()) return
        runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            val body = counts.entries.joinToString("\n") { "${it.key}\t${it.value}" }.toByteArray()
            val tmp = File(context.noBackupFilesDir, "words.tmp")
            tmp.writeBytes(c.iv + c.doFinal(body))
            tmp.renameTo(file())
            dirty = false
        }
    }

    /** "Forget learned words": real delete. */
    @Synchronized
    fun clear() {
        counts.clear()
        dirty = false
        runCatching { file().delete() }
    }

    private fun unlocked() = context.getSystemService(UserManager::class.java).isUserUnlocked
    private fun file() = File(context.noBackupFilesDir, "words.enc")

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

    private companion object { const val ALIAS = "keys_user_words_v1" }
}
