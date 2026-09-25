package com.foxdrop.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private val Faint = Color.White.copy(alpha = 0.7f)

/** Each variant gets its own colour so a row of chips reads at a glance. */
private fun variantColor(id: String) = when (id) {
    "gold" -> Color(0xFFE8C02A)
    "cheat" -> Color(0xFFB57BFF)
    "loot" -> Color(0xFF4CD18A)
    "bounty" -> Color(0xFFFF6B6B)
    else -> Sky
}

@Composable
fun SpritesScreen(vm: FoxViewModel) {
    val load = vm.sprites
    val list = load.value
    val got by vm.prefs.spritesGot.collectAsState()
    var needOnly by remember { mutableStateOf(false) }
    if (list == null) {
        if (load.loading) Spinner() else Problem(load.error ?: "Couldn't load the Sprite list. Pull down to try again.")
        return
    }
    val keys = list.keys
    val have = keys.count { it in got }
    val variantNames = list.variants.associateBy { it.id }
    val shown = if (needOnly) list.sprites.filter { s -> s.variants.any { "${s.id}:$it" !in got } } else list.sprites

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sprite collection", fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text(list.season, fontSize = 13.sp, color = Sky)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$have", fontWeight = FontWeight.Black, fontSize = 34.sp, color = FoxOrange)
                        Text("  of ${keys.size} collected", color = Faint, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    LinearProgressIndicator(
                        progress = { if (keys.isEmpty()) 0f else have.toFloat() / keys.size },
                        color = FoxOrange, trackColor = Night, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Toggle("All Sprites", !needOnly) { needOnly = false }
                        Toggle("Still need", needOnly) { needOnly = true }
                    }
                }
            }
        }
        item { Problem(load.error?.takeIf { !load.loading }) }
        if (shown.isEmpty()) item {
            Text("You've collected every Sprite this season! 🦊🏆", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        }
        items(shown, key = { it.id }) { s -> SpriteCard(s, variantNames, got, vm.prefs::toggleSprite) }
        item {
            Text(
                "Tap a version when you've collected it. Sprites come from Cheat Code chests: find the injector and " +
                    "punch in the arrows. " + list.variants.filter { it.note.isNotBlank() }.joinToString(" ") { "${it.name}: ${it.note.lowercase()}." } +
                    "\nList updated ${list.updated}." + (if (list.credit.isNotBlank()) " ${list.credit}" else ""),
                fontSize = 12.sp, color = Faint, modifier = Modifier.padding(4.dp),
            )
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    if (on) Button(onClick, colors = ButtonDefaults.buttonColors(containerColor = FoxOrange)) { Text(label) }
    else OutlinedButton(onClick) { Text(label) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpriteCard(s: Sprite, names: Map<String, SpriteVariant>, got: Set<String>, onToggle: (String) -> Unit) {
    val count = s.variants.count { "${s.id}:$it" in got }
    val done = count == s.variants.size
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = if (done) BorderStroke(2.dp, Color(0xFFE8C02A)) else null,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (s.img.isNotBlank()) {
                    AsyncImage(
                        model = s.img, contentDescription = "${s.name} Sprite",
                        modifier = Modifier.size(64.dp).padding(end = 10.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Text(if (done) "🏆 all ${s.variants.size}" else "$count / ${s.variants.size}", color = if (done) Color(0xFFE8C02A) else Faint, fontWeight = FontWeight.Bold)
                    }
                    if (s.ability.isNotBlank()) Text(s.ability, fontSize = 14.sp)
                    if (s.where.isNotBlank()) Text("📍 ${s.where}", fontSize = 13.sp, color = Faint)
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.variants.forEach { v ->
                    val key = "${s.id}:$v"
                    val have = key in got
                    val c = variantColor(v)
                    Text(
                        (if (have) "✓ " else "") + (names[v]?.name ?: v),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (have) Night else c,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (have) c else Color.Transparent)
                            .border(2.dp, c, RoundedCornerShape(50))
                            .clickable { onToggle(key) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}
