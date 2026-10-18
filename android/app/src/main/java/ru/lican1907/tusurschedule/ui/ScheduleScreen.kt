package ru.lican1907.tusurschedule.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import ru.lican1907.tusurschedule.data.*
import ru.lican1907.tusurschedule.ui.components.*
import ru.lican1907.tusurschedule.ui.theme.*
import ru.lican1907.tusurschedule.widget.ScheduleWidget
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
            ScheduleTopBar(
                selectedGroup = selectedGroup,
                onGroupSelected = { grp ->
                    selectedGroup = grp
                    repository.selectedGroupId = grp.id
                    refreshSchedule(force = true)
                },
                selectedSubgroup = selectedSubgroup,
                onSubgroupSelected = { sub ->
                    selectedSubgroup = sub
                    repository.selectedSubgroup = sub
                    scope.launch { ScheduleWidget().updateAll(context) }
                },
                availableSubgroups = availableSubgroups,
                selectedPeTeacher = selectedPeTeacher,
                onPeTeacherSelected = { teacher ->
                    selectedPeTeacher = teacher
                    repository.selectedPeTeacher = teacher
                    scope.launch { ScheduleWidget().updateAll(context) }
                },
                peTeachers = peTeachers,
                selectedEnglishTeacher = selectedEnglishTeacher,
                onEnglishTeacherSelected = { teacher ->
                    selectedEnglishTeacher = teacher
                    repository.selectedEnglishTeacher = teacher
                    scope.launch { ScheduleWidget().updateAll(context) }
                },
                englishTeachers = englishTeachers,
                onRefreshClick = { refreshSchedule(force = true) }
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Horizontal Dates Row
            DateStrip(
                days = days,
                selectedDateStr = selectedDateStr,
                onDateSelected = { selectedDateStr = it }
            )

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

    UpdateDialog(
        availableUpdate = availableUpdate,
        isDownloading = isDownloadingUpdate,
        downloadProgress = downloadProgress,
        updateError = updateError,
        onDismiss = { availableUpdate = null },
        onConfirm = {
            val update = availableUpdate ?: return@UpdateDialog
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
        }
    )
}
