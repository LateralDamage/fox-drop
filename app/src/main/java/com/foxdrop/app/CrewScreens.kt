package com.foxdrop.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clockOnly = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
private val dayAndClock = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a").withZone(ZoneId.systemDefault())
private val Faint = Color.White.copy(alpha = 0.65f)

// ---------- Chat tab ----------

@Composable
fun ChatScreen(vm: FoxViewModel) {
    val crew = vm.crew
    LaunchedEffect(Unit) { crew.start() }
    val me = crew.me
    when {
        !Crew.configured -> Centered("🦊", "Fox Chat is coming soon", "It switches on once the chat server is set up.")
        crew.signInError != null -> Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Problem(crew.signInError)
            OutlinedButton(onClick = { crew.start() }) { Text("Try again") }
        }
        me == null -> Spinner()
        me.banned && !me.admin -> Centered("🚫", "Chat is off for this phone", "The admin turned off chat for this phone.")
        !crew.active -> JoinScreen(vm, me)
        else -> ChatRoom(vm, me)
    }
}

@Composable
private fun Centered(emoji: String, title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(emoji, fontSize = 48.sp)
        Text(title, fontWeight = FontWeight.Black, fontSize = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Faint, textAlign = TextAlign.Center)
    }
}

@Composable
private fun JoinScreen(vm: FoxViewModel, me: Me) {
    val crew = vm.crew
    val rejoin = me.member && crew.locked
    var name by remember { mutableStateOf(me.name.orEmpty()) }
    var code by remember { mutableStateOf("") }
    var claiming by remember { mutableStateOf(false) }
    LaunchedEffect(crew.invite) { crew.invite?.let { code = it } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ImageVector.vectorResource(R.drawable.ic_fox_chat), null, tint = FoxOrange, modifier = Modifier.size(36.dp))
            Text("  Fox Chat", fontWeight = FontWeight.Black, fontSize = 26.sp)
        }
        Text(
            if (rejoin) "The invite code changed. Type the new one to get back in."
            else "Chat with your Fox Drop crew and share live-event tips. Ask the admin for the invite code.",
            color = Color.White.copy(alpha = 0.85f),
        )
        OutlinedTextField(name, { name = it.take(20) }, singleLine = true, label = { Text("Chat name") }, modifier = Modifier.fillMaxWidth())
        Text("Use a nickname, not your real name.", fontSize = 12.sp, color = Faint)
        OutlinedTextField(code, { code = it }, singleLine = true, label = { Text("Invite code") }, modifier = Modifier.fillMaxWidth())
        Problem(crew.notice)
        Button(
            onClick = { crew.join(name, code) }, enabled = name.isNotBlank() && code.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = FoxOrange), modifier = Modifier.fillMaxWidth(),
        ) { Text(if (rejoin) "Get back in" else "Join the crew") }
        TextButton(onClick = { claiming = true }) { Text("I'm the admin", fontSize = 12.sp, color = Faint) }
    }
    if (claiming) AdminClaimDialog(vm, onClose = { claiming = false })
}

@Composable
private fun AdminClaimDialog(vm: FoxViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Panel,
        title = { Text("Admin sign-in", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Type the admin code from the Firebase console. This phone becomes an admin for good.", fontSize = 13.sp, color = Faint)
                OutlinedTextField(name, { name = it.take(20) }, singleLine = true, label = { Text("Your chat name") })
                OutlinedTextField(code, { code = it }, singleLine = true, label = { Text("Admin code") })
                Problem(vm.crew.notice)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && code.isNotBlank(), onClick = { vm.crew.claimAdmin(name, code, onOk = onClose) }) { Text("Sign in") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun ChatRoom(vm: FoxViewModel, me: Me) {
    val crew = vm.crew
    val blocked by vm.prefs.blocked.collectAsState()
    val posts = crew.chat.filter { it.uid !in blocked }
    val list = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    var acting by remember { mutableStateOf<Post?>(null) }
    var admin by remember { mutableStateOf(false) }

    LaunchedEffect(posts.size) { if (posts.isNotEmpty()) list.animateScrollToItem(posts.size - 1) }
    // Whatever is on screen counts as read, so the watcher doesn't alert on it later.
    LaunchedEffect(posts.lastOrNull()?.at) {
        posts.lastOrNull()?.at?.toEpochMilli()?.let { if (it > vm.prefs.chatSeenAt) vm.prefs.chatSeenAt = it }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (me.admin) {
            Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🛡️ You're the admin", Modifier.weight(1f), fontSize = 13.sp)
                if (crew.reports.isNotEmpty()) Text("🚩 ${crew.reports.size}  ", color = Color(0xFFFFB4A8), fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { admin = true }) { Text("Admin") }
            }
        }
        LazyColumn(
            state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (posts.isEmpty()) item { Text("No messages yet. Say hi! 👋", color = Faint, modifier = Modifier.padding(12.dp)) }
            items(posts, key = { it.id }) { p -> Bubble(p, mine = p.uid == me.uid, onLong = { acting = p }) }
        }
        Problem(crew.notice)
        if (!me.canPost) {
            Text(
                "The admin muted you for now. You can still read the chat.", color = Faint, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().background(Panel).padding(14.dp),
            )
        } else {
            Row(Modifier.fillMaxWidth().background(Panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text, { text = it.take(500) }, maxLines = 4, placeholder = { Text("Message the crew") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(enabled = text.isNotBlank(), onClick = { crew.send(text, onOk = { text = "" }) }) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (text.isNotBlank()) FoxOrange else Faint)
                }
            }
        }
    }
    acting?.let { PostActions(vm, me, it, onClose = { acting = null }) }
    if (admin) AdminDialog(vm, me, onClose = { admin = false })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(p: Post, mine: Boolean, onLong: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (!mine) Text(p.name, fontSize = 12.sp, color = Sky, fontWeight = FontWeight.Bold)
        Box(
            Modifier.widthIn(max = 300.dp)
                .background(if (mine) FoxOrange else Panel, RoundedCornerShape(16.dp))
                .combinedClickable(onClick = {}, onLongClick = onLong)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) { Text(p.text) }
        p.at?.let { Text(clockOnly.format(it), fontSize = 10.sp, color = Faint) }
    }
}

/** Long-press menu on a message or comment: report and hide for anyone, delete/mute/ban for the admin. */
@Composable
private fun PostActions(vm: FoxViewModel, me: Me, p: Post, onClose: () -> Unit) {
    val crew = vm.crew
    val mine = p.uid == me.uid
    val member = crew.members.firstOrNull { it.uid == p.uid }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Panel,
        title = { Text(p.name.ifBlank { "Message" }, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("\"${p.text}\"", color = Faint, maxLines = 4)
                Spacer(Modifier.height(8.dp))
                if (!mine) {
                    TextButton(onClick = { crew.report(p); onClose() }) { Text("🚩 Report to the admin") }
                    TextButton(onClick = { vm.prefs.block(p.uid); onClose() }) { Text("🙈 Hide everything from ${p.name}") }
                }
                if (me.admin) {
                    HorizontalDivider()
                    TextButton(onClick = { crew.delete(p.path); onClose() }) { Text("🗑️ Delete this message") }
                    if (!mine && member != null) {
                        TextButton(onClick = { crew.setMuted(p.uid, !member.muted); onClose() }) {
                            Text(if (member.muted) "🔊 Unmute ${p.name}" else "🔇 Mute ${p.name}")
                        }
                        TextButton(onClick = { crew.setBanned(p.uid, !member.banned); onClose() }) {
                            Text(if (member.banned) "✅ Unban ${p.name}" else "🚫 Ban ${p.name}")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

@Composable
private fun AdminDialog(vm: FoxViewModel, me: Me, onClose: () -> Unit) {
    val crew = vm.crew
    val context = LocalContext.current
    var newCode by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Panel,
        title = { Text("🛡️ Admin", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Text("Invite code", fontWeight = FontWeight.Bold)
                    Text("Now: ${crew.inviteCode ?: "not set yet"}", color = Faint)
                    crew.inviteCode?.let { code ->
                        OutlinedButton(onClick = { shareInvite(context, code) }) { Text("📨 Share invite link") }
                        Text("Sends a link by text or email. Tapping it opens Fox Chat with the code filled in.", fontSize = 12.sp, color = Faint)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newCode, { newCode = it.take(40) }, singleLine = true, placeholder = { Text("New code") }, modifier = Modifier.weight(1f))
                        TextButton(enabled = newCode.isNotBlank(), onClick = { crew.setInvite(newCode, onOk = { newCode = "" }) }) { Text("Change") }
                    }
                    Text("Changing it locks everyone out of the chat until they type the new code. You stay in.", fontSize = 12.sp, color = Faint)
                    Problem(crew.notice)
                }
                item { HorizontalDivider(); Text("Reports (${crew.reports.size})", fontWeight = FontWeight.Bold) }
                if (crew.reports.isEmpty()) item { Text("Nothing reported.", color = Faint) }
                items(crew.reports, key = { "r" + it.id }) { r ->
                    Card(colors = CardDefaults.cardColors(containerColor = Night)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${r.name}: ${r.text}", maxLines = 4)
                            Row {
                                TextButton(onClick = { crew.delete(r.path); crew.dismissReport(r.id) }) { Text("Delete message") }
                                TextButton(onClick = { crew.dismissReport(r.id) }) { Text("Dismiss") }
                            }
                        }
                    }
                }
                item { HorizontalDivider(); Text("Members (${crew.members.size})", fontWeight = FontWeight.Bold) }
                items(crew.members, key = { "m" + it.uid }) { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name + if (m.uid == me.uid) " (you)" else "")
                            Text(
                                listOfNotNull("muted".takeIf { m.muted }, "banned".takeIf { m.banned }).joinToString(" · ").ifBlank { "ok" },
                                fontSize = 11.sp, color = if (m.muted || m.banned) Color(0xFFFFB4A8) else Faint,
                            )
                        }
                        if (m.uid != me.uid) {
                            TextButton(onClick = { crew.setMuted(m.uid, !m.muted) }) { Text(if (m.muted) "Unmute" else "Mute", fontSize = 12.sp) }
                            TextButton(onClick = { crew.setBanned(m.uid, !m.banned) }) { Text(if (m.banned) "Unban" else "Ban", fontSize = 12.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}

// ---------- Crew tips (Events tab) ----------

private fun tipWhen(t: Tip): String = when {
    t.start != null -> dayAndClock.format(t.start)
    t.date != null -> "${t.date} · time not announced yet"
    else -> ""
}

@Composable
fun TipsSection(vm: FoxViewModel) {
    val tips = vm.crew.tips
    var openId by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header("Crew tips", "Events people heard about. Once the admin approves one, it goes on everyone's calendar.")
        if (tips.isEmpty()) Text("No tips waiting. Heard about an event? Add it below and share it with the crew.", fontSize = 13.sp, color = Faint)
        tips.forEach { t ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().clickable { openId = t.id }) {
                Column(Modifier.padding(14.dp)) {
                    Text("💡 ${t.title}", fontWeight = FontWeight.Bold)
                    Text(tipWhen(t), fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    Text("from ${t.name} · tap to comment", fontSize = 11.sp, color = Sky)
                }
            }
        }
    }
    // Looked up live, so the dialog closes on its own if the tip is approved or deleted meanwhile.
    tips.firstOrNull { it.id == openId }?.let { TipDialog(vm, it, onClose = { openId = null }) }
}

@Composable
private fun TipDialog(vm: FoxViewModel, tip: Tip, onClose: () -> Unit) {
    val crew = vm.crew
    val me = crew.me ?: return
    val blocked by vm.prefs.blocked.collectAsState()
    var text by remember { mutableStateOf("") }
    var acting by remember { mutableStateOf<Post?>(null) }
    var approving by remember { mutableStateOf(false) }
    DisposableEffect(tip.id) {
        crew.openTip(tip.id)
        onDispose { crew.openTip(null) }
    }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Panel,
        title = { Text("💡 ${tip.title}", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tipWhen(tip), color = Color.White.copy(alpha = 0.85f))
                if (tip.note.isNotBlank()) Text(tip.note, fontSize = 13.sp, color = Faint)
                Text("from ${tip.name}", fontSize = 12.sp, color = Sky)
                if (me.admin || tip.uid == me.uid) Row {
                    if (me.admin) TextButton(onClick = { approving = true }) { Text("✅ Approve") }
                    TextButton(onClick = { crew.delete("tips/${tip.id}"); onClose() }) { Text("🗑️ Delete tip") }
                }
                HorizontalDivider()
                val comments = crew.comments.filter { it.uid !in blocked }
                LazyColumn(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (comments.isEmpty()) item { Text("No comments yet.", color = Faint, fontSize = 13.sp) }
                    items(comments, key = { it.id }) { c -> Bubble(c, mine = c.uid == me.uid, onLong = { acting = c }) }
                }
                Problem(crew.notice)
                if (me.canPost) Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it.take(300) }, maxLines = 3, placeholder = { Text("Add a comment") }, modifier = Modifier.weight(1f))
                    IconButton(enabled = text.isNotBlank(), onClick = { crew.comment(tip.id, text, onOk = { text = "" }) }) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (text.isNotBlank()) FoxOrange else Faint)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
    acting?.let { PostActions(vm, me, it, onClose = { acting = null }) }
    if (approving) EventDialog(
        heading = "Approve for everyone", confirm = { "Publish" }, initial = tip.asEvent(),
        onDone = { e -> crew.approve(tip, e); approving = false; onClose() },
        onClose = { approving = false },
    )
}

/** The web page behind every invite link; it opens the app with the code, or offers the download. */
fun inviteLink(code: String) = "https://lateraldamage.github.io/fox-drop/join/#" + Uri.encode(code)

/** Opens Android's share sheet (Messages, Gmail and the rest) with the invite link and the code itself. */
private fun shareInvite(context: Context, code: String) {
    val text = "Join my Fox Chat crew in Fox Drop! 🦊\n${inviteLink(code)}\n\nInvite code: $code"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Join my Fox Chat crew")
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Send the invite"))
}
