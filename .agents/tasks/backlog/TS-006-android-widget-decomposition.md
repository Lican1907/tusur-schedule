# Спецификация задачи: tusur-schedule (Task Spec)

> **ID**: `TS-006`  
> **Название**: Декомпозиция монолита ScheduleWidget.kt на изолированные Glance-компоненты и утилиты  
> **Домен**: `Android`  
> **Исполнитель**: `Contractor-Android`  
> **Архитектор**: `Architect`  
> **Куратор**: `Skill-Engineer`  

---

## 1. Цель и контекст
Файл `ScheduleWidget.kt` занимает более 800 строк и объединяет жизненный цикл GlanceAppWidget, расчет статусов и времени пива, форматирование дат, объединение пар разных подгрупп и верстку всех карточек виджета. Необходимо выделить модели, чистые функции расчета и отдельные Glance-компоненты.

## 2. Архитектурные требования и контракты
* `ru.lican1907.tusurschedule.widget.ScheduleWidget` и `ScheduleWidgetReceiver` должны оставаться точками входа (зарегистрированы в `AndroidManifest.xml` и коде приложения).
* Не изменять контракты данных в `data/ScheduleModels.kt`.
* Структура пакета `ru.lican1907.tusurschedule.widget`:
  - `ScheduleWidgetModels.kt`: модели `MergedLessonItem`, `HeroInfo`.
  - `ScheduleWidgetUtils.kt`: чистые функции:
    - `consolidateLessons(lessons: List<LessonItem>): List<MergedLessonItem>`
    - `computeHeroInfo(...)`
    - `computeHeaderStatus(...)`
    - `formatHeaderDate`, `formatDayDate`, `formatRelativeDayTitle`, `formatWeekday`, `formatPairCount`, `pluralizePairs`.
  - `components/WidgetHeader.kt`: Composable Glance-шапки (статус, дата, чип группы/подгруппы, кнопка обновления 34dp).
  - `components/WidgetHeroCard.kt`: Composable Hero-карточки (живой таймер текущей пары или статус отдыха/пива).
  - `components/WidgetDayHeader.kt`: Composable разделителя дней в списке.
  - `components/WidgetLessonCard.kt`: Composable карточки занятия в виджете.
  - `ScheduleWidget.kt`: компактный класс `ScheduleWidget : GlanceAppWidget()`, вызывающий декомпозированные компоненты, а также `RefreshAction` и `ScheduleWidgetReceiver`.

## 3. Рекомендуемые скиллы (Prepared Skills)
- [x] `.agents/skills/task-accelerator/SKILL.md`
- [x] `.agents/skills/android-glance-widget/SKILL.md`
- [x] `.agents/skills/android-compose-architecture/SKILL.md`

## 4. План реализации
1. Создать `widget/ScheduleWidgetModels.kt` с `MergedLessonItem` и `HeroInfo`.
2. Создать `widget/ScheduleWidgetUtils.kt` со всеми чистыми функциями бизнес-логики и форматирования.
3. Создать `widget/components/WidgetHeader.kt`, `WidgetHeroCard.kt`, `WidgetDayHeader.kt`, `WidgetLessonCard.kt`.
4. Отрефакторить `widget/ScheduleWidget.kt` для использования новых компонентов и утилит.
5. Проверить компиляцию Kotlin.

## 5. Критерии приемки (DoD)
- [x] `ScheduleWidget.kt` существенно сокращен (менее 180 строк — фактически 146 строк).
- [x] Все вычисления вынесены в `ScheduleWidgetUtils.kt` (покрыты юнит-тестами в `ScheduleWidgetUtilsTest.kt`).
- [x] Все Glance-компоненты вынесены в `widget/components/`.
- [x] Синтаксис и компиляция Kotlin успешны.
