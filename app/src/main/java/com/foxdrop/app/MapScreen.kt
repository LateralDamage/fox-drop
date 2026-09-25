package com.foxdrop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import java.time.LocalDate

/**
 * The current Battle Royale map with every named location on it, from fortnite-api.com (keyless).
 * The date in the URL makes the image cache roll over daily, so a new season's map shows up by itself.
 */
private fun mapUrl() = "https://fortnite-api.com/images/map_en.png?d=${LocalDate.now()}"

/** Full-screen map: pinch to zoom, drag to look around, double-tap to zoom in or back out. */
@Composable
fun MapDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var size by remember { mutableStateOf(IntSize.Zero) }
        // Keeps the map edge from being dragged past the middle of the screen.
        fun clamp(o: Offset): Offset {
            val mx = size.width * (scale - 1) / 2
            val my = size.height * (scale - 1) / 2
            return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
        }
        Column(Modifier.fillMaxSize().background(Night)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🗺️ Island map", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
                    Text("Pinch to zoom · double-tap to zoom in or out", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close map", tint = Color.White) }
            }
            Box(
                // clipToBounds keeps the zoomed map from drawing over the title bar.
                Modifier.fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged { size = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = clamp(offset + pan)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { tap ->
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero }
                            else {
                                // Zoom toward the spot that was tapped.
                                scale = 3f
                                offset = clamp((Offset(size.width / 2f, size.height / 2f) - tap) * (scale - 1))
                            }
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                SubcomposeAsyncImage(
                    model = mapUrl(), contentDescription = "Fortnite island map with location names",
                    contentScale = ContentScale.Fit,
                    loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FoxOrange) } },
                    error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Couldn't load the map. Check your internet.", color = Color(0xFFFFB4A8)) } },
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
                )
            }
        }
    }
}
