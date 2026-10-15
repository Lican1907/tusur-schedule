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
import ru.lican1907.tusurschedule.data.LessonItem
import ru.lican1907.tusurschedule.data.ScheduleRepository
import ru.lican1907.tusurschedule.ui.MainActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
            val displayDateText = if (targetDay != null) {
                try {
                    val parsedDate = LocalDate.parse(targetDay.date)
                    val today = ScheduleRepository.getTomskToday()
                    val dayOfWeek = parsedDate.format(DateTimeFormatter.ofPattern("EEE", Locale("ru")))
                        .replaceFirstChar { it.uppercase() }
                    val dayOfMonth = parsedDate.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru")))
                    when (parsedDate) {
                        today -> "Сегодня, $dayOfMonth"
                        today.plusDays(1) -> "Завтра, $dayOfMonth"
                        else -> "$dayOfWeek, $dayOfMonth"
                    }
                } catch (_: Exception) {
                    targetDay.date
                }
            } else {
                "Нет данных"
            }

            // 1. Фоновый контейнер виджета (глубокое тёмное стекло 0xF00D1117)
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xF00D1117))
                    .cornerRadius(24.dp)
                    .border(width = 1.dp, color = Color(0x20FFFFFF))
                    .padding(horizontal = 10.dp, vertical = 12.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // 2. Верхняя панель (Header)
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Слева: Чип группы + крупная дата
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .clickable(actionStartActivity<MainActivity>())
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .background(Color(0x25FFFFFF))
                                    .cornerRadius(6.dp)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = currentGroup.name,
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFFE6EDF3)),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = GlanceModifier.height(3.dp))

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

                        // Справа: круглая кнопка обновления (32.dp x 32.dp, cornerRadius 16.dp)
                        Box(
                            modifier = GlanceModifier
                                .width(32.dp)
                                .height(32.dp)
                                .background(Color(0x25FFFFFF))
                                .cornerRadius(16.dp)
                                .clickable(actionRunCallback<RefreshWidgetActionCallback>()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔄",
                                style = TextStyle(fontSize = 14.sp)
                            )
                        }
                    }

                    // 3. Содержимое / Карточки пар
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
                                LiquidGlassLessonCard(lesson, selectedPeTeacher)
                                Spacer(modifier = GlanceModifier.height(8.dp))
                            }

                            // 4. Карточка отдыха («🍺 Пить пиво дозволено»)
                            if (!targetDay.beer_time.isNullOrEmpty()) {
                                item {
                                    Box(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp, bottom = 4.dp)
                                            .background(Color(0x35D29922))
                                            .cornerRadius(16.dp)
                                            .border(width = 1.dp, color = Color(0x60E3B341))
                                            .padding(vertical = 10.dp, horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "🍺 Пиво дозволено с ${targetDay.beer_time} • 🎉 Свобода",
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
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun LiquidGlassLessonCard(lesson: LessonItem, selectedPeTeacher: String?) {
        val (accentColor, cardBg) = getLiquidTint(lesson)

        val locTeacherText = when {
            !lesson.pe_rooms.isNullOrEmpty() -> {
                val room = if (!selectedPeTeacher.isNullOrEmpty() && lesson.pe_rooms.containsKey(selectedPeTeacher)) {
                    lesson.pe_rooms[selectedPeTeacher] ?: ""
                } else {
                    lesson.pe_rooms.values.firstOrNull() ?: ""
                }
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
            lesson.teacher.isNotEmpty() -> "👤 ${lesson.teacher}"
            else -> ""
        }

        val typeBadge = when {
            lesson.is_cancelled -> "❌ ОТМЕНА"
            lesson.is_override -> "⚡ ${lesson.type}"
            else -> lesson.type
        }

        val subjectTitle = if (lesson.is_cancelled) {
            "[ОТМЕНА] ${lesson.displaySubject}"
        } else {
            lesson.displaySubject
        }

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(cardBg)
                .cornerRadius(16.dp)
                .border(width = 1.dp, color = accentColor.copy(alpha = 0.35f))
                .padding(10.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                // Строка 1: Время + Бейдж типа
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${lesson.time_start}–${lesson.time_end}",
                        style = TextStyle(
                            color = ColorProvider(Color(0xCCFFFFFF)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                    )

                    Box(
                        modifier = GlanceModifier
                            .background(accentColor.copy(alpha = 0.3f))
                            .cornerRadius(6.dp)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = typeBadge,
                            style = TextStyle(
                                color = ColorProvider(if (lesson.is_cancelled) Color(0xFFF85149) else accentColor),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                // Строка 2: Название дисциплины
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

                // Строка 3: Аудитория и преподаватель
                if (locTeacherText.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(3.dp))
                    Text(
                        text = locTeacherText,
                        style = TextStyle(
                            color = ColorProvider(Color(0xDDFFFFFF)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        maxLines = 1
                    )
                }

                // Строка 4: Комментарий (если есть)
                if (lesson.is_override && !lesson.override_comment.isNullOrEmpty()) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(Color(0x25000000))
                            .cornerRadius(6.dp)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
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
            }
        }
    }

    private fun getLiquidTint(lesson: LessonItem): Pair<Color, Color> {
        if (lesson.is_cancelled) {
            return Pair(Color(0xFF8B949E), Color(0x18FFFFFF))
        }
        val upperType = lesson.type.uppercase()
        return when {
            upperType.contains("ЛЕК") -> Pair(Color(0xFF2EA043), Color(0x2A2EA043))
            upperType.contains("ПРАК") -> Pair(Color(0xFF388BFD), Color(0x2A388BFD))
            upperType.contains("ЛАБ") -> Pair(Color(0xFFA371F7), Color(0x2EA371F7))
            upperType.contains("СРС") -> Pair(Color(0xFFE3B341), Color(0x25E3B341))
            upperType.contains("КУРС") -> Pair(Color(0xFFD29922), Color(0x25D29922))
            upperType.contains("ЭКЗ") || upperType.contains("ЗАЧ") -> Pair(Color(0xFFF85149), Color(0x2AF85149))
            else -> {
                val base = parseColor(lesson.color_hex)
                Pair(base, base.copy(alpha = 0.16f))
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

class RefreshWidgetActionCallback : ActionCallback {
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

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()
}
