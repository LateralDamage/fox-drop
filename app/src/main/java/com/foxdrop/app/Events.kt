package com.foxdrop.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Epic only hands live-event times to a logged-in game client (the calendar endpoint wants
 * `fortnite:calendar READ`), so there is no keyless feed. Events come from two places instead:
 *  - EVENTS_URL, a hand-kept list on the Fox Drop GitHub Pages site, edited when Epic announces something
 *  - events Kollin adds himself in the app
 * An event with only a date ("date" instead of "start" in the JSON) is approximate: Epic has said the day
 * but not the time, so it gets day-level reminders instead of minute-level ones.
 */
const val EVENTS_URL = "https://lateraldamage.github.io/fox-drop/events.json"

data class LiveEvent(
    val id: String,
    val title: String,
    val start: Instant,
    val approx: Boolean,
    val note: String,
    val link: String?,
    val custom: Boolean,
) {
    /** Epic events usually run 10-30 minutes; call it live for half an hour after it starts. */
    fun isLive(now: Instant = Instant.now()) = !approx && now >= start && now < start.plus(LIVE_FOR)
    fun isOver(now: Instant = Instant.now()) =
        now >= if (approx) start.plus(Duration.ofDays(1)) else start.plus(LIVE_FOR)

    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("note", note)
        .put("link", link ?: "").apply {
            if (approx) put("date", start.atZone(ZoneId.systemDefault()).toLocalDate().toString())
            else put("start", start.toString())
        }

    companion object {
        val LIVE_FOR: Duration = Duration.ofMinutes(30)

        fun fromJson(o: JSONObject, custom: Boolean): LiveEvent? = runCatching {
            val exact = o.optString("start").takeIf { it.isNotBlank() }?.let(Instant::parse)
            val day = o.optString("date").takeIf { it.isNotBlank() }?.let(LocalDate::parse)
            LiveEvent(
                id = o.getString("id"),
                title = o.getString("title"),
                start = exact ?: day!!.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                approx = exact == null,
                note = o.optString("note"),
                link = o.optString("link").takeIf { it.startsWith("http") },
                custom = custom,
            )
        }.getOrNull()

        fun parseList(json: String?, custom: Boolean): List<LiveEvent> = runCatching {
            val t = json?.trim() ?: return emptyList()
            val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).optJSONArray("events") ?: JSONArray()
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let { o -> fromJson(o, custom) } }
        }.getOrDefault(emptyList())
    }
}

/** Every event the phone knows about, hosted and home-made, soonest first, finished ones dropped. */
fun allEvents(prefs: Prefs, now: Instant = Instant.now()): List<LiveEvent> =
    (LiveEvent.parseList(prefs.remoteEvents, custom = false) + prefs.myEvents.value)
        .filter { !it.isOver(now) }
        .distinctBy { it.id }
        .sortedBy { it.start }

fun countdown(d: Duration): String {
    val s = d.seconds.coerceAtLeast(0)
    val days = s / 86400
    val hms = "%02d:%02d:%02d".format((s % 86400) / 3600, (s % 3600) / 60, s % 60)
    return if (days > 0) "${days}d $hms" else hms
}

/**
 * The 15-minute poll is far too coarse for "starts in 10 minutes", so each reminder is its own alarm.
 * Alarms don't survive a reboot or an app update; BootReceiver re-arms them from the cached list.
 */
object EventAlarms {
    private const val ACTION = "com.foxdrop.app.EVENT_PING"
    private val clock = DateTimeFormatter.ofPattern("EEE h:mm a").withZone(ZoneId.systemDefault())
    private val hm = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())

    data class Ping(val key: String, val at: Instant, val title: String, val text: String)

    fun pings(e: LiveEvent): List<Ping> {
        if (e.approx) {
            val zone = ZoneId.systemDefault()
            val day = e.start.atZone(zone).toLocalDate()
            val tail = if (e.note.isNotBlank()) e.note else "Epic hasn't announced the exact time yet."
            return listOf(
                Ping("eve", day.minusDays(1).atTime(LocalTime.of(18, 0)).atZone(zone).toInstant(),
                    "⏰ ${e.title} is tomorrow!", tail),
                Ping("day", day.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant(),
                    "📅 ${e.title} is today!", tail),
            )
        }
        return listOf(
            Ping("1d", e.start.minus(Duration.ofDays(1)), "⏰ ${e.title} is tomorrow!",
                "Starts ${clock.format(e.start)}. Fox Drop will remind you again an hour before."),
            Ping("1h", e.start.minus(Duration.ofHours(1)), "⏰ ${e.title} starts in 1 hour",
                "Starts at ${hm.format(e.start)}. Update Fortnite now and log in early, live events fill up!"),
            Ping("10m", e.start.minus(Duration.ofMinutes(10)), "🔥 ${e.title} in 10 minutes!",
                "Jump in now so you don't miss it!"),
            Ping("live", e.start, "🔴 ${e.title} is LIVE!", "It's happening right now. Go go go!"),
        )
    }

    fun canBeExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun intent(context: Context, code: Int, p: Ping? = null): PendingIntent {
        val i = Intent(context, EventAlarmReceiver::class.java).setAction(ACTION)
        if (p != null) i.putExtra("title", p.title).putExtra("text", p.text).putExtra("at", p.at.toEpochMilli()).putExtra("id", code)
        return PendingIntent.getBroadcast(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Cancel every alarm set last time and set one per future reminder. Cheap, so it runs whenever anything changes. */
    fun reschedule(context: Context) {
        val app = context.applicationContext as FoxApp
        val prefs = app.prefs
        val am = context.getSystemService(AlarmManager::class.java)
        prefs.alarmCodes.forEach { am.cancel(intent(context, it.toInt())) }

        val now = Instant.now()
        val codes = mutableSetOf<String>()
        if (prefs.enabled(AlertKind.EVENTS)) {
            val exact = canBeExact(context)
            allEvents(prefs, now).flatMap { e -> pings(e).map { e to it } }
                .filter { (_, p) -> p.at > now }
                .sortedBy { it.second.at }
                .take(48)
                .forEach { (e, p) ->
                    val code = "${e.id}#${p.key}".hashCode()
                    val pi = intent(context, code, p)
                    val ms = p.at.toEpochMilli()
                    try {
                        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
                        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
                    } catch (_: SecurityException) {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)   // exact-alarm access was just revoked
                    }
                    codes += code.toString()
                }
        }
        prefs.alarmCodes = codes
    }
}

class EventAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = (context.applicationContext as FoxApp).prefs
        if (!prefs.enabled(AlertKind.EVENTS)) return
        // A phone that was off at the time delivers the alarm late; a "starts in 10 minutes" from two hours ago is just noise.
        val at = intent.getLongExtra("at", 0)
        if (System.currentTimeMillis() - at > Duration.ofHours(1).toMillis()) return
        Notify.post(
            context, AlertKind.EVENTS, Tab.EVENTS,
            intent.getStringExtra("title") ?: return, intent.getStringExtra("text").orEmpty(),
            id = intent.getIntExtra("id", 5001),
        )
    }
}

/** Reboots, app updates and a change in exact-alarm access all wipe or weaken the alarms; put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = EventAlarms.reschedule(context)
}
