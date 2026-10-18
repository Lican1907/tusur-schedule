package ru.lican1907.tusurschedule.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.lican1907.tusurschedule.data.DaySchedule
import ru.lican1907.tusurschedule.data.ScheduleRepository
import ru.lican1907.tusurschedule.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DateStrip(
    days: List<DaySchedule>,
    selectedDateStr: String?,
    onDateSelected: (String) -> Unit
) {
    if (days.isEmpty()) return

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(days) { day ->
            val isSelected = day.date == selectedDateStr
            val isToday = day.date == ScheduleRepository.getTomskToday().toString()

            val parsedDate = try {
                LocalDate.parse(day.date)
            } catch (_: Exception) {
                null
            }

            val dayOfWeekStr = parsedDate?.format(DateTimeFormatter.ofPattern("EEE", Locale("ru")))?.uppercase() ?: ""
            val dayOfMonthStr = parsedDate?.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru"))) ?: day.date

            Surface(
                onClick = { onDateSelected(day.date) },
                shape = RoundedCornerShape(14.dp),
                color = when {
                    isSelected -> AccentPurple
                    isToday -> DarkSurfaceVariant
                    else -> DarkSurface
                },
                border = BorderStroke(
                    1.dp,
                    if (isSelected) Color(0x66FFFFFF) else if (isToday) AccentGreen else DarkBorderSubtle
                ),
                modifier = Modifier.width(78.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayOfWeekStr,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else if (isToday) AccentGreen else TextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dayOfMonthStr,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Color.White else TextPrimary
                    )
                    if (isToday) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(if (isSelected) Color.White else AccentGreen, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}
