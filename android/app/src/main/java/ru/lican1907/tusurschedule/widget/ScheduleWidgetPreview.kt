package ru.lican1907.tusurschedule.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.lican1907.tusurschedule.data.DaySchedule
import ru.lican1907.tusurschedule.data.LessonItem

object ScheduleWidgetPreviewData {
    val sampleDays = listOf(
        DaySchedule(
            date = "2026-10-15",
            weekday = 4,
            lessons = emptyList()
        ),
        DaySchedule(
            date = "2026-10-16",
            weekday = 5,
            lessons = listOf(
                LessonItem(
                    time_start = "10:40",
                    time_end = "12:10",
                    type = "ПРАК",
                    subject = "История",
                    location = "ГК 323"
                ),
                LessonItem(
                    time_start = "13:15",
                    time_end = "14:45",
                    type = "ПРАК",
                    subject = "Математика",
                    location = "ГК 323"
                )
            )
        ),
        DaySchedule(
            date = "2026-10-17",
            weekday = 6,
            lessons = listOf(
                LessonItem(
                    time_start = "08:50",
                    time_end = "10:20",
                    type = "ПРАК",
                    subject = "Английский",
                    location = "РК 119"
                ),
                LessonItem(
                    time_start = "08:50",
                    time_end = "10:20",
                    type = "ПРАК",
                    subject = "Английский",
                    location = "РК 119"
                ),
                LessonItem(
                    time_start = "10:40",
                    time_end = "12:10",
                    type = "ЛЕК",
                    subject = "Информатика",
                    location = "ФЭТ 412"
                )
            )
        ),
        DaySchedule(
            date = "2026-10-19",
            weekday = 1,
            lessons = listOf(
                LessonItem(
                    time_start = "08:50",
                    time_end = "10:20",
                    type = "ЛАБ",
                    subject = "Физика",
                    location = "ГК 210"
                )
            )
        )
    )
}

@Preview(name = "Widget 4x2 Compact", widthDp = 340, heightDp = 180)
@Composable
fun PreviewWidget4x2() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xBD12141A))
            .padding(16.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Сегодня · пар нет",
                        color = Color(0x99FFFFFF),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Чт, 15 октября",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("426топ-4", color = Color(0xCCFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x1FFFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("↻", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hero Card (Завтра История)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF6D5BD0))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "ЗАВТРА",
                            color = Color(0xE6FFFFFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "ЧЕРЕЗ 1 ДН. 1 Ч.",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "История",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "10:40–12:10  •  📍 ГК 323  •  Практика",
                        color = Color(0xF2FFFFFF),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Preview(name = "Widget 4x4 Full", widthDp = 340, heightDp = 380)
@Composable
fun PreviewWidget4x4() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xBD12141A))
            .padding(12.dp)
    ) {
        Column {
            PreviewWidget4x2()
        }
    }
}

@Preview(name = "Widget 2x4 Vertical", widthDp = 170, heightDp = 340)
@Composable
fun PreviewWidget2x4() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xBD12141A))
            .padding(12.dp)
    ) {
        Column {
            PreviewWidget4x2()
        }
    }
}
