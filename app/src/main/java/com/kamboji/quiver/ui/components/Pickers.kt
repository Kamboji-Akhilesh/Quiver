package com.kamboji.quiver.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kamboji.quiver.ui.theme.Accent
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Quiver

/** Material3 clock-dial time picker in a Quiver-styled dialog (24-hour). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QvTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    accent: Accent,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Quiver.colors
    val tpState = rememberTimePickerState(initialHour, initialMinute, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.bg2) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Select time", color = colors.dim, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
                TimePicker(
                    state = tpState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = colors.surf2,
                        selectorColor = accent.a,
                        periodSelectorSelectedContainerColor = accent.a,
                        timeSelectorSelectedContainerColor = accent.a.copy(alpha = 0.2f),
                        timeSelectorSelectedContentColor = accent.txt(colors.dark),
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss) { Text("Cancel", color = colors.dim) }
                    TextButton({ onConfirm(tpState.hour, tpState.minute); onDismiss() }) {
                        Text("OK", color = accent.txt(colors.dark), fontFamily = Display)
                    }
                }
            }
        }
    }
}

/** Material3 calendar date picker in a Quiver-styled dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QvDatePickerDialog(
    initialMillisUtc: Long,
    accent: Accent,
    onConfirm: (utcMillis: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Quiver.colors
    val dpState = rememberDatePickerState(initialSelectedDateMillis = initialMillisUtc)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = colors.bg2),
        confirmButton = {
            TextButton({ dpState.selectedDateMillis?.let(onConfirm); onDismiss() }) {
                Text("OK", color = accent.txt(colors.dark), fontFamily = Display)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = colors.dim) } },
    ) {
        DatePicker(
            state = dpState,
            colors = DatePickerDefaults.colors(
                containerColor = colors.bg2,
                selectedDayContainerColor = accent.a,
                todayDateBorderColor = accent.a,
                selectedDayContentColor = colors.bg,
            ),
        )
    }
}
