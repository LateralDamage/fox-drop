package com.foxdrop.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

val Night = Color(0xFF120B2E)
val Panel = Color(0xFF221651)
val FoxOrange = Color(0xFFF26B1D)
val Sky = Color(0xFF4FC3F7)

private val FoxColors = darkColorScheme(
    primary = FoxOrange,
    onPrimary = Color.White,
    secondary = Sky,
    background = Night,
    surface = Night,
    surfaceContainer = Panel,
    surfaceVariant = Panel,
    onBackground = Color.White,
    onSurface = Color.White,
)

class MainActivity : ComponentActivity() {
    private val vm: FoxViewModel by viewModels()
    private var tab by mutableStateOf(Tab.SHOP)
    private var foxRun by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            MaterialTheme(colorScheme = FoxColors) {
                val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(foxRun) {
                    if (!foxRun && Build.VERSION.SDK_INT >= 33 && !Notify.canPost(this@MainActivity)) {
                        ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                Box {
                    FoxDropApp(vm, tab, onTab = { tab = it }, onTestFox = { foxRun = true })
                    if (foxRun) RunningFoxOverlay(message = "Fox Drop!", onDone = { foxRun = false })
                }
            }
        }
    }

    /**
     * The fox runs every time the app is opened: on a fresh start, or after being away more than a few
     * seconds. savedInstanceState can't tell these apart, because Android restores saved state after
     * killing the app in the background. Folding or unfolding the phone takes under a second, so it
     * doesn't count.
     */
    override fun onStart() {
        super.onStart()
        FoxApp.visible = true
        val left = FoxApp.leftAt
        if (left == 0L || System.currentTimeMillis() - left > 3000) foxRun = true
    }

    override fun onStop() {
        super.onStop()
        FoxApp.visible = false
        FoxApp.leftAt = System.currentTimeMillis()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A notification tap carries the tab to open and asks for the running fox. */
    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(Notify.EXTRA_TAB)?.let { name ->
            Tab.entries.firstOrNull { it.name == name }?.let {
                tab = it
                vm.refresh(it)
            }
        }
        if (intent.getBooleanExtra(Notify.EXTRA_FOX, false)) foxRun = true
        intent.removeExtra(Notify.EXTRA_FOX)   // so rotation/recreation doesn't replay it
    }
}
