package ru.lican1907.tusurschedule.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.lican1907.tusurschedule.data.GroupItem
import ru.lican1907.tusurschedule.data.SUPPORTED_GROUPS
import ru.lican1907.tusurschedule.data.normalizeSubgroup
import ru.lican1907.tusurschedule.ui.theme.*

@Composable
fun ScheduleTopBar(
    selectedGroup: GroupItem,
    onGroupSelected: (GroupItem) -> Unit,
    selectedSubgroup: String,
    onSubgroupSelected: (String) -> Unit,
    availableSubgroups: List<String>,
    selectedPeTeacher: String,
    onPeTeacherSelected: (String) -> Unit,
    peTeachers: List<String>,
    selectedEnglishTeacher: String,
    onEnglishTeacherSelected: (String) -> Unit,
    englishTeachers: List<String>,
    onRefreshClick: () -> Unit
) {
    var groupDropdownExpanded by remember { mutableStateOf(false) }
    var subgroupDropdownExpanded by remember { mutableStateOf(false) }
    var peDropdownExpanded by remember { mutableStateOf(false) }
    var englishDropdownExpanded by remember { mutableStateOf(false) }

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
                                    groupDropdownExpanded = false
                                    onGroupSelected(grp)
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
                                subgroupDropdownExpanded = false
                                onSubgroupSelected("ALL")
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
                                    subgroupDropdownExpanded = false
                                    onSubgroupSelected(sub)
                                }
                            )
                        }
                    }
                }
            }

            // Refresh Button
            IconButton(
                onClick = onRefreshClick,
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
                                peDropdownExpanded = false
                                onPeTeacherSelected(teacherName)
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
                                englishDropdownExpanded = false
                                onEnglishTeacherSelected(teacherName)
                            }
                        )
                    }
                }
            }
        }
    }
}
