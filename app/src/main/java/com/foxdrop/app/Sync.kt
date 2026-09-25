package com.foxdrop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Links this phone to Fox Drop on a computer (the web app) so both share the wishlist, the Sprite checklist
 * and the saved Fortnite name. The computer shows a QR code for a one-time links/{code} document that expires
 * in 5 minutes; this phone scans it and the player taps Link (two steps, on two devices). The phone writes
 * its uid into the link, the computer adds it to profiles/{id}.members, and from then on both listen to that
 * profile. firestore.rules keeps each profile to its members. docs/app/app.js is the computer's half.
 */
class LinkModel(private val scope: CoroutineScope, private val prefs: Prefs) {
    /** A scanned code waiting for the player to confirm. */
    var pending by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false); private set
    var linked by mutableStateOf(prefs.profileId != null); private set
    /** How many other devices share this profile. */
    var others by mutableStateOf(0); private set

    private var reg: ListenerRegistration? = null
    // The profile's values as last seen, so a change that arrived from the other device isn't sent back.
    private var lastWishes: List<Wish>? = null
    private var lastSprites: Set<String>? = null

    fun start() {
        if (!Crew.configured) return
        scope.launch { runCatching { Crew.signIn(); prefs.profileId?.let(::listen) } }
        scope.launch { prefs.wishes.drop(1).collect { push() } }
        scope.launch { prefs.spritesGot.drop(1).collect { push() } }
    }

    /** From the in-app scanner or a foxdrop://link link. The code only arms the Link dialog. */
    fun offer(raw: String) {
        val code = codeOf(raw)
        if (code == null) notice = "That isn't a Fox Drop link code. On the computer: Settings → Link your phone."
        else { notice = null; pending = code }
    }

    fun confirm() = scope.launch {
        val code = pending ?: return@launch
        pending = null
        busy = true
        try {
            val me = Crew.signIn()
            val db = Crew.db
            val link = db.collection("links").document(code)
            val snap = link.get().await()
            val exp = snap.getTimestamp("exp")?.toDate()?.time ?: 0
            if (!snap.exists() || snap.getString("phone") != null) throw LinkProblem("That code was already used or cancelled. Make a new one on the computer.")
            if (exp < System.currentTimeMillis()) throw LinkProblem("That code has expired. Make a new one on the computer.")
            link.update("phone", me).await()
            val profile = db.collection("profiles").document(snap.getString("profile") ?: throw LinkProblem("That code is broken. Make a new one."))
            val doc = withTimeout(30_000) { awaitMembership(profile, me) }
            mergeInto(profile, doc)
            prefs.profileId = profile.id
            listen(profile.id)
            notice = null
        } catch (e: LinkProblem) {
            notice = e.message
        } catch (e: TimeoutCancellationException) {
            notice = "The computer didn't answer. Keep Fox Drop open there and try again."
        } catch (e: Exception) {
            notice = "Linking didn't go through. Check your internet and try again."
        } finally {
            busy = false
        }
    }

    fun unlink() = scope.launch {
        val pid = prefs.profileId ?: return@launch
        runCatching {
            Crew.db.collection("profiles").document(pid).update("members", FieldValue.arrayRemove(Crew.signIn())).await()
        }
        forget()
    }

    /** Sends local changes to the profile. The saved Fortnite name goes along with every push. */
    fun push() {
        val pid = prefs.profileId ?: return
        val wishes = prefs.wishes.value
        val sprites = prefs.spritesGot.value
        if (wishes == lastWishes && sprites == lastSprites) return
        lastWishes = wishes
        lastSprites = sprites
        Crew.db.collection("profiles").document(pid).set(fields(wishes, sprites), SetOptions.merge())
    }

    /** Refused reads mean the computer hasn't added this phone yet, so keep asking until it has. */
    private suspend fun awaitMembership(profile: DocumentReference, me: String): DocumentSnapshot {
        while (true) {
            try {
                val d = profile.get().await()
                @Suppress("UNCHECKED_CAST")
                if (me in (d.get("members") as? List<String>).orEmpty()) return d
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
            }
            delay(1500)
        }
    }

    /** First link: keep everything either side had (wishes by id, sprites as a set), then share it. */
    private suspend fun mergeInto(profile: DocumentReference, d: DocumentSnapshot) {
        val theirs = wishesOf(d)
        val wishes = theirs + prefs.wishes.value.filter { w -> theirs.none { it.id == w.id } }
        val sprites = spritesOf(d) + prefs.spritesGot.value
        if (prefs.statsName.isBlank()) d.getString("statsName")?.let { prefs.statsName = it }
        lastWishes = wishes
        lastSprites = sprites
        prefs.setWishes(wishes)
        prefs.setSprites(sprites)
        profile.set(fields(wishes, sprites), SetOptions.merge()).await()
    }

    private fun listen(pid: String) {
        reg?.remove()
        linked = true
        reg = Crew.db.collection("profiles").document(pid).addSnapshotListener { s, e ->
            if (e?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) { forget(); return@addSnapshotListener }
            if (s == null || s.metadata.hasPendingWrites()) return@addSnapshotListener
            @Suppress("UNCHECKED_CAST")
            val members = (s.get("members") as? List<String>).orEmpty()
            others = (members.size - 1).coerceAtLeast(0)
            val w = wishesOf(s)
            val sp = spritesOf(s)
            lastWishes = w
            lastSprites = sp
            if (w != prefs.wishes.value) prefs.setWishes(w)
            if (sp != prefs.spritesGot.value) prefs.setSprites(sp)
            s.getString("statsName")?.takeIf { it.isNotBlank() }?.let { prefs.statsName = it }
        }
    }

    private fun forget() {
        reg?.remove()
        reg = null
        prefs.profileId = null
        linked = false
        others = 0
    }

    private fun fields(wishes: List<Wish>, sprites: Set<String>) = mapOf(
        "wishes" to wishes.map { mapOf("id" to it.id, "name" to it.name, "type" to it.type, "image" to (it.image ?: "")) },
        "sprites" to sprites.sorted(),
        "statsName" to prefs.statsName,
        "at" to FieldValue.serverTimestamp(),
    )

    @Suppress("UNCHECKED_CAST")
    private fun wishesOf(d: DocumentSnapshot) = (d.get("wishes") as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
        val id = m["id"] as? String ?: return@mapNotNull null
        Wish(id, m["name"] as? String ?: "", m["type"] as? String ?: "", (m["image"] as? String)?.ifBlank { null })
    }

    @Suppress("UNCHECKED_CAST")
    private fun spritesOf(d: DocumentSnapshot) = (d.get("sprites") as? List<String>).orEmpty().toSet()

    companion object {
        /** The code from a scanned QR (https://…/fox-drop/link/#CODE) or an app link (foxdrop://link?code=CODE). */
        fun codeOf(raw: String): String? {
            val t = raw.trim()
            val c = when {
                t.startsWith("foxdrop://link") -> android.net.Uri.parse(t).getQueryParameter("code")
                "/fox-drop/link/" in t -> t.substringAfter('#', "")
                else -> t
            }
            return c?.takeIf { it.matches(Regex("[A-Za-z0-9]{20,64}")) }
        }
    }
}

class LinkProblem(message: String) : Exception(message)
