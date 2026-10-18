package ru.lican1907.tusurschedule.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.lican1907.tusurschedule.R

object ScheduleWidgetTheme {
    // Container & Surfaces
    val BgWidgetDark = Color(0xBD12141A)      // rgba(18,20,26,0.74)
    val BgWidgetLight = Color(0xC7FFFFFF)     // rgba(255,255,255,0.78)
    val BorderWidgetDark = Color(0x14FFFFFF)  // rgba(255,255,255,0.08)
    val BorderWidgetLight = Color(0x14000000) // rgba(0,0,0,0.08)

    val CardBg = Color(0x0FFFFFFF)            // rgba(255,255,255,0.06)
    val CardBgLight = Color(0x0F000000)       // rgba(0,0,0,0.06)
    val CardBorder = Color(0x0DFFFFFF)        // rgba(255,255,255,0.05)

    // Typography & Text Colors
    val TextPrimary = Color(0xFFFFFFFF)
    val TextPrimaryLight = Color(0xFF14161A)
    val TextSecondary = Color(0xCCFFFFFF)     // 80%
    val TextMuted = Color(0x99FFFFFF)         // 60%
    val TextSubtle = Color(0x66FFFFFF)        // 40%

    // Lesson Type Colors
    val ColorLecture = Color(0xFF34D399)      // Зелёный (Лекция)
    val ColorPractice = Color(0xFFA78BFA)     // Фиолетовый (Практика)
    val ColorLab = Color(0xFFFBBF24)          // Янтарный (Лабораторная)
    val ColorCancelled = Color(0xFF9CA3AF)    // Серый (Отмена)

    // Dimensions
    val RadiusContainer = 24.dp
    val RadiusHero = 20.dp
    val RadiusCard = 14.dp
    val RadiusChip = 10.dp
    val RadiusBadge = 6.dp
    val ButtonRefreshSize = 34.dp

    val PaddingHorizontal = 12.dp
    val PaddingVertical = 12.dp
    val SpacingCards = 6.dp
    val SpacingDays = 14.dp
    val WidthTimeColumn = 44.dp
    val WidthStripe = 3.dp

    // Font Sizes
    val FontHeaderStatus = 12.sp
    val FontHeaderDate = 18.sp
    val FontHeroTag = 11.sp
    val FontHeroTitle = 18.sp
    val FontHeroDetails = 12.sp
    val FontDayTitle = 15.sp
    val FontDayCount = 12.sp
    val FontTimeStart = 15.sp
    val FontTimeEnd = 12.sp
    val FontSubject = 15.sp
    val FontDetails = 13.sp
    val FontBadge = 11.sp

    fun getLessonTypeColor(type: String, isCancelled: Boolean = false): Color {
        if (isCancelled) return ColorCancelled
        val upper = type.uppercase()
        return when {
            upper.contains("ЛЕК") -> ColorLecture
            upper.contains("ПРАК") -> ColorPractice
            upper.contains("ЛАБ") -> ColorLab
            else -> ColorPractice
        }
    }

    fun getLessonTypeFullName(type: String, isCancelled: Boolean = false): String {
        if (isCancelled) return "Отменено"
        val upper = type.uppercase()
        return when {
            upper.contains("ЛЕК") -> "Лекция"
            upper.contains("ПРАК") -> "Практика"
            upper.contains("ЛАБ") -> "Лабораторная"
            else -> type
        }
    }

    fun getHeroDrawableRes(type: String, isCancelled: Boolean = false): Int {
        if (isCancelled) return R.drawable.bg_hero_cancelled
        val upper = type.uppercase()
        return when {
            upper.contains("ЛЕК") -> R.drawable.bg_hero_lek
            upper.contains("ПРАК") -> R.drawable.bg_hero_prak
            upper.contains("ЛАБ") -> R.drawable.bg_hero_lab
            else -> R.drawable.bg_hero_prak
        }
    }
}
