# Задача TS-003: Поддержка подгрупп в ScheduleModels, SharedPreferences и MainActivity

> **ID**: `TS-003`  
> **Название**: Обновление моделей данных, настроек репозитория и UI выбора подгруппы  
> **Домен**: `Android`  
> **Исполнитель**: `Contractor-Android`  
> **Статус**: `Done`  

---

## 1. Цель
Реализовать поддержку подгрупп в слое данных Android-приложения (`ScheduleModels.kt`, `ScheduleRepository.kt`) и добавить интерактивный селектор подгруппы в шапку экрана `MainActivity.kt` с сохранением выбора пользователя.

---

## 2. Архитектурный контекст
Для фильтрации занятий по подгруппам приложению необходимо:
1. Десериализовать поля `subgroups` и `subgroup` без нарушения обратной совместимости (`val subgroup: String? = null`, `val subgroups: List<String> = emptyList()`).
2. Сохранять выбранную подгруппу в `SharedPreferences` (`selected_subgroup`, значение по умолчанию `"ALL"` — Все подгруппы).
3. Предоставить алгоритм нормализации и матчинга подгрупп (учитывающий вариации названий: `"подгруппа 1"`, `"1"`, `"подгруппа а"`).
4. Дать пользователю возможность переключать подгруппу прямо в UI приложения с немедленным обновлением списка и виджета.

---

## 3. Требования к реализации

### 3.1. `ScheduleModels.kt`
- В `ScheduleResponse` добавить:
  ```kotlin
  val subgroups: List<String> = emptyList()
  ```
- В `LessonItem` добавить:
  ```kotlin
  val subgroup: String? = null
  ```
- Реализовать метод нормализации и проверки совпадения:
  ```kotlin
  fun normalizeSubgroup(raw: String?): String {
      if (raw.isNullOrBlank()) return ""
      return raw.lowercase()
          .replace("подгруппа", "")
          .replace("п/г", "")
          .replace("пг", "")
          .trim()
  }

  fun matchesSubgroup(selectedSubgroup: String): Boolean {
      if (selectedSubgroup.isEmpty() || selectedSubgroup.equals("ALL", ignoreCase = true)) {
          return true
      }
      // Если у занятия нет подгруппы — оно общее для всех студентов группы
      if (subgroup.isNullOrBlank()) {
          return true
      }
      return normalizeSubgroup(subgroup) == normalizeSubgroup(selectedSubgroup)
  }
  ```

### 3.2. `ScheduleRepository.kt`
- Добавить свойство хранения в SharedPreferences:
  ```kotlin
  var selectedSubgroup: String
      get() = prefs.getString("selected_subgroup", "ALL") ?: "ALL"
      set(value) = prefs.edit().putString("selected_subgroup", value).apply()
  ```

### 3.3. `MainActivity.kt`
- В состояние экрана добавить `var selectedSubgroup by remember { mutableStateOf(repository.selectedSubgroup) }`.
- В верхней панели фильтров (рядом с селекторами физкультуры и английского) разместить селектор подгруппы:
  - Список опций: `"Все подгруппы"` (`"ALL"`), затем обнаруженные подгруппы из `scheduleResponse.subgroups` (если список пуст — предусмотреть дефолтные `listOf("1", "2")`).
  - При выборе: обновить состояние, записать в `repository.selectedSubgroup = chosen` и инициировать обновление виджета `ScheduleWidget().updateAll(context)`.
- Список пар на выбранный день фильтровать с помощью `lesson.matchesSubgroup(selectedSubgroup)`.
- Если выбрано `"ALL"`, у пар с указанной подгруппой отображать аккуратный бейдж подгруппы.

---

## 4. Рекомендуемые скиллы
- [x] `.agents/skills/task-accelerator/SKILL.md`

---

## 5. Критерии приемки
- [x] Модели данных десериализуются без ошибок обратной совместимости.
- [x] При первом запуске дефолтное значение — `"ALL"` (видны все пары, поведение не меняется).
- [x] При выборе конкретной подгруппы скрываются занятия других подгрупп, а общие лекции остаются в расписании.
- [x] Выбор подгруппы сохраняется после перезапуска приложения в SharedPreferences.
