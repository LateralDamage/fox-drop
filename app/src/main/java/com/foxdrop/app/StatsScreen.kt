package com.foxdrop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

private val Faint = Color.White.copy(alpha = 0.7f)
private val platforms = listOf("epic" to "PC / Epic", "psn" to "PlayStation", "xbl" to "Xbox")
private val modeNames = listOf("solo" to "Solo", "duo" to "Duos", "squad" to "Squads", "ltm" to "Other modes")

/** Battle Royale stats by Fortnite name: no login, but the player's stats must be public in Fortnite. */
@Composable
fun StatsDialog(vm: FoxViewModel, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var name by remember { mutableStateOf(vm.prefs.statsName) }
        var platform by remember { mutableStateOf(vm.prefs.statsPlatform) }
        var season by remember { mutableStateOf(false) }
        val look = { vm.lookupStats(name, platform, season) }
        Column(Modifier.fillMaxSize().background(Night).statusBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📊 Player stats", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close stats", tint = Color.White) }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    name, { name = it.take(32) }, singleLine = true, label = { Text("Fortnite name") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { look() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Pills(platforms, platform) { platform = it }
                Pills(listOf("life" to "All time", "season" to "This season"), if (season) "season" else "life") { season = it == "season" }
                Button(
                    onClick = look, enabled = name.isNotBlank() && !vm.stats.loading,
                    colors = ButtonDefaults.buttonColors(containerColor = FoxOrange), modifier = Modifier.fillMaxWidth(),
                ) { Text(if (vm.stats.loading) "Looking…" else "Look up") }
                Text(
                    "Pick the platform the Fortnite name belongs to (PC / Epic works for most players). Stats have to be public: " +
                        "Fortnite → Settings → Account and Privacy → Show on Career Leaderboard.",
                    fontSize = 12.sp, color = Faint,
                )
                Problem(vm.stats.error)
                vm.stats.value?.let { StatsResult(it) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Pills(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, label) ->
            if (id == selected) Button({ onPick(id) }, colors = ButtonDefaults.buttonColors(containerColor = FoxOrange)) { Text(label) }
            else OutlinedButton({ onPick(id) }) { Text(label) }
        }
    }
}

@Composable
private fun StatsResult(s: PlayerStats) {
    val o = s.modes["overall"]
    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(16.dp)) {
            Text(s.name, fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color.White)
            s.battlePassLevel?.let { Text("Battle Pass level $it", color = Sky) }
            if (o == null) Text("No matches yet.", color = Faint)
            else {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Big("${o.wins}", "Wins", FoxOrange)
                    Big(pct(o.winRate), "Win rate", Color.White)
                    Big("${o.kills}", "Eliminations", Color.White)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Big(String.format(Locale.US, "%.2f", o.kd), "K/D", Color.White)
                    Big("${o.matches}", "Matches", Color.White)
                    Big("${o.minutesPlayed / 60}h", "Played", Color.White)
                }
            }
        }
    }
    modeNames.forEach { (id, label) ->
        val m = s.modes[id] ?: return@forEach
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                Small("${m.wins}", "wins")
                Small("${m.kills}", "elims")
                Small(String.format(Locale.US, "%.2f", m.kd), "K/D")
                Small("${m.matches}", "games")
            }
        }
    }
}

private fun pct(v: Double) = if (v >= 10) "${v.toInt()}%" else String.format(Locale.US, "%.1f%%", v)

@Composable
private fun Big(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 26.sp, color = color)
        Text(label, fontSize = 12.sp, color = Faint)
    }
}

@Composable
private fun Small(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
        Text(value, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 11.sp, color = Faint)
    }
}
