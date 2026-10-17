package ru.lican1907.tusurschedule.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ScheduleRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tusur_schedule_prefs", Context.MODE_PRIVATE)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    var selectedGroupId: String
        get() = prefs.getString("selected_group_id", "426top-4") ?: "426top-4"
        set(value) = prefs.edit().putString("selected_group_id", value).apply()

    var selectedPeTeacher: String
        get() = prefs.getString("selected_pe_teacher", "Тарасов А.В.") ?: "Тарасов А.В."
        set(value) = prefs.edit().putString("selected_pe_teacher", value).apply()

    var selectedEnglishTeacher: String
        get() = prefs.getString("selected_english_teacher", "Ёлкина Д.М.") ?: "Ёлкина Д.М."
        set(value) = prefs.edit().putString("selected_english_teacher", value).apply()

    fun getSelectedGroup(): GroupItem {
        val currentId = selectedGroupId
        return SUPPORTED_GROUPS.firstOrNull { it.id == currentId } ?: SUPPORTED_GROUPS.first()
    }

    suspend fun loadSchedule(forceRefresh: Boolean = false): Result<ScheduleResponse> =
        withContext(Dispatchers.IO) {
            val group = getSelectedGroup()
            val cacheFile = File(context.filesDir, "schedule_${group.id}.json")

            // If not forcing refresh, try to read cache first
            if (!forceRefresh && cacheFile.exists()) {
                try {
                    val cachedContent = cacheFile.readText(Charsets.UTF_8)
                    val cachedResponse = json.decodeFromString<ScheduleResponse>(cachedContent)
                    if (cachedResponse.days.isNotEmpty()) {
                        return@withContext Result.success(cachedResponse)
                    }
                } catch (_: Exception) {
                    // Fallthrough to network fetch
                }
            }

            // Fetch from network
            val baseUrl = "https://lican1907.github.io/tusur-schedule/"
            val targetUrl = "$baseUrl${group.fileName}"

            var responseJson: String? = null
            try {
                responseJson = fetchUrl(targetUrl)
            } catch (e: Exception) {
                // If 426top-4 failed, try fallback schedule.json
                if (group.id == "426top-4") {
                    try {
                        responseJson = fetchUrl("${baseUrl}schedule.json")
                    } catch (_: Exception) {
                        // ignore and fall through to cache
                    }
                }
            }

            if (!responseJson.isNullOrEmpty()) {
                try {
                    val parsed = json.decodeFromString<ScheduleResponse>(responseJson)
                    // Save to cache
                    cacheFile.writeText(responseJson, Charsets.UTF_8)
                    if (parsed.generated_at.isNotEmpty()) {
                        prefs.edit().putString("last_generated_at_${group.id}", parsed.generated_at).apply()
                    }
                    return@withContext Result.success(parsed)
                } catch (e: Exception) {
                    // JSON parsing failed, try cache below
                }
            }

            // If network failed, try offline cache as final fallback
            if (cacheFile.exists()) {
                try {
                    val cachedContent = cacheFile.readText(Charsets.UTF_8)
                    val cachedResponse = json.decodeFromString<ScheduleResponse>(cachedContent)
                    return@withContext Result.success(cachedResponse)
                } catch (e: Exception) {
                    return@withContext Result.failure(e)
                }
            }

            Result.failure(Exception("Не удалось загрузить расписание. Проверьте интернет-соединение."))
        }

    suspend fun syncIfNewer(groupSlug: String? = null): Boolean = withContext(Dispatchers.IO) {
        val targetGroupId = groupSlug ?: selectedGroupId
        val group = SUPPORTED_GROUPS.firstOrNull { it.id == targetGroupId } ?: getSelectedGroup()
        val cacheFile = File(context.filesDir, "schedule_${group.id}.json")

        var localGeneratedAt = prefs.getString("last_generated_at_${group.id}", null)
        if (localGeneratedAt.isNullOrEmpty() && cacheFile.exists()) {
            try {
                val cached = json.decodeFromString<ScheduleResponse>(cacheFile.readText(Charsets.UTF_8))
                localGeneratedAt = cached.generated_at
                if (!localGeneratedAt.isNullOrEmpty()) {
                    prefs.edit().putString("last_generated_at_${group.id}", localGeneratedAt).apply()
                }
            } catch (_: Exception) {
                // ignore
            }
        }

        val baseUrl = "https://lican1907.github.io/tusur-schedule/"
        val targetUrl = "$baseUrl${group.fileName}"

        var responseJson: String? = null
        try {
            responseJson = fetchUrl(targetUrl)
        } catch (e: Exception) {
            if (group.id == "426top-4") {
                try {
                    responseJson = fetchUrl("${baseUrl}schedule.json")
                } catch (_: Exception) {
                    // ignore
                }
            }
        }

        if (responseJson.isNullOrEmpty()) {
            return@withContext false
        }

        try {
            val remoteResponse = json.decodeFromString<ScheduleResponse>(responseJson)
            val remoteGeneratedAt = remoteResponse.generated_at

            val isNewer = when {
                localGeneratedAt.isNullOrEmpty() -> true
                remoteGeneratedAt.isEmpty() -> false
                else -> {
                    try {
                        val remoteTime = java.time.OffsetDateTime.parse(remoteGeneratedAt)
                        val localTime = java.time.OffsetDateTime.parse(localGeneratedAt)
                        remoteTime.isAfter(localTime)
                    } catch (_: Exception) {
                        remoteGeneratedAt > localGeneratedAt
                    }
                }
            }

            if (isNewer) {
                cacheFile.writeText(responseJson, Charsets.UTF_8)
                if (remoteGeneratedAt.isNotEmpty()) {
                    prefs.edit().putString("last_generated_at_${group.id}", remoteGeneratedAt).apply()
                }
                return@withContext true
            }
        } catch (_: Exception) {
            return@withContext false
        }

        false
    }

    private fun fetchUrl(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "TusurSchedule-Android-App/1.0")
            .header("Accept", "application/json")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP Error: ${response.code}")
            }
            return response.body?.string() ?: throw Exception("Empty response body")
        }
    }

    companion object {
        val TOMSK_ZONE_ID: ZoneId = ZoneId.of("Asia/Tomsk")

        fun getTomskToday(): LocalDate = LocalDate.now(TOMSK_ZONE_ID)

        fun findTodayOrNextDay(days: List<DaySchedule>): DaySchedule? {
            if (days.isEmpty()) return null
            val todayStr = getTomskToday().toString() // YYYY-MM-DD
            // Try exact today
            val todayDay = days.firstOrNull { it.date == todayStr }
            if (todayDay != null) return todayDay

            // Try first upcoming day
            return days.firstOrNull { it.date >= todayStr } ?: days.firstOrNull()
        }
    }
}
