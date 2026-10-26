package com.kamboji.quiver.ui.move

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.R
import com.kamboji.quiver.move.BestEfforts
import com.kamboji.quiver.move.Fix
import com.kamboji.quiver.move.MoveFormat
import com.kamboji.quiver.move.TrackMath
import com.kamboji.quiver.ui.locale.tr
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.Mono
import kotlinx.coroutines.delay

/**
 * A route replay: where the runner is (as a fraction of the distance, advanced
 * every frame so the head glides between fixes), whether it's playing, and the
 * PR banner to show as a record is passed.
 */
class ReplayState(val track: List<Fix>, val prs: List<BestEfforts.Pr>) {
    var progress by mutableStateOf<Float?>(null)
    var playing by mutableStateOf(false)
    var banner by mutableStateOf<String?>(null)

    /** Set when the runner reaches the end; the replay holds there a moment, then resets. */
    var ended by mutableStateOf(false)

    private val dist = DoubleArray(track.size).also { d ->
        for (i in 1 until track.size) d[i] = d[i - 1] + TrackMath.distanceM(track[i - 1], track[i])
    }
    private val total = dist.last()

    /** 8 s for a short walk, +2 s a km, at most 20 s. */
    val durationNs = ((8_000 + total / 1000 * 2_000).coerceAtMost(20_000.0) * 1_000_000).toLong()

    /** Fractional fix index for a fraction of the distance. */
    fun indexAt(f: Float): Float {
        val target = f * total
        val hi = dist.indexOfFirst { it >= target }.let { if (it < 0) dist.lastIndex else it }
        if (hi == 0) return 0f
        val lo = hi - 1
        val span = dist[hi] - dist[lo]
        return lo + if (span > 0) ((target - dist[lo]) / span).toFloat() else 1f
    }

    /** Where the map should draw up to; null when not replaying. */
    val at: Float? get() = progress?.let { indexAt(it) }

    fun toggle() {
        if (!playing && progress == null) progress = 0f
        playing = !playing
    }

    fun restart() {
        progress = 0f
        ended = false
        banner = null
        playing = true
    }

    /** "1.20 km · 6:40" at the head, interpolated like the map. */
    fun label(f: Float): String {
        val i = f.toInt().coerceAtMost(track.lastIndex)
        val j = minOf(i + 1, track.lastIndex)
        val frac = f - i
        val t = track[i].t + ((track[j].t - track[i].t) * frac).toLong()
        return "${MoveFormat.km(dist[i] + (dist[j] - dist[i]) * frac)} · ${MoveFormat.duration(t - track.first().t)}"
    }
}

/** A replay of [track] that runs itself while [ReplayState.playing]. */
@Composable
fun rememberReplay(track: List<Fix>, prs: List<BestEfforts.Pr>): ReplayState {
    val state = remember(track) { ReplayState(track, prs) }
    LaunchedEffect(state, state.playing) {
        if (!state.playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        var lastIdx = state.indexAt(state.progress ?: 0f).toInt()
        while (true) {
            val now = withFrameNanos { it }
            val p = ((state.progress ?: 0f) + (now - last).toFloat() / state.durationNs).coerceAtMost(1f)
            last = now
            state.progress = p
            val idx = state.indexAt(p).toInt()
            if (idx > lastIdx) {
                state.prs.firstOrNull { it.effort.endAt in (state.track[lastIdx].t + 1)..state.track[idx].t }?.let { pr ->
                    state.banner = if (pr.previousMs != null) tr(R.string.move_replay_pr_x_y_z, BestEfforts.label(pr.effort.distanceM), MoveFormat.duration(pr.effort.ms), MoveFormat.duration(pr.previousMs))
                    else tr(R.string.move_replay_first_x_y, BestEfforts.label(pr.effort.distanceM), MoveFormat.duration(pr.effort.ms))
                }
                lastIdx = idx
            }
            if (p >= 1f) break
        }
        state.ended = true
        delay(2_500)
        state.playing = false
        state.progress = null
        state.banner = null
        state.ended = false
    }
    LaunchedEffect(state.banner) { if (state.banner != null) { delay(3_000); state.banner = null } }
    return state
}

/** The distance · time chip and the PR banner, drawn over the map while replaying. */
@Composable
fun BoxScope.ReplayOverlay(state: ReplayState, top: androidx.compose.ui.unit.Dp = 10.dp) {
    val ac = Accents.Move
    state.at?.let { f ->
        Text(
            state.label(f),
            fontSize = 13.sp, fontFamily = Mono, color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = top).clip(RoundedCornerShape(10.dp))
                .background(Color(0xD90F1012)).padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
    state.banner?.let {
        Text(
            it, fontSize = 13.sp, color = ac.on, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = top + 34.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(ac.a, ac.b))).padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}
