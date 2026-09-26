package com.foxdrop.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A feed that is loading, loaded, or failed; the last good value survives a failed refresh. */
data class Load<T>(val value: T? = null, val loading: Boolean = false, val error: String? = null)

class FoxViewModel(app: Application) : AndroidViewModel(app) {
    private val fox = app as FoxApp
    private val api = fox.api
    val prefs = fox.prefs

    var shop by mutableStateOf(Load<Shop>()); private set
    var fresh by mutableStateOf(Load<NewCosmetics>()); private set
    var news by mutableStateOf(Load<News>()); private set
    var game by mutableStateOf(Load<EpicGame>()); private set
    var status by mutableStateOf(Load<Status>()); private set
    /** Tracks whether events.json is loading or failed; the events themselves come from allEvents(prefs). */
    var events by mutableStateOf(Load<Unit>(if (fox.prefs.remoteEvents != null) Unit else null)); private set
    /** Bumped whenever the event list changes, so the Events screen recomposes. */
    var eventsVersion by mutableStateOf(0); private set

    /** Starts from the cached copy so the checklist shows at once, even offline. */
    var sprites by mutableStateOf(Load(fox.prefs.remoteSprites?.let { runCatching { SpriteList.parse(it) }.getOrNull() })); private set

    var stats by mutableStateOf(Load<PlayerStats>()); private set
    /** Hosted settings (the tip jar link); null until loaded, and it simply stays hidden if that fails. */
    var config by mutableStateOf<AppConfig?>(null); private set
    private var statsJob: Job? = null

    /** Looks a player up; a StatsProblem keeps its own wording, anything else is blamed on the network. */
    /** Keeps the Stats name and platform (and shares them with a linked computer). */
    fun saveStatsName(name: String, platform: String) {
        prefs.statsName = name.trim()
        prefs.statsPlatform = platform
        link.push()
    }

    fun lookupStats(name: String, platform: String, season: Boolean) {
        if (name.isBlank()) return
        saveStatsName(name, platform)
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            stats = Load(loading = true)
            stats = try {
                Load(api.stats(name, platform, season))
            } catch (e: StatsProblem) {
                Load(error = e.message)
            } catch (e: Exception) {
                Load(error = "Couldn't reach the stats server. Check your internet and try again.")
            }
        }
    }

    var query by mutableStateOf(""); private set
    var results by mutableStateOf(Load<List<Cosmetic>>()); private set
    private var searchJob: Job? = null

    val crew = CrewModel(viewModelScope, prefs, onEvents = { eventsChanged() })
    val link = LinkModel(viewModelScope, prefs)

    init {
        refreshAll()
        crew.start()
        link.start()
    }

    override fun onCleared() = crew.close()

    fun refreshAll() {
        viewModelScope.launch { shop = fetch(shop) { api.shop() } }
        viewModelScope.launch { fresh = fetch(fresh) { api.newCosmetics() } }
        viewModelScope.launch { news = fetch(news) { api.news() } }
        viewModelScope.launch { game = fetch(game) { api.epicGame() } }
        viewModelScope.launch { status = fetch(status) { api.status() } }
        viewModelScope.launch { refreshEvents() }
        viewModelScope.launch { refreshSprites() }
        viewModelScope.launch { runCatching { api.appConfig() }.onSuccess { config = it } }
    }

    private suspend fun refreshSprites() {
        sprites = Load(sprites.value, loading = true)
        sprites = fetch(sprites) { api.spritesJson().let { prefs.remoteSprites = it; SpriteList.parse(it) } }
    }

    private suspend fun refreshEvents() {
        events = Load(events.value, loading = true)
        events = fetch(events) { prefs.remoteEvents = api.eventsJson() }
        eventsChanged()
    }

    fun addEvent(e: LiveEvent) { prefs.addEvent(e); eventsChanged() }
    fun removeEvent(id: String) { prefs.removeEvent(id); eventsChanged() }

    fun eventsChanged() {
        EventAlarms.reschedule(getApplication())
        eventsVersion++
    }

    fun refresh(tab: Tab) = viewModelScope.launch {
        when (tab) {
            Tab.SHOP, Tab.WISHLIST -> shop = fetch(shop) { api.shop() }
            Tab.NEW -> fresh = fetch(fresh) { api.newCosmetics() }
            Tab.NEWS -> { news = fetch(news) { api.news() }; game = fetch(game) { api.epicGame() } }
            Tab.STATUS -> status = fetch(status) { api.status() }
            Tab.EVENTS -> refreshEvents()
            Tab.CHAT -> crew.start()
            Tab.SPRITES -> refreshSprites()
        }
    }

    fun search(q: String) {
        query = q
        searchJob?.cancel()
        if (q.trim().length < 2) { results = Load(); return }
        searchJob = viewModelScope.launch {
            delay(350)   // wait for typing to pause
            results = Load(results.value, loading = true)
            results = Load(api.search(q))
        }
    }

    private suspend fun <T> fetch(old: Load<T>, block: suspend () -> T): Load<T> {
        return try {
            Load(block())
        } catch (e: Exception) {
            Load(old.value, error = "Couldn't reach the server. Pull down or tap refresh to try again.")
        }
    }
}
