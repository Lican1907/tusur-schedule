---
name: tusur-parser-guard
description: >-
  Use this skill when modifying parser.py, altering BeautifulSoup selectors, adding support
  for new academic groups, or updating how overrides.json is applied to schedule events.
---

# TUSUR Parser Guard & Modification Rules

Скилл регламентирует безопасную модификацию парсера расписания `parser.py`.

---

## 1. Защитные директивы

1. **Никаких запросов ко всему семестру**:
   * Никогда не запускать `python parser.py --all-weeks` во время разработки! Это перегружает сервер университета и занимает несколько минут.
   * Для локального теста запускать парсинг только на текущую неделю:
     ```powershell
     .venv\Scripts\python parser.py
     ```

2. **Сохранение схемы вывода**:
   * Любые новые поля, добавляемые в словарь пары, обязаны иметь строгий тип (String, Boolean, Dict).
   * Не менять имена существующих ключей (`uid`, `time_start`, `type`, `subject`, `location`, `color_id`).

3. **Логика наложения overrides.json**:
   * Оверрайды накладываются по совпадению `date` + `subject` (и опционально `time_start`).
   * Если у оверрайда `action == "cancel"`, в объекте занятия флаг `is_cancelled` выставляется в `True`, а событие в iCalendar помечается как `STATUS:CANCELLED`.

---

## 2. Быстрая проверка корректности парсера
```powershell
.venv\Scripts\python -c "import parser; print('Parser module is valid')"
```
