package com.foxdrop.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Nothing pushes to the phone. Instead this worker polls the public feeds every 15 minutes
 * (Android's minimum) and turns anything new into a local notification. Each check compares
 * against what the previous check saw, and the very first check only records a baseline.
 */
class WatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as FoxApp
        Watcher(app, app.api, app.prefs).checkAll()
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "foxdrop-watch"
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<WatchWorker>(15, TimeUnit.MINUTES).setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun checkNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<WatchWorker>().setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniqueWork("foxdrop-now", ExistingWorkPolicy.REPLACE, req)
        }
    }
}

class Watcher(private val context: Context, private val api: Api, private val prefs: Prefs) {

    private val clock = DateTimeFormatter.ofPattern("EEE h:mm a").withZone(ZoneId.systemDefault())

    private fun alert(kind: AlertKind, tab: Tab, title: String, text: String, id: Int = title.hashCode()) {
        if (prefs.enabled(kind)) Notify.post(context, kind, tab, title, text, id)
    }

    /** Each feed is checked on its own so one being down doesn't silence the others. */
    suspend fun checkAll() {
        runCatching { checkShop() }
        runCatching { checkCosmetics() }
        runCatching { checkNews() }
        runCatching { checkStatus() }
        runCatching { if (Crew.configured) { Crew.signIn(); prefs.cloudEvents = Crew.fetchEvents() } }
        runCatching { checkEvents() }
        runCatching { checkChat() }
        runCatching { checkTips() }
        runCatching { checkReports() }
        prefs.lastCheck = System.currentTimeMillis()
    }

    /** New messages from others since the last one seen; skipped while the app is on screen. */
    private suspend fun checkChat() {
        if (!Crew.configured || !prefs.crewActive || FoxApp.visible) return
        val me = Crew.signIn()
        val since = prefs.chatSeenAt
        if (since == 0L) { prefs.chatSeenAt = System.currentTimeMillis(); return }
        val docs = Crew.db.collection("chat")
            .whereGreaterThan("at", Timestamp(java.time.Instant.ofEpochMilli(since)))
            .orderBy("at").limit(30).get().await().documents
        docs.lastOrNull()?.getTimestamp("at")?.let { prefs.chatSeenAt = it.toInstant().toEpochMilli() }
        val blocked = prefs.blocked.value
        val posts = docs.mapNotNull(::postOf).filter { it.uid != me && it.uid !in blocked }
        if (posts.isEmpty()) return
        alert(
            AlertKind.CHAT, Tab.CHAT,
            if (posts.size == 1) "💬 ${posts[0].name}" else "💬 ${posts.size} new messages",
            posts.takeLast(4).joinToString("\n") { if (posts.size == 1) it.text else "${it.name}: ${it.text}" },
            id = 6001,
        )
    }

    private suspend fun checkTips() {
        if (!Crew.configured || !prefs.crewActive) return
        val me = Crew.signIn()
        val pending = Crew.db.collection("tips").orderBy("at", Query.Direction.DESCENDING).limit(30).get().await()
            .documents.mapNotNull(::tipOf).filter { it.status == "pending" }
        val before = prefs.seenSet("tips")
        prefs.setSeenSet("tips", pending.map { it.id }.toSet())
        if (before == null) return
        pending.filter { it.id !in before && it.uid != me }.forEach {
            alert(
                AlertKind.CHAT, Tab.EVENTS, "💡 Event tip: ${it.title}",
                "From ${it.name}." + if (prefs.crewAdmin) " Tap to approve it or delete it." else " Tap to comment.",
                id = ("tip" + it.id).hashCode(),
            )
        }
    }

    /** Admin only: someone flagged a message. */
    private suspend fun checkReports() {
        if (!Crew.configured || !prefs.crewAdmin) return
        Crew.signIn()
        val ids = Crew.db.collection("reports").get().await().documents.map { it.id }.toSet()
        val before = prefs.seenSet("reports")
        prefs.setSeenSet("reports", ids)
        if (before != null && (ids - before).isNotEmpty()) {
            alert(AlertKind.CHAT, Tab.CHAT, "🚩 ${(ids - before).size} new report(s) in chat", "Open Admin in the Chat tab to review.", id = 6002)
        }
    }

    private suspend fun checkShop() {
        val shop = api.shop()
        val lastHash = prefs.seen("shop_hash")
        if (lastHash == shop.hash) return
        val offers = shop.entries.map { it.offerId }.toSet()
        val before = prefs.seenSet("shop_offers")
        prefs.setSeen("shop_hash", shop.hash)
        prefs.setSeenSet("shop_offers", offers)
        if (lastHash == null || before == null) return

        // Wishlist: alert once per item per shop day, not every time the hash wiggles.
        val day = shop.date?.toString()?.take(10).orEmpty()
        val told = prefs.seenSet("wish_told")?.filter { it.endsWith("@$day") }?.toMutableSet() ?: mutableSetOf()
        val wanted = prefs.wishes.value.associateBy { it.id }
        val hits = shop.entries.flatMap { e -> e.items.filter { it.id in wanted }.map { it to e } }
            .filter { (item, _) -> "${item.id}@$day" !in told }
            .distinctBy { it.first.id }
        hits.forEach { (item, entry) ->
            alert(
                AlertKind.WISHLIST, Tab.SHOP, "🦊 ${item.name} is in the Item Shop!",
                "${entry.title} — ${entry.price} V-Bucks" +
                    (entry.outDate?.let { "  ·  leaves ${clock.format(it)}" } ?: ""),
                id = item.id.hashCode(),
            )
            told += "${item.id}@$day"
        }
        prefs.setSeenSet("wish_told", told)

        val fresh = shop.entries.filter { it.offerId !in before }
        if (fresh.size >= 3) {
            val names = fresh.map { it.title }.distinct()
            alert(
                AlertKind.SHOP, Tab.SHOP, "Item Shop updated: ${fresh.size} new",
                names.take(6).joinToString(", ") + if (names.size > 6) " and more" else "",
                id = 1001,
            )
        }
    }

    private suspend fun checkCosmetics() {
        val n = api.newCosmetics()
        val lastBuild = prefs.seen("build")
        val lastAdd = prefs.seen("cosmetics_added")
        prefs.setSeen("build", n.build)
        prefs.setSeen("cosmetics_added", n.lastBr)
        if (lastBuild != null && lastBuild != n.build && n.build.isNotBlank()) {
            alert(AlertKind.UPDATES, Tab.STATUS, "Fortnite update v${n.build} is out", "A new patch just went live. Time to update!", id = 2001)
        }
        if (lastAdd != null && lastAdd != n.lastBr) {
            val since = runCatching { java.time.Instant.parse(lastAdd) }.getOrNull()
            val added = n.items.filter { since == null || (it.added != null && it.added > since) }
            if (added.isNotEmpty()) {
                alert(
                    AlertKind.COSMETICS, Tab.NEW, "${added.size} new cosmetics added",
                    added.take(6).joinToString(", ") { it.name } + if (added.size > 6) " and more" else "",
                    id = 3001,
                )
            }
        }
    }

    private suspend fun checkNews() {
        val news = api.news()
        val before = prefs.seenSet("news_ids")
        prefs.setSeenSet("news_ids", news.posts.map { it.id }.toSet())
        if (before != null) {
            news.posts.filter { it.id !in before }.forEach {
                alert(AlertKind.NEWS, Tab.NEWS, "📰 ${it.title}", it.body, id = it.id.hashCode())
            }
        }
        val game = api.epicGame()
        val seenNotices = prefs.seenSet("notices")
        prefs.setSeenSet("notices", game.notices.map { it.title }.toSet())
        if (seenNotices != null) {
            game.notices.filter { it.title !in seenNotices }.forEach {
                alert(AlertKind.NEWS, Tab.NEWS, "📢 ${it.title}", it.body)
            }
        }
    }

    private suspend fun checkEvents() {
        val json = api.eventsJson()
        prefs.remoteEvents = json
        val events = hostedEvents(prefs).filter { !it.isOver() }
        // Keyed by id and time, so a moved event counts as news too.
        val sigs = events.associateBy { "${it.id}@${it.start}" }
        val before = prefs.seenSet("events")
        prefs.setSeenSet("events", sigs.keys)
        EventAlarms.reschedule(context)
        if (before == null) return
        val knownIds = before.map { it.substringBefore('@') }.toSet()
        sigs.filterKeys { it !in before }.values.forEach { e ->
            val whenText = if (e.approx) "Expected ${DateTimeFormatter.ofPattern("EEE MMM d").format(e.start.atZone(ZoneId.systemDefault()))}, time not announced yet"
                else "Starts ${DateTimeFormatter.ofPattern("EEE MMM d, h:mm a").withZone(ZoneId.systemDefault()).format(e.start)}"
            val moved = e.id in knownIds
            alert(
                AlertKind.EVENTS, Tab.EVENTS,
                if (moved) "📅 ${e.title}: new time" else "🎉 Live event announced: ${e.title}",
                whenText + (if (e.note.isNotBlank()) "\n${e.note}" else "") + "\nFox Drop will count down and remind you.",
                id = ("new" + e.id).hashCode(),
            )
        }
    }

    private suspend fun checkStatus() {
        val s = api.status()
        val down = s.services.filter { it.status != "operational" }
        val sig = down.joinToString { "${it.name}=${it.status}" }
        val lastSig = prefs.seen("status_sig")
        prefs.setSeen("status_sig", sig)
        if (lastSig != null && sig != lastSig) {
            if (down.isEmpty()) {
                alert(AlertKind.UPDATES, Tab.STATUS, "✅ Fortnite servers are back up", "Everything is running again. Go play!", id = 4001)
            } else {
                alert(
                    AlertKind.UPDATES, Tab.STATUS, "⚠️ Fortnite is having problems",
                    down.joinToString("\n") { "${it.name}: ${pretty(it.status)}" }, id = 4001,
                )
            }
        }

        val seenInc = prefs.seenSet("incidents")
        prefs.setSeenSet("incidents", s.incidents.map { it.id }.toSet())
        if (seenInc != null) s.incidents.filter { it.id !in seenInc }.forEach {
            alert(AlertKind.UPDATES, Tab.STATUS, "⚠️ ${it.name}", it.latest.ifBlank { "Epic is looking into it." }, id = it.id.hashCode())
        }

        // Maintenance: once when it's announced, once when downtime actually starts.
        val seenMaint = prefs.seenSet("maint")
        val keys = s.maintenances.map { "${it.id}:${it.status}" }.toSet()
        prefs.setSeenSet("maint", keys)
        if (seenMaint != null) s.maintenances.filter { "${it.id}:${it.status}" !in seenMaint }.forEach { m ->
            when (m.status) {
                "scheduled" -> alert(
                    AlertKind.UPDATES, Tab.STATUS, "🛠️ Downtime coming ${m.start?.let(clock::format) ?: "soon"}",
                    m.name + (m.end?.let { "\nExpected back ${clock.format(it)}" } ?: ""), id = m.id.hashCode(),
                )
                "in_progress" -> alert(
                    AlertKind.UPDATES, Tab.STATUS, "🛠️ Fortnite servers are down for maintenance",
                    m.name + (m.end?.let { "\nExpected back ${clock.format(it)}" } ?: ""), id = m.id.hashCode(),
                )
            }
        }
    }
}

fun pretty(status: String) = status.replace('_', ' ').replaceFirstChar { it.uppercase() }
