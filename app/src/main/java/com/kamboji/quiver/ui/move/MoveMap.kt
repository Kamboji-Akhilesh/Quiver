package com.kamboji.quiver.ui.move

import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kamboji.quiver.move.Fix
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * OpenStreetMap under a route. Recording never needs this — only viewing
 * fetches tiles. [follow] keeps the newest point centred (live); otherwise
 * the map fits the whole route once.
 */
@Composable
fun MoveMap(track: List<Fix>, follow: Boolean, accent: Color, dark: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    remember {
        Configuration.getInstance().apply {
            // OSM's tile policy requires an identifying user agent.
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
    }
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.5)
            if (dark) overlayManager.tilesOverlay.setColorFilter(DARK_TILES)
            overlays += Polyline(this).apply {
                outlinePaint.color = accent.toArgb()
                outlinePaint.strokeWidth = 12f
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            }
            onResume()
        }
    }
    DisposableEffect(map) { onDispose { map.onDetach() } }

    Box(modifier) {
        AndroidView({ map }) { m ->
            val points = track.map { GeoPoint(it.lat, it.lon) }
            (m.overlays.first { it is Polyline } as Polyline).setPoints(points)
            when {
                points.isEmpty() -> Unit
                follow -> m.controller.setCenter(points.last())
                m.tag == null && points.size > 1 -> {
                    m.tag = "fitted"
                    m.post { m.zoomToBoundingBox(BoundingBox.fromGeoPoints(points).increaseByScale(1.3f), false) }
                }
                m.tag == null -> m.controller.setCenter(points.first())
            }
            m.invalidate()
        }
        // Required by the OpenStreetMap licence.
        Text(
            "© OpenStreetMap contributors",
            fontSize = 9.sp, color = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).background(Color(0x99000000)).padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

/**
 * Dark map: invert, then rotate hue 180° so water stays blue and parks green
 * (a plain invert turns water orange). Slightly dimmed to sit under the UI.
 */
private val DARK_TILES = ColorMatrixColorFilter(
    floatArrayOf(
        0.488f, -1.216f, -0.122f, 0f, 217f,
        -0.362f, -0.366f, -0.122f, 0f, 217f,
        -0.362f, -1.216f, 0.728f, 0f, 217f,
        0f, 0f, 0f, 1f, 0f,
    ),
)
