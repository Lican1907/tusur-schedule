# Задача TS-001: Аудит контрактов и синхронизация схем данных

> **ID**: `TS-001`  
> **Название**: Верификация соответствия полей schedule.json моделям ScheduleModels.kt  
> **Домен**: `Общий`  
> **Исполнитель**: `Contractor-Core`  
> **Статус**: `Backlog`  

---

## 1. Цель
Убедиться, что все поля, генерируемые в `schedule-*.json` текущей версией `parser.py`, корректно маппятся в класс `ScheduleResponse` в `ScheduleModels.kt` и не приводят к `SerializationException` при отсутствии необязательных полей.

## 2. Архитектурные требования
* Проверить значения по умолчанию для всех полей в `LessonItem` и `DaySchedule`.
* Убедиться, что `ignoreUnknownKeys = true` включен в `ScheduleRepository.kt`.

## 3. Рекомендуемые скиллы
- [x] `.agents/skills/task-accelerator/SKILL.md`

## 4. Критерии приемки
- [ ] Все поля `schedule.json` имеют эквиваленты или значения по умолчанию в Kotlin-моделях.
- [ ] Отсутствуют расхождения в именовании (`pe_rooms`, `english_rooms`, `beer_time`).
