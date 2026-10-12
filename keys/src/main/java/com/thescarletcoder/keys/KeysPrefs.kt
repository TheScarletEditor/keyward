package com.thescarletcoder.keys

import android.content.Context
import android.content.SharedPreferences

/**
 * Keyboard settings. Device-protected storage so the keyboard works on the lock screen before first unlock.
 * Holds only switches, never anything typed.
 */
class KeysPrefs(private val context: Context) {
    private val p: SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences("keys", Context.MODE_PRIVATE)

    var sound: Boolean
        get() = p.getBoolean("sound", false)
        set(v) = p.edit().putBoolean("sound", v).apply()

    var vibrate: Boolean
        get() = p.getBoolean("vibrate", false)
        set(v) = p.edit().putBoolean("vibrate", v).apply()

    var suggestions: Boolean
        get() = p.getBoolean("suggestions", true)
        set(v) = p.edit().putBoolean("suggestions", v).apply()

    var autocorrect: Boolean
        get() = p.getBoolean("autocorrect", true)
        set(v) = p.edit().putBoolean("autocorrect", v).apply()

    /** Learn the user's own words. Off until they turn it on. */
    var learn: Boolean
        get() = p.getBoolean("learn", false)
        set(v) = p.edit().putBoolean("learn", v).apply()

    /** System / Dark / Light. */
    var theme: com.thescarletcoder.scarlet.ThemeChoice
        get() = com.thescarletcoder.scarlet.ScarletTokens.parseChoice(p.getString("theme", null))
        set(v) = p.edit().putString("theme", v.name).apply()

    /** "Show the letter above your finger": on by default on phone-width screens, never on a tablet. */
    var bubble: Boolean
        get() = Compact.isCompact(context) && p.getBoolean("bubble", true)
        set(v) = p.edit().putBoolean("bubble", v).apply()

    /** The set-up section has collapsed to "Ready" once already (its glide-in plays only the first time). */
    var readyShown: Boolean
        get() = p.getBoolean("ready_shown", false)
        set(v) = p.edit().putBoolean("ready_shown", v).apply()

    /** Read the file now (call off the main thread); every read after this is from memory. */
    fun preload() { p.all }

    fun listen(l: SharedPreferences.OnSharedPreferenceChangeListener) = p.registerOnSharedPreferenceChangeListener(l)
    fun unlisten(l: SharedPreferences.OnSharedPreferenceChangeListener) = p.unregisterOnSharedPreferenceChangeListener(l)

    /** Key height in dp. Until the user picks one: 64 on a tablet, 50 on a phone-width screen. */
    var keyHeight: Int
        get() = p.getInt("key_height", if (Compact.isCompact(context)) 50 else 64)
        set(v) = p.edit().putInt("key_height", v).apply()
}
