package ru.lican1907.tusurschedule.data

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleResponse(
    val generated_at: String = "",
    val group: String = "",
    val pe_teachers: List<String> = emptyList(),
    val days: List<DaySchedule> = emptyList()
)

@Serializable
data class DaySchedule(
    val date: String = "",
    val weekday: Int? = null,
    val beer_time: String? = null,
    val lessons: List<LessonItem> = emptyList()
)

@Serializable
data class LessonItem(
    val uid: String = "",
    val time_start: String = "",
    val time_end: String = "",
    val is_shifted_time: Boolean = false,
    val type: String = "ЛЕК",
    val subject: String? = null,
    val subject_short: String? = null,
    val subject_full: String? = null,
    val location: String = "",
    val teacher: String = "",
    val color_hex: String = "#2EA043",
    val color_id: String = "10",
    val is_override: Boolean = false,
    val is_cancelled: Boolean = false,
    val override_comment: String? = null,
    val pe_rooms: Map<String, String>? = null
) {
    val displaySubject: String
        get() = subject?.ifEmpty { null }
            ?: subject_short?.ifEmpty { null }
            ?: subject_full?.ifEmpty { null }
            ?: "Занятие"

    fun getResolvedLocation(selectedPeTeacher: String?): String {
        if (!pe_rooms.isNullOrEmpty()) {
            if (!selectedPeTeacher.isNullOrEmpty() && pe_rooms.containsKey(selectedPeTeacher)) {
                val room = pe_rooms[selectedPeTeacher] ?: ""
                return if (room.isNotEmpty()) "Зал: $room ($selectedPeTeacher)" else "см. описание"
            }
            val firstEntry = pe_rooms.entries.firstOrNull()
            if (firstEntry != null) {
                return "Зал: ${firstEntry.value} (${firstEntry.key})"
            }
        }
        return location
    }
}

data class GroupItem(
    val id: String,
    val name: String,
    val fileName: String
)

val SUPPORTED_GROUPS = listOf(
    GroupItem("426top-4", "426топ-4", "schedule-426top-4.json"),
    GroupItem("426top-1", "426топ-1", "schedule-426top-1.json"),
    GroupItem("426top-2", "426топ-2", "schedule-426top-2.json"),
    GroupItem("426-3", "426-3", "schedule-426-3.json")
)
