package com.foxdrop.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val when_ = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a").withZone(ZoneId.systemDefault())

fun rarityColor(r: String): Color = when (r.lowercase()) {
    "common" -> Color(0xFF8A8F98)
    "uncommon" -> Color(0xFF5FA82B)
    "rare" -> Color(0xFF2F8FE0)
    "epic" -> Color(0xFF9B3FD8)
    "legendary" -> Color(0xFFE38A26)
    "mythic" -> Color(0xFFE8C02A)
    "marvel" -> Color(0xFFC5312C)
    "dc" -> Color(0xFF2F5FB8)
    "icon" -> Color(0xFF2EB6BD)
    "gaminglegends" -> Color(0xFF4B3CC4)
    "starwars" -> Color(0xFF333333)
    "dark" -> Color(0xFFB02AA8)
    "frozen" -> Color(0xFF8FC6E8)
    "lava" -> Color(0xFFD14A1F)
    "shadow" -> Color(0xFF454545)
    "slurp" -> Color(0xFF16B5C9)
    else -> Color(0xFF6A5ACD)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoxDropApp(vm: FoxViewModel, tab: Tab, onTab: (Tab) -> Unit, onTestFox: () -> Unit) {
    var settings by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Night,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Night),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(44.dp))
                        // 20sp so the name still fits beside four buttons on the Fold's narrow outer screen.
                        Text("Fox Drop", fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    IconButton(onClick = { stats = true }) { Icon(Icons.Filled.Leaderboard, "Player stats") }
                    IconButton(onClick = { map = true }) { Icon(Icons.Filled.Map, "Island map") }
                    IconButton(onClick = { vm.refresh(tab) }) { Icon(Icons.Filled.Refresh, "Refresh") }
                    IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Settings, "Alerts") }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                val items = listOf(
                    Triple(Tab.SHOP, "Shop", Icons.Filled.Storefront),
                    Triple(Tab.NEW, "New", Icons.Filled.AutoAwesome),
                    Triple(Tab.NEWS, "News", Icons.Filled.Newspaper),
                    Triple(Tab.EVENTS, "Events", Icons.Filled.Celebration),
                    Triple(Tab.CHAT, "Chat", ImageVector.vectorResource(R.drawable.ic_fox_chat)),
                    Triple(Tab.STATUS, "Servers", Icons.Filled.Dns),
                    Triple(Tab.WISHLIST, "Wishlist", Icons.Filled.Favorite),
                    Triple(Tab.SPRITES, "Sprites", ImageVector.vectorResource(R.drawable.ic_sprite)),
                )
                items.forEach { (t, label, icon) ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { onTab(t) }, icon = { Icon(icon, label) },
                        label = { Text(label, maxLines = 1) }, alwaysShowLabel = false,
                    )
                }
            }
        },
    ) { pad ->
        val loading = when (tab) {
            Tab.SHOP, Tab.WISHLIST -> vm.shop.loading
            Tab.NEW -> vm.fresh.loading
            Tab.NEWS -> vm.news.loading || vm.game.loading
            Tab.STATUS -> vm.status.loading
            Tab.EVENTS -> vm.events.loading
            Tab.CHAT -> false
            Tab.SPRITES -> vm.sprites.loading
        }
        PullToRefreshBox(isRefreshing = loading, onRefresh = { vm.refresh(tab) }, modifier = Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize()) {
            when (tab) {
                Tab.SHOP -> ShopScreen(vm, onTab)
                Tab.NEW -> NewScreen(vm)
                Tab.NEWS -> NewsScreen(vm)
                Tab.STATUS -> StatusScreen(vm)
                Tab.EVENTS -> EventsScreen(vm)
                Tab.CHAT -> ChatScreen(vm)
                Tab.WISHLIST -> WishlistScreen(vm, onTab)
                Tab.SPRITES -> SpritesScreen(vm)
            }
        }
    }
    if (settings) SettingsDialog(vm, onTestFox, onClose = { settings = false })
    if (map) MapDialog(onClose = { map = false })
    if (stats) StatsDialog(vm, onClose = { stats = false })
    vm.link.pending?.let {
        AlertDialog(
            onDismissRequest = { vm.link.pending = null },
            containerColor = Panel,
            title = { Text("Link this phone?", fontWeight = FontWeight.Black) },
            text = { Text("This phone and Fox Drop on the computer will share your wishlist, Sprite checklist and saved Fortnite name. Only link a computer you use.") },
            confirmButton = { Button(onClick = { vm.link.confirm() }) { Text("Link") } },
            dismissButton = { TextButton(onClick = { vm.link.pending = null }) { Text("Cancel") } },
        )
    }
}

// ---------- shared bits ----------

@Composable
internal fun Problem(text: String?) {
    if (text == null) return
    Text(text, color = Color(0xFFFFB4A8), modifier = Modifier.fillMaxWidth().padding(12.dp))
}

@Composable
internal fun Spinner() = Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = FoxOrange)
}

@Composable
internal fun Header(text: String, sub: String? = null) {
    Column(Modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp)) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
        if (sub != null) Text(sub, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

/** A tile in the Fortnite style: rarity gradient behind the render, name bar underneath. */
@Composable
private fun ItemTile(
    title: String, subtitle: String, rarity: String, image: String?,
    wished: Boolean, onWish: () -> Unit, badge: String? = null,
) {
    val c = rarityColor(rarity)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = if (wished) BorderStroke(3.dp, Color(0xFFFFD54F)) else null,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.95f), c.copy(alpha = 0.35f)))),
        ) {
            AsyncImage(image, title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            IconButton(onClick = onWish, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    if (wished) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (wished) "Remove from wishlist" else "Add to wishlist",
                    tint = if (wished) Color(0xFFFF4D6D) else Color.White,
                )
            }
            if (badge != null) {
                Text(
                    badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(Color(0xFFFFD54F), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------- Shop ----------

@Composable
private fun ResetCountdown() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    // The shop turns over at 00:00 UTC.
    val nowI = Instant.ofEpochMilli(now)
    val next = nowI.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
    val d = Duration.between(nowI, next)
    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Next shop reset", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                Text(
                    "%d:%02d:%02d".format(d.toHours(), d.toMinutesPart(), d.toSecondsPart()),
                    fontSize = 30.sp, fontWeight = FontWeight.Black, color = FoxOrange,
                )
            }
            Text(DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(next), color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ShopScreen(vm: FoxViewModel, onTab: (Tab) -> Unit) {
    val wishes by vm.prefs.wishes.collectAsState()
    val wishedIds = wishes.map { it.id }.toSet()
    var filter by remember { mutableStateOf("") }
    val shop = vm.shop
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { ResetCountdown() }
        item(span = { GridItemSpan(maxLineSpan) }) { NextEventStrip(vm, onOpen = { onTab(Tab.EVENTS) }) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                filter, { filter = it }, Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) { Icon(Icons.Filled.Close, "Clear") } },
                placeholder = { Text("Search today's shop") },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Problem(shop.error) }
        val entries = shop.value?.entries.orEmpty().filter {
            filter.isBlank() || it.title.contains(filter, true) || it.items.any { i -> i.name.contains(filter, true) }
        }
        if (shop.value == null && shop.loading) item(span = { GridItemSpan(maxLineSpan) }) { Spinner() }
        // Wishlist hits float to the top.
        val hits = entries.filter { e -> e.items.any { it.id in wishedIds } }
        if (hits.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Header("🦊 On your wishlist!") }
            items(hits, key = { "hit" + it.offerId }) { ShopTile(vm, it, wishedIds) }
        }
        entries.groupBy { it.section }.forEach { (section, list) ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "h$section") { Header(section) }
            items(list, key = { it.offerId }) { ShopTile(vm, it, wishedIds) }
        }
    }
}

@Composable
private fun ShopTile(vm: FoxViewModel, e: ShopEntry, wishedIds: Set<String>) {
    val main = e.items.first()
    val price = if (e.price < e.regularPrice) "${e.price} V-Bucks (was ${e.regularPrice})" else "${e.price} V-Bucks"
    val leaves = e.outDate?.let { Duration.between(Instant.now(), it) }?.takeIf { !it.isNegative }
    val badge = when {
        leaves == null -> null
        leaves.toHours() < 24 -> "Leaves today"
        else -> null
    }
    ItemTile(
        title = e.title,
        subtitle = if (e.items.size > 1) "$price · ${e.items.size} items" else "$price · ${main.type}",
        rarity = main.rarity,
        image = e.image,
        wished = e.items.any { it.id in wishedIds },
        onWish = { vm.prefs.toggleWish(Wish(main.id, main.name, main.type, e.image)) },
        badge = badge,
    )
}

// ---------- New cosmetics ----------

@Composable
private fun NewScreen(vm: FoxViewModel) {
    val wishes by vm.prefs.wishes.collectAsState()
    val ids = wishes.map { it.id }.toSet()
    val f = vm.fresh
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header("New in the game files", f.value?.let { "Update v${it.build} · ${it.items.size} new items. Some haven't hit the shop yet." })
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Problem(f.error) }
        if (f.value == null && f.loading) item(span = { GridItemSpan(maxLineSpan) }) { Spinner() }
        items(f.value?.items.orEmpty(), key = { it.id }) { c ->
            ItemTile(
                c.name, c.type, c.rarity, c.image, c.id in ids,
                onWish = { vm.prefs.toggleWish(Wish(c.id, c.name, c.type, c.image)) },
            )
        }
    }
}

// ---------- News ----------

@Composable
private fun NewsScreen(vm: FoxViewModel) {
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Problem(vm.news.error ?: vm.game.error) }
        val notices = vm.game.value?.notices.orEmpty()
        if (notices.isNotEmpty()) {
            item { Header("📢 From Epic") }
            items(notices) { n ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4A2A0A))) {
                    Column(Modifier.padding(14.dp)) {
                        Text(n.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(n.body, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }
        item { Header("📰 News") }
        if (vm.news.value == null && vm.news.loading) item { Spinner() }
        items(vm.news.value?.posts.orEmpty(), key = { it.id }) { p ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                if (p.image != null) AsyncImage(p.image, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
                Column(Modifier.padding(14.dp)) {
                    Text(p.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(p.body, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
        val cups = vm.game.value?.tournaments.orEmpty()
        if (cups.isNotEmpty()) {
            item { Header("🏆 Tournaments & cups", "Times and sign-up are in the game's Compete tab") }
            items(cups, key = { "t" + it.id }) { t ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Row(Modifier.padding(10.dp)) {
                        AsyncImage(t.image, null, Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(t.title, fontWeight = FontWeight.Bold)
                            if (t.subtitle.isNotBlank()) Text(t.subtitle, fontSize = 12.sp, color = Sky)
                            Text(t.body, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Servers ----------

@Composable
private fun StatusScreen(vm: FoxViewModel) {
    val s = vm.status
    val context = LocalContext.current
    fun open(url: String?) { if (url != null) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Problem(s.error) }
        if (s.value == null && s.loading) item { Spinner() }
        val st = s.value ?: return@LazyColumn
        item {
            val up = st.allUp && st.maintenances.none { it.status == "in_progress" }
            Card(colors = CardDefaults.cardColors(containerColor = if (up) Color(0xFF1E6B3A) else Color(0xFF8A1F1F))) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (up) "✅" else "⚠️", fontSize = 40.sp)
                    Text(if (up) "Fortnite is up!" else "Fortnite is having problems", fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
            }
        }
        if (st.maintenances.isNotEmpty()) {
            item { Header("🛠️ Maintenance") }
            items(st.maintenances, key = { it.id }) { m ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.clickable { open(m.link) }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(m.name, fontWeight = FontWeight.Bold)
                        Text(pretty(m.status), color = Sky)
                        m.start?.let { Text("Starts ${when_.format(it)}") }
                        m.end?.let { Text("Back about ${when_.format(it)}") }
                    }
                }
            }
        }
        if (st.incidents.isNotEmpty()) {
            item { Header("⚠️ Problems right now") }
            items(st.incidents, key = { it.id }) { i ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.clickable { open(i.link) }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(i.name, fontWeight = FontWeight.Bold)
                        Text(pretty(i.status), color = Sky)
                        if (i.latest.isNotBlank()) Text(i.latest, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }
        item { Header("Services") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    st.services.forEachIndexed { n, svc ->
                        if (n > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            val ok = svc.status == "operational"
                            Box(Modifier.size(12.dp).background(if (ok) Color(0xFF4CD964) else Color(0xFFFF5A4F), CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Text(svc.name, Modifier.weight(1f))
                            Text(if (ok) "Up" else pretty(svc.status), color = if (ok) Color(0xFF4CD964) else Color(0xFFFF8A80))
                        }
                    }
                }
            }
        }
    }
}

// ---------- Wishlist ----------

@Composable
private fun WishlistScreen(vm: FoxViewModel, onTab: (Tab) -> Unit) {
    val wishes by vm.prefs.wishes.collectAsState()
    val ids = wishes.map { it.id }.toSet()
    val inShop = vm.shop.value?.entries.orEmpty().flatMap { it.items }.map { it.id }.toSet()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Header("My wishlist", "Heart any skin, and Fox Drop pings you the day it shows up in the shop.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    vm.query, vm::search, Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (vm.query.isNotEmpty()) IconButton(onClick = { vm.search("") }) { Icon(Icons.Filled.Close, "Clear") } },
                    placeholder = { Text("Find any skin, emote, pickaxe…") },
                )
            }
        }
        if (vm.query.trim().length >= 2) {
            if (vm.results.loading) item(span = { GridItemSpan(maxLineSpan) }) { Spinner() }
            val found = vm.results.value.orEmpty()
            if (!vm.results.loading && found.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Nothing called \"${vm.query}\"", Modifier.padding(12.dp))
            }
            items(found, key = { "r" + it.id }) { c ->
                ItemTile(
                    c.name, c.type, c.rarity, c.image, c.id in ids,
                    onWish = { vm.prefs.toggleWish(Wish(c.id, c.name, c.type, c.image)) },
                    badge = if (c.id in inShop) "In shop now!" else null,
                )
            }
        } else {
            if (wishes.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No favourites yet. Search above, or tap ♡ on anything in the Shop or New tabs.", Modifier.padding(12.dp), color = Color.White.copy(alpha = 0.8f))
            }
            items(wishes, key = { it.id }) { w ->
                Box(Modifier.clickable(enabled = w.id in inShop) { onTab(Tab.SHOP) }) {
                    ItemTile(
                        w.name, w.type, "", w.image, true,
                        onWish = { vm.prefs.toggleWish(w) },
                        badge = if (w.id in inShop) "In shop now!" else null,
                    )
                }
            }
        }
    }
}

// ---------- Alerts ----------

@Composable
private fun SettingsDialog(vm: FoxViewModel, onTestFox: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Panel,
        title = { Text("Alerts", fontWeight = FontWeight.Black) },
        text = {
            // Scrolls: with the Link section added, the list runs past the bottom of the Fold's outer screen.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!Notify.canPost(context)) {
                    Text("Notifications are off for Fox Drop. Turn them on in Android Settings → Apps → Fox Drop.", color = Color(0xFFFFB4A8))
                    Spacer(Modifier.height(8.dp))
                }
                vm.prefs.appUpdate?.let { u ->
                    Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.apkUrl))) }) {
                        Text("Get Fox Drop ${u.version} 🦊")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                AlertKind.shown.forEach { k ->
                    var on by remember { mutableStateOf(vm.prefs.enabled(k)) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(k.title, fontWeight = FontWeight.Bold)
                            Text(k.blurb, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        }
                        Switch(on, { on = it; vm.prefs.setEnabled(k, it); if (k == AlertKind.EVENTS) vm.eventsChanged() })
                    }
                }
                TipJar(vm)
                Spacer(Modifier.height(8.dp))
                LinkSection(vm)
                Spacer(Modifier.height(8.dp))
                val last = vm.prefs.lastCheck
                Text(
                    "Checks every 15 minutes. " + if (last > 0) "Last check ${when_.format(Instant.ofEpochMilli(last))}." else "First check is running.",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { WatchWorker.checkNow(context) }) { Text("Check now") }
                    OutlinedButton(onClick = {
                        Notify.post(context, AlertKind.WISHLIST, Tab.WISHLIST, "🦊 Test alert", "Tap me to see the fox run!", id = 9999)
                    }) { Text("Test alert") }
                }
                TextButton(onClick = { onClose(); onTestFox() }) { Text("Watch the fox run 🦊") }
                Text("Fox Drop ${BuildConfig.VERSION_NAME}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                Text(
                    "Fox Drop is an unofficial fan app, not made or endorsed by Epic Games. Fortnite is a trademark of Epic Games, Inc.",
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f),
                )
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lateraldamage.github.io/fox-drop/privacy.html")))
                }) { Text("Privacy policy", fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}

/** Linking with the web app on a computer: scan the QR code it shows, or see and undo an existing link. */
@Composable
private fun LinkSection(vm: FoxViewModel) {
    val context = LocalContext.current
    val link = vm.link
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
    Text("💻 Link a computer", fontWeight = FontWeight.Bold)
    if (link.linked) {
        Text(
            if (link.others > 0) "Linked. Your wishlist, Sprites and Fortnite name stay the same on both." else "Linked, waiting for the computer.",
            fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f),
        )
        TextButton(onClick = { link.unlink() }) { Text("Unlink") }
    } else {
        Text(
            "On the computer, open Fox Drop in the browser → ⚙ Settings → Link your phone, then scan the QR code.",
            fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f),
        )
        OutlinedButton(enabled = !link.busy, onClick = {
            val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE).build()
            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { link.offer(it.rawValue.orEmpty()) }
                .addOnFailureListener { link.notice = "The scanner didn't start. Try the phone's camera app on the QR code instead." }
        }) { Text(if (link.busy) "Linking…" else "📷 Scan QR code") }
    }
    Problem(link.notice)
}

/** Opens the tip page in the browser. Sideloaded builds only (Play has its own payment rules), and only once a link is set. */
@Composable
private fun TipJar(vm: FoxViewModel) {
    val c = vm.config ?: return
    if (!BuildConfig.TIP_JAR || c.tipUrl.isBlank()) return
    val context = LocalContext.current
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
    Text("🦊 Tip jar", fontWeight = FontWeight.Bold)
    Text(c.tipNote.ifBlank { "Fox Drop is free with no ads. A grown-up can leave a tip to keep the fox fed." }, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.tipUrl))) }) { Text("Leave a tip ❤️") }
    Spacer(Modifier.height(8.dp))
}
