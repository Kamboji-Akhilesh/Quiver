package com.kamboji.quiver.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kamboji.quiver.expenses.data.ExpenseMath
import com.kamboji.quiver.expenses.data.ExpenseStore
import com.kamboji.quiver.expenses.data.MonthSummary
import com.kamboji.quiver.ui.theme.AppKey
import java.time.YearMonth
import java.util.Locale

private val WidgetRose = ColorProvider(Color(0xFFFB7185))

/** This month's spend at a glance: total + the top categories. */
class SpendWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val month = YearMonth.now()
        val summary = ExpenseMath.summarize(ExpenseMath.inMonth(ExpenseStore(context).getAll(), month))
        provideContent { Spend(context, month, summary) }
    }

    @Composable
    private fun Spend(context: Context, month: YearMonth, summary: MonthSummary) {
        Column(
            GlanceModifier.fillMaxSize().background(WidgetBg).cornerRadius(24.dp)
                .padding(14.dp)
                .clickable(actionStartActivity(openShellIntent(context, AppKey.Expenses))),
        ) {
            Text(
                month.month.name.uppercase(Locale.ENGLISH).take(3) + " SPEND",
                style = TextStyle(color = WidgetRose, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                ExpenseMath.formatPaise(summary.totalPaise),
                style = TextStyle(color = WidgetText, fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(8.dp))
            if (summary.byCategory.isEmpty()) {
                Text(
                    "No expenses yet this month.",
                    style = TextStyle(color = WidgetDim, fontSize = 12.sp),
                )
            } else {
                summary.byCategory.take(3).forEach { (cat, paise) ->
                    Row(
                        GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${cat.emoji} ${cat.label}",
                            style = TextStyle(color = WidgetDim, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Text(
                            ExpenseMath.formatPaise(paise),
                            style = TextStyle(color = WidgetText, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }
}

class SpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendWidget()
}
