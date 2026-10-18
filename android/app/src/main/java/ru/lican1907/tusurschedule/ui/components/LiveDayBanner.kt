package ru.lican1907.tusurschedule.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.lican1907.tusurschedule.data.DaySchedule
import ru.lican1907.tusurschedule.data.LessonItem
import ru.lican1907.tusurschedule.data.ScheduleRepository
import ru.lican1907.tusurschedule.ui.theme.*
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Composable
fun LiveDayBanner(day: DaySchedule) {
    val isToday = day.date == ScheduleRepository.getTomskToday().toString()
    if (!isToday) return

    val now = LocalTime.now(ScheduleRepository.TOMSK_ZONE_ID)

    // Check active lesson
    var activeLesson: LessonItem? = null
    var minutesLeft: Long = 0
    var lessonProgress = 0f

    for (l in day.lessons) {
        if (l.is_cancelled) continue
        try {
            val start = LocalTime.parse(l.time_start)
            val end = LocalTime.parse(l.time_end)
            if (!now.isBefore(start) && now.isBefore(end)) {
                activeLesson = l
                val totalMinutes = ChronoUnit.MINUTES.between(start, end).toFloat()
                val elapsedMinutes = ChronoUnit.MINUTES.between(start, now).toFloat()
                lessonProgress = (elapsedMinutes / totalMinutes).coerceIn(0f, 1f)
                minutesLeft = ChronoUnit.MINUTES.between(now, end)
                break
            }
        } catch (_: Exception) {}
    }

    if (activeLesson != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, Color(0x335F508A))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔴 ИДЁТ ПАРА: ${activeLesson.displaySubject}",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Осталось: $minutesLeft мин",
                        color = AccentPurple,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { lessonProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = AccentPurple,
                    trackColor = DarkSurfaceVariant
                )
            }
        }
    } else if (!day.beer_time.isNullOrEmpty()) {
        val beerTimeParsed = try {
            LocalTime.parse(day.beer_time)
        } catch (_: Exception) {
            null
        }

        if (beerTimeParsed != null && !now.isBefore(beerTimeParsed)) {
            // All lessons over! Beer time!
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0x26B88226),
                border = BorderStroke(1.dp, Color(0x66B88226))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🍺",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Пить пиво дозволено! 🎉",
                            color = AccentAmber,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Все пары сегодняшнего дня позади (с ${day.beer_time})",
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
