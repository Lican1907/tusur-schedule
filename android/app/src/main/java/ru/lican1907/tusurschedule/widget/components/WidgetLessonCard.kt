package ru.lican1907.tusurschedule.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ru.lican1907.tusurschedule.R
import ru.lican1907.tusurschedule.data.normalizeSubgroup
import ru.lican1907.tusurschedule.ui.MainActivity
import ru.lican1907.tusurschedule.widget.MergedLessonItem
import ru.lican1907.tusurschedule.widget.ScheduleWidgetTheme

@Composable
fun WidgetLessonCard(
    item: MergedLessonItem,
    selectedPeTeacher: String,
    selectedEnglishTeacher: String = "",
    showSubgroupBadge: Boolean = false
) {
    val lesson = item.lesson
    val typeColor = ScheduleWidgetTheme.getLessonTypeColor(lesson.type, lesson.is_cancelled)
    val typeFullName = ScheduleWidgetTheme.getLessonTypeFullName(lesson.type, lesson.is_cancelled)
    val resolvedLoc = lesson.getResolvedLocation(selectedPeTeacher, selectedEnglishTeacher)

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.bg_widget_card))
            .cornerRadius(ScheduleWidgetTheme.RadiusCard)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Col 1: Time Column (~48dp)
            Column(
                modifier = GlanceModifier.width(ScheduleWidgetTheme.WidthTimeColumn),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = lesson.time_start,
                    style = TextStyle(
                        color = ColorProvider(ScheduleWidgetTheme.TextPrimary),
                        fontSize = ScheduleWidgetTheme.FontTimeStart,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = lesson.time_end,
                    style = TextStyle(
                        color = ColorProvider(ScheduleWidgetTheme.TextMuted),
                        fontSize = ScheduleWidgetTheme.FontTimeEnd
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Col 2: Vertical Stripe Indicator
            Box(
                modifier = GlanceModifier
                    .width(ScheduleWidgetTheme.WidthStripe)
                    .height(34.dp)
                    .background(ColorProvider(typeColor))
                    .cornerRadius(2.dp)
            ) {}

            Spacer(modifier = GlanceModifier.width(10.dp))

            // Col 3: Details Column
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lesson.displaySubject,
                        style = TextStyle(
                            color = ColorProvider(
                                if (lesson.is_cancelled) ScheduleWidgetTheme.ColorCancelled
                                else ScheduleWidgetTheme.TextPrimary
                            ),
                            fontSize = ScheduleWidgetTheme.FontSubject,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (lesson.is_cancelled) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                    )

                    // Subgroup badge if merged
                    if (item.subgroupCount > 1) {
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Box(
                            modifier = GlanceModifier
                                .background(ImageProvider(R.drawable.bg_subgroup_badge))
                                .cornerRadius(ScheduleWidgetTheme.RadiusBadge)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${item.subgroupCount} подгруппы",
                                style = TextStyle(
                                    color = ColorProvider(ScheduleWidgetTheme.ColorPractice),
                                    fontSize = ScheduleWidgetTheme.FontBadge,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    } else if (!lesson.subgroup.isNullOrBlank() && showSubgroupBadge) {
                        val subDisplay = normalizeSubgroup(lesson.subgroup).ifEmpty { lesson.subgroup!! }
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Box(
                            modifier = GlanceModifier
                                .background(ImageProvider(R.drawable.bg_subgroup_badge))
                                .cornerRadius(ScheduleWidgetTheme.RadiusBadge)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "п/г $subDisplay",
                                style = TextStyle(
                                    color = ColorProvider(ScheduleWidgetTheme.ColorPractice),
                                    fontSize = ScheduleWidgetTheme.FontBadge,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(2.dp))

                val detailsStr = buildString {
                    append(typeFullName)
                    if (resolvedLoc.isNotEmpty()) {
                        append("  •  📍 $resolvedLoc")
                    }
                }

                Text(
                    text = detailsStr,
                    style = TextStyle(
                        color = ColorProvider(ScheduleWidgetTheme.TextSecondary),
                        fontSize = ScheduleWidgetTheme.FontDetails
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun WidgetLessonCard(
    item: MergedLessonItem,
    selectedPeTeacher: String,
    selectedEnglishTeacher: String = "",
    selectedSubgroup: String
) = WidgetLessonCard(
    item = item,
    selectedPeTeacher = selectedPeTeacher,
    selectedEnglishTeacher = selectedEnglishTeacher,
    showSubgroupBadge = selectedSubgroup.equals("ALL", ignoreCase = true) || selectedSubgroup.isBlank()
)
