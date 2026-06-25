package com.kamboji.quiver.currency

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.currency.data.RatePoint
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val moneyFmt = DecimalFormat("#,##0.##")
private val rateFmt = DecimalFormat("#,##0.####")
fun fmtMoney(d: Double): String = moneyFmt.format(d)
fun fmtRate(d: Double): String = rateFmt.format(d)

@Composable
fun OfflineBanner(fetchedAtMillis: Long?) {
    val scheme = MaterialTheme.colorScheme
    val updated = fetchedAtMillis?.let {
        " · updated " + SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(it))
    } ?: ""
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.tertiary.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CloudOff, null, Modifier.size(16.dp), tint = scheme.onSurface)
        Spacer(Modifier.size(6.dp))
        Text(
            "Offline – showing saved data$updated",
            fontSize = 12.sp,
            color = scheme.onSurface,
        )
    }
}

@Composable
fun CurrencyDropdown(
    value: String,
    symbols: List<String>,
    onPick: (String) -> Unit,
    fontSize: TextUnit = 24.sp,
) {
    var open by remember { mutableStateOf(false) }
    val options = remember(symbols, value) { (symbols + value).distinct().sorted() }
    Box {
        Row(
            Modifier.clickable { open = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, fontSize = fontSize, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Filled.ArrowDropDown, "Change currency")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { s ->
                DropdownMenuItem(text = { Text(s) }, onClick = { onPick(s); open = false })
            }
        }
    }
}

@Composable
fun RangeRadio(label: String, selected: Boolean, onTap: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.clickable(onClick = onTap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(12.dp)
                .size(if (selected) 24.dp else 18.dp)
                .border(
                    width = if (selected) 6.dp else 2.dp,
                    color = if (selected) scheme.secondary else Color.Gray,
                    shape = CircleShape,
                )
        )
        Text(
            label,
            fontSize = 16.sp,
            color = if (selected) scheme.onSurface else Color.Gray,
        )
    }
}

@Composable
fun ErrorRetry(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.ErrorOutline, null, Modifier.size(40.dp))
        Spacer(Modifier.size(12.dp))
        Text(message)
        Spacer(Modifier.size(16.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, null)
            Spacer(Modifier.size(8.dp))
            Text("Retry")
        }
    }
}

/** Gradient historical line chart with horizontal gridlines + axis labels. */
@Composable
fun RateLineChart(points: List<RatePoint>, quote: String) {
    val scheme = MaterialTheme.colorScheme
    val lineBrush = remember(scheme) {
        Brush.verticalGradient(listOf(scheme.primary, scheme.secondary, scheme.error))
    }
    val gridColor = scheme.onSurface.copy(alpha = 0.18f)
    val labelArgb = scheme.onSurface.toArgb()
    val density = LocalDensity.current
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    Column {
        Text(
            "Currency in $quote",
            fontSize = 12.sp,
            color = scheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .padding(start = 8.dp, end = 16.dp, bottom = 4.dp),
        ) {
            val minR = points.minOf { it.rate }
            val maxR = points.maxOf { it.rate }
            val span = (maxR - minR).takeIf { it > 0 } ?: 1.0
            val leftPad = with(density) { 44.dp.toPx() }
            val bottomPad = with(density) { 22.dp.toPx() }
            val topPad = with(density) { 6.dp.toPx() }
            val plotW = size.width - leftPad
            val plotH = size.height - topPad - bottomPad
            val textPx = with(density) { 10.sp.toPx() }
            fun xAt(i: Int) = leftPad + plotW * i / (points.size - 1)
            fun yAt(r: Double) = topPad + plotH * (1f - ((r - minR) / span).toFloat())

            // Horizontal gridlines + left value labels.
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true; textSize = textPx; color = labelArgb
                }
                for (g in 0..3) {
                    val frac = g / 3f
                    val y = topPad + plotH * frac
                    drawLine(gridColor, androidx.compose.ui.geometry.Offset(leftPad, y),
                        androidx.compose.ui.geometry.Offset(size.width, y), 1f)
                    canvas.nativeCanvas.drawText(fmtRate(maxR - span * frac), 0f, y + textPx / 3, paint)
                }
                // Bottom date labels: first, middle, last.
                listOf(0, points.size / 2, points.size - 1).distinct().forEach { i ->
                    val label = dateFmt.format(Date(points[i].dateMillis))
                    val tx = (xAt(i) - paint.measureText(label) / 2)
                        .coerceIn(leftPad, size.width - paint.measureText(label))
                    canvas.nativeCanvas.drawText(label, tx, size.height, paint)
                }
            }

            // The rate line.
            val path = Path()
            points.forEachIndexed { i, p ->
                val px = xAt(i)
                val py = yAt(p.rate)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, brush = lineBrush, style = Stroke(width = with(density) { 4.dp.toPx() }))
        }
    }
}
