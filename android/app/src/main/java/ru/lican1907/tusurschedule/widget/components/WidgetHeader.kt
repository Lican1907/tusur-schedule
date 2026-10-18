package ru.lican1907.tusurschedule.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ru.lican1907.tusurschedule.R
import ru.lican1907.tusurschedule.data.normalizeSubgroup
import ru.lican1907.tusurschedule.ui.MainActivity
import ru.lican1907.tusurschedule.widget.RefreshAction
import ru.lican1907.tusurschedule.widget.ScheduleWidgetTheme

@Composable
fun WidgetHeader(
    statusText: String,
    dateText: String,
    groupName: String,
    selectedSubgroup: String = "ALL"
) {
    val chipText = if (selectedSubgroup.equals("ALL", ignoreCase = true) || selectedSubgroup.isBlank()) {
        groupName
    } else {
        val norm = normalizeSubgroup(selectedSubgroup).ifEmpty { selectedSubgroup }
        "$groupName · п/г $norm"
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = statusText,
                style = TextStyle(
                    color = ColorProvider(ScheduleWidgetTheme.TextMuted),
                    fontSize = ScheduleWidgetTheme.FontHeaderStatus,
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 1
            )
            Text(
                text = dateText,
                style = TextStyle(
                    color = ColorProvider(ScheduleWidgetTheme.TextPrimary),
                    fontSize = ScheduleWidgetTheme.FontHeaderDate,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }

        // Group chip
        Box(
            modifier = GlanceModifier
                .background(ImageProvider(R.drawable.bg_chip_group))
                .cornerRadius(ScheduleWidgetTheme.RadiusChip)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = chipText,
                style = TextStyle(
                    color = ColorProvider(ScheduleWidgetTheme.TextSecondary),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        // Refresh 34dp circular button
        Box(
            modifier = GlanceModifier
                .width(ScheduleWidgetTheme.ButtonRefreshSize)
                .height(ScheduleWidgetTheme.ButtonRefreshSize)
                .background(ImageProvider(R.drawable.bg_btn_refresh_34))
                .cornerRadius(17.dp)
                .clickable(actionRunCallback<RefreshAction>()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "↻",
                style = TextStyle(
                    color = ColorProvider(ScheduleWidgetTheme.TextPrimary),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
