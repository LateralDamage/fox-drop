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
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("foxdrop", Context.MODE_PRIVATE)

    private val _wishes = MutableStateFlow(loadWishes())
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

    // Watcher memory: what the last check saw. Null means "never checked", which suppresses alerts on the first pass.
    fun seen(key: String): String? = sp.getString("seen_$key", null)
    fun setSeen(key: String, value: String) = sp.edit().putString("seen_$key", value).apply()
    fun seenSet(key: String): Set<String>? = sp.getStringSet("seenset_$key", null)
    fun setSeenSet(key: String, value: Set<String>) = sp.edit().putStringSet("seenset_$key", value).apply()

    var lastCheck: Long
        get() = sp.getLong("last_check", 0)
        set(v) = sp.edit().putLong("last_check", v).apply()
}
