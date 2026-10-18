---
name: task-reviewer
description: >-
  Quality Gate и регламент приемки задач для tusur-schedule. Выполняет быструю проверку
  компиляции Android-кода через Gradle и проверку синтаксиса Python без сетевого флуда.
---

# Quality Gate для tusur-schedule (Task Reviewer)

Скилл используется **Куратором (Skill Engineer)** для валидации задач перед переносом в `Done`.

---

## 1. Процедура валидации Python-задач

Для задач, затрагивающих `parser.py`, `bot.py` или `gcal_sync.py`:
1. **Проверка синтаксиса и импортов**:
   ```powershell
   .venv\Scripts\python -c "import parser; import bot; import gcal_sync; print('SYNTAX OK')"
   ```
2. **Проверка контракта overrides.json**:
   ```powershell
   .venv\Scripts\python -c "import json; data=json.load(open('overrides.json', encoding='utf-8')); assert isinstance(data, list); print('OVERRIDES OK')"
   ```
3. **Запрет полного прогона**: Убедиться, что подрядчик НЕ запускал полный парсинг семестра.

---

## 2. Процедура валидации Android-задач

Для задач, затрагивающих `android/`:
1. **Проверка компиляции Kotlin и ресурсов**:
   ```powershell
   cd android ; .\gradlew compileDebugSources --no-daemon ; cd ..
   ```
   *(Команда должна завершиться с `BUILD SUCCESSFUL`)*.
2. **Проверка целостности моделей**:
   Убедиться, что изменения в `ScheduleModels.kt` содержат значения по умолчанию для всех новых полей (во избежание падения кэша у пользователей).

---

## 3. Skill Extraction Loop
Если в ходе проверки выявлено, что подрядчик несколько раз спотыкался о синтаксис Glance или Pydantic в боте — Куратор обязан дополнить соответствующий скилл в `.agents/skills/` до закрытия задачи.
