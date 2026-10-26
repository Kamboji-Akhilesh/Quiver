package com.kamboji.quiver.ui.move

import com.kamboji.quiver.ui.locale.trPlural

import com.kamboji.quiver.ui.locale.displayName

import com.kamboji.quiver.ui.locale.tr
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.kamboji.quiver.move.steps.Steps
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kamboji.quiver.R
import com.kamboji.quiver.move.Fix
import com.kamboji.quiver.move.Gpx
import com.kamboji.quiver.move.MoveFormat
import com.kamboji.quiver.move.MovePrefs
import com.kamboji.quiver.move.MoveRecorder
import com.kamboji.quiver.move.MoveRecovery
import com.kamboji.quiver.move.MoveSummary
import com.kamboji.quiver.move.MoveType
import com.kamboji.quiver.move.TrackMath
import com.kamboji.quiver.move.TrackTuning
import com.kamboji.quiver.move.data.MoveDatabase
import com.kamboji.quiver.move.data.MoveEntity
import com.kamboji.quiver.move.data.PointEntity
import com.kamboji.quiver.ui.components.Pill
import com.kamboji.quiver.ui.components.QvTopBar
import com.kamboji.quiver.ui.components.SectionLabel
import com.kamboji.quiver.ui.components.glass
import com.kamboji.quiver.ui.shell.QuiverState
import com.kamboji.quiver.ui.shell.ToastKind
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Mono
import com.kamboji.quiver.ui.theme.Quiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters


/** Monday 00:00 this week, local time — the hub tile and "This week" both use it. */
fun startOfWeekMillis(): Long =
    LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
fun MoveScreen(state: QuiverState) {
    val context = LocalContext.current
    val live by MoveRecorder.live.collectAsState()
    val finished by MoveRecorder.finished.collectAsState()
    var detailId by remember { mutableStateOf<Long?>(null) }
    var showActivity by remember { mutableStateOf(false) }

    // Recording ended → open its summary, once.
    LaunchedEffect(finished) {
        finished?.let { detailId = it; MoveRecorder.finished.value = null }
    }
    // Close anything the OS killed mid-recording.
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { MoveRecovery.finishOrphans(context) } }

    val current = live
    when {
        current != null -> LiveScreen(state, current)
        detailId != null -> {
            BackHandler { detailId = null }
            DetailScreen(state, detailId!!) { detailId = null }
        }
        showActivity -> ActivityScreen(onBack = { showActivity = false }) { detailId = it }
        else -> HomeScreen(state, onActivity = { showActivity = true }) { detailId = it }
    }
}

// ---------------------------------------------------------------- home

@Composable
private fun HomeScreen(state: QuiverState, onActivity: () -> Unit, onOpen: (Long) -> Unit) {
    val ac = Accents.Move
    val colors = Quiver.colors
    val context = LocalContext.current
    val prefs = remember { MovePrefs(context) }
    val dao = remember { MoveDatabase.get(context).dao() }
    val moves by remember { dao.finished() }.collectAsState(emptyList())
    var type by remember { mutableStateOf(prefs.lastType) }
    var weightDialog by remember { mutableStateOf(false) }
    var weight by remember { mutableStateOf(prefs.weightKg) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) MoveRecorder.start(context, type)
        else state.toast(tr(R.string.move_precise_location_is_needed_to), ToastKind.Info)
    }
    fun start() {
        permissions.launch(
            buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (type.countsSteps) add(Manifest.permission.ACTIVITY_RECOGNITION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray(),
        )
    }

    val weekStart = startOfWeekMillis()
    val week = moves.filter { it.startedAt >= weekStart }

    Column(Modifier.fillMaxSize()) {
        QvTopBar(stringResource(R.string.title_move), ac, onBack = { state.go(AppKey.Hub) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 150.dp),
        ) {
            StepsCard(moves, onActivity)
            Spacer(Modifier.height(14.dp))

            // Start card
            Column(Modifier.fillMaxWidth().glass(colors).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoveType.entries.forEach { t -> Pill(t.displayName, t == type, ac, { type = t }) }
                }
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(100.dp))
                        .background(Brush.linearGradient(listOf(ac.a, ac.b)))
                        .clickable { start() }.padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(22.dp), tint = Color.White)
                        Text(tr(R.string.move_start_x, type.displayName.lowercase()), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    tr(R.string.move_recorded_on_this_phone_only),
                    fontSize = 11.5.sp, color = colors.faint, lineHeight = 15.sp,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel(tr(R.string.move_this_week))
            Row(Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(20.dp)).padding(16.dp)) {
                Stat(tr(R.string.unit_km, "%.1f".format(week.sumOf { it.distanceM } / 1000)), tr(R.string.move_stat_distance), Modifier.weight(1f))
                Stat("${week.size}", trPlural(R.plurals.move_stat_activities, week.size), Modifier.weight(1f))
                Stat(MoveFormat.duration(week.sumOf { it.movingMs }), tr(R.string.move_stat_moving), Modifier.weight(1f))
            }
            Text(
                tr(R.string.move_calories_use_x, "%.0f".format(weight)),
                fontSize = 12.sp, color = colors.dim,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp).clickable { weightDialog = true },
            )

            Spacer(Modifier.height(18.dp))
            SectionLabel(tr(R.string.move_activities))
            if (moves.isEmpty()) {
                Text(tr(R.string.move_no_activities_yet_pick_a), fontSize = 13.sp, color = colors.dim, modifier = Modifier.padding(top = 6.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                moves.forEach { m -> ActivityRow(m) { onOpen(m.id) } }
            }
        }
    }

    if (weightDialog) WeightDialog(weight, onDismiss = { weightDialog = false }) {
        prefs.weightKg = it
        weight = it
        weightDialog = false
    }
}

/**
 * Today's steps from the phone's step counter as a ring, the last 7 days'
 * goal rings, and the weekly active-minutes target — like Google Fit's home.
 */
@Composable
private fun StepsCard(moves: List<MoveEntity>, onOpen: () -> Unit) {
    val colors = Quiver.colors
    val ac = Accents.Move
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Steps.hasPermission(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = ok
        if (ok) Steps.start(context)
    }
    if (!Steps.hasChip(context)) return
    if (!allowed) {
        Row(
            Modifier.fillMaxWidth().glass(colors).clip(RoundedCornerShape(22.dp))
                .clickable { ask.launch(Manifest.permission.ACTIVITY_RECOGNITION) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr(R.string.steps_enable), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.text)
                Text(tr(R.string.steps_enable_why), fontSize = 12.sp, color = colors.dim, lineHeight = 16.sp)
            }
            Text(tr(R.string.focus_enable), color = ac.txt(colors.dark), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        return
    }
    val today = LocalDate.now()
    val dao = remember { MoveDatabase.get(context).steps() }
    val hours by remember { dao.between(today.minusDays(6).toString(), today.plusDays(1).toString()) }.collectAsState(emptyList())
    // Read the chip while the screen is open, so the count moves as you walk.
    LaunchedEffect(Unit) { while (true) { Steps.sample(context); delay(30_000) } }
    val byDay = hours.groupBy { it.day }.mapValues { (_, v) -> v.sumOf { it.steps } }
    val now = byDay[today.toString()] ?: 0
    val goal = Steps.goal(context)
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val achieved = week.count { (byDay[it.toString()] ?: 0) >= goal }
    val weekStart = startOfWeekMillis()
    val activeMin = moves.filter { it.startedAt >= weekStart }.sumOf { it.movingMs } / 60_000
    val target = Steps.weeklyMinutes(context)

    Column(Modifier.fillMaxWidth().glass(colors).clickable(onClick = onOpen).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Ring(now.toFloat() / goal, ac.a, colors.border, 10.dp, Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%,d".format(now), fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text)
                    Text(tr(R.string.move_stat_steps), fontSize = 11.sp, color = colors.dim)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr(R.string.steps_goal_x, "%,d".format(goal)), fontSize = 12.5.sp, color = colors.dim)
                Text(tr(R.string.steps_daily_goals, achieved), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    week.forEach { d ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Ring((byDay[d.toString()] ?: 0).toFloat() / goal, ac.a, colors.border, 3.dp, Modifier.size(20.dp))
                            Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()), fontSize = 9.5.sp, color = if (d == today) colors.text else colors.faint)
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(tr(R.string.steps_weekly_target), Modifier.weight(1f), fontSize = 12.5.sp, color = colors.dim)
                Text(tr(R.string.steps_weekly_x_of_y, activeMin, target), fontSize = 12.5.sp, fontFamily = Mono, color = colors.text)
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.border)) {
                Box(Modifier.fillMaxWidth((activeMin.toFloat() / target).coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(ac.a))
            }
        }
    }
}

/** A progress ring; a second lap past the goal draws over the first. */
@Composable
private fun Ring(progress: Float, color: Color, track: Color, width: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val inset = width.toPx() / 2
        val box = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset)
        val at = Offset(inset, inset)
        drawArc(track, 0f, 360f, false, at, box, style = stroke)
        if (progress > 0) drawArc(color, -90f, 360f * progress.coerceAtMost(1f), false, at, box, style = stroke)
    }
}

@Composable
private fun ActivityRow(m: MoveEntity, onClick: () -> Unit) {
    val colors = Quiver.colors
    val ac = Accents.Move
    Row(
        // Auto-detected activities have no route to open.
        Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(20.dp)).then(if (m.auto) Modifier else Modifier.clickable(onClick = onClick)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        RouteThumb(
            MoveSummary.parsePreview(m.preview), ac.a,
            Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).background(ac.a.copy(alpha = 0.10f)),
        )
        Column(Modifier.weight(1f)) {
            Text(MoveSummary.title(m.moveType, m.startedAt), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.text)
            Text(formatDate(m.startedAt), fontSize = 11.5.sp, color = colors.dim)
            Text(
                if (m.auto) tr(R.string.steps_auto_row, MoveFormat.duration(m.movingMs), "%,d".format(m.steps))
                else "${MoveFormat.km(m.distanceM)} · ${MoveFormat.duration(m.movingMs)} · ${MoveFormat.rate(m.moveType, m.distanceM, m.movingMs)}",
                fontSize = 12.sp, fontFamily = Mono, color = colors.text, modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- live

@Composable
private fun LiveScreen(state: QuiverState, live: MoveRecorder.Live) {
    val ac = Accents.Move
    val colors = Quiver.colors
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    Column(Modifier.fillMaxSize()) {
        QvTopBar(tr(R.string.move_recording_x, live.type.displayName.lowercase()), ac, onBack = { state.go(AppKey.Hub) }, trailing = { GpsBadge(live.accuracyM) })
        MoveMap(
            live.track, follow = true, accent = ac.a, dark = colors.dark,
            modifier = Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 18.dp).clip(RoundedCornerShape(24.dp)),
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("%.2f".format(live.distanceM / 1000), fontSize = 64.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text)
            Text(tr(R.string.move_kilometres), fontSize = 13.sp, color = colors.dim)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat(MoveFormat.duration(live.movingMs), tr(R.string.move_moving_time), Modifier.weight(1f))
                Stat(
                    if (live.type == MoveType.Ride) MoveFormat.speed((live.speedMps ?: 0.0) * 3.6)
                    else MoveFormat.pace(live.speedMps?.takeIf { it >= live.type.pauseSpeedMps }?.let { (1000 / it).toLong() }),
                    if (live.type == MoveType.Ride) tr(R.string.move_stat_speed) else tr(R.string.move_current_pace),
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat(MoveFormat.rate(live.type, live.distanceM, live.movingMs), if (live.type == MoveType.Ride) tr(R.string.move_avg_speed) else tr(R.string.move_avg_pace), Modifier.weight(1f))
                Stat(tr(R.string.unit_m, "%.0f".format(live.elevationGainM)), tr(R.string.move_stat_climb), Modifier.weight(1f))
                if (live.type.countsSteps) Stat("${live.steps}", tr(R.string.move_stat_steps), Modifier.weight(1f))
                else Stat(MoveFormat.duration(now - live.startedAt), tr(R.string.move_stat_elapsed), Modifier.weight(1f))
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 36.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(
                if (live.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                if (live.paused) tr(R.string.move_resume) else tr(R.string.move_pause), ac.a,
            ) { if (live.paused) MoveRecorder.resume(context) else MoveRecorder.pause(context) }
            RoundButton(Icons.Filled.Stop, tr(R.string.move_finish), Color(0xFFFB7185)) { MoveRecorder.finish(context) }
        }
        Text(
            tr(R.string.move_discard), fontSize = 12.sp, color = colors.faint,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp).clickable { confirmDiscard = true },
        )
    }

    if (confirmDiscard) ConfirmDialog(
        tr(R.string.move_discard_this_x, live.type.displayName.lowercase()), tr(R.string.move_it_won_t_be_saved), tr(R.string.move_discard),
        onDismiss = { confirmDiscard = false },
    ) { confirmDiscard = false; MoveRecorder.discard(context) }
}

@Composable
private fun GpsBadge(accuracyM: Float?) {
    val colors = Quiver.colors
    val (label, color) = when {
        accuracyM == null -> tr(R.string.move_finding_gps) to colors.faint
        accuracyM <= 10f -> tr(R.string.move_gps_x_m, accuracyM.toInt()) to Color(0xFF34D399)
        accuracyM <= TrackTuning.MAX_ACCURACY_M -> tr(R.string.move_gps_x_m, accuracyM.toInt()) to Color(0xFFFBBF24)
        else -> tr(R.string.move_weak_gps) to Color(0xFFFB7185)
    }
    Row(
        Modifier.clip(RoundedCornerShape(100.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

// ---------------------------------------------------------------- detail

@Composable
private fun DetailScreen(state: QuiverState, id: Long, onBack: () -> Unit) {
    val ac = Accents.Move
    val colors = Quiver.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { MoveDatabase.get(context).dao() }
    var confirmDelete by remember { mutableStateOf(false) }

    val loaded by produceState<Pair<MoveEntity, List<PointEntity>>?>(null, id) {
        value = withContext(Dispatchers.IO) { dao.byId(id)?.let { it to dao.points(id) } }
    }
    val (move, points) = loaded ?: run {
        Column(Modifier.fillMaxSize()) { QvTopBar(stringResource(R.string.title_move), ac, onBack = onBack) }
        return
    }
    val type = move.moveType
    val title = MoveSummary.title(type, move.startedAt)
    val track = remember(points) { MoveSummary.replay(type, points).track }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            // Raw fixes (only the hopeless ones dropped): Strava then smooths them itself, like its own recordings.
            val raw = points.map { it.toFix() }.filter { it.accM <= TrackTuning.MAX_ACCURACY_M }
            runCatching {
                context.contentResolver.openOutputStream(uri)!!.use { it.write(Gpx.write(title, type, raw).toByteArray()) }
            }.onSuccess { withContext(Dispatchers.Main) { state.toast(tr(R.string.move_gpx_saved)) } }
                .onFailure { withContext(Dispatchers.Main) { state.toast(tr(R.string.move_export_failed), ToastKind.Error) } }
        }
    }

    Column(Modifier.fillMaxSize()) {
        QvTopBar(title, ac, onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 150.dp)) {
            Text(formatDate(move.startedAt), fontSize = 12.5.sp, color = colors.dim, modifier = Modifier.padding(start = 2.dp, bottom = 10.dp))
            if (track.size > 1) {
                MoveMap(
                    track, follow = false, accent = ac.a, dark = colors.dark,
                    modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(24.dp)),
                )
                Spacer(Modifier.height(14.dp))
            }
            Column(Modifier.fillMaxWidth().glass(colors).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Stat(MoveFormat.km(move.distanceM), tr(R.string.move_stat_distance), Modifier.weight(1f))
                    Stat(MoveFormat.duration(move.movingMs), tr(R.string.move_moving_time), Modifier.weight(1f))
                    Stat(MoveFormat.rate(type, move.distanceM, move.movingMs), if (type == MoveType.Ride) tr(R.string.move_avg_speed) else tr(R.string.move_avg_pace), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    Stat(tr(R.string.unit_m, "%.0f".format(move.elevationGainM)), tr(R.string.move_stat_climb), Modifier.weight(1f))
                    Stat(MoveFormat.duration(move.elapsedMs), tr(R.string.move_stat_elapsed), Modifier.weight(1f))
                    Stat(if (move.steps > 0) "${move.steps}" else "${move.calories}", if (move.steps > 0) tr(R.string.move_stat_steps) else tr(R.string.move_stat_kcal), Modifier.weight(1f))
                }
                if (move.steps > 0) Text(tr(R.string.move_x_kcal, move.calories), fontSize = 12.sp, color = colors.dim)
            }

            if (move.splits.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionLabel(tr(R.string.move_splits))
                Splits(move.splits, type)
            }

            val profile = remember(track) { elevationProfile(track) }
            if (profile.size > 2) {
                Spacer(Modifier.height(18.dp))
                SectionLabel(tr(R.string.move_elevation))
                ElevationChart(profile, ac.a, Modifier.fillMaxWidth().height(110.dp).glass(colors, RoundedCornerShape(20.dp)).padding(12.dp))
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(tr(R.string.move_export_gpx), false, ac, { exporter.launch("${title.replace(' ', '-')}-${LocalDate.now()}.gpx") })
                Pill(tr(R.string.move_delete), false, Accents.Expenses, { confirmDelete = true })
            }
            Text(
                tr(R.string.move_gpx_opens_in_strava_google),
                fontSize = 11.5.sp, color = colors.faint, lineHeight = 15.sp, modifier = Modifier.padding(top = 10.dp),
            )
        }
    }

    if (confirmDelete) ConfirmDialog(tr(R.string.move_delete_this_activity), tr(R.string.move_its_route_and_stats_are), tr(R.string.move_delete), onDismiss = { confirmDelete = false }) {
        confirmDelete = false
        scope.launch {
            withContext(Dispatchers.IO) { dao.delete(id) }
            state.toast(tr(R.string.move_activity_deleted), ToastKind.Error)
            onBack()
        }
    }
}

@Composable
private fun Splits(splits: List<Long>, type: MoveType) {
    val colors = Quiver.colors
    val ac = Accents.Move
    val fastest = splits.min()
    Column(Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        splits.forEachIndexed { i, ms ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${i + 1}", fontSize = 12.sp, fontFamily = Mono, color = colors.dim, modifier = Modifier.width(22.dp))
                Box(Modifier.weight(1f).height(10.dp)) {
                    // Faster split → longer bar, like Strava.
                    Box(
                        Modifier.fillMaxWidth((fastest.toFloat() / ms).coerceIn(0.15f, 1f)).height(10.dp)
                            .clip(RoundedCornerShape(5.dp)).background(if (ms == fastest) ac.a else ac.a.copy(alpha = 0.45f)),
                    )
                }
                Text(
                    if (type == MoveType.Ride) MoveFormat.speed(3600.0 / (ms / 1000.0)) else MoveFormat.pace(ms / 1000),
                    fontSize = 12.sp, fontFamily = Mono, color = colors.text,
                )
            }
        }
    }
}

/** (cumulative distance m, altitude m) along the cleaned track. */
private fun elevationProfile(track: List<Fix>): List<Pair<Double, Double>> {
    var d = 0.0
    return track.zipWithNext().mapNotNull { (a, b) ->
        d += TrackMath.distanceM(a, b)
        b.altM?.let { d to it }
    }
}

@Composable
private fun ElevationChart(profile: List<Pair<Double, Double>>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val maxD = profile.last().first.coerceAtLeast(1.0)
        val minA = profile.minOf { it.second }
        val span = (profile.maxOf { it.second } - minA).coerceAtLeast(10.0)
        fun pt(p: Pair<Double, Double>) = Offset((p.first / maxD * size.width).toFloat(), (size.height - (p.second - minA) / span * size.height).toFloat())
        val line = Path().apply { profile.forEachIndexed { i, p -> pt(p).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } } }
        val fill = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------- shared bits

@Composable
internal fun RouteThumb(points: List<Pair<Double, Double>>, color: Color, modifier: Modifier) {
    Canvas(modifier.padding(8.dp)) {
        if (points.size < 2) return@Canvas
        val minLat = points.minOf { it.first }
        val maxLat = points.maxOf { it.first }
        val minLon = points.minOf { it.second }
        val maxLon = points.maxOf { it.second }
        // Keep the route's real shape: one scale for both axes (lon shrinks with latitude).
        val lonScale = kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2))
        val spanX = ((maxLon - minLon) * lonScale).coerceAtLeast(1e-9)
        val spanY = (maxLat - minLat).coerceAtLeast(1e-9)
        val scale = minOf(size.width / spanX, size.height / spanY)
        val ox = (size.width - spanX * scale) / 2
        val oy = (size.height - spanY * scale) / 2
        val path = Path()
        points.forEachIndexed { i, (lat, lon) ->
            val x = (ox + (lon - minLon) * lonScale * scale).toFloat()
            val y = (oy + (maxLat - lat) * scale).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = Quiver.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text, maxLines = 1)
        Text(label, fontSize = 11.5.sp, color = colors.dim, maxLines = 1)
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    val colors = Quiver.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(color).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, Modifier.size(34.dp), tint = Color.White) }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = Quiver.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(if (colors.dark) Color(0xFF14141F) else Color(0xFFFAFBFE))
                .border(1.dp, colors.border, RoundedCornerShape(28.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
            Text(body, fontSize = 13.sp, color = colors.dim)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(tr(R.string.move_cancel), false, Accents.Move, onDismiss, Modifier.weight(1f))
                Pill(action, true, Accents.Expenses, onConfirm, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WeightDialog(current: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    val colors = Quiver.colors
    var text by remember { mutableStateOf("%.0f".format(current)) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(if (colors.dark) Color(0xFF14141F) else Color(0xFFFAFBFE))
                .border(1.dp, colors.border, RoundedCornerShape(28.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tr(R.string.move_your_weight), fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
            Text(tr(R.string.move_only_used_to_estimate_calories), fontSize = 13.sp, color = colors.dim)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(100.dp)).background(colors.surf)
                    .border(1.dp, colors.border, RoundedCornerShape(100.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    text, { new -> text = new.filter { it.isDigit() || it == '.' }.take(5) }, singleLine = true,
                    textStyle = TextStyle(color = colors.text, fontSize = 15.sp), cursorBrush = SolidColor(Accents.Move.a),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                Text(tr(R.string.unit_kg), fontSize = 15.sp, color = colors.dim, modifier = Modifier.align(Alignment.CenterEnd))
            }
            Pill(tr(R.string.move_save), true, Accents.Move, {
                text.toDoubleOrNull()?.takeIf { it in 25.0..300.0 }?.let(onSave)
            }, Modifier.fillMaxWidth())
        }
    }
}

// Built per call so the pattern follows the phone's current locale.
private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", java.util.Locale.getDefault()))
