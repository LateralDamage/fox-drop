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
