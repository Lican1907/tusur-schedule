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
    selectedSubgroup: String = "ALL",
    isNarrow: Boolean = false
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
                    fontSize = if (isNarrow) 11.sp else ScheduleWidgetTheme.FontHeaderStatus,
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 1
            )
            Text(
                text = dateText,
                style = TextStyle(
                    color = ColorProvider(ScheduleWidgetTheme.TextPrimary),
                    fontSize = if (isNarrow) 15.sp else ScheduleWidgetTheme.FontHeaderDate,
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
                .padding(horizontal = if (isNarrow) 8.dp else 10.dp, vertical = if (isNarrow) 4.dp else 6.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = chipText,
                style = TextStyle(
                    color = ColorProvider(ScheduleWidgetTheme.TextSecondary),
                    fontSize = if (isNarrow) 11.sp else 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }

        Spacer(modifier = GlanceModifier.width(if (isNarrow) 6.dp else 8.dp))

        // Refresh circular button
        val btnSize = if (isNarrow) 30.dp else ScheduleWidgetTheme.ButtonRefreshSize
        Box(
            modifier = GlanceModifier
                .width(btnSize)
                .height(btnSize)
                .background(ImageProvider(R.drawable.bg_btn_refresh_34))
                .cornerRadius(btnSize / 2)
                .clickable(actionRunCallback<RefreshAction>()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "↻",
                style = TextStyle(
                    color = ColorProvider(ScheduleWidgetTheme.TextPrimary),
                    fontSize = if (isNarrow) 14.sp else 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
