package ru.lican1907.tusurschedule.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.lican1907.tusurschedule.BuildConfig
import ru.lican1907.tusurschedule.data.AppUpdateInfo
import ru.lican1907.tusurschedule.ui.theme.*

@Composable
fun UpdateDialog(
    availableUpdate: AppUpdateInfo?,
    isDownloading: Boolean,
    downloadProgress: Int,
    updateError: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (availableUpdate == null) return

    val update = availableUpdate
    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) {
                onDismiss()
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
                if (isDownloading) {
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
                            text = updateError,
                            color = Color(0xFFF85149),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Обновить", color = Color.White)
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("Позже", color = TextSecondary)
                }
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
