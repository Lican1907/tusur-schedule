# Задача TS-002: Парсинг подгрупп и экспорт поля subgroup в schedule-*.json

> **ID**: `TS-002`  
> **Название**: Извлечение подгрупп занятий в parser.py и экспорт в JSON-контракт  
> **Домен**: `Python / Backend`  
> **Исполнитель**: `Contractor-Python`  
> **Статус**: `Done`  

---

## 1. Цель
Обеспечить извлечение информации о подгруппах (`subgroup`) из HTML-расписания ТУСУР в `parser.py` и передачу этого поля в генерируемые артефакты `schedule-*.json` и `schedule.json` в соответствии со спецификацией [schedule-schema.md](../../architecture/data-contracts/schedule-schema.md).

---

## 2. Архитектурный контекст
В текущей реализации `parser.py` извлекает `subgroup` из модального окна `modal_data.get("Подгруппа")` и `span.note` внутри `parse_lesson_cell()` (строки ~777–783), однако значение лишь конкатенируется в текстовое поле `description` и не сохраняется в структурированный словарь занятия для Android-приложения.

---

## 3. Требования к реализации
1. **Сохранение в `Event`**:
   - В `parse_lesson_cell()` при наличии `subgroup` сохранять его в iCalendar Event через кастомное свойство: `event.add("x-subgroup", subgroup.strip())`.
2. **Извлечение в `get_event_lesson_data()`**:
   - Извлекать `subgroup` из свойства `ev.get("x-subgroup")` (с фоллбэком на регулярное выражение `r"Подгруппа:\s*(.+)"` из `description`).
   - Возвращать в результирующем словаре `"subgroup": subgroup.strip() if subgroup else None`.
3. **Формирование `build_schedule_data()`**:
   - Включать поле `"subgroup"` в объект каждого занятия `lesson_obj`.
   - На верхнем уровне ответа (`ScheduleResponse`) формировать отсортированный список всех уникальных обнаруженных подгрупп:
     ```python
     subgroups = sorted(list({
         l["subgroup"] for d in days for l in d["lessons"] if l.get("subgroup")
     }))
     ```
   - Добавить `"subgroups": subgroups` в возвращаемый словарь рядом с `pe_teachers` и `english_teachers`.
4. **Безопасность выполнения (tusur-parser-guard)**:
   - **Категорически запрещен** полный парсинг семестра (`--all-weeks`) во время тестирования!
   - Проверять изменения строго локально на одной неделе либо сохраненном мок-файле HTML.

---

## 4. Рекомендуемые скиллы
- [x] `.agents/skills/task-accelerator/SKILL.md`
- [x] `.agents/skills/tusur-parser-guard/SKILL.md`

---

## 5. Критерии приемки
- [x] `schedule.json` и `schedule-426top-4.json` содержат массив `"subgroups"` на верхнем уровне.
- [x] Каждое занятие в `"lessons"` содержит строковое поле `"subgroup"` (или `null`, если пара общая для всей группы).
- [x] Синтаксис проверен через `python -m py_compile parser.py`.
- [x] Нет регрессий в существующих полях (`pe_rooms`, `is_shifted_time`, `beer_time`).
