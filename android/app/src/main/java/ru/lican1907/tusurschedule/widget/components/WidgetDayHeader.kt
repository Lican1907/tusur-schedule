package ru.lican1907.tusurschedule.widget.components

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ru.lican1907.tusurschedule.data.DaySchedule
import ru.lican1907.tusurschedule.widget.ScheduleWidgetTheme
import ru.lican1907.tusurschedule.widget.consolidateLessons
import ru.lican1907.tusurschedule.widget.formatDayDate
import ru.lican1907.tusurschedule.widget.formatPairCount
import ru.lican1907.tusurschedule.widget.formatRelativeDayTitle
import java.time.LocalDate

@Composable
fun WidgetDayHeader(
    day: DaySchedule,
    today: LocalDate
) {
    val dayDate = try {
        LocalDate.parse(day.date)
    } catch (_: Exception) {
        today
    }
    val dayMerged = consolidateLessons(day.lessons)
    WidgetDayHeader(
        title = formatRelativeDayTitle(dayDate, today),
        dateStr = formatDayDate(dayDate),
        countText = formatPairCount(dayMerged.size)
    )
}

@Composable
fun WidgetDayHeader(
    title: String,
    dateStr: String,
    countText: String
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title, $dateStr",
            style = TextStyle(
                color = ColorProvider(ScheduleWidgetTheme.TextPrimary),
                fontSize = ScheduleWidgetTheme.FontDayTitle,
                fontWeight = FontWeight.Bold
            ),
            modifier = GlanceModifier.defaultWeight()
        )

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
