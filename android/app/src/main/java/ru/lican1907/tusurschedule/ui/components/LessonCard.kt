package ru.lican1907.tusurschedule.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.lican1907.tusurschedule.data.LessonItem
import ru.lican1907.tusurschedule.data.normalizeSubgroup
import ru.lican1907.tusurschedule.ui.theme.*

@Composable
fun LessonCard(
    lesson: LessonItem,
    selectedPeTeacher: String?,
    selectedEnglishTeacher: String? = null,
    dateStr: String? = null,
    showSubgroupBadge: Boolean = false
) {
    val (cardBrush, cardBorderColor) = getLessonBrushAndBorder(lesson)
    val resolvedAud = lesson.getResolvedLocation(selectedPeTeacher, selectedEnglishTeacher, dateStr)
    val resolvedTeacher = lesson.getResolvedTeacher(selectedEnglishTeacher)

    val typeTag = when {
        lesson.is_cancelled -> "ОТМЕНА"
        lesson.type.uppercase().contains("ЛЕК") -> "ЛЕК"
        lesson.type.uppercase().contains("ПРАК") -> "ПРАК"
        lesson.type.uppercase().contains("ЛАБ") -> "ЛАБ"
        else -> lesson.type
    }

    val shortSubject = lesson.subject_short?.ifEmpty { null }
        ?: lesson.subject?.ifEmpty { null }
        ?: lesson.displaySubject

    val line1 = if (lesson.is_cancelled) {
        "ОТМЕНА • $shortSubject"
    } else if (lesson.is_override) {
        "⚡ $typeTag • $shortSubject"
    } else {
        "$typeTag • $shortSubject"
    }

    val timeStr = if (lesson.time_start.isNotEmpty() && lesson.time_end.isNotEmpty()) {
        "${lesson.time_start}–${lesson.time_end}"
    } else {
        lesson.time_start.ifEmpty { "–" }
    }

    val line2 = buildString {
        append(timeStr)
        if (resolvedAud.isNotEmpty()) {
            append(", 📍 ")
            append(resolvedAud)
        }
        if (resolvedTeacher.isNotEmpty() && !resolvedAud.contains(resolvedTeacher)) {
            append(" • ")
            append(resolvedTeacher)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBrush)
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Line 1: [ТИП] • [Предмет]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = line1,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (lesson.is_cancelled) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!lesson.subgroup.isNullOrBlank() && showSubgroupBadge) {
                            val subDisplay = normalizeSubgroup(lesson.subgroup).ifEmpty { lesson.subgroup!! }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0x3358A6FF),
                                border = BorderStroke(1.dp, Color(0x6658A6FF))
                            ) {
                                Text(
                                    text = "п/г $subDisplay",
                                    color = AccentBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (lesson.is_shifted_time) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0x33D29922)
                            ) {
                                Text(
                                    text = "⚠️ Сдвиг 12:30",
                                    color = AccentAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Line 2: [Время], 📍 [Аудитория]
                Text(
                    text = line2,
                    color = Color(0xE6FFFFFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1
                )

                // Override / Cancellation Banner
                if (!lesson.override_comment.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (lesson.is_cancelled) Color(0x33484F58) else Color(0x33D29922),
                        border = BorderStroke(1.dp, if (lesson.is_cancelled) Color(0x4DFFFFFF) else Color(0x66FFD566))
                    ) {
                        Text(
                            text = if (lesson.is_cancelled) "❌ ${lesson.override_comment}" else "⚡ ${lesson.override_comment}",
                            color = if (lesson.is_cancelled) Color(0xFFE6EDF3) else AccentAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

fun getLessonBrushAndBorder(lesson: LessonItem): Pair<Brush, Color> {
    if (lesson.is_cancelled) {
        return Pair(
            Brush.verticalGradient(listOf(CardCancelledTop, CardCancelledBottom)),
            if (lesson.is_override) AccentAmber else DarkBorder
        )
    }
    val upperType = lesson.type.uppercase()
    val brush = when {
        upperType.contains("ЛЕК") -> Brush.verticalGradient(listOf(CardLekTop, CardLekBottom))
        upperType.contains("ПРАК") -> Brush.verticalGradient(listOf(CardPrakTop, CardPrakBottom))
        upperType.contains("ЛАБ") -> Brush.verticalGradient(listOf(CardLabTop, CardLabBottom))
        else -> Brush.verticalGradient(listOf(CardPrakTop, CardPrakBottom))
    }
    val borderColor = if (lesson.is_override) AccentAmber else Color(0x33FFFFFF)
    return Pair(brush, borderColor)
}

fun parseColor(hex: String): Color {
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = clean.toLong(16)
        if (clean.length == 6) {
            Color(colorInt or 0x00000000FF000000)
        } else {
            Color(colorInt)
        }
    } catch (_: Exception) {
        AccentPurple
    }
}
