package com.foxdrop.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Every source is public and needs no key:
 *  - fortnite-api.com: Item Shop, BR news, newly added cosmetics, cosmetic search
 *  - status.epicgames.com: the Statuspage feed Epic posts outages and maintenance to
 *  - Epic's own fortnite-game content page: in-game notices and the tournament list
 *  - the Fox Drop events.json on GitHub Pages: live events, kept by hand (see Events.kt)
 */
data class ShopItem(val id: String, val name: String, val type: String, val rarity: String)

data class ShopEntry(
    val offerId: String,
    val title: String,
    val section: String,
    val price: Int,
    val regularPrice: Int,
    val image: String?,
    val outDate: Instant?,
    val items: List<ShopItem>,
)

data class Shop(val hash: String, val date: Instant?, val entries: List<ShopEntry>)

data class Cosmetic(
    val id: String,
    val name: String,
    val type: String,
    val rarity: String,
    val description: String,
    val image: String?,
    val added: Instant?,
)

data class NewCosmetics(val build: String, val lastBr: String, val items: List<Cosmetic>)

data class NewsPost(val id: String, val title: String, val body: String, val image: String?)
data class News(val hash: String, val posts: List<NewsPost>)

data class Notice(val title: String, val body: String)
data class Tournament(val id: String, val title: String, val subtitle: String, val body: String, val image: String?)
data class EpicGame(val notices: List<Notice>, val tournaments: List<Tournament>)

data class ServiceStatus(val name: String, val status: String)
data class Incident(val id: String, val name: String, val status: String, val impact: String, val latest: String, val link: String?)
data class Maintenance(val id: String, val name: String, val status: String, val start: Instant?, val end: Instant?, val link: String?)
data class Status(
    val services: List<ServiceStatus>,
    val incidents: List<Incident>,
    val maintenances: List<Maintenance>,
) {
    val allUp get() = services.all { it.status == "operational" }
}

/** One mode's totals; winRate is already a percentage (12.5 means 12.5%). */
data class ModeStats(
    val wins: Int, val kills: Int, val kd: Double, val matches: Int,
    val winRate: Double, val top10: Int, val minutesPlayed: Int,
)

/** [modes] is keyed overall/solo/duo/squad/ltm; a mode never played is simply missing. */
data class PlayerStats(val name: String, val battlePassLevel: Int?, val modes: Map<String, ModeStats>)

/** A stats refusal worded for the player (private stats, unknown name, no key). */
class StatsProblem(message: String) : Exception(message)

data class AppRelease(val version: String, val title: String, val apkUrl: String) {
    /** True when this release is newer than [installed], comparing 1.4.2-style numbers part by part. */
    fun isNewerThan(installed: String): Boolean {
        val a = version.split('.').map { it.toIntOrNull() ?: 0 }
        val b = installed.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    companion object {
        const val LATEST_APK = "https://github.com/LateralDamage/fox-drop/releases/latest/download/FoxDrop.apk"
    }
}

class Api(private val http: OkHttpClient) {

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code} from $url")
            JSONObject(r.body.string())
        }
    }

    suspend fun shop(): Shop {
        val d = get("https://fortnite-api.com/v2/shop").getJSONObject("data")
        val entries = d.optJSONArray("entries").objects().mapNotNull { e ->
            val br = e.optJSONArray("brItems") ?: return@mapNotNull null   // skip jam tracks, cars, LEGO kits
            val items = br.objects().map { b ->
                ShopItem(
                    b.getString("id"), b.optString("name"),
                    b.optJSONObject("type")?.optString("displayValue").orEmpty(),
                    b.optJSONObject("rarity")?.optString("value").orEmpty(),
                )
            }
            if (items.isEmpty()) return@mapNotNull null
            val first = br.getJSONObject(0)
            val render = e.optJSONObject("newDisplayAsset")?.optJSONArray("renderImages")
                ?.optJSONObject(0)?.str("image")
            val icons = first.optJSONObject("images")
            ShopEntry(
                offerId = e.getString("offerId"),
                title = e.optJSONObject("bundle")?.str("name") ?: items[0].name,
                section = e.optJSONObject("layout")?.str("name") ?: "Shop",
                price = e.optInt("finalPrice"),
                regularPrice = e.optInt("regularPrice"),
                image = render ?: icons?.str("featured") ?: icons?.str("icon"),
                outDate = e.str("outDate")?.let(::instant),
                items = items,
            )
        }
        return Shop(d.getString("hash"), d.str("date")?.let(::instant), entries)
    }

    suspend fun newCosmetics(): NewCosmetics {
        val d = get("https://fortnite-api.com/v2/cosmetics/new").getJSONObject("data")
        val items = d.getJSONObject("items").optJSONArray("br").objects().map(::cosmetic)
            .sortedByDescending { it.added }
        return NewCosmetics(
            d.optString("build").replace("++Fortnite+Release-", "").substringBefore("-CL"),
            d.optJSONObject("lastAdditions")?.optString("br").orEmpty(),
            items,
        )
    }

    suspend fun search(name: String): List<Cosmetic> {
        val q = java.net.URLEncoder.encode(name.trim(), "UTF-8")
        val o = runCatching {
            get("https://fortnite-api.com/v2/cosmetics/br/search/all?name=$q&matchMethod=contains")
        }.getOrElse { return emptyList() }   // 404 means "no match"
        return o.optJSONArray("data").objects().map(::cosmetic).take(60)
    }

    suspend fun news(): News {
        val d = get("https://fortnite-api.com/v2/news/br").getJSONObject("data")
        val posts = d.optJSONArray("motds").objects()
            .filter { !it.optBoolean("hidden") }
            .map { NewsPost(it.getString("id"), it.optString("title"), it.optString("body"), it.str("image") ?: it.str("tileImage")) }
        return News(d.getString("hash"), posts)
    }

    suspend fun epicGame(): EpicGame {
        val g = get("https://fortnitecontent-website-prod07.ol.epicgames.com/content/api/pages/fortnite-game")
        val notices = g.optJSONObject("emergencynoticev2")?.optJSONObject("emergencynotices")
            ?.optJSONArray("emergencynotices").objects()
            .filter { !it.optBoolean("hidden") }
            .map { Notice(it.optString("title"), it.optString("body").trim()) }
        val tournaments = g.optJSONObject("tournamentinformation")?.optJSONObject("tournament_info")
            ?.optJSONArray("tournaments").objects()
            .map {
                Tournament(
                    it.optString("tournament_display_id"),
                    it.str("long_format_title") ?: it.optString("title_line_1"),
                    it.optString("title_line_2"),
                    it.optString("details_description"),
                    it.str("square_poster_image") ?: it.str("playlist_tile_image"),
                )
            }
            .filter { it.title.isNotBlank() }
            .distinctBy { it.title }   // Epic lists each cup once per platform
        return EpicGame(notices, tournaments)
    }

    /** The raw events.json text, so the caller can cache exactly what it parsed. */
    suspend fun eventsJson(): String = withContext(Dispatchers.IO) {
        // GitHub Pages caches for 10 minutes; the query string sidesteps a stale edge copy.
        http.newCall(Request.Builder().url("$EVENTS_URL?t=${System.currentTimeMillis() / 60000}").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code} from $EVENTS_URL")
            r.body.string().also { JSONObject(it) }   // throws on a half-written file instead of caching it
        }
    }

    /**
     * A player's Battle Royale stats by display name. Needs the fortnite-api.com key, and the player must have
     * "public game stats" on in Fortnite, so each refusal becomes a message a kid can act on.
     */
    suspend fun stats(name: String, accountType: String, season: Boolean): PlayerStats = withContext(Dispatchers.IO) {
        if (BuildConfig.FORTNITE_API_KEY.isBlank()) throw StatsProblem("Stats aren't switched on in this copy of Fox Drop yet.")
        val url = okhttp3.HttpUrl.Builder().scheme("https").host("fortnite-api.com").addPathSegments("v2/stats/br/v2")
            .addQueryParameter("name", name.trim()).addQueryParameter("accountType", accountType)
            .addQueryParameter("timeWindow", if (season) "season" else "lifetime").build()
        val req = Request.Builder().url(url).header("Authorization", BuildConfig.FORTNITE_API_KEY).build()
        http.newCall(req).execute().use { r ->
            when (r.code) {
                200 -> {}
                403 -> throw StatsProblem("${name.trim()}'s stats are private. In Fortnite: Settings → Account and Privacy → turn on Show on Career Leaderboard.")
                404 -> throw StatsProblem("No player called \"${name.trim()}\" with stats. Check the spelling and the platform.")
                401 -> throw StatsProblem("Fox Drop's stats key isn't working. Tell whoever looks after Fox Drop.")
                429 -> throw StatsProblem("Too many lookups right now. Try again in a minute.")
                else -> error("HTTP ${r.code}")
            }
            val d = JSONObject(r.body.string()).getJSONObject("data")
            val all = d.optJSONObject("stats")?.optJSONObject("all")
            val modes = listOf("overall", "solo", "duo", "squad", "ltm").mapNotNull { m ->
                all?.optJSONObject(m)?.let { o ->
                    m to ModeStats(
                        o.optInt("wins"), o.optInt("kills"), o.optDouble("kd", 0.0), o.optInt("matches"),
                        o.optDouble("winRate", 0.0), o.optInt("top10"), o.optInt("minutesPlayed"),
                    )
                }
            }.toMap()
            PlayerStats(
                d.optJSONObject("account")?.optString("name").orEmpty().ifBlank { name.trim() },
                d.optJSONObject("battlePass")?.optInt("level"),
                modes,
            )
        }
    }

    /** The raw sprites.json text, checked by parsing before anyone caches it. */
    suspend fun spritesJson(): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url("$SPRITES_URL?t=${System.currentTimeMillis() / 60000}").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code} from $SPRITES_URL")
            r.body.string().also { SpriteList.parse(it) }
        }
    }

    suspend fun status(): Status {
        val s = get("https://status.epicgames.com/api/v2/summary.json")
        val comps = s.optJSONArray("components").objects()
        val fortnite = comps.firstOrNull { it.optString("name") == "Fortnite" && it.optBoolean("group") }
        val fid = fortnite?.optString("id")
        val services = comps.filter { fid != null && it.optString("group_id") == fid }
            .map { ServiceStatus(it.optString("name"), it.optString("status")) }
        val fortniteIds = comps.filter { it.optString("group_id") == fid }.map { it.optString("id") }.toSet() + setOfNotNull(fid)
        fun touchesFortnite(o: JSONObject) =
            o.optJSONArray("components").objects().any { it.optString("id") in fortniteIds } ||
                o.optString("name").contains("Fortnite", ignoreCase = true)
        val incidents = s.optJSONArray("incidents").objects().filter(::touchesFortnite).map {
            Incident(
                it.getString("id"), it.optString("name"), it.optString("status"), it.optString("impact"),
                it.optJSONArray("incident_updates")?.optJSONObject(0)?.optString("body").orEmpty(),
                it.str("shortlink"),
            )
        }
        val maint = s.optJSONArray("scheduled_maintenances").objects().filter(::touchesFortnite).map {
            Maintenance(
                it.getString("id"), it.optString("name"), it.optString("status"),
                it.str("scheduled_for")?.let(::instant), it.str("scheduled_until")?.let(::instant), it.str("shortlink"),
            )
        }
        return Status(services, incidents, maint)
    }

    /** The newest Fox Drop on GitHub. Unauthenticated calls get 60 an hour per IP; the watcher makes 4. */
    suspend fun latestRelease(): AppRelease {
        val r = get("https://api.github.com/repos/LateralDamage/fox-drop/releases/latest")
        val apk = r.optJSONArray("assets").objects().firstOrNull { it.optString("name") == "FoxDrop.apk" }
        return AppRelease(
            r.getString("tag_name").removePrefix("v"),
            r.optString("name").replace('\n', ' '),
            apk?.str("browser_download_url") ?: AppRelease.LATEST_APK,
        )
    }

    private fun cosmetic(c: JSONObject): Cosmetic {
        val img = c.optJSONObject("images")
        return Cosmetic(
            c.getString("id"), c.optString("name"),
            c.optJSONObject("type")?.optString("displayValue").orEmpty(),
            c.optJSONObject("rarity")?.optString("value").orEmpty(),
            c.optString("description"),
            img?.str("icon") ?: img?.str("smallIcon"),
            c.str("added")?.let(::instant),
        )
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

/** optString returns "null" for JSON nulls; this returns a real null for missing or blank values. */
private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun instant(s: String): Instant? = runCatching { Instant.parse(s) }.getOrNull()
