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
                    tag = "СЕЙЧАС · ИДЁТ",
                    countdown = "осталось $leftMinutes мин",
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
                    val h = untilMinutes / 60
                    val m = untilMinutes % 60
                    if (m > 0) "через $h ч $m мин" else "через $h ч"
                } else {
                    "через $untilMinutes мин"
                }
                return HeroInfo(
                    lesson = les,
                    isLive = false,
                    tag = "СЛЕДУЮЩАЯ · СЕГОДНЯ",
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
                val tag = if (targetDate == today.plusDays(1)) {
                    "СЛЕДУЮЩАЯ · ЗАВТРА"
                } else {
                    "СЛЕДУЮЩАЯ · ${formatWeekday(targetDate).uppercase()}"
                }
                val totalMinutes = Duration.between(nowDateTime, lessonDateTime).toMinutes().coerceAtLeast(0)
                val daysUntil = totalMinutes / (24 * 60)
                val remainingMinutes = totalMinutes % (24 * 60)
                val hoursUntil = remainingMinutes / 60
                val minsUntil = remainingMinutes % 60
                val countdownStr = when {
                    daysUntil > 0 -> if (hoursUntil > 0) "через $daysUntil дн $hoursUntil ч" else "через $daysUntil дн"
                    hoursUntil > 0 && minsUntil > 0 -> "через $hoursUntil ч $minsUntil мин"
                    hoursUntil > 0 -> "через $hoursUntil ч"
                    else -> "через $minsUntil мин"
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
        heroInfo.isLive && heroInfo.lesson != null -> "Сейчас · идёт пара"
        todayRemaining.isNotEmpty() -> "Сегодня · ${todayRemaining.size} ${pluralizePairs(todayRemaining.size)}"
        todayLessons.isNotEmpty() -> "Сегодня · пар больше нет"
        else -> "Сегодня · пар нет"
    }
}

private val RU_LOCALE: Locale = Locale.forLanguageTag("ru")

fun formatHeaderDate(date: LocalDate): String {
    val dow = date.format(DateTimeFormatter.ofPattern("EEE", RU_LOCALE)).replaceFirstChar { it.uppercase() }
    val d = date.dayOfMonth
    val m = date.format(DateTimeFormatter.ofPattern("MMMM", RU_LOCALE))
    return "$dow, $d $m"
}

fun formatDaySubtitle(date: LocalDate, isRelative: Boolean): String {
    val d = date.dayOfMonth
    val m = date.format(DateTimeFormatter.ofPattern("MMM", RU_LOCALE)).lowercase().removeSuffix(".")
    return if (isRelative) {
        val dow = date.format(DateTimeFormatter.ofPattern("EEE", RU_LOCALE)).lowercase().removeSuffix(".")
        "$dow, $d $m"
    } else {
        "$d $m"
    }
}

fun formatDayDate(date: LocalDate): String {
    val d = date.dayOfMonth
    val m = date.format(DateTimeFormatter.ofPattern("MMM", RU_LOCALE)).lowercase().removeSuffix(".")
    return "$d $m"
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
