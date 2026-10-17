package ru.lican1907.tusurschedule.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import ru.lican1907.tusurschedule.R
import ru.lican1907.tusurschedule.data.DaySchedule
import ru.lican1907.tusurschedule.data.LessonItem
import ru.lican1907.tusurschedule.data.ScheduleRepository
import ru.lican1907.tusurschedule.ui.MainActivity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MergedLessonItem(
    val lesson: LessonItem,
    val subgroupCount: Int = 1
)

data class HeroInfo(
    val lesson: LessonItem?,
    val isLive: Boolean = false,
    val isRest: Boolean = false,
    val tag: String = "",
    val countdown: String = "",
    val progress: Float = 0f,
    val restTitle: String = "🎉 На сегодня всё!",
    val restSubtitle: String = "Можно отдыхать"
)

class ScheduleWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = ScheduleRepository(context)
        val schedule = repo.loadSchedule(forceRefresh = false).getOrNull()
        val selectedPeTeacher = repo.selectedPeTeacher
        val selectedEnglishTeacher = repo.selectedEnglishTeacher
        val groupName = repo.getSelectedGroup().name

        provideContent {
            val size = LocalSize.current
            val isCompact = size.height < 220.dp

            val today = ScheduleRepository.getTomskToday()
            val nowTime = LocalTime.now(ScheduleRepository.TOMSK_ZONE_ID)
            val nowDateTime = LocalDateTime.now(ScheduleRepository.TOMSK_ZONE_ID)

            val allDays = schedule?.days ?: emptyList()
            val todayDay = allDays.firstOrNull { it.date == today.toString() }

            // Consolidated today lessons
            val todayRawLessons = todayDay?.lessons ?: emptyList()
            val todayMerged = consolidateLessons(todayRawLessons)

            // Remaining lessons today
            val todayRemaining = todayMerged.filter { item ->
                try {
                    val end = LocalTime.parse(item.lesson.time_end)
                    nowTime.isBefore(end)
                } catch (_: Exception) {
                    true
                }
            }

            // Hero lesson detection
            val heroInfo = computeHeroInfo(
                today = today,
                todayLessons = todayMerged,
                allDays = allDays,
                nowTime = nowTime,
                nowDateTime = nowDateTime,
                beerTime = todayDay?.beer_time
            )

            // Header status string
            val headerStatus = computeHeaderStatus(
                heroInfo = heroInfo,
                todayLessons = todayMerged,
                todayRemaining = todayRemaining
            )

            val headerDateStr = formatHeaderDate(today)

            // Upcoming days (2-week window)
            val horizonEnd = today.plusDays(14)
            val upcomingDays = allDays.filter { day ->
                try {
                    val d = LocalDate.parse(day.date)
                    d.isAfter(today) && !d.isAfter(horizonEnd) && day.lessons.isNotEmpty()
                } catch (_: Exception) {
                    false
                }
            }

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
                    WidgetHeader(
                        statusText = headerStatus,
                        dateText = headerDateStr,
                        groupName = groupName
                    )

                    Spacer(modifier = GlanceModifier.height(10.dp))

                    // 2. Hero Card
                    WidgetHeroCard(
                        hero = heroInfo,
                        selectedPeTeacher = selectedPeTeacher,
                        selectedEnglishTeacher = selectedEnglishTeacher
                    )

                    // 3. Scrollable Schedule List (in full mode 4x4+)
                    if (!isCompact) {
                        Spacer(modifier = GlanceModifier.height(12.dp))

                        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            // Remaining today pairs
                            if (todayRemaining.isNotEmpty()) {
                                item {
                                    WidgetDayHeader(
                                        title = "Сегодня",
                                        dateStr = formatDayDate(today),
                                        countText = formatPairCount(todayRemaining.size)
                                    )
                                    Spacer(modifier = GlanceModifier.height(6.dp))
                                }

                                items(todayRemaining) { merged ->
                                    WidgetLessonCard(
                                        merged = merged,
                                        selectedPeTeacher = selectedPeTeacher,
                                        selectedEnglishTeacher = selectedEnglishTeacher
                                    )
                                    Spacer(modifier = GlanceModifier.height(ScheduleWidgetTheme.SpacingCards))
                                }
                            }

                            // Upcoming days
                            upcomingDays.forEach { day ->
                                val dayDate = LocalDate.parse(day.date)
                                val dayMerged = consolidateLessons(day.lessons)

                                item {
                                    Spacer(modifier = GlanceModifier.height(10.dp))
                                    WidgetDayHeader(
                                        title = formatRelativeDayTitle(dayDate, today),
                                        dateStr = formatDayDate(dayDate),
                                        countText = formatPairCount(dayMerged.size)
                                    )
                                    Spacer(modifier = GlanceModifier.height(6.dp))
                                }

                                items(dayMerged) { merged ->
                                    WidgetLessonCard(
                                        merged = merged,
                                        selectedPeTeacher = selectedPeTeacher,
                                        selectedEnglishTeacher = selectedEnglishTeacher
                                    )
                                    Spacer(modifier = GlanceModifier.height(ScheduleWidgetTheme.SpacingCards))
                                }
                            }

                            item {
                                Spacer(modifier = GlanceModifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun WidgetHeader(
        statusText: String,
        dateText: String,
        groupName: String
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = statusText,
                    style = TextStyle(
                        color = ColorProvider(ScheduleWidgetTheme.TextMuted),
                        fontSize = ScheduleWidgetTheme.FontHeaderStatus,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1
                )
                Text(
                    text = dateText,
                    style = TextStyle(
                        color = ColorProvider(ScheduleWidgetTheme.TextPrimary),
                        fontSize = ScheduleWidgetTheme.FontHeaderDate,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }

            // Group chip
            Box(
                modifier = GlanceModifier
                    .background(ImageProvider(R.drawable.bg_chip_group))
                    .cornerRadius(ScheduleWidgetTheme.RadiusChip)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = groupName,
                    style = TextStyle(
                        color = ColorProvider(ScheduleWidgetTheme.TextSecondary),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Refresh 34dp circular button
            Box(
                modifier = GlanceModifier
                    .width(ScheduleWidgetTheme.ButtonRefreshSize)
                    .height(ScheduleWidgetTheme.ButtonRefreshSize)
                    .background(ImageProvider(R.drawable.bg_btn_refresh_34))
                    .cornerRadius(17.dp)
                    .clickable(actionRunCallback<RefreshAction>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↻",
                    style = TextStyle(
                        color = ColorProvider(ScheduleWidgetTheme.TextPrimary),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun WidgetHeroCard(
        hero: HeroInfo,
        selectedPeTeacher: String,
        selectedEnglishTeacher: String = ""
    ) {
        if (hero.isRest || hero.lesson == null) {
            // Rest state card
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ImageProvider(R.drawable.bg_widget_empty_day))
                    .cornerRadius(ScheduleWidgetTheme.RadiusHero)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                Column {
                    Text(
                        text = hero.restTitle,
                        style = TextStyle(
                            color = ColorProvider(ScheduleWidgetTheme.TextPrimary),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = hero.restSubtitle,
                        style = TextStyle(
                            color = ColorProvider(ScheduleWidgetTheme.TextMuted),
                            fontSize = 13.sp
                        )
                    )
                }
            }
            return
        }

        val lesson = hero.lesson
        val bgDrawable = ScheduleWidgetTheme.getHeroDrawableRes(lesson.type, lesson.is_cancelled)
        val resolvedLoc = lesson.getResolvedLocation(selectedPeTeacher, selectedEnglishTeacher)
        val typeFullName = ScheduleWidgetTheme.getLessonTypeFullName(lesson.type, lesson.is_cancelled)

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ImageProvider(bgDrawable))
                .cornerRadius(ScheduleWidgetTheme.RadiusHero)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                // Top Line: Tag & Countdown
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = hero.tag,
                        style = TextStyle(
                            color = ColorProvider(Color(0xE6FFFFFF)),
                            fontSize = ScheduleWidgetTheme.FontHeroTag,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )

                    if (hero.countdown.isNotEmpty()) {
                        Text(
                            text = hero.countdown,
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = ScheduleWidgetTheme.FontHeroTag,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                // Line 2: Subject Title
                Text(
                    text = lesson.displaySubject,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = ScheduleWidgetTheme.FontHeroTitle,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (lesson.is_cancelled) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    maxLines = 1
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                // Line 3: Details (Time • Location • Type)
                val details = buildString {
                    append("${lesson.time_start}–${lesson.time_end}")
                    if (resolvedLoc.isNotEmpty()) {
                        append("  •  📍 $resolvedLoc")
                    }
                    append("  •  $typeFullName")
                }

                Text(
                    text = details,
                    style = TextStyle(
                        color = ColorProvider(Color(0xF2FFFFFF)),
                        fontSize = ScheduleWidgetTheme.FontHeroDetails,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1
                )

                // Optional Progress Bar if Live
                if (hero.isLive && hero.progress > 0f) {
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(ColorProvider(Color(0x33FFFFFF)))
                            .cornerRadius(2.dp)
                    ) {
                        val progressWeight = hero.progress.coerceIn(0.05f, 1f)
                        Row(modifier = GlanceModifier.fillMaxSize()) {
                            Box(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .fillMaxHeight()
                                    .background(ColorProvider(Color.White))
                                    .cornerRadius(2.dp)
                            ) {}
                            if (progressWeight < 0.95f) {
                                Spacer(modifier = GlanceModifier.defaultWeight())
                            }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun WidgetDayHeader(
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

    @androidx.compose.runtime.Composable
    private fun WidgetLessonCard(
        merged: MergedLessonItem,
        selectedPeTeacher: String,
        selectedEnglishTeacher: String = ""
    ) {
        val lesson = merged.lesson
        val typeColor = ScheduleWidgetTheme.getLessonTypeColor(lesson.type, lesson.is_cancelled)
        val typeFullName = ScheduleWidgetTheme.getLessonTypeFullName(lesson.type, lesson.is_cancelled)
        val resolvedLoc = lesson.getResolvedLocation(selectedPeTeacher, selectedEnglishTeacher)

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ImageProvider(R.drawable.bg_widget_card))
                .cornerRadius(ScheduleWidgetTheme.RadiusCard)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Col 1: Time Column (~48dp)
                Column(
                    modifier = GlanceModifier.width(ScheduleWidgetTheme.WidthTimeColumn),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = lesson.time_start,
                        style = TextStyle(
                            color = ColorProvider(ScheduleWidgetTheme.TextPrimary),
                            fontSize = ScheduleWidgetTheme.FontTimeStart,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = lesson.time_end,
                        style = TextStyle(
                            color = ColorProvider(ScheduleWidgetTheme.TextMuted),
                            fontSize = ScheduleWidgetTheme.FontTimeEnd
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                // Col 2: Vertical Stripe Indicator
                Box(
                    modifier = GlanceModifier
                        .width(ScheduleWidgetTheme.WidthStripe)
                        .height(34.dp)
                        .background(ColorProvider(typeColor))
                        .cornerRadius(2.dp)
                ) {}

                Spacer(modifier = GlanceModifier.width(10.dp))

                // Col 3: Details Column
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lesson.displaySubject,
                            style = TextStyle(
                                color = ColorProvider(
                                    if (lesson.is_cancelled) ScheduleWidgetTheme.ColorCancelled
                                    else ScheduleWidgetTheme.TextPrimary
                                ),
                                fontSize = ScheduleWidgetTheme.FontSubject,
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (lesson.is_cancelled) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight()
                        )

                        // Subgroup badge if merged
                        if (merged.subgroupCount > 1) {
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Box(
                                modifier = GlanceModifier
                                    .background(ImageProvider(R.drawable.bg_subgroup_badge))
                                    .cornerRadius(ScheduleWidgetTheme.RadiusBadge)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${merged.subgroupCount} подгруппы",
                                    style = TextStyle(
                                        color = ColorProvider(ScheduleWidgetTheme.ColorPractice),
                                        fontSize = ScheduleWidgetTheme.FontBadge,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(2.dp))

                    val detailsStr = buildString {
                        append(typeFullName)
                        if (resolvedLoc.isNotEmpty()) {
                            append("  •  📍 $resolvedLoc")
                        }
                    }

                    Text(
                        text = detailsStr,
                        style = TextStyle(
                            color = ColorProvider(ScheduleWidgetTheme.TextSecondary),
                            fontSize = ScheduleWidgetTheme.FontDetails
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }

    // Helper functions
    private fun consolidateLessons(lessons: List<LessonItem>): List<MergedLessonItem> {
        val result = mutableListOf<MergedLessonItem>()
        val visited = BooleanArray(lessons.size)

        for (i in lessons.indices) {
            if (visited[i]) continue
            val base = lessons[i]
            var count = 1
            for (j in i + 1 until lessons.size) {
                if (!visited[j]) {
                    val other = lessons[j]
                    if (base.time_start == other.time_start &&
                        base.time_end == other.time_end &&
                        base.displaySubject == other.displaySubject &&
                        base.location == other.location
                    ) {
                        visited[j] = true
                        count++
                    }
                }
            }
            visited[i] = true
            result.add(MergedLessonItem(base, count))
        }
        return result
    }

    private fun computeHeroInfo(
        today: LocalDate,
        todayLessons: List<MergedLessonItem>,
        allDays: List<DaySchedule>,
        nowTime: LocalTime,
        nowDateTime: LocalDateTime,
        beerTime: String?
    ): HeroInfo {
        // 1. Check if lesson is currently live
        for (item in todayLessons) {
            val les = item.lesson
            if (les.is_cancelled) continue
            try {
                val start = LocalTime.parse(les.time_start)
                val end = LocalTime.parse(les.time_end)
                if (!nowTime.isBefore(start) && nowTime.isBefore(end)) {
                    val totalSecs = Duration.between(start, end).seconds.toFloat()
                    val passedSecs = Duration.between(start, nowTime).seconds.toFloat()
                    val leftMinutes = Duration.between(nowTime, end).toMinutes()
                    val progress = (passedSecs / totalSecs).coerceIn(0f, 1f)
                    return HeroInfo(
                        lesson = les,
                        isLive = true,
                        tag = "СЕЙЧАС ИДЁТ",
                        countdown = "ОСТАЛОСЬ ${leftMinutes} МИН",
                        progress = progress
                    )
                }
            } catch (_: Exception) {}
        }

        // 2. Check next upcoming lesson today
        for (item in todayLessons) {
            val les = item.lesson
            if (les.is_cancelled) continue
            try {
                val start = LocalTime.parse(les.time_start)
                if (nowTime.isBefore(start)) {
                    val untilMinutes = Duration.between(nowTime, start).toMinutes()
                    val countdownStr = if (untilMinutes >= 60) {
                        "ЧЕРЕЗ ${untilMinutes / 60} Ч ${untilMinutes % 60} МИН"
                    } else {
                        "ЧЕРЕЗ $untilMinutes МИН"
                    }
                    return HeroInfo(
                        lesson = les,
                        isLive = false,
                        tag = "СЛЕДУЮЩАЯ",
                        countdown = countdownStr
                    )
                }
            } catch (_: Exception) {}
        }

        // 3. Search next lesson on upcoming days
        val upcomingDays = allDays.filter {
            try {
                LocalDate.parse(it.date).isAfter(today) && it.lessons.isNotEmpty()
            } catch (_: Exception) { false }
        }.sortedBy { it.date }

        for (day in upcomingDays) {
            val targetDate = LocalDate.parse(day.date)
            for (les in day.lessons) {
                if (les.is_cancelled) continue
                try {
                    val start = LocalTime.parse(les.time_start)
                    val lessonDateTime = LocalDateTime.of(targetDate, start)
                    val tag = if (targetDate == today.plusDays(1)) "ЗАВТРА" else formatWeekday(targetDate).uppercase()
                    val daysUntil = Duration.between(nowDateTime, lessonDateTime).toDays()
                    val hoursUntil = Duration.between(nowDateTime, lessonDateTime).toHours() % 24
                    val countdownStr = if (daysUntil > 0) {
                        "ЧЕРЕЗ $daysUntil ДН. $hoursUntil Ч."
                    } else {
                        "ЧЕРЕЗ $hoursUntil Ч."
                    }
                    return HeroInfo(
                        lesson = les,
                        isLive = false,
                        tag = tag,
                        countdown = countdownStr
                    )
                } catch (_: Exception) {}
            }
        }

        // Rest state
        val restSub = if (!beerTime.isNullOrEmpty()) "🍺 Можно пить пиво с $beerTime!" else "Пар на ближайшее время нет"
        return HeroInfo(lesson = null, isRest = true, restTitle = "🎉 На сегодня всё!", restSubtitle = restSub)
    }

    private fun computeHeaderStatus(
        heroInfo: HeroInfo,
        todayLessons: List<MergedLessonItem>,
        todayRemaining: List<MergedLessonItem>
    ): String {
        return when {
            heroInfo.isLive && heroInfo.lesson != null -> "Сейчас идёт: ${heroInfo.lesson.displaySubject}"
            todayRemaining.isNotEmpty() -> "${todayRemaining.size} ${pluralizePairs(todayRemaining.size)} сегодня"
            todayLessons.isNotEmpty() -> "Сегодня · пар больше нет"
            else -> "Сегодня · пар нет"
        }
    }

    private fun formatHeaderDate(date: LocalDate): String {
        val dow = date.format(DateTimeFormatter.ofPattern("EEE", Locale("ru"))).replaceFirstChar { it.uppercase() }
        val d = date.dayOfMonth
        val m = date.format(DateTimeFormatter.ofPattern("MMMM", Locale("ru")))
        return "$dow, $d $m"
    }

    private fun formatDayDate(date: LocalDate): String {
        val d = date.dayOfMonth
        val m = date.format(DateTimeFormatter.ofPattern("MMM", Locale("ru"))).removeSuffix(".")
        return "$d $m."
    }

    private fun formatRelativeDayTitle(date: LocalDate, today: LocalDate): String {
        return when (date) {
            today -> "Сегодня"
            today.plusDays(1) -> "Завтра"
            else -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale("ru"))).replaceFirstChar { it.uppercase() }
        }
    }

    private fun formatWeekday(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("EEEE", Locale("ru"))).replaceFirstChar { it.uppercase() }
    }

    private fun formatPairCount(count: Int): String {
        return "$count ${pluralizePairs(count)}"
    }

    private fun pluralizePairs(count: Int): String {
        val mod10 = count % 10
        val mod100 = count % 100
        return when {
            mod100 in 11..14 -> "пар"
            mod10 == 1 -> "пара"
            mod10 in 2..4 -> "пары"
            else -> "пар"
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
