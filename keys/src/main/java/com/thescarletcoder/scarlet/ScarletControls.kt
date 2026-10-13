// ScarletControls v1 (2026-10-09). Keep identical in Keys and Lyrics.
package com.thescarletcoder.scarlet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Token-styled on/off switch. Teal track when on; moves with the NAV voice (instant when animations are removed). */
@Composable
fun ScarletSwitch(checked: Boolean, onChange: (Boolean) -> Unit, p: ScarletPalette, modifier: Modifier = Modifier) {
    val ms = Motion.duration(LocalContext.current, Motion.NAV_MS)
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, tween(ms, easing = Motion.NAV_EASE), label = "thumb")
    val track by animateColorAsState(Color(if (checked) p.tealWash else p.surface), tween(ms, easing = Motion.NAV_EASE), label = "track")
    val edge = Color(if (checked) p.tealEdge else p.line)
    Box(
        modifier.size(48.dp, 28.dp).background(track, CircleShape).border(1.dp, edge, CircleShape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Box(Modifier.offset(x = x).align(Alignment.CenterStart).size(22.dp).background(Color(if (checked) p.teal else p.soft), CircleShape))
    }
}

/** Token-styled single text field with a placeholder. */
@Composable
fun ScarletField(
    value: String, onChange: (String) -> Unit, placeholder: String, p: ScarletPalette, font: FontFamily,
    modifier: Modifier = Modifier, keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true, keyboardOptions = keyboardOptions,
        textStyle = TextStyle(color = Color(p.text), fontSize = 17.sp, fontFamily = font),
        cursorBrush = SolidColor(Color(p.text)),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().background(Color(p.raised), shape)
                    .border(1.dp, Color(if (focused) p.tealEdge else p.line), shape).padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, color = Color(p.faint), fontSize = 17.sp, fontFamily = font)
                inner()
            }
        },
    )
}
