package ru.lican1907.tusurschedule.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ru.lican1907.tusurschedule.R
import ru.lican1907.tusurschedule.data.LessonItem
import ru.lican1907.tusurschedule.data.ScheduleRepository
import ru.lican1907.tusurschedule.ui.MainActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = ScheduleRepository(context)
        val scheduleResult = repo.loadSchedule(forceRefresh = false)
        val schedule = scheduleResult.getOrNull()
        val selectedPeTeacher = repo.selectedPeTeacher

        provideContent {
            val today = ScheduleRepository.getTomskToday()
            val todayStr = today.toString()
            val horizonEnd = today.plusDays(14)

            val allDays = schedule?.days ?: emptyList()
            val todayDay = allDays.firstOrNull { it.date == todayStr }
            val now = LocalTime.now(ScheduleRepository.TOMSK_ZONE_ID)

            // Уроки на сегодня: скрываем завершившиеся пары (LocalTime.now() >= lesson.time_end)
            val todayRemainingLessons = todayDay?.lessons?.filter { lesson ->
                try {
                    if (lesson.time_end.isNotEmpty()) {
                        val end = LocalTime.parse(lesson.time_end)
                        now.isBefore(end)
                    } else true
                } catch (_: Exception) {
                    true
                }
            } ?: emptyList()

            // Предстоящие дни на горизонте 2 недель (отфильтровываем пустые дни)
            val upcomingDays = allDays.filter { day ->
                try {
                    val d = LocalDate.parse(day.date)
                    d.isAfter(today) && !d.isAfter(horizonEnd) && day.lessons.isNotEmpty()
                } catch (_: Exception) {
                    false
                }
            }

            // Основной полупрозрачный контейнер жидкого стекла (Liquid Glassmorphism)
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.bg_widget_glass))
                    .cornerRadius(30.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // Тонкая шапка с названием группы и кнопкой обновления
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ТУСУР • ${repo.getSelectedGroup().name}",
                            style = TextStyle(
                                color = ColorProvider(Color(0xCCFFFFFF)),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier
                                .defaultWeight()
                                .clickable(actionStartActivity<MainActivity>())
                        )

                        Box(
                            modifier = GlanceModifier
                                .width(36.dp)
                                .height(28.dp)
                                .background(ImageProvider(R.drawable.bg_btn_frosted))
                                .cornerRadius(14.dp)
                                .clickable(actionRunCallback<RefreshAction>()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "↻",
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // Список расписания (Scrollable LazyColumn)
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        // 1. Блок сегодняшнего дня (Today)
                        item {
                            WidgetDateHeader(
                                text = formatWidgetDate(today),
                                isFirst = true
                            )
                        }

                        if (todayRemainingLessons.isEmpty()) {
                            item {
                                EmptyFinishedCard()
                                Spacer(modifier = GlanceModifier.height(6.dp))
                            }
                            if (!todayDay?.beer_time.isNullOrEmpty()) {
                                item {
                                    CalendarBeerCard(todayDay!!.beer_time!!)
                                    Spacer(modifier = GlanceModifier.height(6.dp))
                                }
                            }
                        } else {
                            items(todayRemainingLessons) { lesson ->
                                CalendarEventCard(lesson, selectedPeTeacher, isToday = true)
                                Spacer(modifier = GlanceModifier.height(6.dp))
                            }
                            if (!todayDay?.beer_time.isNullOrEmpty()) {
                                item {
                                    CalendarBeerCard(todayDay!!.beer_time!!)
                                    Spacer(modifier = GlanceModifier.height(6.dp))
                                }
                            }
                        }

                        // 2. Предстоящие дни (Upcoming Days в пределах 2 недель)
                        upcomingDays.forEach { day ->
                            item {
                                WidgetDateHeader(
                                    text = formatWidgetDate(day.date),
                                    isFirst = false
                                )
                            }
                            items(day.lessons) { lesson ->
                                CalendarEventCard(lesson, selectedPeTeacher, isToday = false)
                                Spacer(modifier = GlanceModifier.height(6.dp))
                            }
                            if (!day.beer_time.isNullOrEmpty()) {
                                item {
                                    CalendarBeerCard(day.beer_time)
                                    Spacer(modifier = GlanceModifier.height(6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun WidgetDateHeader(text: String, isFirst: Boolean = false) {
        Text(
            text = text,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = GlanceModifier.padding(
                top = if (isFirst) 0.dp else 12.dp,
                bottom = 8.dp
            )
        )
    }

    @androidx.compose.runtime.Composable
    private fun CalendarEventCard(
        lesson: LessonItem,
        selectedPeTeacher: String?,
        isToday: Boolean
    ) {
        val bgDrawableRes = getCardDrawableRes(lesson)

        val typeTag = when {
            lesson.is_cancelled -> "ОТМЕНА"
            lesson.type.uppercase().contains("ЛЕК") -> "ЛЕК"
            lesson.type.uppercase().contains("ПРАК") -> "ПРАК"
            lesson.type.uppercase().contains("ЛАБ") -> "ЛАБ"
            else -> lesson.type
        }

        val shortSubject = lesson.subject_short?.ifEmpty { null }
            ?: lesson.subject?.ifEmpty { null }
            ?: lesson.displaySubject

        val line1 = if (lesson.is_cancelled) {
            "ОТМЕНА • $shortSubject"
        } else {
            "$typeTag • $shortSubject"
        }

        val loc = when {
            !lesson.pe_rooms.isNullOrEmpty() -> {
                val teacherKey = if (!selectedPeTeacher.isNullOrEmpty() && lesson.pe_rooms.containsKey(selectedPeTeacher)) {
                    selectedPeTeacher
                } else {
                    lesson.pe_rooms.keys.firstOrNull() ?: ""
                }
                val room = lesson.pe_rooms[teacherKey] ?: lesson.location
                if (room.isNotEmpty()) "Зал $room" else lesson.location
            }
            else -> lesson.location
        }

        val now = LocalTime.now(ScheduleRepository.TOMSK_ZONE_ID)
        val isLive = isToday && !lesson.is_cancelled && try {
            if (lesson.time_start.isNotEmpty() && lesson.time_end.isNotEmpty()) {
                val start = LocalTime.parse(lesson.time_start)
                val end = LocalTime.parse(lesson.time_end)
                !now.isBefore(start) && now.isBefore(end)
            } else false
        } catch (_: Exception) {
            false
        }

        val timePart = lesson.time_start.ifEmpty { "–" }
        val line2 = buildString {
            append(timePart)
            if (loc.isNotEmpty()) {
                append(", 📍 ")
                append(loc)
            }
            if (isLive) {
                append(" • 🟢 сейчас")
            }
        }

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ImageProvider(bgDrawableRes))
                .cornerRadius(18.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = line1,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (lesson.is_cancelled) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    maxLines = 1
                )

                Spacer(modifier = GlanceModifier.height(2.dp))

                Text(
                    text = line2,
                    style = TextStyle(
                        color = ColorProvider(Color(0xE6FFFFFF)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1
                )

                if (!lesson.override_comment.isNullOrEmpty()) {
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = "⚡ ${lesson.override_comment}",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFFFD566)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun EmptyFinishedCard() {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ImageProvider(R.drawable.bg_card_empty))
                .cornerRadius(18.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Text(
                text = "На сегодня больше ничего",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFE0E0E0)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun CalendarBeerCard(beerTime: String) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ImageProvider(R.drawable.bg_card_beer))
                .cornerRadius(18.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = "🍺 Пиво дозволено",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = "С $beerTime • 🎉 Свобода",
                    style = TextStyle(
                        color = ColorProvider(Color(0xE6FFFFFF)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1
                )
            }
        }
    }

    private fun getCardDrawableRes(lesson: LessonItem): Int {
        if (lesson.is_cancelled) {
            return R.drawable.bg_card_cancelled
        }
        val upperType = lesson.type.uppercase()
        return when {
            upperType.contains("ЛЕК") -> R.drawable.bg_card_lek
            upperType.contains("ПРАК") -> R.drawable.bg_card_prak
            upperType.contains("ЛАБ") -> R.drawable.bg_card_lab
            else -> R.drawable.bg_card_prak
        }
    }

    private fun formatWidgetDate(date: LocalDate): String {
        val dow = date.format(DateTimeFormatter.ofPattern("EEE", Locale("ru")))
            .replaceFirstChar { it.uppercase() }
        val d = date.dayOfMonth
        val m = date.format(DateTimeFormatter.ofPattern("MMM", Locale("ru")))
            .removeSuffix(".")
        return "$dow, $d $m."
    }

    private fun formatWidgetDate(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr)
            formatWidgetDate(date)
        } catch (_: Exception) {
            dateStr
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val repo = ScheduleRepository(context)
        repo.loadSchedule(forceRefresh = true)
        ScheduleWidget().updateAll(context)
    }
}

typealias RefreshWidgetActionCallback = RefreshAction

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()
}
