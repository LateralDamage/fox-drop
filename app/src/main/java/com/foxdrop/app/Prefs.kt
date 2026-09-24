package com.foxdrop.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** A cosmetic Kollin wants; the name and picture are kept so the list shows without a network call. */
data class Wish(val id: String, val name: String, val type: String, val image: String?)

enum class AlertKind(val key: String, val title: String, val blurb: String) {
    WISHLIST("wishlist", "Wishlist in the shop", "When something on your wishlist shows up in the Item Shop"),
    SHOP("shop", "Item Shop reset", "Once a day when the shop changes"),
    UPDATES("updates", "Updates & downtime", "New patches, server maintenance, outages, and when servers come back"),
    NEWS("news", "News", "New in-game news posts and Epic notices"),
    COSMETICS("cosmetics", "New cosmetics", "When new skins, emotes and more are added to the game files"),
    EVENTS("events", "Live events", "A day, an hour and 10 minutes before a live event, when it goes live, and when a new one is announced"),
    CHAT("chat", "Fox Chat & tips", "New Fox Chat messages and event tips from your crew"),
    APP("app", "Fox Drop updates", "When a new version of Fox Drop is ready to download");

    companion object {
        /** The Play build is updated by Play, so it never offers its own updates. */
        val shown get() = entries.filter { it != APP || BuildConfig.SELF_UPDATE }
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("foxdrop", Context.MODE_PRIVATE)

    private val _wishes = MutableStateFlow(loadWishes())
    private val _myEvents = MutableStateFlow(LiveEvent.parseList(sp.getString("my_events", "[]"), custom = true))
    /** Events Kollin typed in himself. */
    val myEvents: StateFlow<List<LiveEvent>> = _myEvents
    val wishes: StateFlow<List<Wish>> = _wishes

    fun enabled(kind: AlertKind) = sp.getBoolean("alert_${kind.key}", true)
    fun setEnabled(kind: AlertKind, on: Boolean) = sp.edit().putBoolean("alert_${kind.key}", on).apply()

    fun isWished(id: String) = _wishes.value.any { it.id == id }
    fun toggleWish(w: Wish) {
        val now = _wishes.value.toMutableList()
        if (!now.removeAll { it.id == w.id }) now.add(0, w)
        _wishes.value = now
        sp.edit().putString("wishes", org.json.JSONArray(now.map {
            JSONObject().put("id", it.id).put("name", it.name).put("type", it.type).put("image", it.image ?: "")
        }).toString()).apply()
    }

    private fun loadWishes(): List<Wish> = runCatching {
        val a = org.json.JSONArray(sp.getString("wishes", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            Wish(it.getString("id"), it.optString("name"), it.optString("type"), it.optString("image").ifBlank { null })
        }
    }.getOrDefault(emptyList())

    fun addEvent(e: LiveEvent) = saveMine(_myEvents.value.filter { it.id != e.id } + e)
    fun removeEvent(id: String) = saveMine(_myEvents.value.filter { it.id != id })
    private fun saveMine(list: List<LiveEvent>) {
        // Anything more than a day over is dropped so the list tidies itself.
        val keep = list.filter { !it.isOver(java.time.Instant.now().minus(java.time.Duration.ofDays(1))) }
        _myEvents.value = keep
        sp.edit().putString("my_events", org.json.JSONArray(keep.map { it.toJson() }).toString()).apply()
    }

    /** The last events.json that downloaded, kept so alarms can be re-armed after a reboot with no network. */
    var remoteEvents: String?
        get() = sp.getString("remote_events", null)
        set(v) = sp.edit().putString("remote_events", v).apply()

    /** Official crew events from Firestore, in the events.json shape; kept for the same offline reason. */
    var cloudEvents: String?
        get() = sp.getString("cloud_events", null)
        set(v) = sp.edit().putString("cloud_events", v).apply()

    /** Set by the chat screen's state so the background watcher knows whether to look at chat and tips. */
    var crewActive: Boolean
        get() = sp.getBoolean("crew_active", false)
        set(v) = sp.edit().putBoolean("crew_active", v).apply()
    var crewAdmin: Boolean
        get() = sp.getBoolean("crew_admin", false)
        set(v) = sp.edit().putBoolean("crew_admin", v).apply()

    /** Server time of the newest chat message already seen, so the watcher only alerts on newer ones. */
    var chatSeenAt: Long
        get() = sp.getLong("chat_seen_at", 0)
        set(v) = sp.edit().putLong("chat_seen_at", v).apply()

    /** People this phone chose to hide in chat. Local only; reporting is what reaches the admin. */
    private val _blocked = MutableStateFlow(sp.getStringSet("blocked", emptySet()) ?: emptySet())
    val blocked: StateFlow<Set<String>> = _blocked
    fun block(uid: String) {
        _blocked.value = _blocked.value + uid
        sp.edit().putStringSet("blocked", _blocked.value).apply()
    }

    /** Request codes of the alarms currently set, so the next reschedule can cancel them. */
    var alarmCodes: Set<String>
        get() = sp.getStringSet("alarm_codes", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("alarm_codes", v).apply()

    // Watcher memory: what the last check saw. Null means "never checked", which suppresses alerts on the first pass.
    fun seen(key: String): String? = sp.getString("seen_$key", null)
    fun setSeen(key: String, value: String) = sp.edit().putString("seen_$key", value).apply()
    fun seenSet(key: String): Set<String>? = sp.getStringSet("seenset_$key", null)
    fun setSeenSet(key: String, value: Set<String>) = sp.edit().putStringSet("seenset_$key", value).apply()

    /** A newer Fox Drop the watcher found on GitHub (version and APK link), or null when up to date. */
    var appUpdate: AppRelease?
        get() = sp.getString("app_update", null)?.split('\n')?.takeIf { it.size == 3 }?.let { AppRelease(it[0], it[1], it[2]) }
        set(v) = sp.edit().putString("app_update", v?.let { "${it.version}\n${it.title}\n${it.apkUrl}" }).apply()

    var lastCheck: Long
        get() = sp.getLong("last_check", 0)
        set(v) = sp.edit().putLong("last_check", v).apply()
}
