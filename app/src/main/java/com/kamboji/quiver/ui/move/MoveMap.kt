package com.kamboji.quiver.ui.move

import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import com.kamboji.quiver.move.PaceColours
import java.io.File

/**
 * OpenStreetMap under a route. Recording never needs this — only viewing
 * fetches tiles. [follow] keeps the newest point centred (live trail);
 * otherwise the map fits the whole route once. [paceColours] colours the
 * route slow → fast like Strava; [replayAt] draws it only up to that fix,
 * with a dot that the map follows.
 */
@Composable
fun MoveMap(
    track: List<Fix>,
    follow: Boolean,
    accent: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
    paceColours: Boolean = false,
    replayAt: Int? = null,
) {
    val map = rememberOsmMap(dark)
    val drawn = remember { arrayOfNulls<Any>(2) } // the track + colouring the route overlays were built for
    val progress = remember { Polyline(map).apply { outlinePaint.color = accent.toArgb(); outlinePaint.strokeWidth = 12f; outlinePaint.strokeCap = Paint.Cap.ROUND } }
    val dot = remember { Polygon(map).apply { fillPaint.color = android.graphics.Color.WHITE; outlinePaint.color = accent.toArgb(); outlinePaint.strokeWidth = 6f } }

    Box(modifier) {
        AndroidView({ map }) { m ->
            val points = track.map { GeoPoint(it.lat, it.lon) }
            if (drawn[0] !== track || drawn[1] != (paceColours to (replayAt != null))) {
                drawn[0] = track
                drawn[1] = paceColours to (replayAt != null)
                m.overlays.clear()
                routeLines(m, track, accent, paceColours, faded = replayAt != null).forEach { m.overlays += it }
                if (replayAt != null) { m.overlays += progress; m.overlays += dot }
            }
            if (replayAt != null && points.isNotEmpty()) {
                val i = replayAt.coerceIn(0, points.lastIndex)
                progress.setPoints(points.subList(0, i + 1))
                dot.points = Polygon.pointsAsCircle(points[i], 9.0)
                m.controller.setCenter(points[i])
            }
            when {
                points.isEmpty() || replayAt != null -> Unit
                follow -> m.controller.setCenter(points.last())
                m.tag == null && points.size > 1 -> {
                    m.tag = "fitted"
                    fit(m, points)
                }
                m.tag == null -> m.controller.setCenter(points.first())
            }
            m.invalidate()
        }
        Attribution()
    }
}

/**
 * Every route on one map, translucent, so the paths you take most glow
 * brightest — a personal heatmap. Drawn from the stored thumbnails, so no
 * point table is read.
 */
@Composable
fun HeatMap(routes: List<List<Pair<Double, Double>>>, accent: Color, dark: Boolean, modifier: Modifier = Modifier) {
    val map = rememberOsmMap(dark)
    Box(modifier) {
        AndroidView({ map }) { m ->
            if (m.tag == routes) return@AndroidView
            m.tag = routes
            m.overlays.clear()
            val all = routes.filter { it.size > 1 }.map { r -> r.map { (lat, lon) -> GeoPoint(lat, lon) } }
            all.forEach { pts ->
                m.overlays += Polyline(m).apply {
                    setPoints(pts)
                    outlinePaint.color = accent.copy(alpha = 0.35f).toArgb()
                    outlinePaint.strokeWidth = 9f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }
            }
            val flat = all.flatten()
            if (flat.size > 1) fit(m, flat)
            m.invalidate()
        }
        Attribution()
    }
}

/**
 * Zooms to show [points]. A straight-line route has a zero-width box, which
 * osmdroid can't zoom to (it stays at world view), so the box gets at least
 * ~400 m each way; and it waits for the first layout if the map has no size yet.
 */
private fun fit(m: MapView, points: List<GeoPoint>) {
    val n = points.maxOf { it.latitude }
    val s = points.minOf { it.latitude }
    val e = points.maxOf { it.longitude }
    val w = points.minOf { it.longitude }
    // 30% margin, and never narrower than ≈ 400 m.
    val halfLat = maxOf((n - s) * 0.65, 0.002)
    val halfLon = maxOf((e - w) * 0.65, 0.002)
    val cLat = (n + s) / 2
    val cLon = (e + w) / 2
    val box = BoundingBox(cLat + halfLat, cLon + halfLon, cLat - halfLat, cLon - halfLon)
    if (m.width > 0) m.post { m.zoomToBoundingBox(box, false) }
    else m.addOnFirstLayoutListener { _, _, _, _, _ -> m.zoomToBoundingBox(box, false) }
}

@Composable
private fun rememberOsmMap(dark: Boolean): MapView {
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
            onResume()
        }
    }
    DisposableEffect(map) { onDispose { map.onDetach() } }
    return map
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Attribution() {
    // Required by the OpenStreetMap licence.
    Text(
        "© OpenStreetMap contributors",
        fontSize = 9.sp, color = Color.White,
        modifier = Modifier.align(Alignment.BottomEnd).background(Color(0x99000000)).padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** Slow → fast, like Strava's pace map. */
private val PACE = intArrayOf(0xFF3B82F6.toInt(), 0xFF06B6D4.toInt(), 0xFF22C55E.toInt(), 0xFFEAB308.toInt(), 0xFFF97316.toInt(), 0xFFEF4444.toInt())

/** The route as one line, or as runs of pace-coloured segments. */
private fun routeLines(m: MapView, track: List<Fix>, accent: Color, paceColours: Boolean, faded: Boolean): List<Polyline> {
    fun line(pts: List<GeoPoint>, color: Int) = Polyline(m).apply {
        setPoints(pts)
        outlinePaint.color = if (faded) (color and 0x00FFFFFF) or 0x55000000 else color
        outlinePaint.strokeWidth = 12f
        outlinePaint.strokeCap = Paint.Cap.ROUND
    }
    val points = track.map { GeoPoint(it.lat, it.lon) }
    if (!paceColours || track.size < 3) return listOf(line(points, accent.toArgb()))
    val buckets = PaceColours.buckets(track, PACE.size)
    val out = mutableListOf<Polyline>()
    var start = 0
    for (i in 1..buckets.size) {
        if (i == buckets.size || buckets[i] != buckets[start]) {
            out += line(points.subList(start, i + 1), PACE[buckets[start]])
            start = i
        }
    }
    return out
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
