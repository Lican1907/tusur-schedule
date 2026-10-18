package ru.lican1907.tusurschedule.widget

import ru.lican1907.tusurschedule.data.LessonItem

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
