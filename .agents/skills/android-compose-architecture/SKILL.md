---
name: android-compose-architecture
description: >-
  Use this skill when developing, refactoring, modifying UI components, or inspecting the Jetpack
  Compose architecture in tusur-schedule (MainActivity.kt, ScheduleScreen.kt, and ui/components/*).
  Enforces strict file size limits (<200 lines, MainActivity <60 lines), state-hoisting, and targeted file pinning.
---

# Jetpack Compose Architecture Guide (tusur-schedule)

Этот скилл регламентирует компонентную архитектуру Android UI на базе Jetpack Compose в проекте `tusur-schedule`. Он защищает кодовую базу от разрастания монолитных файлов, предотвращает bloat контекста агентов и фиксирует правила изоляции компонентов.

---

## 1. Directives (Ключевые директивы)

1. **Строгие лимиты размера файлов**:
   - `MainActivity.kt` — **строго менее 60 строк**. Содержит исключительно жизненный цикл `ComponentActivity` и регистрацию фонового воркера.
   - Любой компонент в `ui/components/` и `ScheduleScreen.kt` — **строго не более 200 строк**.
   - Если файл превышает 200 строк, он обязан быть декомпозирован на подкомпоненты или вспомогательные функции.
2. **State-Hoisting (Подъем состояния)**:
   - Все компоненты в `ui/components/` должны быть **stateless**. Они получают состояние через параметры и передают действия наверх через лямбды (`onDateSelected`, `onGroupSelected`, `onRefresh`).
   - Управление состоянием (Repository, UpdateManager, выбранные фильтры, корутины) сосредоточено в `ScheduleScreen.kt`.
3. **Целевой пиннинг файлов (Target File Pinning)**:
   - Агентам **ЗАПРЕЩЕНО** читать всю директорию `ui/` или монолитно просматривать все файлы. Открывай строго 1 целевой файл в соответствии с картой пиннинга.

---

## 2. Structure (Карта структуры пакета `ru.lican1907.tusurschedule.ui`)

```
android/app/src/main/java/ru/lican1907/tusurschedule/ui/
├── MainActivity.kt                # Точка входа (<60 строк): lifecycle, WorkManager, Theme { ScheduleScreen() }
├── ScheduleScreen.kt              # Верхнеуровневый экран (<200 строк): состояние, Scaffold, связывание компонентов
├── components/                    # Изолированные stateless Composable-компоненты (<200 строк каждый)
│   ├── ScheduleTopBar.kt          # Верхняя панель: селектор группы, чип подгруппы, фильтры физ-ры/англ, Refresh
│   ├── DateStrip.kt               # Горизонтальный календарь дней (LazyRow) с индикаторами пар и выбором даты
│   ├── LiveDayBanner.kt           # Баннер активной пары (таймер, прогресс) и баннер пива («Пить пиво дозволено!»)
│   ├── LessonCard.kt              # Карточка пары: тип, аудитория, время, подгруппа, оверрайды, getLessonBrushAndBorder
│   └── UpdateDialog.kt            # Модальный диалог обновления приложения со статусом загрузки APK
└── theme/
    ├── Color.kt                   # Палитра цветов и градиентов
    └── Theme.kt                   # TusurScheduleTheme
```

---

## 3. Target File Pinning Guide (Гайд по целевому пиннингу для агентов)

Перед началом работы агент обязан сопоставить задачу с целевым файлом и открывать **только его**:

| Задача / Область изменений | Целевой файл для пиннинга | Что НЕ нужно читать |
|:---|:---|:---|
| Селектор группы, выбор подгруппы, выбор преподавателя (PE/English), кнопка Refresh | `ui/components/ScheduleTopBar.kt` | `MainActivity.kt`, `LessonCard.kt` |
| Горизонтальная лента дней, переключение даты, подсветка сегодняшнего дня | `ui/components/DateStrip.kt` | `ScheduleScreen.kt`, `LessonCard.kt` |
| Обратный отсчет до конца/начала пары, прогресс текущего занятия, статус пива | `ui/components/LiveDayBanner.kt` | `ScheduleTopBar.kt`, `UpdateDialog.kt` |
| Верстка карточки занятия, цвет бейджа (лекция/практика/лаба), подгруппы, оверрайды | `ui/components/LessonCard.kt` | `DateStrip.kt`, `LiveDayBanner.kt` |
| Диалог загрузки обновления APK, прогресс-бар скачивания, кнопка установки | `ui/components/UpdateDialog.kt` | `ScheduleScreen.kt`, `MainActivity.kt` |
| Управление стейтом (StateFlow, LaunchedEffect), координация диалогов, Snackbar | `ui/ScheduleScreen.kt` | `ui/components/*` (кроме сигнатур) |
| Жизненный цикл Activity, WorkManager PeriodicSync, системные Intent-ы | `ui/MainActivity.kt` | Любые Composable-компоненты |

---

## 4. Rules & Code Examples

### 4.1. Канонический вид `MainActivity.kt` (<60 строк)
```kotlin
package ru.lican1907.tusurschedule.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.lican1907.tusurschedule.ui.theme.TusurScheduleTheme
import ru.lican1907.tusurschedule.worker.ScheduleSyncWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ScheduleSyncWorker.enqueuePeriodicSync(this)

        setContent {
            TusurScheduleTheme {
                ScheduleScreen()
            }
        }
    }
}
```

### 4.2. Шаблон Stateless-компонента (State-Hoisting)
```kotlin
package ru.lican1907.tusurschedule.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.lican1907.tusurschedule.data.LessonItem

@Composable
fun LessonCard(
    lesson: LessonItem,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    // Вся логика отображения берется из переданных параметров.
    // Никаких прямых обращений к базам данных, репозиториям или SharedPreferences!
}
```

---

## 5. Anti-Patterns (Что делать ЗАПРЕЩЕНО)

* ❌ **Раздувать файлы свыше 200 строк**: При превышении немедленно выносить вспомогательные Composable или утилиты стилей.
* ❌ **Внедрять бизнес-логику в компоненты**: Передавать `ScheduleRepository` или `AppUpdateManager` внутрь `LessonCard` или `DateStrip` строго запрещено. Компоненты получают только чистые модели (`LessonItem`, `DaySchedule`, `String`, `Boolean`) и коллбеки `() -> Unit`.
* ❌ **Использовать Android View или XML**: Весь UI строится на декларативном Jetpack Compose.
* ❌ **Нарушать контракты моделей**: Изменение структуры `ScheduleModels.kt` ради UI запрещено без согласования с `TS-001`.
* ❌ **Читать все UI файлы разом**: Загрузка всех файлов директории приводит к context-bloat и падению скорости агента в 3-5 раз.

---

## 6. Verification Command (Проверка синтаксиса и компиляции)

```powershell
cd android ; .\gradlew :app:compileDebugKotlin ; cd ..
```
