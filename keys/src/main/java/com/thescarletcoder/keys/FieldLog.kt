package com.thescarletcoder.keys

import android.content.Context
import android.text.InputType

/**
 * Personal and debug builds only (BuildConfig.TUNING): the last few text boxes Keys opened in, as the app and the
 * kind of field it asked for, plus whether the number pad came up. Never any text. Stays on the device, in Keys'
 * own storage, so a field that should be numeric but says "text" can be found (the owner, 2026-10-09).
 * In credential-encrypted storage, so it can't be read before the first unlock, and never from private or password
 * boxes (the caller skips those, Q38). The device-protected copy older builds kept is deleted.
 */
object FieldLog {
    private const val PREFS = "keys_fields"
    private const val KEY = "recent"
    private const val MAX = 10
    /** The old device-protected copy is deleted once per run. */
    private var cleaned = false

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun unlocked(c: Context) = c.getSystemService(android.os.UserManager::class.java).isUserUnlocked

    fun add(c: Context, pkg: String?, cls: Int, variation: Int, numpad: Boolean) {
        if (!unlocked(c)) return
        if (!cleaned) { c.createDeviceProtectedStorageContext().deleteSharedPreferences(PREFS); cleaned = true }
        val line = "${pkg ?: "?"}  ·  ${kind(cls, variation)}  ·  ${if (numpad) "number pad" else "letters"}"
        val old = read(c)
        if (old.firstOrNull() == line) return
        prefs(c).edit().putString(KEY, (listOf(line) + old).take(MAX).joinToString("\n")).apply()
    }

    fun read(c: Context): List<String> =
        if (!unlocked(c)) emptyList() else prefs(c).getString(KEY, "").orEmpty().split("\n").filter { it.isNotBlank() }

    private fun kind(cls: Int, variation: Int): String = when (cls) {
        InputType.TYPE_CLASS_NUMBER -> "number"
        InputType.TYPE_CLASS_PHONE -> "phone"
        InputType.TYPE_CLASS_DATETIME -> "date/time"
        InputType.TYPE_CLASS_TEXT -> "text" + when (variation) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> " (password)"
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> " (email)"
            InputType.TYPE_TEXT_VARIATION_URI -> " (web address)"
            0 -> ""
            else -> " (variation $variation)"
        }
        0 -> "none (the app didn't say)"
        else -> "class $cls"
    }
}
