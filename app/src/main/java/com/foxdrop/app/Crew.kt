package com.foxdrop.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The crew: an invite-only chat plus event tips, on Firebase (Firestore + anonymous sign-in).
 * firestore.rules in the repo root is the real gatekeeper; the app only hides what the rules would refuse.
 *  - Joining takes the invite code. Changing the code locks everyone out until they type the new one.
 *  - The admin is whichever phone typed the admin code, which is set once in the Firebase console.
 *  - Tips wait for the admin; approving one copies it into `events`, which every Fox Drop phone reads.
 */
object Crew {
    val configured = BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    fun init(context: Context) {
        if (!configured || FirebaseApp.getApps(context).isNotEmpty()) return
        FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .build(),
        )
    }

    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** Every phone gets an anonymous account on first use. It lasts until the app is uninstalled or its data cleared. */
    suspend fun signIn(): String {
        val auth = FirebaseAuth.getInstance()
        return auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
    }

    /** Official events in the events.json shape, cached in Prefs so alarms can be re-armed offline after a reboot. */
    fun eventsToJson(docs: List<DocumentSnapshot>): String = JSONArray(docs.mapNotNull { d ->
        val title = d.getString("title") ?: return@mapNotNull null
        val o = JSONObject().put("id", CREW_PREFIX + d.id).put("title", title).put("note", d.getString("note").orEmpty())
        val start = d.getTimestamp("start")
        val date = d.getString("date")
        when {
            start != null -> o.put("start", start.toInstant().toString())
            date != null -> o.put("date", date)
            else -> return@mapNotNull null
        }
        o
    }).toString()

    suspend fun fetchEvents(): String = eventsToJson(db.collection("events").get().await().documents)

    /** An event's time as Firestore fields: `start` when the minute is known, `date` when only the day is. */
    fun whenFields(e: LiveEvent): Map<String, Any> =
        if (e.approx) mapOf("date" to e.start.atZone(ZoneId.systemDefault()).toLocalDate().toString())
        else mapOf("start" to Timestamp(e.start))

    /** Official events carry this prefix in the app, so they can't collide with events.json ids. */
    const val CREW_PREFIX = "crew-"
}

/** A chat message, or a comment under a tip. `path` is its Firestore document path. */
data class Post(val id: String, val path: String, val uid: String, val name: String, val text: String, val at: Instant?)

data class Tip(
    val id: String, val uid: String, val name: String, val title: String, val note: String,
    val start: Instant?, val date: String?, val at: Instant?, val status: String,
) {
    /** The tip as an event, to prefill the approve dialog. */
    fun asEvent(): LiveEvent = LiveEvent(
        id = Crew.CREW_PREFIX + id, title = title,
        start = start ?: date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.atStartOfDay(ZoneId.systemDefault())?.toInstant() ?: Instant.now(),
        approx = start == null, note = note, link = null, custom = false,
    )
}

data class Member(val uid: String, val name: String, val muted: Boolean, val banned: Boolean)
data class Report(val id: String, val path: String, val uid: String, val name: String, val text: String)

/** Who this phone is to the crew. */
data class Me(
    val uid: String, val admin: Boolean, val adminName: String?,
    val member: Boolean, val name: String?, val muted: Boolean, val banned: Boolean,
) {
    /** Must match authored() in firestore.rules: an admin who never joined posts under the admin name. */
    val displayName get() = if (admin && !member) adminName ?: "Admin" else name.orEmpty()
    val canPost get() = admin || (member && !banned && !muted)
}

private fun DocumentSnapshot.time(field: String): Instant? =
    getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant()

fun postOf(d: DocumentSnapshot): Post? {
    val uid = d.getString("uid") ?: return null
    return Post(d.id, d.reference.path, uid, d.getString("name").orEmpty(), d.getString("text").orEmpty(), d.time("at"))
}

fun tipOf(d: DocumentSnapshot): Tip? = Tip(
    id = d.id, uid = d.getString("uid") ?: return null, name = d.getString("name").orEmpty(),
    title = d.getString("title") ?: return null, note = d.getString("note").orEmpty(),
    start = d.getTimestamp("start")?.toInstant(), date = d.getString("date"), at = d.time("at"),
    status = d.getString("status") ?: "pending",
)

/** Live crew state for the screens. Firestore listeners call back on the main thread. */
class CrewModel(private val scope: CoroutineScope, private val prefs: Prefs, private val onEvents: () -> Unit) {
    var me by mutableStateOf<Me?>(null); private set
    var signInError by mutableStateOf<String?>(null); private set
    /** The rules refused the chat: the admin changed the invite code since this phone joined. */
    var locked by mutableStateOf(false); private set
    /** The last action that failed, shown under the chat or in a dialog. */
    var notice by mutableStateOf<String?>(null)
    /** An invite code from a tapped invite link, filled into the join screen. */
    var invite by mutableStateOf<String?>(null)

    var chat by mutableStateOf<List<Post>>(emptyList()); private set
    var tips by mutableStateOf<List<Tip>>(emptyList()); private set
    var comments by mutableStateOf<List<Post>>(emptyList()); private set
    var members by mutableStateOf<List<Member>>(emptyList()); private set
    var reports by mutableStateOf<List<Report>>(emptyList()); private set
    var inviteCode by mutableStateOf<String?>(null); private set

    /** In the chat: the admin, or a member whose code is current and who isn't banned. */
    val active get() = me?.let { it.admin || (it.member && !it.banned && !locked) } == true

    private var uid: String? = null
    private var starting = false
    private var adminDoc: DocumentSnapshot? = null
    private var memberDoc: DocumentSnapshot? = null
    private val base = mutableListOf<ListenerRegistration>()
    private val feeds = mutableListOf<ListenerRegistration>()
    private var feedKey: String? = null
    private var commentsReg: ListenerRegistration? = null

    fun start() {
        if (!Crew.configured || uid != null || starting) return
        starting = true
        signInError = null
        scope.launch {
            try {
                val id = Crew.signIn()
                uid = id
                val db = Crew.db
                // Only server-confirmed copies count. Right after joining or claiming admin, the listener first
                // sees the local, unsaved write; opening the feeds then races the server, the rules refuse the
                // chat listen, and a refused listener never comes back. MetadataChanges.INCLUDE is what delivers
                // the confirmation: without it Firestore stays quiet, because the saved copy has the same data.
                base += db.collection("admins").document(id).addSnapshotListener(MetadataChanges.INCLUDE) { s, _ ->
                    if (s != null && !s.metadata.hasPendingWrites()) { adminDoc = s; update() }
                }
                base += db.collection("members").document(id).addSnapshotListener(MetadataChanges.INCLUDE) { s, _ ->
                    if (s != null && !s.metadata.hasPendingWrites()) { memberDoc = s; update() }
                }
                base += db.collection("events").addSnapshotListener { s, _ ->
                    if (s != null) { prefs.cloudEvents = Crew.eventsToJson(s.documents); onEvents() }
                }
            } catch (e: Exception) {
                signInError = "Couldn't reach the chat server. Check your internet and try again."
            } finally {
                starting = false
            }
        }
    }

    private fun update() {
        val id = uid ?: return
        val a = adminDoc?.takeIf { it.exists() }
        val m = memberDoc?.takeIf { it.exists() }
        me = Me(
            uid = id, admin = a != null, adminName = a?.getString("name"),
            member = m != null, name = m?.getString("name"),
            muted = m?.getBoolean("muted") == true, banned = m?.getBoolean("banned") == true,
        )
        resubscribe()
        prefs.crewActive = active
        prefs.crewAdmin = me?.admin == true
    }

    /** (Re)opens the member feeds whenever admin status, the joined code or the ban changes. */
    private fun resubscribe() {
        val m = me ?: return
        val key = "${m.admin}|${memberDoc?.getString("code")}|${m.banned}"
        if (key == feedKey) return
        feedKey = key
        locked = false
        feeds.forEach { it.remove() }
        feeds.clear()
        chat = emptyList(); tips = emptyList(); members = emptyList(); reports = emptyList(); inviteCode = null
        if (!m.admin && !(m.member && !m.banned)) return

        val db = Crew.db
        feeds += db.collection("chat").orderBy("at", Query.Direction.DESCENDING).limit(200)
            .addSnapshotListener { s, e ->
                if (e != null) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED && !m.admin) {
                        locked = true
                        prefs.crewActive = false
                    } else {
                        retryFeeds()   // a dead listener never recovers on its own
                    }
                }
                if (s != null) chat = s.documents.mapNotNull(::postOf).reversed()
            }
        feeds += db.collection("tips").orderBy("at", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { s, _ -> if (s != null) tips = s.documents.mapNotNull(::tipOf).filter { it.status == "pending" } }
        if (m.admin) {
            feeds += db.collection("members").addSnapshotListener { s, _ ->
                if (s != null) members = s.documents.map {
                    Member(it.id, it.getString("name").orEmpty(), it.getBoolean("muted") == true, it.getBoolean("banned") == true)
                }.sortedBy { it.name.lowercase() }
            }
            feeds += db.collection("reports").addSnapshotListener { s, _ ->
                if (s != null) reports = s.documents.map {
                    Report(it.id, it.getString("path").orEmpty(), it.getString("uid").orEmpty(),
                        it.getString("name").orEmpty(), it.getString("text").orEmpty())
                }
            }
            feeds += db.document("config/invite").addSnapshotListener { s, _ -> inviteCode = s?.getString("code") }
        }
    }

    /** Reopens the feeds a few seconds after one failed, once per failure. */
    private fun retryFeeds() {
        scope.launch {
            kotlinx.coroutines.delay(3000)
            feedKey = null
            resubscribe()
        }
    }

    fun openTip(id: String?) {
        commentsReg?.remove()
        commentsReg = null
        comments = emptyList()
        if (id == null) return
        commentsReg = Crew.db.collection("tips").document(id).collection("comments").orderBy("at").limit(200)
            .addSnapshotListener { s, _ -> if (s != null) comments = s.documents.mapNotNull(::postOf) }
    }

    fun close() {
        (base + feeds).forEach { it.remove() }
        commentsReg?.remove()
    }

    // ---------- actions ----------

    /** Runs a write; a refusal from the rules becomes [denied], anything else is blamed on the network. */
    private fun act(denied: String, onOk: () -> Unit = {}, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                notice = null
                onOk()
            } catch (e: Exception) {
                notice = if ((e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) denied
                    else "That didn't go through. Check your internet and try again."
            }
        }
    }

    private fun stamp(vararg fields: Pair<String, Any>): Map<String, Any> {
        val m = me!!
        return mapOf("uid" to m.uid, "name" to m.displayName, "at" to FieldValue.serverTimestamp()) + fields
    }

    fun join(name: String, code: String, onOk: () -> Unit = {}) = act("That invite code didn't work. Check it with the admin.", onOk) {
        val ref = Crew.db.collection("members").document(uid!!)
        if (memberDoc?.exists() == true) ref.update(mapOf("code" to code.trim(), "name" to name.trim())).await()
        else ref.set(mapOf(
            "code" to code.trim(), "name" to name.trim(), "muted" to false, "banned" to false,
            "joined" to FieldValue.serverTimestamp(),
        )).await()
    }

    fun claimAdmin(name: String, code: String, onOk: () -> Unit = {}) = act("That admin code didn't work.", onOk) {
        Crew.db.collection("admins").document(uid!!).set(mapOf("code" to code.trim(), "name" to name.trim())).await()
    }

    fun send(text: String, onOk: () -> Unit = {}) = act("You can't post right now.", onOk) {
        Crew.db.collection("chat").add(stamp("text" to text.trim())).await()
    }

    fun postTip(e: LiveEvent) = act("You can't post tips right now.") {
        Crew.db.collection("tips").add(stamp("title" to e.title, "note" to e.note, "status" to "pending") + Crew.whenFields(e)).await()
    }

    fun comment(tipId: String, text: String, onOk: () -> Unit = {}) = act("You can't comment right now.", onOk) {
        Crew.db.collection("tips").document(tipId).collection("comments").add(stamp("text" to text.trim())).await()
    }

    fun report(p: Post) = act("Couldn't send the report.") {
        Crew.db.collection("reports").add(mapOf(
            "by" to uid!!, "path" to p.path, "uid" to p.uid, "name" to p.name, "text" to p.text,
            "at" to FieldValue.serverTimestamp(),
        )).await()
    }

    /** Deletes a message, comment or tip by path. Admins can delete anything; authors can delete their own tips. */
    fun delete(path: String) = act("Only the admin can delete that.") { Crew.db.document(path).delete().await() }

    // ---------- admin ----------

    fun approve(tip: Tip, e: LiveEvent) = act("Only the admin can approve tips.") {
        val db = Crew.db
        db.runBatch { b ->
            b.set(db.collection("events").document(tip.id),
                mapOf("title" to e.title, "note" to e.note, "at" to FieldValue.serverTimestamp()) + Crew.whenFields(e))
            b.update(db.collection("tips").document(tip.id), "status", "approved")
        }.await()
    }

    fun publish(e: LiveEvent) = act("Only the admin can publish events.") {
        Crew.db.collection("events").add(mapOf("title" to e.title, "note" to e.note, "at" to FieldValue.serverTimestamp()) + Crew.whenFields(e)).await()
    }

    fun removeOfficial(eventId: String) = act("Only the admin can remove official events.") {
        Crew.db.collection("events").document(eventId.removePrefix(Crew.CREW_PREFIX)).delete().await()
    }

    fun setInvite(code: String, onOk: () -> Unit = {}) = act("Only the admin can change the invite code.", onOk) {
        Crew.db.document("config/invite").set(mapOf("code" to code.trim())).await()
    }

    fun setMuted(uid: String, on: Boolean) = act("Only the admin can mute.") {
        Crew.db.collection("members").document(uid).update("muted", on).await()
    }

    fun setBanned(uid: String, on: Boolean) = act("Only the admin can ban.") {
        Crew.db.collection("members").document(uid).update("banned", on).await()
    }

    fun dismissReport(id: String) = act("Only the admin can clear reports.") {
        Crew.db.collection("reports").document(id).delete().await()
    }
}
