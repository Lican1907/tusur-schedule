package ru.lican1907.tusurschedule.data

import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleResponse(
    val generated_at: String = "",
    val group: String = "",
    val pe_teachers: List<String> = emptyList(),
    val english_teachers: List<String> = emptyList(),
    val subgroups: List<String> = emptyList(),
    val days: List<DaySchedule> = emptyList()
)

@Serializable
data class DaySchedule(
    val date: String = "",
    val weekday: Int? = null,
    val beer_time: String? = null,
    val lessons: List<LessonItem> = emptyList()
)

fun normalizeSubgroup(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw.lowercase()
        .replace("подгруппа", "")
        .replace("п/г", "")
        .replace("пг", "")
        .trim()
}

@Serializable
data class LessonItem(
    val uid: String = "",
    val date: String = "",
    val time_start: String = "",
    val time_end: String = "",
    val is_shifted_time: Boolean = false,
    val type: String = "ЛЕК",
    val subject: String? = null,
    val subject_short: String? = null,
    val subject_full: String? = null,
    val location: String = "",
    val teacher: String = "",
    val subgroup: String? = null,
    val color_hex: String = "#2EA043",
    val color_id: String = "10",
    val is_override: Boolean = false,
    val is_cancelled: Boolean = false,
    val override_comment: String? = null,
    val pe_rooms: Map<String, String>? = null,
    val english_rooms: Map<String, String>? = null
) {
    val displaySubject: String
        get() = subject?.ifEmpty { null }
            ?: subject_short?.ifEmpty { null }
            ?: subject_full?.ifEmpty { null }
            ?: "Занятие"

    fun matchesSubgroup(selectedSubgroup: String): Boolean {
        if (selectedSubgroup.isEmpty() || selectedSubgroup.equals("ALL", ignoreCase = true)) {
            return true
        }
        // Если у занятия нет подгруппы — оно общее для всех студентов группы
        if (subgroup.isNullOrBlank()) {
            return true
        }
        return normalizeSubgroup(subgroup) == normalizeSubgroup(selectedSubgroup)
    }

    fun isEnglishLesson(): Boolean {
        if (!english_rooms.isNullOrEmpty()) return true
        val text = listOfNotNull(subject, subject_short, subject_full, displaySubject, teacher)
            .joinToString(" ")
            .lowercase()
        return text.contains("английск") || text.contains("иностранн") || text.contains("english")
    }

    fun isPeLesson(): Boolean {
        if (!pe_rooms.isNullOrEmpty()) return true
        val text = listOfNotNull(subject, subject_short, subject_full, displaySubject, teacher)
            .joinToString(" ")
            .lowercase()
        return text.contains("физ-ра") || text.contains("физическ") || text.contains("физкульт") || text.contains("фкис")
    }

    private fun extractDayOfWeek(dateStr: String?): Int? {
        val candidate = when {
            !dateStr.isNullOrEmpty() -> dateStr
            date.isNotEmpty() -> date
            else -> {
                val match = Regex("""\b(20\d\d)(0\d|1[0-2])([0-2]\d|3[01])\b""").find(uid)
                if (match != null) {
                    val (y, m, d) = match.destructured
                    "$y-$m-$d"
                } else null
            }
        }
        return try {
            candidate?.let { LocalDate.parse(it).dayOfWeek.value }
        } catch (_: Exception) {
            null
        }
    }

    fun getResolvedLocation(
        selectedPeTeacher: String?,
        selectedEnglishTeacher: String? = null,
        dateStr: String? = null
    ): String {
        if (isPeLesson() && !pe_rooms.isNullOrEmpty()) {
            if (!selectedPeTeacher.isNullOrEmpty()) {
                val room = pe_rooms[selectedPeTeacher]
                    ?: pe_rooms.entries.firstOrNull {
                        it.key.replace('ё', 'е').equals(selectedPeTeacher.replace('ё', 'е'), ignoreCase = true)
                    }?.value
                if (!room.isNullOrEmpty()) {
                    return "Зал: $room ($selectedPeTeacher)"
                }
            }
            val firstEntry = pe_rooms.entries.firstOrNull()
            if (firstEntry != null) {
                return "Зал: ${firstEntry.value} (${firstEntry.key})"
            }
            return location
        }

        if (isEnglishLesson()) {
            val teacher = selectedEnglishTeacher?.takeIf { it.isNotEmpty() } ?: "Ёлкина Д.М."

            // 1. Dynamic english_rooms map if provided
            if (!english_rooms.isNullOrEmpty()) {
                val matched = english_rooms[teacher]
                    ?: english_rooms.entries.firstOrNull {
                        it.key.replace('ё', 'е').equals(teacher.replace('ё', 'е'), ignoreCase = true)
                    }?.value
                if (!matched.isNullOrEmpty()) {
                    return "$matched ($teacher)"
                }
            }

            // 2. Day-of-week schedule (Monday=1 -> РК 326, Saturday=6 -> РК 325 for Elkina)
            val dow = extractDayOfWeek(dateStr)
            if (dow != null) {
                val teacherSchedule = DEFAULT_ENGLISH_ROOMS_SCHEDULE[teacher]
                    ?: DEFAULT_ENGLISH_ROOMS_SCHEDULE.entries.firstOrNull {
                        it.key.replace('ё', 'е').equals(teacher.replace('ё', 'е'), ignoreCase = true)
                    }?.value

                val room = teacherSchedule?.get(dow)
                if (!room.isNullOrEmpty()) {
                    return "$room ($teacher)"
                }
            }

            // 3. Fallback: if location already present
            if (location.isNotEmpty()) {
                val cleanLoc = location.removePrefix("📍").trim()
                return "$cleanLoc ($teacher)"
            }
            return "РК ($teacher)"
        }

        return location
    }

    fun getResolvedTeacher(selectedEnglishTeacher: String?): String {
        if (isEnglishLesson() && !selectedEnglishTeacher.isNullOrEmpty()) {
            return selectedEnglishTeacher
        }
        return teacher
    }
}

val DEFAULT_ENGLISH_TEACHERS = listOf(
    "Ёлкина Д.М.",
    "Белкина Д.А.",
    "Горских М.Е.",
    "Менгардт Е.Р.",
    "Серебрякова О.А."
)

val DEFAULT_ENGLISH_ROOMS_SCHEDULE: Map<String, Map<Int, String>> = mapOf(
    "Ёлкина Д.М." to mapOf(
        1 to "РК 326", // Понедельник
        6 to "РК 325"  // Суббота
    ),
    "Елкина Д.М." to mapOf(
        1 to "РК 326",
        6 to "РК 325"
    )
)

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
