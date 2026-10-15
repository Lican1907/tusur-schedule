package ru.lican1907.tusurschedule.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import ru.lican1907.tusurschedule.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String
)

class AppUpdateManager(private val context: Context) {

    private val checkClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val downloadClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun checkUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val candidateUrls = listOf(
            "https://github.com/Lican1907/tusur-schedule/releases/download/latest-apk/version.json",
            "https://lican1907.github.io/tusur-schedule/version.json"
        )

        for (url in candidateUrls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "TusurSchedule-Android-App/${BuildConfig.APP_VERSION_NAME}")
                    .header("Accept", "application/json")
                    .build()

                checkClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val remoteVersionCode = json.getInt("version_code")
                    val remoteVersionName = json.optString("version_name", "1.0.$remoteVersionCode")
                    val downloadUrl = json.optString(
                        "download_url",
                        "https://github.com/Lican1907/tusur-schedule/releases/download/latest-apk/tusur-schedule.apk"
                    )

                    if (remoteVersionCode > BuildConfig.APP_VERSION_CODE) {
                        return@withContext AppUpdateInfo(
                            versionCode = remoteVersionCode,
                            versionName = remoteVersionName,
                            downloadUrl = downloadUrl
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("AppUpdateManager", "Failed to check update from $url: ${e.message}")
            }
        }
        null
    }

    suspend fun downloadAndInstall(
        updateInfo: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            val apkFile = File(updatesDir, "tusur-schedule.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .header("User-Agent", "TusurSchedule-Android-App/${BuildConfig.APP_VERSION_NAME}")
                .build()

            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("Пустой ответ сервера"))
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L

                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (contentLength > 0) {
                                val progress = ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                                onProgress(progress)
                            }
                        }
                        output.flush()
                    }
                }

                if (contentLength > 0 && totalBytesRead < contentLength) {
                    apkFile.delete()
                    return@withContext Result.failure(Exception("Загрузка не завершена ($totalBytesRead из $contentLength байт)"))
                }
            }

            // Проверка целостности загруженного APK файла
            if (!apkFile.exists() || apkFile.length() < 100_000L) {
                apkFile.delete()
                return@withContext Result.failure(Exception("Файл обновления поврежден или пуст (${apkFile.length()} байт)"))
            }

            // Проверка сигнатуры ZIP/APK архива (первые байты PK 0x03 0x04)
            val isZipArchive = try {
                FileInputStream(apkFile).use { fis ->
                    val header = ByteArray(4)
                    val read = fis.read(header)
                    read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                            header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
                }
            } catch (_: Exception) {
                false
            }

            if (!isZipArchive) {
                apkFile.delete()
                return@withContext Result.failure(Exception("Некорректный формат пакета APK"))
            }

            onProgress(100)

            // Делаем файл доступным для чтения системному установщику
            apkFile.setReadable(true, false)

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            // Явное предоставление прав на чтение всем обработчикам Intent установки
            val resolveInfoList = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resolveInfoList) {
                val targetPackage = resolveInfo.activityInfo.packageName
                context.grantUriPermission(targetPackage, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(intent)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "Error downloading/installing update", e)
            Result.failure(e)
        }
    }
}
