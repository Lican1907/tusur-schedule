package ru.lican1907.tusurschedule.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
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
import androidx.glance.layout.fillMaxHeight
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
import ru.lican1907.tusurschedule.data.LessonItem
import ru.lican1907.tusurschedule.data.ScheduleRepository
import ru.lican1907.tusurschedule.ui.MainActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class ScheduleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = ScheduleRepository(context)
        val scheduleResult = repo.loadSchedule(forceRefresh = false)
        val schedule = scheduleResult.getOrNull()
        val currentGroup = repo.getSelectedGroup()
        val selectedPeTeacher = repo.selectedPeTeacher

        val targetDay = if (schedule != null) {
            ScheduleRepository.findTodayOrNextDay(schedule.days)
        } else null

        provideContent {
            val isToday = targetDay != null && targetDay.date == ScheduleRepository.getTomskToday().toString()

            val displayDateText = if (targetDay != null) {
                try {
                    val parsedDate = LocalDate.parse(targetDay.date)
                    val dayOfWeek = parsedDate.format(DateTimeFormatter.ofPattern("EEE", Locale("ru")))
                        .replaceFirstChar { it.uppercase() }
                    val dayOfMonth = parsedDate.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru")))
                        .replace(".", "")
                    "$dayOfWeek, $dayOfMonth"
                } catch (_: Exception) {
                    targetDay.date
                }
            } else {
                "Нет данных"
            }

            // 1. Главный контейнер виджета (Liquid Glass подложка)
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0x22FFFFFF))
                    .cornerRadius(24.dp)
                    .padding(1.dp)
            ) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(Color(0xF20B0F14))
                        .cornerRadius(23.dp)
                        .border(width = 1.dp, color = Color(0x22FFFFFF))
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                ) {
                    Column(modifier = GlanceModifier.fillMaxSize()) {
                        // 2. Шапка виджета (Header)
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Слева: плашка группы "426топ-4" и рядом дата "Чт, 15 окт"
                            Row(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .clickable(actionStartActivity<MainActivity>()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = GlanceModifier
                                        .background(Color(0x25FFFFFF))
                                        .cornerRadius(6.dp)
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = currentGroup.name,
                                        style = TextStyle(
                                            color = ColorProvider(Color.White),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = GlanceModifier.width(8.dp))

                                Text(
                                    text = displayDateText,
                                    style = TextStyle(
                                        color = ColorProvider(Color.White),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1
                                )
                            }

                            // Справа: круглая стеклянная кнопка 🔄 32.dp x 32.dp, cornerRadius 16.dp
                            Box(
                                modifier = GlanceModifier
                                    .width(32.dp)
                                    .height(32.dp)
                                    .background(Color(0x25FFFFFF))
                                    .cornerRadius(16.dp)
                                    .clickable(actionRunCallback<RefreshAction>()),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🔄",
                                    style = TextStyle(fontSize = 14.sp)
                                )
                            }
                        }

                        // 3. Компоновка списка: Таймлайн + Карточки (LazyColumn)
                        if (targetDay == null || targetDay.lessons.isEmpty()) {
                            Box(
                                modifier = GlanceModifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Пар нет 🎉",
                                        style = TextStyle(
                                            color = ColorProvider(Color.White),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = GlanceModifier.height(4.dp))
                                    Text(
                                        text = "Можно отдыхать!",
                                        style = TextStyle(
                                            color = ColorProvider(Color(0xCCFFFFFF)),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        } else {
                            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                                items(targetDay.lessons) { lesson ->
                                    TimelineLessonRow(lesson, selectedPeTeacher, isToday)
                                }

                                // 4. Нижняя карточка отдыха («Пиво дозволено / Свобода»)
                                if (!targetDay.beer_time.isNullOrEmpty()) {
                                    item {
                                        TimelineBeerRow(targetDay.beer_time)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun TimelineLessonRow(
        lesson: LessonItem,
        selectedPeTeacher: String?,
        isToday: Boolean
    ) {
        val colors = getTimelineColors(lesson)

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Левая ось (Timeline Indicator)
            Box(
                modifier = GlanceModifier
                    .width(16.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                // Вертикальная линия толщиной 1.5.dp цвета Color(0x30FFFFFF)
                Box(
                    modifier = GlanceModifier
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(Color(0x30FFFFFF))
                ) {}

                // Круглый узел диаметром 10.dp со скруглением 5.dp
                Box(
                    modifier = GlanceModifier
                        .padding(top = 10.dp)
                        .width(10.dp)
                        .height(10.dp)
                        .background(colors.nodeColor)
                        .cornerRadius(5.dp)
                ) {}
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Стеклянная карточка пары (Liquid Glass Pill)
            LiquidGlassLessonCard(
                lesson = lesson,
                selectedPeTeacher = selectedPeTeacher,
                isToday = isToday,
                colors = colors,
                modifier = GlanceModifier.defaultWeight()
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun TimelineBeerRow(beerTime: String) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Левая ось: золотисто-янтарный узел
            Box(
                modifier = GlanceModifier
                    .width(16.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                // Вертикальная линия
                Box(
                    modifier = GlanceModifier
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(Color(0x30FFFFFF))
                ) {}

                // Узел: золотисто-янтарный Color(0xFFE3B341)
                Box(
                    modifier = GlanceModifier
                        .padding(top = 10.dp)
                        .width(10.dp)
                        .height(10.dp)
                        .background(Color(0xFFE3B341))
                        .cornerRadius(5.dp)
                ) {}
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Медово-янтарная капсула «Пиво дозволено / Свобода»
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .background(Color(0x99E3B341))
                    .cornerRadius(16.dp)
                    .padding(1.5.dp)
            ) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(Color(0x35E3B341))
                        .cornerRadius(15.dp)
                        .border(width = 1.5.dp, color = Color(0x99E3B341))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🍺 Пиво дозволено с $beerTime • 🎉 Свобода",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFFFD566)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 2
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun LiquidGlassLessonCard(
        lesson: LessonItem,
        selectedPeTeacher: String?,
        isToday: Boolean,
        colors: LessonColors,
        modifier: GlanceModifier
    ) {
        // Расчет живого статуса текущей пары (Live Status & Progress)
        val now = LocalTime.now(ScheduleRepository.TOMSK_ZONE_ID)
        val (isLive, minutesLeft, progress) = try {
            if (isToday && !lesson.is_cancelled && lesson.time_start.isNotEmpty() && lesson.time_end.isNotEmpty()) {
                val start = LocalTime.parse(lesson.time_start)
                val end = LocalTime.parse(lesson.time_end)
                if (!now.isBefore(start) && now.isBefore(end)) {
                    val totalMin = ChronoUnit.MINUTES.between(start, end).toFloat()
                    val elapsedMin = ChronoUnit.MINUTES.between(start, now).toFloat()
                    val p = if (totalMin > 0f) (elapsedMin / totalMin).coerceIn(0f, 1f) else 0f
                    val left = ChronoUnit.MINUTES.between(now, end)
                    Triple(true, left, p)
                } else {
                    Triple(false, 0L, 0f)
                }
            } else {
                Triple(false, 0L, 0f)
            }
        } catch (_: Exception) {
            Triple(false, 0L, 0f)
        }

        // Локация и преподаватель
        val locTeacherText = when {
            !lesson.pe_rooms.isNullOrEmpty() -> {
                val teacherKey = when {
                    !selectedPeTeacher.isNullOrEmpty() && lesson.pe_rooms.containsKey(selectedPeTeacher) -> selectedPeTeacher
                    lesson.teacher.isNotEmpty() && lesson.pe_rooms.containsKey(lesson.teacher) -> lesson.teacher
                    else -> selectedPeTeacher ?: lesson.teacher
                }
                val room = lesson.pe_rooms[teacherKey] ?: lesson.location
                if (room.isNotEmpty()) "📍 Зал $room" else if (lesson.location.isNotEmpty()) "📍 ${lesson.location}" else ""
            }
            lesson.location.isNotEmpty() -> {
                if (lesson.teacher.isNotEmpty()) {
                    val shortTeacher = lesson.teacher.split(" ").take(2).joinToString(" ")
                    "📍 ${lesson.location} • $shortTeacher"
                } else {
                    "📍 ${lesson.location}"
                }
            }
            lesson.teacher.isNotEmpty() -> {
                val shortTeacher = lesson.teacher.split(" ").take(2).joinToString(" ")
                "👤 $shortTeacher"
            }
            else -> ""
        }

        // Бейдж типа пары
        val badgeText = when {
            lesson.is_cancelled -> "❌ ОТМЕНА"
            lesson.type.uppercase().contains("ЛЕК") -> "[ЛЕК]"
            lesson.type.uppercase().contains("ПРАК") -> "[ПРАК]"
            lesson.type.uppercase().contains("ЛАБ") -> "[ЛАБ]"
            lesson.type.isNotEmpty() -> "[${lesson.type}]"
            else -> ""
        }

        val rawTitle = lesson.subject_short?.ifEmpty { null }
            ?: lesson.subject_full?.ifEmpty { null }
            ?: lesson.displaySubject

        val subjectTitle = if (lesson.is_cancelled) {
            "[ОТМЕНА] $rawTitle"
        } else {
            rawTitle
        }

        Box(
            modifier = modifier
                .background(colors.borderColor)
                .cornerRadius(16.dp)
                .padding(1.dp)
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(colors.cardBg)
                    .cornerRadius(15.dp)
                    .border(width = 1.dp, color = colors.borderColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    // 4. Живой статус (если пара идет прямо сейчас): плашка 🟢 ИДЁТ СЕЙЧАС
                    if (isLive) {
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(Color(0x332EA043))
                                .cornerRadius(6.dp)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🟢 ИДЁТ СЕЙЧАС",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF2EA043)),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = GlanceModifier.defaultWeight()
                            )
                            if (minutesLeft > 0) {
                                Text(
                                    text = "ещё $minutesLeft мин ⏳",
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFFE6EDF3)),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                        Spacer(modifier = GlanceModifier.height(4.dp))
                    }

                    // 1. Верхняя строка: Время + Бейдж
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${lesson.time_start}–${lesson.time_end}",
                            style = TextStyle(
                                color = ColorProvider(Color(0xDDFFFFFF)),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight()
                        )

                        if (badgeText.isNotEmpty()) {
                            Box(
                                modifier = GlanceModifier
                                    .background(colors.badgeBg)
                                    .cornerRadius(4.dp)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    style = TextStyle(
                                        color = ColorProvider(colors.badgeTextColor),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(4.dp))

                    // 2. Название дисциплины
                    Text(
                        text = subjectTitle,
                        style = TextStyle(
                            color = ColorProvider(if (lesson.is_cancelled) Color(0xFF8B949E) else Color.White),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (lesson.is_cancelled) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        maxLines = 2
                    )

                    // 3. Локация и преподаватель
                    if (locTeacherText.isNotEmpty()) {
                        Spacer(modifier = GlanceModifier.height(3.dp))
                        Text(
                            text = locTeacherText,
                            style = TextStyle(
                                color = ColorProvider(Color(0xB3FFFFFF)),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            maxLines = 1
                        )
                    }

                    // 5. Комментарий замены (если override_comment != null): ⚡ ${lesson.override_comment}
                    if (!lesson.override_comment.isNullOrEmpty()) {
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(Color(0x20F0883E))
                                .cornerRadius(4.dp)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "⚡ ${lesson.override_comment}",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFFF0883E)),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 2
                            )
                        }
                    }

                    // 4. Тонкая горизонтальная линия прогресса занятия внизу карточки (высота 2.dp, цвет Color(0xFF2EA043))
                    if (isLive) {
                        Spacer(modifier = GlanceModifier.height(6.dp))
                        LinearProgressIndicator(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(2.dp),
                            progress = progress,
                            color = ColorProvider(Color(0xFF2EA043)),
                            backgroundColor = ColorProvider(Color(0x332EA043))
                        )
                    }
                }
            }
        }
    }

    private data class LessonColors(
        val nodeColor: Color,
        val cardBg: Color,
        val borderColor: Color,
        val badgeBg: Color,
        val badgeTextColor: Color
    )

    private fun getTimelineColors(lesson: LessonItem): LessonColors {
        if (lesson.is_cancelled) {
            return LessonColors(
                nodeColor = Color(0xFF484F58),
                cardBg = Color(0x14FFFFFF),
                borderColor = Color(0x20FFFFFF),
                badgeBg = Color(0x33F85149),
                badgeTextColor = Color(0xFFF85149)
            )
        }

        val upperType = lesson.type.uppercase()
        return when {
            upperType.contains("ЛЕК") -> LessonColors(
                nodeColor = Color(0xFF2EA043),
                cardBg = Color(0x242EA043),
                borderColor = Color(0x662EA043),
                badgeBg = Color(0x332EA043),
                badgeTextColor = Color(0xFF2EA043)
            )
            upperType.contains("ПРАК") -> LessonColors(
                nodeColor = Color(0xFF388BFD),
                cardBg = Color(0x24388BFD),
                borderColor = Color(0x66388BFD),
                badgeBg = Color(0x33388BFD),
                badgeTextColor = Color(0xFF388BFD)
            )
            upperType.contains("ЛАБ") -> LessonColors(
                nodeColor = Color(0xFFA371F7),
                cardBg = Color(0x24A371F7),
                borderColor = Color(0x66A371F7),
                badgeBg = Color(0x33A371F7),
                badgeTextColor = Color(0xFFA371F7)
            )
            else -> {
                val base = parseColor(lesson.color_hex)
                LessonColors(
                    nodeColor = base,
                    cardBg = base.copy(alpha = 0.14f),
                    borderColor = base.copy(alpha = 0.40f),
                    badgeBg = base.copy(alpha = 0.20f),
                    badgeTextColor = base
                )
            }
        }
    }

    private fun parseColor(hex: String): Color {
        return try {
            val clean = hex.removePrefix("#")
            val colorInt = clean.toLong(16)
            if (clean.length == 6) {
                Color(colorInt or 0x00000000FF000000)
            } else {
                Color(colorInt)
            }
        } catch (_: Exception) {
            Color(0xFF58A6FF)
        }
    }
}

private fun GlanceModifier.border(width: androidx.compose.ui.unit.Dp, color: Color): GlanceModifier = this

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
