package com.thescarletcoder.keys

import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import android.os.StrictMode
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.repeatOnLifecycle
import com.thescarletcoder.scarlet.Motion
import com.thescarletcoder.scarlet.ScarletField
import com.thescarletcoder.scarlet.ScarletPalette
import com.thescarletcoder.scarlet.ScarletSwitch
import com.thescarletcoder.scarlet.ScarletTokens
import com.thescarletcoder.scarlet.ThemeChoice
import com.thescarletcoder.scarlet.TuningPanel
import com.thescarletcoder.scarlet.Tunables

@OptIn(ExperimentalLayoutApi::class)
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().penaltyLog().build())
        // Settings, tuning values and "Recent fields" are read on a background thread, then the page is drawn (Q14).
        // Until then the window shows its plain background (light or dark with the system), never a wrong value.
        // After the first time in this process everything is in memory, so a rotation, fold or resize draws at once (R5).
        warm?.let { (prefs, fields) -> show(prefs, fields); return }
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                // The app's context, not this screen's: the settings object outlives this screen (kept below).
                val prefs = KeysPrefs(applicationContext).also { it.preload() }
                KeysTuning.init(this@SettingsActivity)
                prefs to (if (BuildConfig.TUNING) FieldLog.read(this@SettingsActivity) else emptyList())
            }
            warm = loaded
            show(loaded.first, loaded.second)
        }
    }

    private companion object {
        /** Set once preferences and tuning are loaded in this process: the settings and "Recent fields" as read then. */
        @Volatile var warm: Pair<KeysPrefs, List<String>>? = null
    }

    private fun show(prefs: KeysPrefs, fields: List<String>) {
        // Learned words are read off the main thread below (K5).
        val words = UserWords.get(this)
        val inter = FontFamily(Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium))
        val fraunces = FontFamily(Font(R.font.fraunces_display))

        setContent {
            var choice by remember { mutableStateOf(prefs.theme) }
            val uiMode = LocalConfiguration.current.uiMode // the system flipping light/dark recomposes this
            val p = remember(choice, uiMode) { ScarletTokens.resolve(choice) }
            LaunchedEffect(p.dark) {
                val bars = if (p.dark) SystemBarStyle.dark(TRANSPARENT) else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            var tuning by remember { mutableStateOf(false) }
            // Learned words: up here, outside the pages, so opening Tuning or the licenses doesn't reset a Forget in
            // progress or its result (R3). Nothing shows until the store has been read off the main thread (K5).
            var status by remember { mutableStateOf<UserWords.Status?>(null) }
            LaunchedEffect(Unit) { words.loadAsync { status = it } }
            var forgetting by remember { mutableStateOf(false) }
            var forgetResult by rememberSaveable { mutableStateOf<String?>(null) } // survives rotation
            var forgetFailed by rememberSaveable { mutableStateOf(false) } // keeps the pill to try again
            // Saved across rotation and theme changes, so the page that's open stays open.
            var licenses by rememberSaveable { mutableStateOf(false) }
            var readingIndex by rememberSaveable { mutableIntStateOf(-1) }
            val reading = Licenses.parts.getOrNull(readingIndex)
            BackHandler(enabled = tuning) { tuning = false }
            BackHandler(enabled = licenses && reading == null) { licenses = false }
            BackHandler(enabled = reading != null) { readingIndex = -1 }
            val scroll = rememberScrollState()
            // Where the main page was, so Back from Tuning or the licenses returns to the same spot.
            var mainScroll by rememberSaveable { mutableIntStateOf(0) }
            // Moving to another page opens it at its top, and back to the main page where it was. Only on a real move:
            // after a rotation the scroll state restores itself.
            val page = when { tuning -> "tuning"; reading != null -> "license $readingIndex"; licenses -> "licenses"; else -> "main" }
            var shownPage by rememberSaveable { mutableStateOf(page) }
            LaunchedEffect(page) {
                if (page == shownPage) return@LaunchedEffect
                if (page == "main") {
                    // Wait for the main page to be laid out (its height), or the scroll stops at the short page's end.
                    withTimeoutOrNull(1000) { snapshotFlow { scroll.maxValue }.first { it >= mainScroll } }
                    scroll.scrollTo(mainScroll)
                } else scroll.scrollTo(0)
                shownPage = page
            }

            Column(
                Modifier.fillMaxSize().background(Color(p.bg)).safeDrawingPadding().verticalScroll(scroll).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val open = reading
                if (licenses) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(open?.license ?: "Open source licenses", color = Color(p.text), fontSize = 30.sp, fontFamily = fraunces,
                            modifier = Modifier.weight(1f).semantics { heading() })
                        Pill("Back", false, p, inter) { if (open != null) readingIndex = -1 else licenses = false }
                    }
                    if (open != null) LicenseText(open, p, inter) else LicensesList(p, inter) { readingIndex = Licenses.parts.indexOf(it) }
                    return@Column
                }
                if (tuning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tuning", color = Color(p.text), fontSize = 30.sp, fontFamily = fraunces, modifier = Modifier.weight(1f))
                        Pill("Back", false, p, inter) { tuning = false }
                    }
                    TuningPanel(p, inter)
                    return@Column
                }

                Header(p, fraunces, inter)

                var tick by remember { mutableIntStateOf(0) }
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
                @Suppress("UNUSED_EXPRESSION") tick
                val imm = getSystemService(InputMethodManager::class.java)
                val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
                val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty().startsWith(packageName)

                Section("Set up", p, inter) {
                    if (enabled && current) Ready(p, inter, firstTime = !prefs.readyShown) { prefs.readyShown = true }
                    else {
                        Step(1, "Turn on Scarlet Keys", if (enabled) "On" else "Not on yet", enabled, p, inter) {
                            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        }
                        Step(2, "Switch to Scarlet Keys", if (current) "In use" else "Not in use", current, p, inter) {
                            imm.showInputMethodPicker()
                        }
                    }
                    Text("Tip: hold the space bar any time to switch keyboards.", color = Color(p.soft), fontSize = 14.sp, fontFamily = inter)
                }

                Section("Typing", p, inter) {
                    Toggle("Suggestions", "Word suggestions above the keys.", prefs.suggestions, p, inter) { prefs.suggestions = it }
                    Toggle("Autocorrect", "Fixes clear typos when you press space. Backspace right after puts your word back.", prefs.autocorrect, p, inter) { prefs.autocorrect = it }
                    Toggle(
                        "Learn my words",
                        "Remembers words you use so it stops correcting them. Single words only, encrypted on this device. Never from password fields or fields an app marks as private, like incognito tabs.",
                        prefs.learn, p, inter,
                    ) { prefs.learn = it }
                    // The list's state, if there's anything to say (Q2, Q12). Forget clears every case and starts a new list.
                    val notice = status?.notice ?: WordStore.Notice.NONE
                    val count = status?.count ?: 0
                    val noticeText = when (notice) {
                        WordStore.Notice.SET_ASIDE -> "Your learned words couldn't be read on this device, so it started a new list."
                        WordStore.Notice.NEWER -> "Your learned words were saved by a newer version of Scarlet Keys. This version leaves them as they are and won't learn new words."
                        WordStore.Notice.NOT_LEARNING -> "Your learned words couldn't be read, so Scarlet Keys isn't learning new words for now."
                        WordStore.Notice.NONE -> null
                    }
                    noticeText?.let { Text(it, color = Color(p.soft), fontSize = 14.sp, fontFamily = inter) }
                    if (count > 0 || noticeText != null || forgetFailed) {
                        // One Forget at a time (Forget 3); "Forgotten." only when every copy is gone (Forget 1).
                        Pill(if (count > 0) "Forget learned words ($count)" else "Forget learned words", false, p, inter, enabled = !forgetting) {
                            forgetting = true; forgetResult = null
                            words.forgetAsync { ok, s ->
                                forgetting = false; status = s; forgetFailed = !ok
                                forgetResult = if (ok) "Forgotten." else "Couldn't forget your words. Try again."
                            }
                        }
                    }
                    forgetResult?.let { Text(it, color = Color(p.soft), fontSize = 14.sp, fontFamily = inter) }
                }

                Section("Feel", p, inter) {
                    Text("Theme", color = Color(p.text), fontSize = 18.sp, fontFamily = inter)
                    // Wraps onto a second line at large text sizes instead of squeezing the pills (R4).
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(ThemeChoice.SYSTEM to "System", ThemeChoice.DARK to "Dark", ThemeChoice.LIGHT to "Light").forEach { (c, name) ->
                            Pill(name, choice == c, p, inter, choice = true) {
                                choice = c; prefs.theme = c
                                ScarletTokens.applyToSystem(this@SettingsActivity, c)
                            }
                        }
                    }
                    Toggle("Key sound", null, prefs.sound, p, inter) { prefs.sound = it }
                    Toggle("Key vibration", null, prefs.vibrate, p, inter) { prefs.vibrate = it }
                    var h by remember { mutableIntStateOf(prefs.keyHeight) }
                    Text("Key height", color = Color(p.text), fontSize = 18.sp, fontFamily = inter)
                    val compact = Compact.isCompact(this@SettingsActivity)
                    // Wraps onto a second line at large text sizes instead of squeezing the pills (R4).
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        (if (compact) listOf(44 to "Small", 50 to "Medium", 58 to "Large") else listOf(56 to "Small", 64 to "Medium", 76 to "Large"))
                            .forEach { (dp, name) -> Pill(name, h == dp, p, inter, choice = true) { h = dp; prefs.keyHeight = dp } }
                    }
                    var bubble by remember { mutableStateOf(prefs.bubble) }
                    if (compact) Toggle("Show the letter above your finger", "A bubble with the letter you're pressing, above your thumb.", bubble, p, inter) {
                        prefs.bubble = it; bubble = it
                    }
                    Preview(p, h, bubble && compact)
                }

                Section("Try it", p, inter) {
                    var t by remember { mutableStateOf("") }
                    ScarletField(t, { t = it }, "Type here to test", p, inter)
                }

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(p.surface))
                        .border(1.dp, Color(p.line), RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClickLabel = "Open the licenses") { mainScroll = scroll.value; licenses = true }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Open source licenses", color = Color(p.text), fontSize = 18.sp, fontFamily = inter)
                        Text("The free software Scarlet Keys is built with, and its licenses.", color = Color(p.soft), fontSize = 14.sp, fontFamily = inter)
                    }
                    Text("Open", color = Color(p.soft), fontSize = 15.sp, fontFamily = inter)
                }

                if (BuildConfig.TUNING) {
                    // Which fields asked for what (the owner, 2026-10-09: number fields don't always open the number pad).
                    Section("Recent fields", p, inter) {
                        val rows = fields
                        if (rows.isEmpty()) Text("Open a few text boxes, then come back.", color = Color(p.soft), fontSize = 14.sp, fontFamily = inter)
                        rows.forEach { Text(it, color = Color(p.text), fontSize = 14.sp, fontFamily = inter) }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(p.surface))
                            .border(1.dp, Color(p.line), RoundedCornerShape(16.dp)).clickable { mainScroll = scroll.value; tuning = true }.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Tuning", color = Color(p.text), fontSize = 18.sp, fontFamily = inter)
                            Text("Feel values for this build. Not in public releases.", color = Color(p.soft), fontSize = 14.sp, fontFamily = inter)
                        }
                        Text("Open", color = Color(p.soft), fontSize = 15.sp, fontFamily = inter)
                    }
                }
            }
        }
    }
}

/** The raven with a soft scarlet halo, then the name in Fraunces. */
@Composable
private fun Header(p: ScarletPalette, fraunces: FontFamily, inter: FontFamily) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(84.dp)) { drawCircle(Color(p.scarlet).copy(alpha = 0.14f)) }
            Image(painterResource(R.mipmap.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(112.dp))
        }
        Text("Scarlet Keys", color = Color(p.text), fontSize = 36.sp, fontFamily = fraunces)
        Text("Your keyboard. No internet access, nothing typed is ever logged or sent anywhere.",
            color = Color(p.soft), fontSize = 16.sp, fontFamily = inter)
    }
}

/** Both set-up steps done: one line. The first time, it glides in (LUX). */
@Composable
private fun Ready(p: ScarletPalette, inter: FontFamily, firstTime: Boolean, onShown: () -> Unit) {
    val ms = Motion.duration(LocalContext.current, Motion.LUX_MS)
    val state = remember { MutableTransitionState(!firstTime).apply { targetState = true } }
    LaunchedEffect(Unit) { if (firstTime) onShown() }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(ms, easing = Motion.LUX_EASE)) + slideInVertically(tween(ms, easing = Motion.LUX_EASE)) { it / 2 },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Check(Color(p.teal))
            Text("Ready. Scarlet Keys is on and in use.", color = Color(p.text), fontSize = 17.sp, fontFamily = inter)
        }
    }
}

@Composable
private fun Check(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val s = size.width / 24f
        val path = Path().apply { moveTo(5f * s, 12.5f * s); lineTo(10f * s, 17.5f * s); lineTo(19f * s, 7f * s) }
        drawPath(path, color, style = Stroke(width = 2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A real keyboard that redraws as theme, key height, depth and bubble settings change. Types nowhere. */
@Composable
private fun Preview(p: ScarletPalette, keyHeight: Int, bubble: Boolean) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val tuned = Tunables.version.intValue
    AndroidView(
        factory = { ctx ->
            KeyboardPanel(ctx, NoKeys, {}).also { it.strip.show(listOf("Scarlet", "Keys", "keyboard"), 1) }
        },
        update = { panel ->
            panel.apply(KeyColors(p), keyHeight, bubble, Motion.scale(context))
            panel.keyboard.invalidate()
        },
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
    )
}

private object NoKeys : KeyListener {
    override fun onPressFeedback(key: Key) = Unit
    override fun onKey(key: Key) = Unit
    override fun onAlt(text: String) = Unit
    override fun onDeleteRepeat() = Unit
    override fun onSpaceLongPress() = Unit
    override fun onShiftLock() = Unit
}

@Composable
private fun Section(title: String, p: ScarletPalette, font: FontFamily, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(p.surface)).border(1.dp, Color(p.line), shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = Color(p.soft), fontSize = 15.sp, fontFamily = font, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun Step(n: Int, title: String, status: String, done: Boolean, p: ScarletPalette, font: FontFamily, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color(if (done) p.raised else p.scarletWash))
            .border(1.dp, Color(if (done) p.line else p.scarletEdge), shape).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$n", color = Color(p.soft), fontSize = 18.sp, fontFamily = font, modifier = Modifier.padding(end = 14.dp))
        Text(title, color = Color(p.text), fontSize = 18.sp, fontFamily = font, modifier = Modifier.weight(1f))
        Text(status, color = Color(if (done) p.teal else p.soft), fontSize = 15.sp, fontFamily = font)
    }
}

@Composable
private fun Toggle(title: String, note: String?, initial: Boolean, p: ScarletPalette, font: FontFamily, onChange: (Boolean) -> Unit) {
    var on by remember { mutableStateOf(initial) }
    // One item for TalkBack: "title, note, switch, on" (K4); the switch inside doesn't get its own stop.
    Row(
        Modifier.fillMaxWidth().toggleable(value = on, role = Role.Switch) { on = it; onChange(it) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color(p.text), fontSize = 18.sp, fontFamily = font)
            note?.let { Text(it, color = Color(p.soft), fontSize = 14.sp, fontFamily = font) }
        }
        ScarletSwitch(on, { on = it; onChange(it) }, p, Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun Pill(
    label: String, selected: Boolean, p: ScarletPalette, font: FontFamily, enabled: Boolean = true,
    /** One of a set (Theme, Key height): TalkBack says "radio button, selected" (K4). */
    choice: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.defaultMinSize(minHeight = 48.dp).alpha(if (enabled) 1f else 0.5f).clip(shape)
            .background(Color(if (selected) p.tealWash else p.raised))
            .border(1.dp, Color(if (selected) p.tealEdge else p.line), shape)
            .then(
                if (choice) Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            ).padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color(p.text), fontSize = 16.sp, fontFamily = font, fontWeight = FontWeight.Medium) }
}
