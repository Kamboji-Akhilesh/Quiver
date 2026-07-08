package com.kamboji.quiver.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kamboji.quiver.R
import com.kamboji.quiver.ui.locale.AppLocale
import com.kamboji.quiver.ui.shell.QuiverActivity
import com.kamboji.quiver.ui.shell.ShellCommand

/**
 * One-tap capture from the home screen: new note / new task / add expense /
 * speak to the AI. Reuses the app-shortcut actions (and their icons), so the
 * shell routes these exactly like a long-press shortcut.
 */
class QuickActionsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Widgets run outside an Activity, so they don't inherit the locale-wrapped
        // base context — resolve labels against the user's chosen app language.
        val localized = AppLocale.localized(context)
        provideContent { Actions(context, localized) }
    }

    @Composable
    private fun Actions(context: Context, localized: Context) {
        fun launch(action: String, mic: Boolean = false): Intent =
            Intent(context, QuiverActivity::class.java)
                .setAction(action)
                .apply { if (mic) putExtra(ShellCommand.EXTRA_AI_MIC, true) }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        Row(
            GlanceModifier.fillMaxSize().background(WidgetBg).cornerRadius(24.dp)
                .padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Action(R.drawable.ic_shortcut_note, localized.getString(R.string.widget_note), launch(ShellCommand.ACTION_NEW_NOTE))
            Action(R.drawable.ic_shortcut_task, localized.getString(R.string.widget_task), launch(ShellCommand.ACTION_NEW_TASK))
            Action(R.drawable.ic_shortcut_expense, localized.getString(R.string.widget_expense), launch(ShellCommand.ACTION_ADD_EXPENSE))
            Action(R.drawable.ic_shortcut_mic, localized.getString(R.string.widget_speak), launch(ShellCommand.ACTION_ASK_AI, mic = true))
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.Action(icon: Int, label: String, intent: Intent) {
        Column(
            GlanceModifier.defaultWeight().clickable(actionStartActivity(intent)).padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(ImageProvider(icon), contentDescription = label, modifier = GlanceModifier.size(40.dp))
            Spacer(GlanceModifier.height(4.dp))
            Text(
                label,
                style = TextStyle(color = WidgetDim, fontSize = 11.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}
