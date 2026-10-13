package com.thescarletcoder.keys

import android.app.KeyguardManager
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.view.View
import android.view.WindowManager
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
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
import androidx.lifecycle.LifecycleEventObserver
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
        // Taps that pass through another app's window on top are ignored, so an overlay can't trick a tap on Forget all
        // or a switch (slice 8).
        window.decorView.filterTouchesWhenObscured = true
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

    /** The PIN prompt that's up, if any: closed with this screen, so a rotation never leaves it pointing at the old one. */
    private var asking: CancellationSignal? = null

    override fun onDestroy() {
        asking?.cancel(); asking = null
        super.onDestroy()
    }

    /** Your words asks for the phone's PIN, fingerprint or face first, when the phone has one (Q35). */
    private fun confirmItsYou(open: () -> Unit) {
        if (!getSystemService(KeyguardManager::class.java).isDeviceSecure) { open(); return }
        BiometricPrompt.Builder(this)
            .setTitle("Your words")
            .setSubtitle("Confirm it's you to see your words.")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
            .authenticate(CancellationSignal().also { asking = it }, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { asking = null; open() }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { asking = null }
            })
    }

    private fun hideFromCapture(hide: Boolean) {
        if (hide) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setRecentsScreenshotEnabled(!hide)
        window.decorView.importantForContentCapture =
            if (hide) View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS else View.IMPORTANT_FOR_CONTENT_CAPTURE_AUTO
        window.decorView.importantForAutofill =
            if (hide) View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS else View.IMPORTANT_FOR_AUTOFILL_AUTO
        // Only accessibility tools that are real assistive tech (TalkBack, Switch Access) can read the words (Q42).
        window.decorView.setAccessibilityDataSensitive(
            if (hide) View.ACCESSIBILITY_DATA_SENSITIVE_YES else View.ACCESSIBILITY_DATA_SENSITIVE_AUTO,
        )
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
            // The last Forget's result (null: none yet); survives rotation, and a failure keeps the pill to try again.
            var forgetOk by rememberSaveable { mutableStateOf<Boolean?>(null) }
            // Saved across rotation and theme changes, so the page that's open stays open.
            var licenses by rememberSaveable { mutableStateOf(false) }
            var wordsPage by rememberSaveable { mutableStateOf(false) }
            val forget = {
                forgetting = true; forgetOk = null
                words.forgetAsync { ok, s -> forgetting = false; status = s; forgetOk = ok }
            }
            var readingIndex by rememberSaveable { mutableIntStateOf(-1) }
            val reading = Licenses.parts.getOrNull(readingIndex)
            BackHandler(enabled = tuning) { tuning = false }
            BackHandler(enabled = wordsPage) { wordsPage = false }
            // Your words is shut whenever Keyward leaves the screen (not on a rotation), so it's never restored, shown
            // in Recents or reopened without asking again (Q35).
            val owner = LocalLifecycleOwner.current
            DisposableEffect(owner) {
                val watch = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP && !isChangingConfigurations) wordsPage = false }
                owner.lifecycle.addObserver(watch)
                onDispose { owner.lifecycle.removeObserver(watch) }
            }
            // While it's open: no screenshots or recordings, blank in Recents, nothing for content capture or autofill
            // (which on-device assistants read) (Q35).
            val wordsOpen = wordsPage
            DisposableEffect(wordsOpen) {
                if (wordsOpen) hideFromCapture(true)
                // Leaving the page drops the words from memory too: Keyward may not be the keyboard in use, so no screen
                // off would ever drop them (Q33). Read again when needed.
                onDispose { if (wordsOpen) { hideFromCapture(false); if (!isChangingConfigurations) words.unloadInBackground() } }
            }
            BackHandler(enabled = licenses && reading == null) { licenses = false }
            BackHandler(enabled = reading != null) { readingIndex = -1 }
            val scroll = rememberScrollState()
            // Where the main page was, so Back from Tuning or the licenses returns to the same spot.
            var mainScroll by rememberSaveable { mutableIntStateOf(0) }
            // Moving to another page opens it at its top, and back to the main page where it was. Only on a real move:
            // after a rotation the scroll state restores itself.
            val page = when { tuning -> "tuning"; wordsPage -> "words"; reading != null -> "license $readingIndex"; licenses -> "licenses"; else -> "main" }
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
                if (licenses) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(reading?.license ?: "Open source licenses", color = Color(p.text), fontSize = 30.sp, fontFamily = fraunces,
                            modifier = Modifier.weight(1f).semantics { heading() })
                        Pill("Back", false, p, inter) { if (reading != null) readingIndex = -1 else licenses = false }
                    }
                    if (reading != null) LicenseText(reading, p, inter) else LicensesList(p, inter) { readingIndex = Licenses.parts.indexOf(it) }
                    return@Column
                }
                if (wordsPage) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Your words", color = Color(p.text), fontSize = 30.sp, fontFamily = fraunces,
                            modifier = Modifier.weight(1f).semantics { heading() })
                        Pill("Back", false, p, inter) { wordsPage = false }
                    }
                    WordsPage(words, status, { status = it }, forgetting, forgetOk, forget, prefs.learn, p, inter) { forgetOk = null }
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
                        Step(1, "Turn on Keyward", if (enabled) "On" else "Not on yet", enabled, p, inter) {
                            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        }
                        Step(2, "Switch to Keyward", if (current) "In use" else "Not in use", current, p, inter) {
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
                        "Learns words you use often (after three uses), so it stops correcting them. Single words only, encrypted on this device. Never from password fields or fields an app marks as private, like incognito tabs.",
                        prefs.learn, p, inter,
                    ) { prefs.learn = it }
                    // The list's state, if there's anything to say (Q2, Q12); the Your words page has the list and Forget (Q27).
                    noticeText(status?.notice)?.let { Text(it, color = Color(p.soft), fontSize = 14.sp, fontFamily = inter) }
                    val count = status?.count
                    NavCard("Your words", if (count == null) "Search, remove or add." else "${wordsCount(count)}. Search, remove or add.", "Open", p, inter,
                        role = Role.Button, clickLabel = "Open your words") { confirmItsYou { mainScroll = scroll.value; wordsPage = true } }
                }

                Section("Feel", p, inter) {
                    ChoiceRow("Theme", p, inter) {
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
                    val compact = Compact.isCompact(this@SettingsActivity)
                    ChoiceRow("Key height", p, inter) {
                        (if (compact) listOf(44 to "Small", 50 to "Medium", 58 to "Large") else listOf(56 to "Small", 64 to "Medium", 76 to "Large"))
                            .forEach { (dp, name) -> Pill(name, h == dp, p, inter, choice = true) { h = dp; prefs.keyHeight = dp } }
                    }
                    var bubble by remember { mutableStateOf(prefs.bubble) }
                    if (compact) Toggle("Show the letter above your finger", "A bubble with the letter you're pressing, above your thumb.", bubble, p, inter) {
                        prefs.bubble = it; bubble = it
                    }
                    Box(Modifier.padding(top = 4.dp)) { Preview(p, h, bubble && compact) }
                }

                Section("Try it", p, inter) {
                    var t by remember { mutableStateOf("") }
                    ScarletField(t, { t = it }, "Type here to test", p, inter)
                }

                // Settings doubles as the About page: what Keyward does to protect you, then who made it (the owner, Q46 to Q49).
                Section("Your privacy", p, inter) {
                    Promise(KeyIcons.NO_NETWORK, "No internet access. Keyward can't send anything anywhere, and nothing you type is logged.", p, inter)
                    Promise(KeyIcons.LOCK, "Your words are encrypted on this phone, with a key kept in Android's protected key store that only works while the phone is unlocked.", p, inter)
                    Promise(KeyIcons.FINGERPRINT, "Words used only once or twice aren't stored as text, and nothing records the order you typed in.", p, inter)
                    Promise(KeyIcons.HIDDEN, "The keyboard is hidden from screenshots, recordings and screen sharing, and it never learns from passwords or private boxes.", p, inter)
                    Promise(KeyIcons.NONE, "No ads, no tracking, no account.", p, inter)
                }

                Section("About", p, inter) {
                    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Keyward", color = Color(p.text), fontSize = 22.sp, fontFamily = fraunces)
                        Text("Version ${BuildConfig.VERSION_NAME}", color = Color(p.soft), fontSize = 13.sp, fontFamily = inter)
                        Text("A private keyboard for Android. Free and open source.", color = Color(p.soft), fontSize = 15.sp, fontFamily = inter)
                    }
                }

                NavCard("Open source licenses", "The free software Keyward is built with, and its licenses.", "Open", p, inter,
                    role = Role.Button, clickLabel = "Open the licenses") { mainScroll = scroll.value; licenses = true }

                if (BuildConfig.TUNING) {
                    // Which fields asked for what (the owner, 2026-10-09: number fields don't always open the number pad).
                    Section("Recent fields", p, inter) {
                        if (fields.isEmpty()) Text("Open a few text boxes, then come back.", color = Color(p.soft), fontSize = 14.sp, fontFamily = inter)
                        fields.forEach { Text(it, color = Color(p.text), fontSize = 14.sp, fontFamily = inter) }
                    }
                    NavCard("Tuning", "Feel values for this build. Not in public releases.", "Open", p, inter) { mainScroll = scroll.value; tuning = true }
                }

                MakerCard(p, fraunces, inter)
            }
        }
    }
}

private fun wordsCount(n: Int) = if (n == 1) "1 word" else "$n words"

/** One privacy point: a small teal icon and a plain sentence. */
@Composable
private fun Promise(icon: String, text: String, p: ScarletPalette, font: FontFamily) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Parsed once per size, not on every draw.
        Box(Modifier.padding(top = 2.dp).size(20.dp).drawWithCache {
            val path = KeyIcons.path(icon, size.width).asComposePath()
            val stroke = Stroke(width = KeyIcons.STROKE * size.width / KeyIcons.GRID, cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind { drawPath(path, Color(p.teal), style = stroke) }
        })
        Text(text, color = Color(p.text), fontSize = 15.sp, fontFamily = font, modifier = Modifier.weight(1f))
    }
}

/**
 * The Scarlet Coder card, as in the other Scarlet apps: the raven, the name, the address and the line. Information only:
 * no link out of the app (the owner, Q48). The address in scarlet as in Sudoku (Q49), in shades that read as text.
 */
@Composable
private fun MakerCard(p: ScarletPalette, fraunces: FontFamily, inter: FontFamily) {
    val card = Modifier.card(p).padding(16.dp).semantics(mergeDescendants = true) {}
    val raven = @Composable {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(44.dp)) { drawCircle(Color(p.scarlet).copy(alpha = 0.14f)) }
            Image(painterResource(R.mipmap.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(56.dp))
        }
    }
    // At large text sizes the raven goes above, so the address keeps its line instead of breaking mid-word.
    if (LocalDensity.current.fontScale > 1.3f) Column(card, verticalArrangement = Arrangement.spacedBy(10.dp)) { raven(); MakerText(p, fraunces, inter) }
    else Row(card, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) { raven(); MakerText(p, fraunces, inter) }
}

@Composable
private fun MakerText(p: ScarletPalette, fraunces: FontFamily, inter: FontFamily) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("The Scarlet Coder", color = Color(p.text), fontSize = 18.sp, fontFamily = fraunces)
        Text("thescarletcoder.com", color = Color(if (p.dark) 0xFFFF4D57.toInt() else 0xFFC40411.toInt()), fontSize = 14.sp,
            fontFamily = inter, fontWeight = FontWeight.Medium)
        Text("Honest tools · Fun games · No tricks", color = Color(p.soft), fontSize = 12.sp, fontFamily = inter)
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
        Text("Keyward", color = Color(p.text), fontSize = 36.sp, fontFamily = fraunces)
        Text("A keyboard by The Scarlet Coder. No internet access, nothing typed is ever logged or sent anywhere.",
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
            Text("Ready. Keyward is on and in use.", color = Color(p.text), fontSize = 17.sp, fontFamily = inter)
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
            KeyboardPanel(ctx, NoKeys, { _, _ -> }).also { it.strip.show(listOf("Your", "Keyward", "keyboard"), 1) }
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
    Column(
        Modifier.card(p).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = Color(p.soft), fontSize = 15.sp, fontFamily = font, fontWeight = FontWeight.Medium)
        content()
    }
}

/**
 * A label with its choice pills, kept together, padded like a [Toggle] row so every row in a section is the same
 * distance apart. The pills wrap onto a second line at large text sizes instead of squeezing (R4).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(label: String, p: ScarletPalette, font: FontFamily, pills: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Color(p.text), fontSize = 18.sp, fontFamily = font)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            pills()
        }
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
internal fun Pill(
    label: String, selected: Boolean, p: ScarletPalette, font: FontFamily, enabled: Boolean = true,
    /** One of a set (Theme, Key height): TalkBack says "radio button, selected" (K4). */
    choice: Boolean = false,
    /** A delete action: outlined in scarlet (Q30). The only other scarlet outline is a set-up step not done yet (S7-3). */
    danger: Boolean = false,
    clickLabel: String? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.defaultMinSize(minHeight = 48.dp).alpha(if (enabled) 1f else 0.5f).clip(shape)
            .background(Color(if (selected) p.tealWash else p.raised))
            .then(
                // Slightly lighter in dark so the outline reaches 3:1 on the button (Q44).
                if (danger) Modifier.border(1.5.dp, Color(if (p.dark) 0xFFF0323E.toInt() else p.scarlet), shape)
                else Modifier.border(1.dp, Color(if (selected) p.tealEdge else p.line), shape),
            )
            .then(
                if (choice) Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                else Modifier.clickable(enabled = enabled, role = Role.Button, onClickLabel = clickLabel, onClick = onClick),
            ).padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color(p.text), fontSize = 16.sp, fontFamily = font, fontWeight = FontWeight.Medium) }
}
