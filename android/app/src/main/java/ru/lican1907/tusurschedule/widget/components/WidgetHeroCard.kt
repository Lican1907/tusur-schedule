package ru.lican1907.tusurschedule.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ru.lican1907.tusurschedule.R
import ru.lican1907.tusurschedule.ui.MainActivity
import ru.lican1907.tusurschedule.widget.HeroInfo
import ru.lican1907.tusurschedule.widget.ScheduleWidgetTheme

@Composable
fun WidgetHeroCard(
    hero: HeroInfo,
    selectedPeTeacher: String,
    selectedEnglishTeacher: String = ""
) {
    if (hero.isRest || hero.lesson == null) {
        // Rest state card
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ImageProvider(R.drawable.bg_widget_empty_day))
                .cornerRadius(ScheduleWidgetTheme.RadiusHero)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Column {
                Text(
                    text = hero.restTitle,
                    style = TextStyle(
                        color = ColorProvider(ScheduleWidgetTheme.TextPrimary),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = hero.restSubtitle,
                    style = TextStyle(
                        color = ColorProvider(ScheduleWidgetTheme.TextMuted),
                        fontSize = 13.sp
                    )
                )
            }
        }
        return
    }

    val lesson = hero.lesson
    val bgDrawable = ScheduleWidgetTheme.getHeroDrawableRes(lesson.type, lesson.is_cancelled)
    val resolvedLoc = lesson.getResolvedLocation(selectedPeTeacher, selectedEnglishTeacher)
    val typeFullName = ScheduleWidgetTheme.getLessonTypeFullName(lesson.type, lesson.is_cancelled)

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(bgDrawable))
            .cornerRadius(ScheduleWidgetTheme.RadiusHero)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            // Top Line: Tag & Countdown
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = hero.tag,
                    style = TextStyle(
                        color = ColorProvider(Color(0xE6FFFFFF)),
                        fontSize = ScheduleWidgetTheme.FontHeroTag,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                if (hero.countdown.isNotEmpty()) {
                    Text(
                        text = hero.countdown,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = ScheduleWidgetTheme.FontHeroTag,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Line 2: Subject Title
            Text(
                text = lesson.displaySubject,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = ScheduleWidgetTheme.FontHeroTitle,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (lesson.is_cancelled) TextDecoration.LineThrough else TextDecoration.None
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Line 3: Details (Time • Location • Type)
            val details = buildString {
                append("${lesson.time_start}–${lesson.time_end}")
                if (resolvedLoc.isNotEmpty()) {
                    append("  •  📍 $resolvedLoc")
                }
                append("  •  $typeFullName")
            }

            Text(
                text = details,
                style = TextStyle(
                    color = ColorProvider(Color(0xF2FFFFFF)),
                    fontSize = ScheduleWidgetTheme.FontHeroDetails,
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 1
            )

            // Optional Progress Bar if Live
            if (hero.isLive && hero.progress > 0f) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(ColorProvider(Color(0x33FFFFFF)))
                        .cornerRadius(2.dp)
                ) {
                    val progressWeight = hero.progress.coerceIn(0.05f, 1f)
                    Row(modifier = GlanceModifier.fillMaxSize()) {
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight()
                                .background(ColorProvider(Color.White))
                                .cornerRadius(2.dp)
                        ) {}
                        if (progressWeight < 0.95f) {
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }
                    }
                }
            }
        }
    }
}
