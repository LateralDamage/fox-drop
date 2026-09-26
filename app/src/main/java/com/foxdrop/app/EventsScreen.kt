package com.foxdrop.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val fullWhen = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a").withZone(ZoneId.systemDefault())
private val dayOnly = DateTimeFormatter.ofPattern("EEE MMM d").withZone(ZoneId.systemDefault())
private val endTime = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())

/** Ticks once a second while on screen. */
@Composable
private fun rememberNow(): Instant {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    return Instant.ofEpochMilli(now)
}

private fun whenText(e: LiveEvent) =
    if (e.approx) "${dayOnly.format(e.start)} · time not announced yet"
    else fullWhen.format(e.start) + (e.end?.let { " – " + endTime.format(it) } ?: "")

/** "ends in 2:41:10" while a timed event with an end is on, else null. */
private fun endsIn(e: LiveEvent, now: Instant) = e.end?.takeIf { e.isLive(now) }?.let { countdown(Duration.between(now, it)) }

@Composable
fun EventsScreen(vm: FoxViewModel) {
    val context = LocalContext.current
    val now = rememberNow()
    val mine by vm.prefs.myEvents.collectAsState()
    vm.eventsVersion   // read so a refresh recomposes
    val events = remember(now.epochSecond / 30, mine, vm.eventsVersion) { allEvents(vm.prefs) }
    var adding by remember { mutableStateOf(false) }
    // Exact-alarm access is granted in Android Settings (BootReceiver re-arms on the change); just re-check the card.
    val exact = remember(now.epochSecond / 2) { EventAlarms.canBeExact(context) }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Problem(vm.events.error) }
        val next = events.firstOrNull()
        item { if (next == null) NoEventCard() else HeroCountdown(next, now) }

        if (!exact && Build.VERSION.SDK_INT >= 31) item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4A2A0A))) {
                Column(Modifier.padding(14.dp)) {
                    Text("⏰ Make reminders right on time", fontWeight = FontWeight.Bold)
                    Text(
                        "Android may hold the \"10 minutes to go\" alert back a few minutes. Allow alarms for Fox Drop and it lands on the minute.",
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                        )
                    }) { Text("Allow alarms") }
                }
            }
        }

        val admin = vm.crew.me?.admin == true
        fun removable(e: LiveEvent) = e.custom || (admin && e.id.startsWith(Crew.CREW_PREFIX))
        fun remove(e: LiveEvent) = if (e.custom) vm.removeEvent(e.id) else vm.crew.removeOfficial(e.id)

        val rest = events.drop(1)
        if (rest.isNotEmpty()) {
            item { Header("Coming up") }
            items(rest, key = { it.id }) { e -> EventRow(e, now, canDelete = removable(e), onDelete = { remove(e) }) }
        }
        if (next != null && removable(next)) item {
            TextButton(onClick = { remove(next) }) {
                Text(if (next.custom) "Remove \"${next.title}\"" else "Remove \"${next.title}\" for everyone")
            }
        }

        if (vm.crew.active) item { TipsSection(vm) }

        item {
            Button(
                onClick = { adding = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = FoxOrange),
            ) {
                Icon(Icons.Filled.Add, null)
                Text("  Add an event I heard about")
            }
        }
        item {
            Text(
                "Epic doesn't share event times with apps, so the Fox Drop list is updated by hand when Epic announces one. " +
                    "Fox Drop reminds you a day before, an hour before, 10 minutes before, and when it goes live.",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(4.dp),
            )
        }
    }
    if (adding) {
        val me = vm.crew.me
        val canShare = vm.crew.active && me?.canPost == true
        val admin = me?.admin == true
        var share by remember { mutableStateOf(canShare) }
        EventDialog(
            heading = "Add a live event",
            confirm = { if (!share) "Add" else if (admin) "Publish" else "Send tip" },
            onDone = { e ->
                when {
                    !share -> vm.addEvent(e)
                    admin -> vm.crew.publish(e)
                    else -> vm.crew.postTip(e)
                }
                adding = false
            },
            onClose = { adding = false },
            extra = {
                if (canShare) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (admin) "Publish for everyone" else "Share with the crew", fontWeight = FontWeight.Bold)
                        Text(
                            if (admin) "Every Fox Drop phone gets the countdown and reminders."
                            else if (share) "The admin checks tips before they go on everyone's calendar."
                            else "Only on this phone.",
                            fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    Switch(share, { share = it })
                }
            },
        )
    }
}

@Composable
private fun HeroCountdown(e: LiveEvent, now: Instant) {
    val live = e.isLive(now)
    val bg = if (live) listOf(Color(0xFFD62246), Color(0xFF7A1030)) else listOf(Color(0xFF6A3DE8), Color(0xFF2B1A70))
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(bg)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (live) "🔴 LIVE NOW" else "NEXT LIVE EVENT", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(6.dp))
            Text(e.title, fontWeight = FontWeight.Black, fontSize = 24.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            val d = Duration.between(now, e.start)
            when {
                live && e.end != null -> {
                    Text("ends in", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                    Text(endsIn(e, now)!!, fontSize = 44.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFD54F))
                }
                live -> Text("Go go go!", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFD54F))
                e.approx -> {
                    val days = Duration.between(now, e.start).toDays()
                    Text(if (days <= 0) "Today!" else "about $days day${if (days == 1L) "" else "s"}",
                        fontSize = 40.sp, fontWeight = FontWeight.Black, color = FoxOrange)
                }
                else -> Text(countdown(d), fontSize = 44.sp, fontWeight = FontWeight.Black, color = FoxOrange)
            }
            Spacer(Modifier.height(6.dp))
            Text(whenText(e), color = Color.White.copy(alpha = 0.85f))
            if (e.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(e.note, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
            }
            if (e.custom) Text("Added by you", fontSize = 11.sp, color = Sky)
        }
    }
}

@Composable
private fun NoEventCard() = Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🦊", fontSize = 40.sp)
        Text("No live event on the calendar yet", fontWeight = FontWeight.Bold)
        Text("When Epic announces one, it shows up here with a countdown.", fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun EventRow(e: LiveEvent, now: Instant, canDelete: Boolean, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.title, fontWeight = FontWeight.Bold)
                Text(whenText(e), fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
                if (e.custom) Text("Added by you", fontSize = 11.sp, color = Sky)
            }
            val d = Duration.between(now, e.start)
            Text(
                when {
                    e.isLive(now) -> endsIn(e, now)?.let { "LIVE · $it" } ?: "LIVE"
                    e.approx -> "~${d.toDays()}d"
                    d.toDays() > 0 -> "${d.toDays()}d"
                    else -> countdown(d)
                },
                fontWeight = FontWeight.Black, color = FoxOrange,
            )
            if (canDelete) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Remove") }
        }
    }
}

/** Compact version for the top of the Shop tab; tapping it opens Events. */
@Composable
fun NextEventStrip(vm: FoxViewModel, onOpen: () -> Unit) {
    val now = rememberNow()
    val mine by vm.prefs.myEvents.collectAsState()
    val e = remember(now.epochSecond / 30, mine, vm.eventsVersion) { allEvents(vm.prefs) }.firstOrNull() ?: return
    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (e.isLive(now)) "🔴 Live now" else "Next live event", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                Text(e.title, fontWeight = FontWeight.Bold)
            }
            val d = Duration.between(now, e.start)
            Text(
                when {
                    e.isLive(now) -> endsIn(e, now)?.let { "ends $it" } ?: "GO!"
                    e.approx -> "~${d.toDays()} days"
                    else -> countdown(d)
                },
                fontSize = 22.sp, fontWeight = FontWeight.Black, color = FoxOrange,
            )
        }
    }
}

/**
 * Add (or, for the admin, approve) an event. Leaving the time empty makes it a day-only event, for when
 * Epic has named the day but not the hour. [extra] sits under the fields, e.g. the share-with-crew switch.
 */
@Composable
fun EventDialog(
    heading: String, confirm: (LiveEvent?) -> String, onDone: (LiveEvent) -> Unit, onClose: () -> Unit,
    initial: LiveEvent? = null, extra: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var day by remember { mutableStateOf(initial?.start?.atZone(zone)?.toLocalDate()?.takeIf { !it.isBefore(LocalDate.now()) } ?: LocalDate.now()) }
    var time by remember { mutableStateOf(initial?.takeIf { !it.approx }?.start?.atZone(zone)?.toLocalTime()) }
    val start = time?.let { day.atTime(it).atZone(zone).toInstant() }
    val inPast = start != null && start < Instant.now()
    val event = if (title.isBlank() || inPast) null else LiveEvent(
        id = initial?.id ?: "my-${System.currentTimeMillis()}", title = title.trim(),
        start = start ?: day.atStartOfDay(zone).toInstant(), approx = start == null,
        note = note.trim(), link = null, custom = initial == null,
    )
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Panel,
        title = { Text(heading, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it.take(80) }, singleLine = true, placeholder = { Text("What's the event?") })
                OutlinedButton(onClick = {
                    DatePickerDialog(context, { _, y, m, d -> day = LocalDate.of(y, m + 1, d) }, day.year, day.monthValue - 1, day.dayOfMonth)
                        .apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
                }, modifier = Modifier.fillMaxWidth()) { Text("📅  ${DateTimeFormatter.ofPattern("EEE MMM d").format(day)}") }
                OutlinedButton(onClick = {
                    val t = time ?: LocalTime.of(14, 0)
                    TimePickerDialog(context, { _, h, min -> time = LocalTime.of(h, min) }, t.hour, t.minute, false).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(time?.let { "⏰  " + DateTimeFormatter.ofPattern("h:mm a").format(it) } ?: "⏰  Pick a time (if you know it)")
                }
                if (time != null) TextButton(onClick = { time = null }) { Text("Time not announced yet", fontSize = 12.sp) }
                OutlinedTextField(note, { note = it.take(300) }, maxLines = 3, placeholder = { Text("Where did you hear it? (optional)") })
                if (inPast) Text("That time already passed.", color = Color(0xFFFFB4A8), fontSize = 13.sp)
                Text("Times are your phone's time zone. Tip: Epic usually says Eastern time (ET).",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                extra?.invoke()
            }
        },
        confirmButton = {
            TextButton(enabled = event != null, onClick = { onDone(event!!) }) { Text(confirm(event)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
