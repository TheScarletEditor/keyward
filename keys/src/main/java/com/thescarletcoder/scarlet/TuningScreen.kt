// TuningScreen v1 (2026-10-09). Keep identical in Keys and Lyrics. Personal and debug builds only.
package com.thescarletcoder.scarlet

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Every tunable, grouped, each with a slider, its value and a reset. Changes apply live. */
@Composable
fun TuningPanel(p: ScarletPalette, font: FontFamily) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = Tunables.version.intValue // redraw on change
    val text = Color(p.text); val soft = Color(p.soft); val line = Color(p.line); val teal = Color(p.teal)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Values apply as you move them. Copy values puts them on the clipboard as name = value lines.",
            color = soft, fontSize = 14.sp, fontFamily = font)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton("Copy values", p, font) {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Tuning", Tunables.copyText()))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }
            SmallButton("Reset all", p, font) { Tunables.resetAll() }
        }
        Tunables.list().groupBy { it.group }.forEach { (group, items) ->
            Text(group, color = soft, fontSize = 15.sp, fontFamily = font, fontWeight = FontWeight.Medium)
            items.forEach { t ->
                val cur = Tunables[t.name]
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.name, color = text, fontSize = 15.sp, fontFamily = font, modifier = Modifier.weight(1f))
                        Text(if (t.isInt) cur.toInt().toString() else Tunables.trim(cur), color = text, fontSize = 15.sp,
                            fontFamily = font, modifier = Modifier.width(64.dp))
                        SmallButton("Reset", p, font, enabled = cur != t.default) { Tunables.reset(t.name) }
                    }
                    Slider(
                        value = cur, onValueChange = { Tunables.set(t.name, it) }, valueRange = t.min..t.max,
                        colors = SliderDefaults.colors(thumbColor = teal, activeTrackColor = teal, inactiveTrackColor = line),
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallButton(label: String, p: ScarletPalette, font: FontFamily, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.defaultMinSize(minHeight = 40.dp).clip(shape).background(Color(p.raised)).border(1.dp, Color(p.line), shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color(if (enabled) p.text else p.faint), fontSize = 14.sp, fontFamily = font, fontWeight = FontWeight.Medium) }
}
