package com.kamboji.quiver.ui.move

import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Brush
import kotlin.math.log2
import androidx.compose.runtime.key
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxSize
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
import com.kamboji.quiver.move.MovePrefs
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
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
 * otherwise the map fits the whole route once. [paceColours] shades the
 * route slow → fast.
 *
 * [replayAt] (a fractional fix index) turns it into a Strava-style flyover:
 * the map tilts back in perspective, turns so the runner heads up the screen,
 * zooms in and follows, and the route is drawn only as far as the runner has
 * got — nothing ahead is shown.
 */
@Composable
fun MoveMap(
    track: List<Fix>,
    follow: Boolean,
    accent: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
    paceColours: Boolean = false,
    replayAt: Float? = null,
    /** Overrides Settings › Show place names on maps (the full-screen map's own switch). */
    labels: Boolean? = null,
) {
    val context = LocalContext.current
    val showLabels = labels ?: remember { MovePrefs(context).mapLabels }
    // A different tile set is a different map view, so switching labels rebuilds it.
    key(showLabels) { RouteMap(track, follow, accent, dark, modifier, paceColours, replayAt, showLabels) }
}

@Composable
private fun RouteMap(
    track: List<Fix>,
    follow: Boolean,
    accent: Color,
    dark: Boolean,
    modifier: Modifier,
    paceColours: Boolean,
    replayAt: Float?,
    showLabels: Boolean,
) {
    val map = rememberOsmMap(dark, showLabels)
    val replaying = replayAt != null
    val tilt by animateFloatAsState(if (replaying) 55f else 0f, tween(900), label = "tilt")
    val density = LocalDensity.current.density
    // Built once per track + colouring: each run of equal pace is one line over a range of fixes.
    val runs = remember(track, paceColours) { routeRuns(track, accent, paceColours) }
    val lines = remember(runs) { runs.map { (_, color) -> routeLine(map, color) } }
    val dot = remember { Polygon(map).apply { fillPaint.color = android.graphics.Color.WHITE; outlinePaint.color = accent.toArgb(); outlinePaint.strokeWidth = 6f } }
    val camera = remember { FloatArray(2) } // [0] smoothed heading, [1] 1 while a flyover has run (to re-fit after)

    Box(modifier.clipToBounds().background(sky(dark))) {
        Box(
            Modifier
                // During a flyover the map is laid out taller than the view (its centre 60%
                // down) so, tilted back, the far side reaches the horizon instead of ending
                // in a hard edge partway up a tall screen.
                .layout { measurable, constraints ->
                    val h = constraints.maxHeight
                    val mapH = if (replaying) (h * MAP_STRETCH).toInt() else h
                    val placeable = measurable.measure(constraints.copy(minHeight = mapH, maxHeight = mapH))
                    layout(constraints.maxWidth, h) { placeable.place(0, (h * 0.6f - mapH / 2f).toInt()) }
                }
                .graphicsLayer {
                    // Perspective tilt; widened a little so the receding sides stay covered.
                    rotationX = tilt
                    cameraDistance = 7f * density
                    val grow = 1f + tilt / 55f * 0.35f
                    scaleX = grow
                    scaleY = grow
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
        ) {
            AndroidView({ map }, Modifier.fillMaxSize()) { m ->
                val points = track.map { GeoPoint(it.lat, it.lon) }
                if (m.overlays.size != lines.size + 1 || m.overlays.firstOrNull() !== lines.firstOrNull()) {
                    m.overlays.clear()
                    m.overlays += lines
                    m.overlays += dot
                }
                if (replayAt != null && points.isNotEmpty()) {
                    val i = replayAt.toInt().coerceIn(0, points.lastIndex)
                    val j = minOf(i + 1, points.lastIndex)
                    val f = (replayAt - i).toDouble()
                    // The head sits between fixes, so the line and the camera move smoothly.
                    val head = lerp(points[i], points[j], f)
                    runs.forEachIndexed { k, (range, _) ->
                        lines[k].setPoints(
                            when {
                                range.first > i -> emptyList()
                                range.last <= i -> points.subList(range.first, range.last + 1)
                                else -> points.subList(range.first, i + 1) + head
                            },
                        )
                    }
                    if (camera[1] == 0f) {
                        camera[1] = 1f
                        camera[0] = bearing(points, i, 0f)
                        // Frame about a third of the route (400 m - 3 km across), whatever the screen density.
                        val routeM = points.zipWithNext { a, b -> a.distanceToAsDouble(b) }.sum()
                        val wantAcross = (routeM / 3).coerceIn(400.0, 3_000.0)
                        val across = metresPerPixel(m) * m.width
                        if (across > 0) m.controller.setZoom((m.zoomLevelDouble + log2(across / wantAcross)).coerceAtMost(m.maxZoomLevel))
                    }
                    // Heading-up, eased so GPS wiggles don't spin the camera.
                    val target = bearing(points, i, camera[0])
                    val diff = ((target - camera[0] + 540f) % 360f) - 180f
                    camera[0] = (camera[0] + diff * 0.06f + 360f) % 360f
                    m.mapOrientation = -camera[0]
                    // ~20 px across whatever the zoom: metres per pixel at this latitude and zoom.
                    // ~11 dp across whatever the zoom or screen density.
                    val metresPerPx = metresPerPixel(m)
                    dot.points = Polygon.pointsAsCircle(head, metresPerPx * 5.5 * density)
                    // Look ahead: centre the camera in front of the runner, which sits them low on screen.
                    m.controller.setCenter(head.destinationPoint(metresPerPx * m.height / MAP_STRETCH * 0.18, camera[0].toDouble()))
                } else {
                    if (camera[1] == 1f) { // flyover over: back to the flat, north-up whole route
                        camera[1] = 0f
                        m.mapOrientation = 0f
                        m.tag = null
                    }
                    runs.forEachIndexed { k, (range, _) -> lines[k].setPoints(points.subList(range.first, range.last + 1)) }
                    dot.points = emptyList()
                    when {
                        points.isEmpty() -> Unit
                        follow -> m.controller.setCenter(points.last())
                        m.tag == null && points.size > 1 -> {
                            m.tag = "fitted"
                            fit(m, points)
                        }
                        m.tag == null -> m.controller.setCenter(points.first())
                    }
                }
                m.invalidate()
            }
        }
        // Haze towards the horizon, like distance fog, so the far map fades into the sky.
        if (tilt > 0f) Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = tilt / 55f }
                .background(Brush.verticalGradient(0f to sky(dark), 0.32f to sky(dark).copy(alpha = 0f))),
        )
        Attribution(showLabels)
    }
}

private const val MAP_STRETCH = 2.2f

private fun sky(dark: Boolean) = if (dark) Color(0xFF15171C) else Color(0xFFE6ECF2)

/** Metres one screen pixel covers right now (follows zoom, latitude and tiles scaled to screen density; not rotation). */
private fun metresPerPixel(m: MapView): Double =
    m.projection.metersToPixels(1f).let { if (it > 0) 1.0 / it else 0.0 }

private fun lerp(a: GeoPoint, b: GeoPoint, f: Double) =
    GeoPoint(a.latitude + (b.latitude - a.latitude) * f, a.longitude + (b.longitude - a.longitude) * f)

/** Direction of travel at fix [i], over the next ~30 m (one fix is too jittery); [fallback] when standing still. */
private fun bearing(points: List<GeoPoint>, i: Int, fallback: Float): Float {
    val from = points[i]
    val to = points.subList(i, points.size).firstOrNull { it.distanceToAsDouble(from) >= 30 } ?: points.last()
    if (to.distanceToAsDouble(from) < 3) return fallback
    return from.bearingTo(to).toFloat()
}

/**
 * Every route on one map, translucent, so the paths you take most glow
 * brightest — a personal heatmap. Drawn from the stored thumbnails, so no
 * point table is read.
 */
@Composable
fun HeatMap(routes: List<List<Pair<Double, Double>>>, accent: Color, dark: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val labels = remember { MovePrefs(context).mapLabels }
    val map = rememberOsmMap(dark, labels)
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
        Attribution(labels)
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
    if (m.width > 0 && m.height > 0) m.post { m.zoomToBoundingBox(box, false) }
    else m.addOnFirstLayoutListener { _, _, _, _, _ -> m.zoomToBoundingBox(box, false) }
}

@Composable
private fun rememberOsmMap(dark: Boolean, labels: Boolean): MapView {
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
            if (labels) {
                setTileSource(TileSourceFactory.MAPNIK)
                if (dark) overlayManager.tilesOverlay.setColorFilter(DARK_TILES)
            } else {
                // Esri's canvas stops at zoom 16; past that the map would stay blank.
                setTileSource(quietTiles(dark))
                maxZoomLevel = 16.0
            }
            setMultiTouchControls(true)
            // Tiles at screen density: map text stays readable instead of shrinking to pixel size.
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.5)
            onResume()
        }
    }
    DisposableEffect(map) { onDispose { map.onDetach() } }
    return map
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Attribution(labels: Boolean) {
    // Required by the OpenStreetMap (and Esri) terms.
    Text(
        if (labels) "© OpenStreetMap contributors" else "Esri · © OpenStreetMap contributors",
        fontSize = 9.sp, color = Color.White,
        modifier = Modifier.align(Alignment.BottomEnd).background(Color(0x99000000)).padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** Pace in one colour: faint where slower, strong where faster (6 steps). */
private val PACE_ALPHA = floatArrayOf(0.25f, 0.4f, 0.55f, 0.7f, 0.85f, 1f)

/** The route as fix ranges with a colour each: one range, or runs of equal pace. */
private fun routeRuns(track: List<Fix>, accent: Color, paceColours: Boolean): List<Pair<IntRange, Int>> {
    if (track.size < 2) return listOf(0..maxOf(0, track.lastIndex) to accent.toArgb())
    if (!paceColours || track.size < 3) return listOf(0..track.lastIndex to accent.toArgb())
    val buckets = PaceColours.buckets(track, PACE_ALPHA.size)
    val out = mutableListOf<Pair<IntRange, Int>>()
    var start = 0
    for (i in 1..buckets.size) {
        if (i == buckets.size || buckets[i] != buckets[start]) {
            out += (start..i) to accent.copy(alpha = PACE_ALPHA[buckets[start]]).toArgb()
            start = i
        }
    }
    return out
}

private fun routeLine(m: MapView, color: Int) = Polyline(m).apply {
    outlinePaint.color = color
    outlinePaint.strokeWidth = 12f
    outlinePaint.strokeCap = Paint.Cap.ROUND
}

/**
 * Without labels (the default): Esri's grey canvas, which drops place, shop
 * and landmark names and keeps only faint street names. With labels
 * (Settings › Show place names on maps): standard OpenStreetMap, dimmed for dark.
 * Both are keyless.
 */
private fun quietTiles(dark: Boolean): OnlineTileSourceBase {
    val style = if (dark) "World_Dark_Gray_Base" else "World_Light_Gray_Base"
    return object : OnlineTileSourceBase(
        "esri-$style", 0, 16, 256, "",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/$style/MapServer/tile/"),
        "Esri, HERE, Garmin, © OpenStreetMap contributors",
    ) {
        // Esri orders tiles z/y/x.
        override fun getTileURLString(index: Long) =
            baseUrl + MapTileIndex.getZoom(index) + "/" + MapTileIndex.getY(index) + "/" + MapTileIndex.getX(index)
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
