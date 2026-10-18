package ru.lican1907.tusurschedule.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ru.lican1907.tusurschedule.R
import ru.lican1907.tusurschedule.data.DaySchedule
import ru.lican1907.tusurschedule.ui.MainActivity
import ru.lican1907.tusurschedule.widget.ScheduleWidgetTheme
import ru.lican1907.tusurschedule.widget.consolidateLessons
import ru.lican1907.tusurschedule.widget.formatDaySubtitle
import ru.lican1907.tusurschedule.widget.formatPairCount
import ru.lican1907.tusurschedule.widget.formatRelativeDayTitle
import java.time.LocalDate

@Composable
fun WidgetDayHeader(
    day: DaySchedule,
    today: LocalDate
) {
    val dayDate = runCatching { LocalDate.parse(day.date) }.getOrDefault(today)
    val dayMerged = consolidateLessons(day.lessons)
    val isRelative = (dayDate == today || dayDate == today.plusDays(1))
    val title = formatRelativeDayTitle(dayDate, today)
    val subtitle = formatDaySubtitle(dayDate, isRelative)
    val count = if (dayMerged.isNotEmpty()) formatPairCount(dayMerged.size) else null
    WidgetDayHeader(
        title = title,
        subtitle = subtitle,
        countText = count
    )
}

@Composable
fun WidgetDayHeader(
    title: String,
    subtitle: String,
    countText: String? = null
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = ColorProvider(ScheduleWidgetTheme.TextPrimary),
                    fontSize = ScheduleWidgetTheme.FontDayTitle,
                    fontWeight = FontWeight.Bold
                )
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = subtitle,
                    style = TextStyle(
                        color = ColorProvider(ScheduleWidgetTheme.TextMuted),
                        fontSize = ScheduleWidgetTheme.FontDaySubtitle,
                        fontWeight = FontWeight.Normal
                    )
                )
            }
        }

        if (!countText.isNullOrBlank()) {
            Text(
                text = countText,
                style = TextStyle(
                    color = ColorProvider(ScheduleWidgetTheme.TextMuted),
                    fontSize = ScheduleWidgetTheme.FontDayCount,
                    fontWeight = FontWeight.Normal
                )
            )
        }
    }
}

@Composable
fun WidgetEmptyDayCard(
    hasHadLessons: Boolean
) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.bg_widget_empty_day))
            .cornerRadius(ScheduleWidgetTheme.RadiusCard)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Text(
            text = if (hasHadLessons) "🌙  Пары закончились — отдыхай" else "🌙  Пар нет — отдыхай",
            style = TextStyle(
                color = ColorProvider(ScheduleWidgetTheme.TextMuted),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal
            )
        )
    }
}
