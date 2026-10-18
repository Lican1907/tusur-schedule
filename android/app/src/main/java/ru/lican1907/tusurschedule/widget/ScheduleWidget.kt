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
            val isCompact = size.height < 220.dp

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
                    // 1. Header
                    WidgetHeader(headerStatus, headerDateStr, groupName, selectedSubgroup)
                    Spacer(modifier = GlanceModifier.height(10.dp))

                    // 2. Hero Card
                    WidgetHeroCard(heroInfo, selectedPeTeacher, selectedEnglishTeacher)

                    // 3. Scrollable Schedule List (in full mode 4x4+)
                    if (!isCompact) {
                        Spacer(modifier = GlanceModifier.height(12.dp))

                        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            // Remaining today pairs
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

                            // Upcoming days
                            upcomingDays.forEach { day ->
                                val dayDate = LocalDate.parse(day.date)
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
