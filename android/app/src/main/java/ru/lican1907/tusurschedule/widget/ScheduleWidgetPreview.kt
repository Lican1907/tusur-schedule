package ru.lican1907.tusurschedule.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.lican1907.tusurschedule.data.DaySchedule
import ru.lican1907.tusurschedule.data.LessonItem

object ScheduleWidgetPreviewData {
    val sampleDays = listOf(
        DaySchedule(
            date = "2026-10-15",
            weekday = 4,
            lessons = emptyList()
        ),
        DaySchedule(
            date = "2026-10-16",
            weekday = 5,
            lessons = listOf(
                LessonItem(
                    time_start = "10:40",
                    time_end = "12:10",
                    type = "ПРАК",
                    subject = "История",
                    location = "ГК 323"
                ),
                LessonItem(
                    time_start = "13:15",
                    time_end = "14:45",
                    type = "ПРАК",
                    subject = "Математика",
                    location = "ГК 323"
                )
            )
        ),
        DaySchedule(
            date = "2026-10-17",
            weekday = 6,
            lessons = listOf(
                LessonItem(
                    time_start = "08:50",
                    time_end = "10:20",
                    type = "ПРАК",
                    subject = "Английский",
                    location = "РК 119",
                    subgroup = "1"
                ),
                LessonItem(
                    time_start = "08:50",
                    time_end = "10:20",
                    type = "ПРАК",
                    subject = "Английский",
                    location = "РК 119",
                    subgroup = "2"
                ),
                LessonItem(
                    time_start = "10:40",
                    time_end = "12:10",
                    type = "ЛЕК",
                    subject = "Информатика",
                    location = "ФЭТ 412"
                )
            )
        ),
        DaySchedule(
            date = "2026-10-19",
            weekday = 1,
            lessons = listOf(
                LessonItem(
                    time_start = "08:50",
                    time_end = "10:20",
                    type = "ЛАБ",
                    subject = "Физика",
                    location = "ГК 210"
                )
            )
        )
    )
}

@Preview(name = "Widget Concept Redesign Full (4x4)", widthDp = 360, heightDp = 640)
@Composable
fun PreviewWidgetFullConcept() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ScheduleWidgetTheme.RadiusContainer))
            .background(ScheduleWidgetTheme.BgWidgetDark)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Header
            PreviewHeaderRow(
                status = "Сегодня · пар больше нет",
                date = "Чт, 15 октября",
                group = "ИВТ-21"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Hero Card (Завтра История)
            PreviewHeroCard(
                tag = "СЛЕДУЮЩАЯ · ЗАВТРА",
                countdown = "через 22 ч 40 мин",
                title = "История",
                time = "10:40–12:10",
                location = "ГК 323",
                type = "Практика",
                bgGradient = Color(0xFF6D5BD0)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Section Сегодня
            PreviewDayHeaderRow("Сегодня", "чт, 15 окт", null)
            Spacer(modifier = Modifier.height(6.dp))
            PreviewEmptyDayCard("🌙  Пары закончились — отдыхай")

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Section Завтра
            PreviewDayHeaderRow("Завтра", "пт, 16 окт", "2 пары")
            Spacer(modifier = Modifier.height(6.dp))
            PreviewLessonCardRow(
                timeStart = "10:40",
                timeEnd = "12:10",
                subject = "История",
                type = "Практика",
                typeColor = ScheduleWidgetTheme.ColorPractice,
                location = "ГК 323"
            )
            Spacer(modifier = Modifier.height(6.dp))
            PreviewLessonCardRow(
                timeStart = "13:15",
                timeEnd = "14:45",
                subject = "Математика",
                type = "Практика",
                typeColor = ScheduleWidgetTheme.ColorPractice,
                location = "ГК 323"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Section Суббота
            PreviewDayHeaderRow("Суббота", "17 окт", "2 пары")
            Spacer(modifier = Modifier.height(6.dp))
            PreviewLessonCardRow(
                timeStart = "08:50",
                timeEnd = "10:20",
                subject = "Английский",
                type = "Практика",
                typeColor = ScheduleWidgetTheme.ColorPractice,
                location = "РК 119",
                badgeText = "2 подгруппы"
            )
            Spacer(modifier = Modifier.height(6.dp))
            PreviewLessonCardRow(
                timeStart = "10:40",
                timeEnd = "12:10",
                subject = "Информатика",
                type = "Лекция",
                typeColor = ScheduleWidgetTheme.ColorLecture,
                location = "ФЭТ 412"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 6. Section Понедельник
            PreviewDayHeaderRow("Понедельник", "19 окт", "1 пара")
            Spacer(modifier = Modifier.height(6.dp))
            PreviewLessonCardRow(
                timeStart = "08:50",
                timeEnd = "10:20",
                subject = "Физика",
                type = "Лабораторная",
                typeColor = ScheduleWidgetTheme.ColorLab,
                location = "ГК 210"
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview(name = "Widget 4x2 Compact", widthDp = 340, heightDp = 180)
@Composable
fun PreviewWidget4x2() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ScheduleWidgetTheme.RadiusContainer))
            .background(ScheduleWidgetTheme.BgWidgetDark)
            .padding(14.dp)
    ) {
        Column {
            PreviewHeaderRow(
                status = "Сегодня · пар нет",
                date = "Чт, 15 октября",
                group = "ИВТ-21"
            )

            Spacer(modifier = Modifier.height(10.dp))

            PreviewHeroCard(
                tag = "СЛЕДУЮЩАЯ · ЗАВТРА",
                countdown = "через 22 ч 40 мин",
                title = "История",
                time = "10:40–12:10",
                location = "ГК 323",
                type = "Практика",
                bgGradient = Color(0xFF6D5BD0)
            )
        }
    }
}

@Composable
private fun PreviewHeaderRow(status: String, date: String, group: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = status,
                color = ScheduleWidgetTheme.TextMuted,
                fontSize = ScheduleWidgetTheme.FontHeaderStatus
            )
            Text(
                text = date,
                color = ScheduleWidgetTheme.TextPrimary,
                fontSize = ScheduleWidgetTheme.FontHeaderDate,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(ScheduleWidgetTheme.RadiusChip))
                .background(Color(0x1FFFFFFF))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = group,
                color = ScheduleWidgetTheme.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(ScheduleWidgetTheme.ButtonRefreshSize)
                .clip(CircleShape)
                .background(Color(0x1FFFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "↻",
                color = ScheduleWidgetTheme.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PreviewHeroCard(
    tag: String,
    countdown: String,
    title: String,
    time: String,
    location: String,
    type: String,
    bgGradient: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScheduleWidgetTheme.RadiusHero))
            .background(bgGradient)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tag,
                    color = Color(0xE6FFFFFF),
                    fontSize = ScheduleWidgetTheme.FontHeroTag,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = countdown,
                    color = Color.White,
                    fontSize = ScheduleWidgetTheme.FontHeroTag,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = ScheduleWidgetTheme.FontHeroTitle,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "🕒 $time   📍 $location   $type",
                color = Color(0xF2FFFFFF),
                fontSize = ScheduleWidgetTheme.FontHeroDetails
            )
        }
    }
}

@Composable
private fun PreviewDayHeaderRow(title: String, subtitle: String, countText: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = ScheduleWidgetTheme.TextPrimary,
                fontSize = ScheduleWidgetTheme.FontDayTitle,
                fontWeight = FontWeight.Bold
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = subtitle,
                    color = ScheduleWidgetTheme.TextMuted,
                    fontSize = ScheduleWidgetTheme.FontDaySubtitle,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        if (!countText.isNullOrBlank()) {
            Text(
                text = countText,
                color = ScheduleWidgetTheme.TextMuted,
                fontSize = ScheduleWidgetTheme.FontDayCount,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun PreviewEmptyDayCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScheduleWidgetTheme.RadiusCard))
            .background(Color(0x0AFFFFFF))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = message,
            color = ScheduleWidgetTheme.TextMuted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun PreviewLessonCardRow(
    timeStart: String,
    timeEnd: String,
    subject: String,
    type: String,
    typeColor: Color,
    location: String,
    badgeText: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScheduleWidgetTheme.RadiusCard))
            .background(Color(0x0FFFFFFF))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(ScheduleWidgetTheme.WidthTimeColumn),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = timeStart,
                    color = ScheduleWidgetTheme.TextPrimary,
                    fontSize = ScheduleWidgetTheme.FontTimeStart,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = timeEnd,
                    color = ScheduleWidgetTheme.TextMuted,
                    fontSize = ScheduleWidgetTheme.FontTimeEnd
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .width(ScheduleWidgetTheme.WidthStripe)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(typeColor)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subject,
                        color = ScheduleWidgetTheme.TextPrimary,
                        fontSize = ScheduleWidgetTheme.FontSubject,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    if (!badgeText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(ScheduleWidgetTheme.RadiusBadge))
                                .background(Color(0x24A78BFA))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = ScheduleWidgetTheme.TextSecondary,
                                fontSize = ScheduleWidgetTheme.FontBadge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = type,
                        color = typeColor,
                        fontSize = ScheduleWidgetTheme.FontDetails,
                        fontWeight = FontWeight.Medium
                    )

                    if (location.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "📍 $location",
                            color = ScheduleWidgetTheme.TextMuted,
                            fontSize = ScheduleWidgetTheme.FontDetails
                        )
                    }
                }
            }
        }
    }
}
