package com.foxdrop.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fortnite Sprites for the collection tracker. Epic has no public list and no way to read a player's
 * collection without their login, so the list is hand-kept in docs/sprites.json on the Fox Drop Pages
 * site (edit and push, no app release) and Kollin checks off what he has. The checklist lives on the phone.
 */
const val SPRITES_URL = "https://lateraldamage.github.io/fox-drop/sprites.json"

data class SpriteVariant(val id: String, val name: String, val note: String)

/** Pictures sit next to sprites.json on the Pages site, so "img" is a path relative to it. */
private const val SITE = "https://lateraldamage.github.io/fox-drop/"

/**
 * One Sprite family; [variants] are the variant ids it comes in that can be collected now (most have all of them).
 * [locked] maps a variant that isn't out yet to its release day ("Nov 7"); those show as lock chips and don't count.
 * [img] is blank when there's no picture.
 */
data class Sprite(
    val id: String, val name: String, val ability: String, val where: String, val variants: List<String>, val img: String = "",
    val locked: Map<String, String> = emptyMap(),
)

data class SpriteList(
    val season: String, val updated: String, val variants: List<SpriteVariant>, val sprites: List<Sprite>, val credit: String = "",
) {
    /** Every collectable, as the keys the checklist stores: "family:variant". */
    val keys get() = sprites.flatMap { s -> s.variants.map { "${s.id}:$it" } }

    companion object {
        fun parse(json: String): SpriteList {
            val o = JSONObject(json)
            val va = o.getJSONArray("variants")
            val variants = (0 until va.length()).map { va.getJSONObject(it) }
                .map { SpriteVariant(it.getString("id"), it.getString("name"), it.optString("note")) }
            val all = variants.map { it.id }
            val now = System.currentTimeMillis()
            // "soon" maps a variant to its release date. "variants" lists only what's out, so older app versions
            // (which ignore "soon") never show an unreleased one; here it unlocks once the date passes.
            // "upcoming" holds whole Sprites that aren't out yet, and older versions ignore it too.
            fun sprite(s: JSONObject, default: List<String>): Sprite {
                val own = s.optJSONArray("variants")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: default
                val soon = s.optJSONObject("soon")?.let { m -> m.keys().asSequence().associateWith { m.getString(it) } }.orEmpty()
                val open = soon.filterValues { (releaseAt(it) ?: 0) <= now }.keys
                val locked = soon.filterKeys { it !in open }.mapValues { (_, d) -> dayLabel(d) }
                val have = own + open
                return Sprite(
                    s.getString("id"), s.getString("name"), s.optString("ability"), s.optString("where"),
                    all.filter { it in have } + have.filter { it !in all }.distinct(),
                    s.optString("img").let { if (it.isBlank() || it.startsWith("http")) it else SITE + it },
                    all.filter { it in locked }.associateWith { locked.getValue(it) } + locked.filterKeys { it !in all },
                )
            }
            fun JSONArray?.sprites(default: List<String>) =
                if (this == null) emptyList() else (0 until length()).map { sprite(getJSONObject(it), default) }
            val sprites = o.getJSONArray("sprites").sprites(all) + o.optJSONArray("upcoming").sprites(emptyList())
            return SpriteList(o.optString("season"), o.optString("updated"), variants, sprites, o.optString("credit"))
        }
    }
}

/** A UTC instant like 2026-11-07T14:00:00Z, or a plain date meaning local midnight (same rule as config.json). */
private fun releaseAt(s: String): Long? =
    runCatching { Instant.parse(s).toEpochMilli() }
        .recoverCatching { LocalDate.parse(s).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        .getOrNull()

private fun dayLabel(s: String): String = releaseAt(s)?.let {
    DateTimeFormatter.ofPattern("MMM d", Locale.US).format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
} ?: "soon"
