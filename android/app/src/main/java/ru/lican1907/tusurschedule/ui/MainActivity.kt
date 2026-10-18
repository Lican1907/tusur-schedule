package ru.lican1907.tusurschedule.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import ru.lican1907.tusurschedule.BuildConfig
import ru.lican1907.tusurschedule.data.*
import ru.lican1907.tusurschedule.ui.theme.*
import ru.lican1907.tusurschedule.widget.ScheduleWidget
import ru.lican1907.tusurschedule.worker.ScheduleSyncWorker
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enqueue periodic background sync for the widget
        ScheduleSyncWorker.enqueuePeriodicSync(this)

        setContent {
            TusurScheduleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    ScheduleScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen() {
    val context = LocalContext.current
    val repository = remember { ScheduleRepository(context) }
    val updateManager = remember { AppUpdateManager(context) }
    val scope = rememberCoroutineScope()

    var scheduleResponse by remember { mutableStateOf<ScheduleResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedDateStr by remember { mutableStateOf<String?>(null) }
    var selectedGroup by remember { mutableStateOf(repository.getSelectedGroup()) }
    var selectedPeTeacher by remember { mutableStateOf(repository.selectedPeTeacher) }
    var selectedEnglishTeacher by remember { mutableStateOf(repository.selectedEnglishTeacher) }
    var selectedSubgroup by remember { mutableStateOf(repository.selectedSubgroup) }

    var groupDropdownExpanded by remember { mutableStateOf(false) }
    var peDropdownExpanded by remember { mutableStateOf(false) }
    var englishDropdownExpanded by remember { mutableStateOf(false) }
    var subgroupDropdownExpanded by remember { mutableStateOf(false) }

    // In-App update state
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var updateError by remember { mutableStateOf<String?>(null) }

    fun refreshSchedule(force: Boolean) {
        scope.launch {
            isLoading = true
            val result = repository.loadSchedule(forceRefresh = force)
            result.onSuccess { res ->
                scheduleResponse = res
                if (selectedDateStr == null || res.days.none { it.date == selectedDateStr }) {
                    val defaultDay = ScheduleRepository.findTodayOrNextDay(res.days)
                    selectedDateStr = defaultDay?.date ?: res.days.firstOrNull()?.date
                }
                ScheduleWidget().updateAll(context)
            }.onFailure { err ->
                Toast.makeText(context, err.message ?: "Ошибка загрузки", Toast.LENGTH_SHORT).show()
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedGroup) {
        refreshSchedule(force = false)
    }

    LaunchedEffect(Unit) {
        scope.launch {
            val updated = repository.syncIfNewer(selectedGroup.id)
            if (updated) {
                refreshSchedule(force = false)
            }
        }
        scope.launch {
            val update = updateManager.checkUpdate()
            if (update != null) {
                availableUpdate = update
            }
        }
    }

    val days = scheduleResponse?.days ?: emptyList()
    val currentDaySchedule = days.firstOrNull { it.date == selectedDateStr }
    val peTeachers = scheduleResponse?.pe_teachers ?: emptyList()
    val englishTeachers = scheduleResponse?.english_teachers?.takeIf { it.isNotEmpty() } ?: DEFAULT_ENGLISH_TEACHERS
    val rawSubgroups = scheduleResponse?.subgroups?.filter { it.isNotBlank() } ?: emptyList()
    val availableSubgroups = if (rawSubgroups.isNotEmpty()) rawSubgroups else listOf("1", "2")

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .border(BorderStroke(1.dp, DarkBorder))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Top Row: Group Selector + Subgroup Selector + Refresh Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Group Selector Dropdown
                        Box {
                            Surface(
                                onClick = { groupDropdownExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                color = DarkSurfaceVariant,
                                border = BorderStroke(1.dp, DarkBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.School,
                                        contentDescription = null,
                                        tint = AccentBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = selectedGroup.name,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = groupDropdownExpanded,
                                onDismissRequest = { groupDropdownExpanded = false },
                                modifier = Modifier.background(DarkSurface)
                            ) {
                                SUPPORTED_GROUPS.forEach { grp ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = grp.name,
                                                color = if (grp.id == selectedGroup.id) AccentBlue else TextPrimary
                                            )
                                        },
                                        onClick = {
                                            selectedGroup = grp
                                            repository.selectedGroupId = grp.id
                                            groupDropdownExpanded = false
                                            refreshSchedule(force = true)
                                        }
                                    )
                                }
                            }
                        }

                        // Subgroup Selector Dropdown
                        Box {
                            Surface(
                                onClick = { subgroupDropdownExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                color = DarkSurfaceVariant,
                                border = BorderStroke(1.dp, DarkBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Groups,
                                        contentDescription = null,
                                        tint = AccentPurple,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val chipText = if (selectedSubgroup.equals("ALL", ignoreCase = true) || selectedSubgroup.isEmpty()) {
                                        "Все п/г"
                                    } else {
                                        val norm = normalizeSubgroup(selectedSubgroup).ifEmpty { selectedSubgroup }
                                        "п/г $norm"
                                    }
                                    Text(
                                        text = chipText,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = subgroupDropdownExpanded,
                                onDismissRequest = { subgroupDropdownExpanded = false },
                                modifier = Modifier.background(DarkSurface)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Все подгруппы",
                                            color = if (selectedSubgroup.equals("ALL", ignoreCase = true) || selectedSubgroup.isEmpty()) AccentPurple else TextPrimary
                                        )
                                    },
                                    onClick = {
                                        selectedSubgroup = "ALL"
                                        repository.selectedSubgroup = "ALL"
                                        subgroupDropdownExpanded = false
                                        scope.launch { ScheduleWidget().updateAll(context) }
                                    }
                                )
                                availableSubgroups.forEach { sub ->
                                    val norm = normalizeSubgroup(sub).ifEmpty { sub }
                                    val isChosen = normalizeSubgroup(selectedSubgroup) == norm
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Подгруппа $norm",
                                                color = if (isChosen) AccentPurple else TextPrimary
                                            )
                                        },
                                        onClick = {
                                            selectedSubgroup = sub
                                            repository.selectedSubgroup = sub
                                            subgroupDropdownExpanded = false
                                            scope.launch { ScheduleWidget().updateAll(context) }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Refresh Button
                    IconButton(
                        onClick = { refreshSchedule(force = true) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                            .border(BorderStroke(1.dp, DarkBorder), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Обновить",
                            tint = AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Row: PE Teacher Selector & English Teacher Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // PE Teacher Selector Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            onClick = { peDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceVariant,
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = selectedPeTeacher,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = peDropdownExpanded,
                            onDismissRequest = { peDropdownExpanded = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            val teachersList = if (peTeachers.isNotEmpty()) peTeachers else listOf(selectedPeTeacher)
                            teachersList.forEach { teacherName ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = teacherName,
                                            color = if (teacherName == selectedPeTeacher) AccentGreen else TextPrimary
                                        )
                                    },
                                    onClick = {
                                        selectedPeTeacher = teacherName
                                        repository.selectedPeTeacher = teacherName
                                        peDropdownExpanded = false
                                        scope.launch { ScheduleWidget().updateAll(context) }
                                    }
                                )
                            }
                        }
                    }

                    // English Teacher Selector Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            onClick = { englishDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceVariant,
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = selectedEnglishTeacher,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = englishDropdownExpanded,
                            onDismissRequest = { englishDropdownExpanded = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            val teachersList = if (englishTeachers.isNotEmpty()) englishTeachers else listOf(selectedEnglishTeacher)
                            teachersList.forEach { teacherName ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = teacherName,
                                            color = if (teacherName == selectedEnglishTeacher) AccentBlue else TextPrimary
                                        )
                                    },
                                    onClick = {
                                        selectedEnglishTeacher = teacherName
                                        repository.selectedEnglishTeacher = teacherName
                                        englishDropdownExpanded = false
                                        scope.launch { ScheduleWidget().updateAll(context) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Horizontal Dates Row
            if (days.isNotEmpty()) {
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
                            onClick = { selectedDateStr = day.date },
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

            val filteredLessons = currentDaySchedule?.lessons?.filter { it.matchesSubgroup(selectedSubgroup) } ?: emptyList()
            val effectiveBeerTime = filteredLessons.filter { !it.is_cancelled }.mapNotNull {
                try { LocalTime.parse(it.time_end) } catch (_: Exception) { null }
            }.maxOrNull()?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: currentDaySchedule?.beer_time
            val displayDaySchedule = currentDaySchedule?.copy(lessons = filteredLessons, beer_time = effectiveBeerTime)

            // Live Day Status Banner (Active Lesson Timer / Beer Banner)
            if (displayDaySchedule != null) {
                LiveDayBanner(day = displayDaySchedule)
            }

            // Lessons List
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentPurple)
                }
            } else if (displayDaySchedule == null || displayDaySchedule.lessons.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = CardEmptyBg,
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🎉",
                                fontSize = 32.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "В этот день пар нет\nМожно отдыхать!",
                                color = Color(0xFFE0E0E0),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 22.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(displayDaySchedule.lessons) { lesson ->
                        LessonCard(
                            lesson = lesson,
                            selectedPeTeacher = selectedPeTeacher,
                            selectedEnglishTeacher = selectedEnglishTeacher,
                            dateStr = selectedDateStr,
                            showSubgroupBadge = selectedSubgroup.equals("ALL", ignoreCase = true)
                        )
                    }
                }
            }
        }
    }

    if (availableUpdate != null) {
        val update = availableUpdate!!
        AlertDialog(
            onDismissRequest = {
                if (!isDownloadingUpdate) {
                    availableUpdate = null
                }
            },
            title = {
                Text(
                    text = "Доступно обновление приложения!",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    if (isDownloadingUpdate) {
                        Text(
                            text = "Скачивание обновления: $downloadProgress%",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentBlue,
                            trackColor = DarkSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Установлена версия ${BuildConfig.APP_VERSION_NAME}, доступна ${update.versionName}. Обновить сейчас?",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        if (updateError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = updateError!!,
                                color = Color(0xFFF85149),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!isDownloadingUpdate) {
                    Button(
                        onClick = {
                            isDownloadingUpdate = true
                            updateError = null
                            scope.launch {
                                val res = updateManager.downloadAndInstall(update) { progress ->
                                    downloadProgress = progress
                                }
                                if (res.isSuccess) {
                                    availableUpdate = null
                                    isDownloadingUpdate = false
                                } else {
                                    isDownloadingUpdate = false
                                    updateError = res.exceptionOrNull()?.message ?: "Ошибка скачивания"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Text("Обновить", color = Color.White)
                    }
                }
            },
            dismissButton = {
                if (!isDownloadingUpdate) {
                    TextButton(onClick = { availableUpdate = null }) {
                        Text("Позже", color = TextSecondary)
                    }
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun LiveDayBanner(day: DaySchedule) {
    val isToday = day.date == ScheduleRepository.getTomskToday().toString()
    if (!isToday) return

    val now = LocalTime.now(ScheduleRepository.TOMSK_ZONE_ID)

    // Check active lesson
    var activeLesson: LessonItem? = null
    var minutesLeft: Long = 0
    var lessonProgress = 0f

    for (l in day.lessons) {
        if (l.is_cancelled) continue
        try {
            val start = LocalTime.parse(l.time_start)
            val end = LocalTime.parse(l.time_end)
            if (!now.isBefore(start) && now.isBefore(end)) {
                activeLesson = l
                val totalMinutes = ChronoUnit.MINUTES.between(start, end).toFloat()
                val elapsedMinutes = ChronoUnit.MINUTES.between(start, now).toFloat()
                lessonProgress = (elapsedMinutes / totalMinutes).coerceIn(0f, 1f)
                minutesLeft = ChronoUnit.MINUTES.between(now, end)
                break
            }
        } catch (_: Exception) {}
    }

    if (activeLesson != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, Color(0x335F508A))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔴 ИДЁТ ПАРА: ${activeLesson.displaySubject}",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Осталось: $minutesLeft мин",
                        color = AccentPurple,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { lessonProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = AccentPurple,
                    trackColor = DarkSurfaceVariant
                )
            }
        }
    } else if (!day.beer_time.isNullOrEmpty()) {
        val beerTimeParsed = try {
            LocalTime.parse(day.beer_time)
        } catch (_: Exception) {
            null
        }

        if (beerTimeParsed != null && !now.isBefore(beerTimeParsed)) {
            // All lessons over! Beer time!
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0x26B88226),
                border = BorderStroke(1.dp, Color(0x66B88226))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🍺",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Пить пиво дозволено! 🎉",
                            color = AccentAmber,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Все пары сегодняшнего дня позади (с ${day.beer_time})",
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

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

private fun getLessonBrushAndBorder(lesson: LessonItem): Pair<Brush, Color> {
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

private fun parseColor(hex: String): Color {
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
