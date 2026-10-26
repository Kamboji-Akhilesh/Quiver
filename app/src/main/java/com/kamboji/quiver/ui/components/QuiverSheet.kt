package com.kamboji.quiver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kamboji.quiver.ui.theme.LocalQuiverColors
import com.kamboji.quiver.ui.theme.Quiver
import kotlinx.coroutines.launch

/**
 * Animated, swipe-dismissible bottom sheet styled for Quiver. Rendered in its
 * own window so it always sits above the floating dock.
 *
 * The [content] lambda receives a `hide()` callback: call it after an action
 * (pick, save, delete) to play the slide-out animation before [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuiverModalSheet(
    onDismiss: () -> Unit,
    content: @Composable (hide: () -> Unit) -> Unit,
) {
    val colors = Quiver.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hide: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (colors.flat) colors.surf else if (colors.dark) Color(0xF012121C) else Color(0xF5FAFBFE),
        contentColor = colors.text,
        scrimColor = Color(0x8C04040A),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                Box(Modifier.size(width = 40.dp, height = 5.dp).clip(RoundedCornerShape(4.dp)).background(colors.border2))
            }
        },
    ) {
        // Flat themes: the sheet is the raised surface, so fields and cards inside it sit recessed on the ground colour.
        CompositionLocalProvider(LocalQuiverColors provides if (colors.flat) colors.copy(surf = colors.bg, surf2 = colors.bg) else colors) {
            content(hide)
        }
    }
}
