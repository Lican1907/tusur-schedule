---
name: android-glance-widget
description: >-
  Use this skill when developing, styling, debugging, refactoring, or updating the Android home screen
  widget using Jetpack Glance in tusur-schedule (ScheduleWidget.kt, ScheduleWidgetModels.kt,
  ScheduleWidgetUtils.kt, and widget/components/*).
---

# Android Glance Widget Best Practices & Architecture (tusur-schedule)

Этот скилл регламентирует модульную архитектуру и правила верстки виджета расписания ТУСУР на базе Jetpack Glance.

---

## 1. Directives (Ключевые директивы)

1. **Строгое разделение UI и логики**:
   - Вся бизнес-логика, расчет таймеров, форматирование дат и слияние подгрупп выносятся в `ScheduleWidgetUtils.kt` как **чистые функции**.
   - `ScheduleWidgetUtils.kt` **НЕ ДОЛЖЕН** импортировать пакеты `androidx.glance.*` или зависеть от Glance Context — это обеспечивает прямое покрытие юнит-тестами без моков Android.
2. **Модульность компонентов**:
   - Все UI-элементы виджета разбиты на изолированные Composable-функции в `ru.lican1907.tusurschedule.widget.components`.
   - `ScheduleWidget.kt` содержит только верхнеуровневый `GlanceAppWidget` (<180 строк), точку входа `provideGlance`, `RefreshAction` и `ScheduleWidgetReceiver`.
3. **Целевой пиннинг файлов**:
   - Перед редактированием виджета агент определяет точечный файл для правок, избегая чтения всего пакета `widget/`.

---

## 2. Structure (Модульная структура пакета `ru.lican1907.tusurschedule.widget`)

```
android/app/src/main/java/ru/lican1907/tusurschedule/widget/
├── ScheduleWidget.kt              # Точка входа (<180 строк): GlanceAppWidget, provideGlance, RefreshAction, Receiver
├── ScheduleWidgetModels.kt        # Модели данных виджета: HeroInfo, MergedLessonItem
├── ScheduleWidgetUtils.kt         # Чистые функции бизнес-логики и форматирования (без зависимости от Glance)
├── ScheduleWidgetTheme.kt         # Цвета, стили и градиенты виджета
├── ScheduleWidgetPreview.kt       # Превью для Android Studio (если доступно)
└── components/                    # Изолированные Glance Composable-компоненты
    ├── WidgetHeader.kt            # Шапка виджета: статус, дата, чип группы/подгруппы, кнопка обновления (34dp)
    ├── WidgetHeroCard.kt          # Hero-карточка: таймер активной пары или статус отдыха/пива
    ├── WidgetDayHeader.kt         # Разделитель дней в расписании (дата, день недели, количество пар)
    └── WidgetLessonCard.kt        # Карточка отдельной пары (время, тип, аудитория, подгруппа)
```

---

## 3. Target File Pinning Guide (Гайд по целевому пиннингу для агентов)

| Задача / Область изменений | Целевой файл для пиннинга | Что НЕ нужно читать |
|:---|:---|:---|
| Бизнес-логика, таймеры пар, пивной статус, форматирование дат, объединение пар | `widget/ScheduleWidgetUtils.kt` | `ScheduleWidget.kt`, `components/*` |
| Модели HeroInfo, MergedLessonItem | `widget/ScheduleWidgetModels.kt` | `ScheduleWidget.kt` |
| Шапка виджета, кнопка принудительного обновления, чип группы/подгруппы | `widget/components/WidgetHeader.kt` | `WidgetLessonCard.kt`, `WidgetHeroCard.kt` |
| Hero-баннер текущей пары, статус пива ("Пить пиво дозволено!") | `widget/components/WidgetHeroCard.kt` | `WidgetHeader.kt`, `WidgetLessonCard.kt` |
| Заголовок дня в списке расписания (разделитель дней) | `widget/components/WidgetDayHeader.kt` | `WidgetHeroCard.kt`, `ScheduleWidgetUtils.kt` |
| Верстка строки/карточки занятия в виджете | `widget/components/WidgetLessonCard.kt` | `WidgetHeader.kt`, `ScheduleWidget.kt` |
| Жизненный цикл GlanceAppWidget, регистрация BroadcastReceiver, обработка кликов Refresh | `widget/ScheduleWidget.kt` | `ScheduleWidgetUtils.kt`, `components/*` |

---

## 4. Чистые функции в `ScheduleWidgetUtils.kt`

В файле `ScheduleWidgetUtils.kt` находятся детерминированные функции без побочных эффектов:
* `consolidateLessons(lessons: List<LessonItem>): List<MergedLessonItem>` — объединение параллельных пар разных подгрупп в одну строку.
* `computeHeroInfo(lessons: List<LessonItem>, now: LocalTime): HeroInfo?` — вычисление текущей/следующей пары и прогресса.
* `computeHeaderStatus(hasLessonsToday: Boolean, isDayFinished: Boolean): String` — статус для шапки.
* Форматирование дат и текста:
  - `formatHeaderDate(date: LocalDate): String`
  - `formatDayDate(date: LocalDate): String`
  - `formatRelativeDayTitle(date: LocalDate, today: LocalDate): String`
  - `formatWeekday(date: LocalDate): String`
  - `formatPairCount(count: Int): String`
  - `pluralizePairs(count: Int): String`

---

## 5. Главные ограничения Jetpack Glance (RemoteViews)

1. **Не все Compose-модификаторы поддерживаются**:
   * Glance транслирует код в системный `RemoteViews`.
   * Использовать ТОЛЬКО импорты из `androidx.glance.*` (например `androidx.glance.layout.*`, `androidx.glance.text.*`).
   * **ЗАПРЕЩЕНО**: `androidx.compose.ui.Modifier.clickable` (вместо этого использовать `androidx.glance.action.clickable` или `actionRunCallback`).
   * **ЗАПРЕЩЕНО**: Анимации (`AnimatedVisibility`) и прямое Canvas-рисование в Glance.
   * **ЗАПРЕЩЕНО**: Использовать `LazyColumn` со сложными вложенными скроллами.

2. **Обновление состояния виджета**:
   Для принудительного обновления виджета из фона (`ScheduleSyncWorker.kt`, `MainActivity.kt` или `RefreshAction`):
   ```kotlin
   import androidx.glance.appwidget.updateAll

   // Обновление всех экземпляров виджета
   ScheduleWidget().updateAll(context)
   ```

3. **Стилизация карточек**:
   * Цвета фона карточек пар (лекция, практика, лаба, отмена) берутся из `android/app/src/main/res/drawable/`.
   * Для отмененных пар используется визуальная перечеркнутая полоса и стиль отмены (`is_cancelled = true`).

---

## 6. Verification Command (Проверка сборки виджета)

```powershell
cd android ; .\gradlew :app:compileDebugKotlin ; cd ..
```
