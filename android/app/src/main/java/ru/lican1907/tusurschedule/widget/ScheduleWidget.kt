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
            val darkBg = Color(0xF2161B22)
            val textPrimary = Color(0xFFF0F6FC)
            val textMuted = Color(0xFF8B949E)
            val beerGold = Color(0xFFD29922)

            val displayDateText = if (targetDay != null) {
                try {
                    val parsedDate = LocalDate.parse(targetDay.date)
                    val today = ScheduleRepository.getTomskToday()
                    val prefix = when (parsedDate) {
                        today -> "Сегодня"
                        today.plusDays(1) -> "Завтра"
                        else -> parsedDate.format(DateTimeFormatter.ofPattern("EEE", Locale("ru")))
                    }
                    val dateFormatted = parsedDate.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru")))
                    "$prefix, $dateFormatted"
                } catch (_: Exception) {
                    targetDay.date
                }
            } else {
                "Нет данных"
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(darkBg)
                    .cornerRadius(16.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // Header Row: Group name and date stacked vertically on left, Refresh on right
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .clickable(actionStartActivity<MainActivity>())
                        ) {
                            Text(
                                text = currentGroup.name,
                                style = TextStyle(
                                    color = ColorProvider(textPrimary),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = displayDateText,
                                style = TextStyle(
                                    color = ColorProvider(textMuted),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                maxLines = 1
                            )
                        }

                        // Refresh button (28.dp touch area)
                        Box(
                            modifier = GlanceModifier
                                .width(28.dp)
                                .height(28.dp)
                                .clickable(actionRunCallback<RefreshWidgetActionCallback>()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔄",
                                style = TextStyle(fontSize = 14.sp)
                            )
                        }
                    }

                    // Content
                    if (targetDay == null || targetDay.lessons.isEmpty()) {
                        Box(
                            modifier = GlanceModifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Пар нет 🎉",
                                style = TextStyle(
                                    color = ColorProvider(textMuted),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    } else {
                        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            items(targetDay.lessons) { lesson ->
                                VerticalLessonRow(lesson, selectedPeTeacher)
                                Spacer(modifier = GlanceModifier.height(5.dp))
                            }

                            // Beer time banner at the end of the day
                            if (!targetDay.beer_time.isNullOrEmpty()) {
                                item {
                                    Box(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp, bottom = 2.dp)
                                            .background(Color(0x33D29922))
                                            .cornerRadius(8.dp)
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "🍺 Пиво с ${targetDay.beer_time}",
                                            style = TextStyle(
                                                color = ColorProvider(beerGold),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            maxLines = 1
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
    private fun VerticalLessonRow(lesson: LessonItem, selectedPeTeacher: String?) {
        val lessonColor = parseColor(lesson.color_hex)
        val textPrimary = Color(0xFFF0F6FC)
        val textMuted = Color(0xFF8B949E)
        val cardBg = Color(0xFF21262D)

        val resolvedAud = lesson.getResolvedLocation(selectedPeTeacher)
        val audText = if (resolvedAud.isNotEmpty()) "📍 $resolvedAud" else ""

        val typeBadge = when {
            lesson.is_cancelled -> "ОТМЕНА"
            lesson.is_override -> "⚡ ${lesson.type}"
            else -> lesson.type
        }

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(cardBg)
                .cornerRadius(8.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Color bar
                Box(
                    modifier = GlanceModifier
                        .width(3.dp)
                        .height(34.dp)
                        .background(lessonColor)
                        .cornerRadius(2.dp)
                ) {}

                Spacer(modifier = GlanceModifier.width(6.dp))

                // Vertical stack inside card
                Column(modifier = GlanceModifier.defaultWeight()) {
                    // Row 1: Time and Type Badge
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${lesson.time_start}–${lesson.time_end}",
                            style = TextStyle(
                                color = ColorProvider(textMuted),
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight()
                        )

                        Text(
                            text = typeBadge,
                            style = TextStyle(
                                color = ColorProvider(if (lesson.is_cancelled) Color(0xFF8B949E) else lessonColor),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(2.dp))

                    // Row 2: Subject short
                    Text(
                        text = lesson.displaySubject,
                        style = TextStyle(
                            color = ColorProvider(if (lesson.is_cancelled) Color(0xFF8B949E) else textPrimary),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (lesson.is_cancelled) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        maxLines = 2
                    )

                    // Row 3: Location
                    if (audText.isNotEmpty()) {
                        Spacer(modifier = GlanceModifier.height(1.dp))
                        Text(
                            text = audText,
                            style = TextStyle(
                                color = ColorProvider(textMuted),
                                fontSize = 11.sp
                            ),
                            maxLines = 1
                        )
                    }

                    // Optional override comment
                    if (lesson.is_override && !lesson.override_comment.isNullOrEmpty()) {
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = "⚡ ${lesson.override_comment}",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFD29922)),
                                fontSize = 10.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
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
