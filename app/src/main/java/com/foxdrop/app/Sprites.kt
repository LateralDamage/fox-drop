package com.foxdrop.app

import org.json.JSONObject

/**
 * Fortnite Sprites for the collection tracker. Epic has no public list and no way to read a player's
 * collection without their login, so the list is hand-kept in docs/sprites.json on the Fox Drop Pages
 * site (edit and push, no app release) and Kollin checks off what he has. The checklist lives on the phone.
 */
const val SPRITES_URL = "https://lateraldamage.github.io/fox-drop/sprites.json"

data class SpriteVariant(val id: String, val name: String, val note: String)

/** One Sprite family; [variants] are the variant ids it comes in (most have all of them). */
data class Sprite(val id: String, val name: String, val ability: String, val where: String, val variants: List<String>)

data class SpriteList(val season: String, val updated: String, val variants: List<SpriteVariant>, val sprites: List<Sprite>) {
    /** Every collectable, as the keys the checklist stores: "family:variant". */
    val keys get() = sprites.flatMap { s -> s.variants.map { "${s.id}:$it" } }

    companion object {
        fun parse(json: String): SpriteList {
            val o = JSONObject(json)
            val va = o.getJSONArray("variants")
            val variants = (0 until va.length()).map { va.getJSONObject(it) }
                .map { SpriteVariant(it.getString("id"), it.getString("name"), it.optString("note")) }
            val all = variants.map { it.id }
            val sa = o.getJSONArray("sprites")
            val sprites = (0 until sa.length()).map { sa.getJSONObject(it) }.map { s ->
                val own = s.optJSONArray("variants")
                Sprite(
                    s.getString("id"), s.getString("name"), s.optString("ability"), s.optString("where"),
                    own?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: all,
                )
            }
            return SpriteList(o.optString("season"), o.optString("updated"), variants, sprites)
        }
    }
}
