// Tunables v1 (2026-10-09). Keep identical in Keys and Lyrics.
package com.thescarletcoder.scarlet

import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf

/**
 * Feel values Sam tunes on the device without rebuilding (SCARLET-POLISH-FOUNDATION section 3).
 * Each app registers its own list once at startup. Reads are a plain in-memory lookup, cheap enough for every key press.
 */
data class Tunable(
    val name: String,
    val group: String,
    val default: Float,
    val min: Float,
    val max: Float,
    val step: Float,
    val isInt: Boolean = false,
)

object Tunables {
    private var prefs: SharedPreferences? = null
    private val all = LinkedHashMap<String, Tunable>()
    private val values = HashMap<String, Float>()

    /** Bumped on every change, so Compose screens and views can redraw. */
    val version = mutableIntStateOf(0)
    private val listeners = mutableListOf<() -> Unit>()

    /** Call once per process with the app's list and where to keep the values. Later calls only add new names. */
    @Synchronized
    fun init(store: SharedPreferences, list: List<Tunable>) {
        prefs = store
        list.forEach { t ->
            all[t.name] = t
            values[t.name] = store.getFloat(t.name, t.default).coerceIn(t.min, t.max)
        }
    }

    fun list(): List<Tunable> = all.values.toList()

    operator fun get(name: String): Float = values[name] ?: all[name]?.default ?: 0f
    fun int(name: String): Int = get(name).toInt()

    fun set(name: String, v: Float) {
        val t = all[name] ?: return
        val stepped = (Math.round((v - t.min) / t.step) * t.step + t.min).coerceIn(t.min, t.max)
        values[name] = stepped
        prefs?.edit()?.putFloat(name, stepped)?.apply()
        changed()
    }

    fun reset(name: String) { all[name]?.let { set(name, it.default) } }

    fun resetAll() {
        all.values.forEach { values[it.name] = it.default }
        prefs?.edit()?.apply { all.keys.forEach { remove(it) } }?.apply()
        changed()
    }

    /** "name = value" lines, for pasting back into the spec. */
    fun copyText(): String = all.values.joinToString("\n") { t ->
        "${t.name} = " + if (t.isInt) get(t.name).toInt().toString() else trim(get(t.name))
    }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    private fun changed() { version.intValue++; listeners.toList().forEach { it() } }

    internal fun trim(v: Float): String = if (v == Math.floor(v.toDouble()).toFloat()) v.toInt().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')
}
