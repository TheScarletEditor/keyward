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

    /** Learn Sam's own words. Off until she turns it on. */
    var learn: Boolean
        get() = p.getBoolean("learn", false)
        set(v) = p.edit().putBoolean("learn", v).apply()

    /** Key height in dp. Until Sam picks one: 64 on the tablet, 50 on a phone-width screen. */
    var keyHeight: Int
        get() = p.getInt("key_height", if (Compact.isCompact(context)) 50 else 64)
        set(v) = p.edit().putInt("key_height", v).apply()
}
