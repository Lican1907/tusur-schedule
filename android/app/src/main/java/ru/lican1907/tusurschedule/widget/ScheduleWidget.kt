package ru.lican1907.tusurschedule.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import ru.lican1907.tusurschedule.R
import ru.lican1907.tusurschedule.data.ScheduleRepository
import ru.lican1907.tusurschedule.widget.components.WidgetDayHeader
import ru.lican1907.tusurschedule.widget.components.WidgetHeader
import ru.lican1907.tusurschedule.widget.components.WidgetHeroCard
import ru.lican1907.tusurschedule.widget.components.WidgetLessonCard
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ScheduleWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = ScheduleRepository(context)
        val schedule = repo.loadSchedule(forceRefresh = false).getOrNull()
        val selectedPeTeacher = repo.selectedPeTeacher
        val selectedEnglishTeacher = repo.selectedEnglishTeacher
        val selectedSubgroup = repo.selectedSubgroup
        val groupName = repo.getSelectedGroup().name

        provideContent {
            val size = LocalSize.current
            val isNarrow = size.width < 220.dp
            val isVeryCompact = size.height < 110.dp

            val today = ScheduleRepository.getTomskToday()
            val nowTime = LocalTime.now(ScheduleRepository.TOMSK_ZONE_ID)
            val nowDateTime = LocalDateTime.now(ScheduleRepository.TOMSK_ZONE_ID)

            val allDays = schedule?.days ?: emptyList()
            val filteredAllDays = allDays.map { day ->
                day.copy(lessons = day.lessons.filter { it.matchesSubgroup(selectedSubgroup) })
            }
            val todayDay = filteredAllDays.firstOrNull { it.date == today.toString() }

            // Consolidated today lessons
            val todayRawLessons = todayDay?.lessons ?: emptyList()
            val todayMerged = consolidateLessons(todayRawLessons)

            // Remaining lessons today
            val todayRemaining = todayMerged.filter { item ->
                runCatching { LocalTime.parse(item.lesson.time_end) }.map { nowTime.isBefore(it) }.getOrDefault(true)
            }

            // Effective beer time for today based on student's active lessons
            val effectiveBeerTime = todayRawLessons.filter { !it.is_cancelled }
                .mapNotNull { runCatching { LocalTime.parse(it.time_end) }.getOrNull() }
                .maxOrNull()?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: todayDay?.beer_time

            // Hero lesson detection
            val heroInfo = computeHeroInfo(
                today = today,
                todayLessons = todayMerged,
                allDays = filteredAllDays,
                nowTime = nowTime,
                nowDateTime = nowDateTime,
                beerTime = effectiveBeerTime
            )

            // Header status string
            val headerStatus = computeHeaderStatus(heroInfo, todayMerged, todayRemaining)
            val headerDateStr = formatHeaderDate(today)

            // Upcoming days (2-week window)
            val horizonEnd = today.plusDays(14)
            val upcomingDays = filteredAllDays.filter { day ->
                val d = runCatching { LocalDate.parse(day.date) }.getOrNull()
                d != null && d.isAfter(today) && !d.isAfter(horizonEnd) && day.lessons.isNotEmpty()
            }

            val showSubgroupBadge = selectedSubgroup.equals("ALL", ignoreCase = true) || selectedSubgroup.isBlank()

            // Main Glass Container
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.bg_widget_container))
                    .cornerRadius(ScheduleWidgetTheme.RadiusContainer)
                    .padding(horizontal = ScheduleWidgetTheme.PaddingHorizontal, vertical = ScheduleWidgetTheme.PaddingVertical)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // 1. Header (pinned at top)
                    WidgetHeader(headerStatus, headerDateStr, groupName, selectedSubgroup, isNarrow = isNarrow)
                    Spacer(modifier = GlanceModifier.height(8.dp))

                    if (!isVeryCompact) {
                        // 2. Continuous Scrollable Schedule List (Google Calendar style)
                        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            // 2.1 Hero Card at top of agenda
                            item {
                                WidgetHeroCard(heroInfo, selectedPeTeacher, selectedEnglishTeacher)
                                Spacer(modifier = GlanceModifier.height(10.dp))
                            }

                            // 2.2 Remaining today pairs
                            if (todayRemaining.isNotEmpty()) {
                                item {
                                    WidgetDayHeader("Сегодня", formatDayDate(today), formatPairCount(todayRemaining.size))
                                    Spacer(modifier = GlanceModifier.height(6.dp))
                                }

                                items(todayRemaining) { merged ->
                                    WidgetLessonCard(merged, selectedPeTeacher, selectedEnglishTeacher, showSubgroupBadge)
                                    Spacer(modifier = GlanceModifier.height(ScheduleWidgetTheme.SpacingCards))
                                }
                            }

                            // 2.3 Upcoming days
                            upcomingDays.forEach { day ->
                                val dayDate = runCatching { LocalDate.parse(day.date) }.getOrNull() ?: return@forEach
                                val dayMerged = consolidateLessons(day.lessons)

                                item {
                                    Spacer(modifier = GlanceModifier.height(10.dp))
                                    WidgetDayHeader(formatRelativeDayTitle(dayDate, today), formatDayDate(dayDate), formatPairCount(dayMerged.size))
                                    Spacer(modifier = GlanceModifier.height(6.dp))
                                }

                                items(dayMerged) { merged ->
                                    WidgetLessonCard(merged, selectedPeTeacher, selectedEnglishTeacher, showSubgroupBadge)
                                    Spacer(modifier = GlanceModifier.height(ScheduleWidgetTheme.SpacingCards))
                                }
                            }

                            item { Spacer(modifier = GlanceModifier.height(8.dp)) }
                        }
                    } else {
                        // In ultra-compact mode (height < 110dp), only show Hero Card
                        WidgetHeroCard(heroInfo, selectedPeTeacher, selectedEnglishTeacher)
                    }
                }
            }
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = ScheduleRepository(context)
        repo.loadSchedule(forceRefresh = true)
        ScheduleWidget().updateAll(context)
    }
}

typealias RefreshWidgetActionCallback = RefreshAction

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()
}
