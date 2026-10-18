package ru.lican1907.tusurschedule.widget

import ru.lican1907.tusurschedule.data.DaySchedule
import ru.lican1907.tusurschedule.data.LessonItem
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun consolidateLessons(lessons: List<LessonItem>): List<MergedLessonItem> {
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

fun computeHeroInfo(
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

fun computeHeaderStatus(
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

private val RU_LOCALE: Locale = Locale.forLanguageTag("ru")

fun formatHeaderDate(date: LocalDate): String {
    val dow = date.format(DateTimeFormatter.ofPattern("EEE", RU_LOCALE)).replaceFirstChar { it.uppercase() }
    val d = date.dayOfMonth
    val m = date.format(DateTimeFormatter.ofPattern("MMM", RU_LOCALE)).removeSuffix(".")
    return "$dow, $d $m"
}

fun formatDayDate(date: LocalDate): String {
    val d = date.dayOfMonth
    val m = date.format(DateTimeFormatter.ofPattern("MMM", RU_LOCALE)).removeSuffix(".")
    return "$d $m."
}

fun formatRelativeDayTitle(date: LocalDate, today: LocalDate): String {
    return when (date) {
        today -> "Сегодня"
        today.plusDays(1) -> "Завтра"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE", RU_LOCALE)).replaceFirstChar { it.uppercase() }
    }
}

fun formatWeekday(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("EEEE", RU_LOCALE)).replaceFirstChar { it.uppercase() }
}

fun formatPairCount(count: Int): String {
    return "$count ${pluralizePairs(count)}"
}

fun pluralizePairs(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod100 in 11..14 -> "пар"
        mod10 == 1 -> "пара"
        mod10 in 2..4 -> "пары"
        else -> "пар"
    }
}
