package com.thescarletcoder.keys

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

private val Bg = Color(0xFF171614)
private val Surface = Color(0xFF211F1C)
private val Line = Color(0x1FFAF9F6)
private val Bone = Color(0xFFFAF9F6)
private val Soft = Color(0xFF908E86)
private val Teal = Color(0xFF5E9B8C)
private val ScarletSoft = Color(0x73E20514)
private val ScarletWash = Color(0x24E20514)

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = KeysPrefs(this)
        val words = UserWords(this).also { it.load() }
        val inter = FontFamily(Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium))

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Surface, primary = Teal)) {
                var tick by remember { mutableIntStateOf(0) }
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
                @Suppress("UNUSED_EXPRESSION") tick
                val imm = getSystemService(InputMethodManager::class.java)
                val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
                val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty().startsWith(packageName)

                Column(
                    Modifier.fillMaxSize().background(Bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Text("Scarlet Keys", color = Bone, fontSize = 34.sp, fontFamily = inter, fontWeight = FontWeight.Medium)
                    Text(
                        "Your keyboard. No internet access, nothing typed is ever logged or sent anywhere.",
                        color = Soft, fontSize = 16.sp, fontFamily = inter,
                    )

                    Section("Set up", inter) {
                        Step(1, "Turn on Scarlet Keys", if (enabled) "On" else "Not on yet", enabled, inter) {
                            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        }
                        Step(2, "Switch to Scarlet Keys", if (current) "In use" else "Not in use", current, inter) {
                            imm.showInputMethodPicker()
                        }
                        Text("Tip: hold the space bar any time to switch keyboards.", color = Soft, fontSize = 14.sp, fontFamily = inter)
                    }

                    Section("Typing", inter) {
                        Toggle("Suggestions", "Word suggestions above the keys.", prefs.suggestions, inter) { prefs.suggestions = it }
                        Toggle("Autocorrect", "Fixes clear typos when you press space. Backspace right after puts your word back.", prefs.autocorrect, inter) { prefs.autocorrect = it }
                        var learn by remember { mutableStateOf(prefs.learn) }
                        Toggle(
                            "Learn my words",
                            "Remembers words you use so it stops correcting them. Single words only, encrypted on this tablet. Never from password fields or Scarlet Command's private screens.",
                            learn, inter,
                        ) { prefs.learn = it; learn = it }
                        var count by remember { mutableIntStateOf(words.count) }
                        if (count > 0) {
                            Button("Forget learned words ($count)", inter) { words.clear(); count = 0 }
                        }
                    }

                    Section("Feel", inter) {
                        Toggle("Key sound", null, prefs.sound, inter) { prefs.sound = it }
                        Toggle("Key vibration", null, prefs.vibrate, inter) { prefs.vibrate = it }
                        var h by remember { mutableIntStateOf(prefs.keyHeight) }
                        Text("Key height", color = Bone, fontSize = 18.sp, fontFamily = inter)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            (if (Compact.isCompact(this@SettingsActivity)) listOf(44 to "Small", 50 to "Medium", 58 to "Large")
                            else listOf(56 to "Small", 64 to "Medium", 76 to "Large")).forEach { (dp, name) ->
                                Chip(name, h == dp, inter) { h = dp; prefs.keyHeight = dp }
                            }
                        }
                    }

                    Section("Try it", inter) {
                        var t by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = t, onValueChange = { t = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Type here to test", color = Soft) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Bone, unfocusedTextColor = Bone, cursorColor = Bone,
                                focusedBorderColor = ScarletSoft, unfocusedBorderColor = Line,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, font: FontFamily, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Surface).border(1.dp, Line, shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = Soft, fontSize = 15.sp, fontFamily = font, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun Step(n: Int, title: String, status: String, done: Boolean, font: FontFamily, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$n.  $title", color = Bone, fontSize = 18.sp, fontFamily = font, modifier = Modifier.weight(1f))
        Text(status, color = if (done) Teal else Soft, fontSize = 15.sp, fontFamily = font, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Toggle(title: String, detail: String?, initial: Boolean, font: FontFamily, onChange: (Boolean) -> Unit) {
    var on by remember { mutableStateOf(initial) }
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable { on = !on; onChange(on) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Bone, fontSize = 18.sp, fontFamily = font)
            if (detail != null) Text(detail, color = Soft, fontSize = 14.sp, fontFamily = font)
        }
        Switch(
            checked = on, onCheckedChange = { on = it; onChange(it) },
            colors = SwitchDefaults.colors(checkedThumbColor = Bone, checkedTrackColor = Teal, uncheckedThumbColor = Soft, uncheckedTrackColor = Bg, uncheckedBorderColor = Line),
        )
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, font: FontFamily, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.defaultMinSize(minWidth = 88.dp, minHeight = 56.dp).clip(shape)
            .background(if (selected) Teal.copy(alpha = 0.22f) else Bg)
            .border(1.dp, if (selected) Teal.copy(alpha = 0.7f) else Line, shape)
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) Bone else Soft, fontSize = 16.sp, fontFamily = font) }
}

@Composable
private fun Button(text: String, font: FontFamily, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.defaultMinSize(minHeight = 56.dp).clip(shape).background(ScarletWash).border(1.dp, ScarletSoft, shape)
            .clickable(onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Bone, fontSize = 16.sp, fontFamily = font, fontWeight = FontWeight.Medium) }
}
