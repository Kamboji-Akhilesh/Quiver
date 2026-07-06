package com.kamboji.quiver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Quiver

/**
 * The bottom composer panel shared by the Calendar and Expenses new/edit
 * sheets: scrim, rounded panel, drag handle, title, scrollable [content] and a
 * pinned [footer].
 *
 * Keyboard behaviour is the whole reason this exists as ONE component. Two
 * things must both hold, and history shows they regress independently:
 *  1. The activity declares android:windowSoftInputMode="adjustResize" — with
 *     the default mode the window PANS under the keyboard and no inset ever
 *     reaches Compose, so the footer silently disappears under the IME.
 *  2. imePadding() lifts the panel, and [content] takes only the height LEFT
 *     OVER after the footer is measured (weight with fill = false) — so the
 *     footer can never be squeezed out when space gets tight.
 */
@Composable
fun QuiverComposerSheet(
    title: String,
    onDismiss: () -> Unit,
    maxFieldsHeight: Dp = 340.dp,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Quiver.colors
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x8C04040A))
                .clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .imePadding()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(if (colors.dark) Color(0xF012121C) else Color(0xF5FAFBFE))
                .border(1.dp, colors.border, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .clickable(remember { MutableInteractionSource() }, null) {}
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.padding(bottom = 14.dp).size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(4.dp)).background(colors.border2),
            )
            Text(
                title,
                fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = maxFieldsHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) { content() }
            Spacer(Modifier.height(14.dp))
            footer()
        }
    }
}
