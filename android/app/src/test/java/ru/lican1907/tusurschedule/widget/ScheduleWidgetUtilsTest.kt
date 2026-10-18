package ru.lican1907.tusurschedule.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lican1907.tusurschedule.data.DaySchedule
import ru.lican1907.tusurschedule.data.LessonItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleWidgetUtilsTest {

    @Test
    fun testFormatHeaderDate() {
        val date = LocalDate.of(2026, 10, 15) // Thursday
        assertEquals("Чт, 15 октября", formatHeaderDate(date))
    }

    @Test
    fun testFormatDaySubtitle() {
        val dateRelative = LocalDate.of(2026, 10, 15) // Thursday
        assertEquals("чт, 15 окт", formatDaySubtitle(dateRelative, isRelative = true))

        val dateNonRelative = LocalDate.of(2026, 10, 17) // Saturday
        assertEquals("17 окт", formatDaySubtitle(dateNonRelative, isRelative = false))
    }

    @Test
    fun testPluralizePairs() {
        assertEquals("пар", pluralizePairs(0))
        assertEquals("пара", pluralizePairs(1))
        assertEquals("пары", pluralizePairs(2))
        assertEquals("пары", pluralizePairs(4))
        assertEquals("пар", pluralizePairs(5))
        assertEquals("пар", pluralizePairs(11))
        assertEquals("пар", pluralizePairs(12))
        assertEquals("пар", pluralizePairs(14))
        assertEquals("пар", pluralizePairs(20))
        assertEquals("пара", pluralizePairs(21))
        assertEquals("пары", pluralizePairs(22))
        assertEquals("пар", pluralizePairs(25))
    }

    @Test
    fun testFormatPairCount() {
        assertEquals("1 пара", formatPairCount(1))
        assertEquals("3 пары", formatPairCount(3))
        assertEquals("5 пар", formatPairCount(5))
    }

    @Test
    fun testConsolidateLessons() {
        val l1 = LessonItem(time_start = "08:50", time_end = "10:20", subject = "Math", location = "323", subgroup = "1")
        val l2 = LessonItem(time_start = "08:50", time_end = "10:20", subject = "Math", location = "323", subgroup = "2")
        val l3 = LessonItem(time_start = "10:40", time_end = "12:10", subject = "Physics", location = "210")

        val merged = consolidateLessons(listOf(l1, l2, l3))
        assertEquals(2, merged.size)
        assertEquals(2, merged[0].subgroupCount)
        assertEquals("Math", merged[0].lesson.displaySubject)
        assertEquals(1, merged[1].subgroupCount)
        assertEquals("Physics", merged[1].lesson.displaySubject)
    }

    @Test
    fun testFormatRelativeDayTitle() {
        val today = LocalDate.of(2026, 10, 15) // Thursday
        assertEquals("Сегодня", formatRelativeDayTitle(today, today))
        assertEquals("Завтра", formatRelativeDayTitle(today.plusDays(1), today))
        val saturday = LocalDate.of(2026, 10, 17)
        assertEquals("Суббота", formatRelativeDayTitle(saturday, today))
        val tuesday = LocalDate.of(2026, 10, 20)
        assertEquals("Вторник", formatRelativeDayTitle(tuesday, today))
    }

    @Test
    fun testComputeHeaderStatus() {
        val heroLive = HeroInfo(lesson = LessonItem(subject = "Math"), isLive = true)
        val statusLive = computeHeaderStatus(heroLive, emptyList(), emptyList())
        assertEquals("Сейчас · идёт пара", statusLive)

        val heroUpcoming = HeroInfo(lesson = LessonItem(subject = "Physics"), isLive = false)
        val remaining1 = listOf(MergedLessonItem(LessonItem(subject = "Physics")))
        assertEquals("Сегодня · 1 пара", computeHeaderStatus(heroUpcoming, remaining1, remaining1))

        val remaining2 = listOf(
            MergedLessonItem(LessonItem(subject = "Physics")),
            MergedLessonItem(LessonItem(subject = "Math"))
        )
        assertEquals("Сегодня · 2 пары", computeHeaderStatus(heroUpcoming, remaining2, remaining2))

        val statusDone = computeHeaderStatus(HeroInfo(lesson = null, isRest = true), remaining1, emptyList())
        assertEquals("Сегодня · пар больше нет", statusDone)

        val statusEmpty = computeHeaderStatus(HeroInfo(lesson = null, isRest = true), emptyList(), emptyList())
        assertEquals("Сегодня · пар нет", statusEmpty)
    }

    @Test
    fun testComputeHeroInfoLive() {
        val today = LocalDate.of(2026, 10, 18)
        val l1 = LessonItem(time_start = "10:00", time_end = "11:30", subject = "Math")
        val merged = listOf(MergedLessonItem(l1))
        val nowTime = LocalTime.of(10, 30)
        val nowDateTime = LocalDateTime.of(today, nowTime)

        val hero = computeHeroInfo(
            today = today,
            todayLessons = merged,
            allDays = emptyList(),
            nowTime = nowTime,
            nowDateTime = nowDateTime,
            beerTime = "11:30"
        )

        assertTrue(hero.isLive)
        assertEquals("СЕЙЧАС · ИДЁТ", hero.tag)
        assertEquals("осталось 60 мин", hero.countdown)
        assertEquals("Math", hero.lesson?.displaySubject)
    }

    @Test
    fun testComputeHeroInfoUpcomingToday() {
        val today = LocalDate.of(2026, 10, 18)
        val l1 = LessonItem(time_start = "12:00", time_end = "13:30", subject = "English")
        val merged = listOf(MergedLessonItem(l1))
        val nowTime = LocalTime.of(10, 30)
        val nowDateTime = LocalDateTime.of(today, nowTime)

        val hero = computeHeroInfo(
            today = today,
            todayLessons = merged,
            allDays = emptyList(),
            nowTime = nowTime,
            nowDateTime = nowDateTime,
            beerTime = "13:30"
        )

        assertFalse(hero.isLive)
        assertEquals("СЛЕДУЮЩАЯ · СЕГОДНЯ", hero.tag)
        assertEquals("через 1 ч 30 мин", hero.countdown)
        assertEquals("English", hero.lesson?.displaySubject)
    }

    @Test
    fun testComputeHeroInfoUpcomingTomorrow() {
        val today = LocalDate.of(2026, 10, 15)
        val tomorrow = today.plusDays(1)
        val tomorrowLesson = LessonItem(time_start = "10:40", time_end = "12:10", subject = "History")
        val days = listOf(
            DaySchedule(date = tomorrow.toString(), weekday = 5, lessons = listOf(tomorrowLesson))
        )
        // Now is 12:00 today. Until tomorrow 10:40 is 22 hours 40 minutes.
        val nowTime = LocalTime.of(12, 0)
        val nowDateTime = LocalDateTime.of(today, nowTime)

        val hero = computeHeroInfo(
            today = today,
            todayLessons = emptyList(),
            allDays = days,
            nowTime = nowTime,
            nowDateTime = nowDateTime,
            beerTime = null
        )

        assertFalse(hero.isLive)
        assertEquals("СЛЕДУЮЩАЯ · ЗАВТРА", hero.tag)
        assertEquals("через 22 ч 40 мин", hero.countdown)
        assertEquals("History", hero.lesson?.displaySubject)
    }

    @Test
    fun testComputeHeroInfoUpcomingTomorrowMoreThanDay() {
        val sunday = LocalDate.of(2026, 10, 18)
        val monday = sunday.plusDays(1)
        val mondayLesson = LessonItem(time_start = "16:00", time_end = "17:30", subject = "Physics")
        val days = listOf(
            DaySchedule(date = monday.toString(), weekday = 1, lessons = listOf(mondayLesson))
        )
        // Sunday 12:00 to Monday 16:00 is 1 day 4 hours. Monday is tomorrow for Sunday.
        val nowTime = LocalTime.of(12, 0)
        val nowDateTime = LocalDateTime.of(sunday, nowTime)

        val hero = computeHeroInfo(
            today = sunday,
            todayLessons = emptyList(),
            allDays = days,
            nowTime = nowTime,
            nowDateTime = nowDateTime,
            beerTime = null
        )

        assertFalse(hero.isLive)
        assertEquals("СЛЕДУЮЩАЯ · ЗАВТРА", hero.tag)
        assertEquals("через 1 дн 4 ч", hero.countdown)
        assertEquals("Physics", hero.lesson?.displaySubject)
    }

    @Test
    fun testComputeHeroInfoUpcomingFutureDays() {
        val saturday = LocalDate.of(2026, 10, 17) // Saturday
        val monday = saturday.plusDays(2) // Monday
        val mondayLesson = LessonItem(time_start = "16:00", time_end = "17:30", subject = "Physics")
        val days = listOf(
            DaySchedule(date = monday.toString(), weekday = 1, lessons = listOf(mondayLesson))
        )
        // Saturday 12:00 to Monday 16:00 is 2 days 4 hours.
        val nowTime = LocalTime.of(12, 0)
        val nowDateTime = LocalDateTime.of(saturday, nowTime)

        val hero = computeHeroInfo(
            today = saturday,
            todayLessons = emptyList(),
            allDays = days,
            nowTime = nowTime,
            nowDateTime = nowDateTime,
            beerTime = null
        )

        assertFalse(hero.isLive)
        assertEquals("СЛЕДУЮЩАЯ · ПОНЕДЕЛЬНИК", hero.tag)
        assertEquals("через 2 дн 4 ч", hero.countdown)
        assertEquals("Physics", hero.lesson?.displaySubject)
    }

    @Test
    fun testComputeHeroInfoRestWithBeerTime() {
        val today = LocalDate.of(2026, 10, 18)
        val nowTime = LocalTime.of(18, 0)
        val nowDateTime = LocalDateTime.of(today, nowTime)

        val hero = computeHeroInfo(
            today = today,
            todayLessons = emptyList(),
            allDays = emptyList(),
            nowTime = nowTime,
            nowDateTime = nowDateTime,
            beerTime = "17:30"
        )

        assertTrue(hero.isRest)
        assertNull(hero.lesson)
        assertEquals("🍺 Можно пить пиво с 17:30!", hero.restSubtitle)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val test = ScheduleWidgetUtilsTest()
            test.testFormatHeaderDate()
            test.testFormatDaySubtitle()
            test.testPluralizePairs()
            test.testFormatPairCount()
            test.testConsolidateLessons()
            test.testFormatRelativeDayTitle()
            test.testComputeHeaderStatus()
            test.testComputeHeroInfoLive()
            test.testComputeHeroInfoUpcomingToday()
            test.testComputeHeroInfoUpcomingTomorrow()
            test.testComputeHeroInfoUpcomingTomorrowMoreThanDay()
            test.testComputeHeroInfoUpcomingFutureDays()
            test.testComputeHeroInfoRestWithBeerTime()
            println("ALL SCHEDULE WIDGET UTILS TESTS PASSED SUCCESSFULLY!")
        }
    }
}
