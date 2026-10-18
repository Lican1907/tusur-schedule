# Спецификация задачи: tusur-schedule (Task Spec)

> **ID**: `TS-XXX`  
> **Название**: Краткое описание фичи/багфикса  
> **Домен**: `Android` / `Python-Parser` / `Telegram-Bot` / `DevOps`  
> **Исполнитель**: `Contractor-Python` / `Contractor-Android` / `Contractor-DevOps`  
> **Архитектор**: `Architect`  
> **Куратор**: `Skill-Engineer`  

---

## 1. Цель и контекст
*Какую задачу пользователя решает эта доработка?*

## 2. Архитектурные требования и контракты
* Затрагивает ли `overrides.json` или `schedule.json`?
* Требуются ли изменения в `ScheduleModels.kt`?
* Ограничения: (не запускать `python parser.py --all-weeks`, не использовать неподдерживаемые модификаторы Glance).

## 3. Рекомендуемые скиллы (Prepared Skills)
- [ ] `.agents/skills/task-accelerator/SKILL.md`
- [ ] `.agents/skills/<domain-skill>/SKILL.md`

## 4. План реализации
1. Шаг 1: Определение целевых файлов (не более 1-2).
2. Шаг 2: Реализация изменения (diff-first).
3. Шаг 3: Локальная валидация (однострочник Python или `./gradlew compileDebugSources`).

## 5. Критерии приемки (DoD)
- [ ] Целевые файлы отредактированы без побочных эффектов.
- [ ] Для Python: `.venv/Scripts/python -c "import <module>; print('OK')"` успешен.
- [ ] Для Android: `./gradlew assembleDebug` (или `compileDebugKotlin`) завершается с кодом 0.
- [ ] Контракты данных не нарушены.
