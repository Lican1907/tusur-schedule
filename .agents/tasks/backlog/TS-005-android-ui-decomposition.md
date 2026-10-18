# Спецификация задачи: tusur-schedule (Task Spec)

> **ID**: `TS-005`  
> **Название**: Декомпозиция монолита MainActivity.kt на модульные Compose-компоненты  
> **Домен**: `Android`  
> **Исполнитель**: `Contractor-Android`  
> **Архитектор**: `Architect`  
> **Куратор**: `Skill-Engineer`  

---

## 1. Цель и контекст
Файл `MainActivity.kt` разросся почти до 1000 строк и объединяет Activity, управление состоянием экрана, меню селекторов, карточки пар, живой таймер и логику диалогов обновления. Необходимо декомпозировать файл на изолированные компоненты с сохранением всей функциональности, UI-состояния и совместимости с Manifest.

## 2. Архитектурные требования и контракты
* `ru.lican1907.tusurschedule.ui.MainActivity` должна оставаться точкой входа (объявлена в `AndroidManifest.xml`).
* Не изменять контракты данных в `data/ScheduleModels.kt`.
* Структура пакета `ru.lican1907.tusurschedule.ui`:
  - `MainActivity.kt` (~40-50 строк): только `onCreate`, `ScheduleSyncWorker.enqueuePeriodicSync(this)`, `setContent { TusurScheduleTheme { ScheduleScreen() } }`.
  - `ScheduleScreen.kt`: верхнеуровневый Composable с состоянием (repository, updateManager, selectedGroup, pe, english, subgroup, dates), координацией обновления и Scaffold.
  - `components/ScheduleTopBar.kt`: Composable для селектора группы, селектора подгруппы, селекторов преподавателей физкультуры/английского и кнопки Refresh.
  - `components/DateStrip.kt`: Composable горизонтального календаря дней (`LazyRow`).
  - `components/LiveDayBanner.kt`: Composable активной идущей пары с таймером и прогресс-баром, а также баннера пива («Пить пиво дозволено!»).
  - `components/LessonCard.kt`: Composable карточки занятия с типом, подгруппой, временем, аудиторией, оверрайдами и функцией `getLessonBrushAndBorder`.
  - `components/UpdateDialog.kt`: Composable всплывающего диалога обновления приложения со статусом загрузки APK.
* Все компоненты используют `ru.lican1907.tusurschedule.ui.theme.*` и существующие ресурсы.

## 3. Рекомендуемые скиллы (Prepared Skills)
- [x] `.agents/skills/task-accelerator/SKILL.md`
- [x] `.agents/skills/android-compose-architecture/SKILL.md`

## 4. План реализации
1. Выделить `ui/components/LessonCard.kt` (`LessonCard`, `getLessonBrushAndBorder`).
2. Выделить `ui/components/LiveDayBanner.kt` (`LiveDayBanner`).
3. Выделить `ui/components/DateStrip.kt` (`DateStrip`).
4. Выделить `ui/components/ScheduleTopBar.kt` (`ScheduleTopBar`).
5. Выделить `ui/components/UpdateDialog.kt` (`UpdateDialog`).
6. Создать `ui/ScheduleScreen.kt` со связыванием выделенных компонентов.
7. Сократить `ui/MainActivity.kt` до минимальной Activity.
8. Проверить синтаксис Kotlin компилятором `kotlinc`.

## 5. Критерии приемки (DoD)
- [x] `MainActivity.kt` содержит не более 60 строк кода (фактически 27 строк).
- [x] Все вынесенные компоненты находятся в пакете `ru.lican1907.tusurschedule.ui.components` (или `ru.lican1907.tusurschedule.ui`).
- [x] Функциональность и визуальный вид полностью сохранены (селекторы, чипы, баннеры, таймеры, карточки, диалог).
- [x] Синтаксис и компиляция файлов Kotlin успешны.
