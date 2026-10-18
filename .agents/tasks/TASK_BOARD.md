# Сводная доска задач проекта (Task Board: tusur-schedule)

Синхронизируется **Куратором (Skill Engineer)** и **Архитектором (Architect)**.

---

## 📊 Статусная таблица

| ID | Название задачи | Домен | Исполнитель | Статус | Необходимые скиллы | Ссылка на ТЗ |
|:---|:----------------|:------|:------------|:-------|:-------------------|:-------------|
| `TS-001` | Аудит контрактов и синхронизация схем | Общий | `Contractor-Core` | `Backlog` | `task-accelerator` | [TS-001](backlog/TS-001-audit-contracts.md) |
| `TS-002` | Парсинг подгрупп и экспорт в JSON | Python | `Contractor-Python` | `Done` | `task-accelerator`, `tusur-parser-guard` | [TS-002](backlog/TS-002-parser-subgroups.md) |
| `TS-003` | Поддержка подгрупп в моделях и UI Android | Android | `Contractor-Android` | `Done` | `task-accelerator` | [TS-003](backlog/TS-003-android-subgroup-ui.md) |
| `TS-004` | Фильтрация подгрупп в Glance-виджете | Android | `Contractor-Android` | `Done` | `task-accelerator`, `android-glance-widget` | [TS-004](backlog/TS-004-widget-subgroup-filter.md) |
| `TS-005` | Декомпозиция монолита MainActivity.kt | Android | `Contractor-Android` | `Done` | `task-accelerator`, `android-compose-architecture` | [TS-005](backlog/TS-005-android-ui-decomposition.md) |
| `TS-006` | Декомпозиция монолита ScheduleWidget.kt | Android | `Contractor-Android` | `Done` | `task-accelerator`, `android-glance-widget` | [TS-006](backlog/TS-006-android-widget-decomposition.md) |
| `TS-007` | Синтез скилла по архитектуре Compose UI | Управление | `Skill-Engineer` | `Done` | `task-accelerator`, `skill-creator-meta` | [TS-007](backlog/TS-007-android-architecture-skills.md) |

---

## 🗂️ Распределение по колонкам

### 📥 1. Backlog (Сформировано Архитектором)
* [TS-001-audit-contracts.md](backlog/TS-001-audit-contracts.md) — Первичная верификация соответствия полей `schedule.json` моделям `ScheduleModels.kt`.

### ⚙️ 2. In Progress (В работе у Подрядчиков)
*(Задачи в активной разработке)*

### 🔍 3. Review (На проверке у Куратора)
*(Задачи, проходящие проверку линтерами, компиляцией Gradle и dry-run тестами)*

### ✅ 4. Done (Завершено и принято)
* [TS-002-parser-subgroups.md](backlog/TS-002-parser-subgroups.md) — Извлечение подгрупп занятий в `parser.py` и экспорт поля `subgroup` в JSON (`Contractor-Python`).
* [TS-003-android-subgroup-ui.md](backlog/TS-003-android-subgroup-ui.md) — Обновление моделей `ScheduleModels.kt`, `ScheduleRepository.kt` и UI в `MainActivity.kt` (`Contractor-Android`).
* [TS-004-widget-subgroup-filter.md](backlog/TS-004-widget-subgroup-filter.md) — Фильтрация занятий в Hero-карточке, списках и отображение чипа в `ScheduleWidget.kt` (`Contractor-Android`).
* [TS-005-android-ui-decomposition.md](backlog/TS-005-android-ui-decomposition.md) — Модуляризация Compose экранов и компонентов (`Contractor-Android`).
* [TS-006-android-widget-decomposition.md](backlog/TS-006-android-widget-decomposition.md) — Выделение Glance-компонентов и утилит форматирования (`Contractor-Android`).
* [TS-007-android-architecture-skills.md](backlog/TS-007-android-architecture-skills.md) — Синтез скилла по архитектуре Compose UI и обновление базы знаний (`Skill-Engineer`).

---

## 💡 Реестр скиллов проекта (Skill Inventory)
| Название скилла | Назначение | Домен |
|:----------------|:-----------|:------|
| [task-accelerator](../skills/task-accelerator/SKILL.md) | Защита от bloat, скоростное диффовое редактирование без сетевого флуда | Общий |
| [skill-creator-meta](../skills/skill-creator-meta/SKILL.md) | Мета-скилл для Куратора: как синтезировать новые скиллы | Управление |
| [task-reviewer](../skills/task-reviewer/SKILL.md) | Quality Gate: запуск Gradle assembleDebug и быстрый Python import | QA |
| [android-compose-architecture](../skills/android-compose-architecture/SKILL.md) | Компонентная архитектура Jetpack Compose, правила декомпозиции и целевой пиннинг файлов | Android |
| [android-glance-widget](../skills/android-glance-widget/SKILL.md) | Модульная архитектура, чистые утилиты и ограничения виджета Jetpack Glance | Android |
| [tusur-parser-guard](../skills/tusur-parser-guard/SKILL.md) | Безопасная правка BeautifulSoup-парсера через моки | Python |
| [telegram-gemini-bot](../skills/telegram-gemini-bot/SKILL.md) | Работа с Telegram ботом и Gemini Structured Outputs | Python |
