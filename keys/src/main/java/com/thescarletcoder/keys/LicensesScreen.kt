package com.thescarletcoder.keys

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thescarletcoder.scarlet.ScarletPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The list of parts; tapping one shows its license in full ([LicenseText]). */
@Composable
fun LicensesList(p: ScarletPalette, font: FontFamily, onOpen: (Licenses.Part) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Scarlet Keys is free software. It's built with these, each under its own license.",
            color = Color(p.soft), fontSize = 16.sp, fontFamily = font)
        Licenses.parts.forEach { part ->
            NavCard(part.name, part.license, "Read", p, font, titleSize = 17, role = Role.Button, clickLabel = "Read the license") { onOpen(part) }
        }
    }
}

/** A card that opens another page: title, a line under it, and a word on the right ("Open", "Read"). */
@Composable
internal fun NavCard(
    title: String, note: String, action: String, p: ScarletPalette, font: FontFamily,
    titleSize: Int = 18, role: Role? = null, clickLabel: String? = null, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(p.surface))
            .border(1.dp, Color(p.line), RoundedCornerShape(16.dp))
            .clickable(role = role, onClickLabel = clickLabel, onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(p.text), fontSize = titleSize.sp, fontFamily = font)
            Text(note, color = Color(p.soft), fontSize = 14.sp, fontFamily = font)
        }
        Text(action, color = Color(p.soft), fontSize = 15.sp, fontFamily = font)
    }
}

/** One license in full, read from the APK off the main thread. */
@Composable
fun LicenseText(part: Licenses.Part, p: ScarletPalette, font: FontFamily) {
    val context = LocalContext.current
    var text by remember(part) { mutableStateOf(Licenses.cache[part.asset]) }
    LaunchedEffect(part) {
        if (text != null) return@LaunchedEffect
        text = withContext(Dispatchers.IO) {
            runCatching { Licenses.reflow(context.assets.open(part.asset).bufferedReader().use { it.readText() }) }
                .onSuccess { Licenses.cache[part.asset] = it }
                .getOrDefault("This license text is missing from the app. It's in the source code on GitHub.")
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(part.name, color = Color(p.soft), fontSize = 15.sp, fontFamily = font)
        text?.let { Text(it, color = Color(p.text), fontSize = 14.sp, fontFamily = font, lineHeight = 20.sp) }
    }
}
