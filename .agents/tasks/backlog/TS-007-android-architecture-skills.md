# Спецификация задачи: tusur-schedule (Task Spec)

> **ID**: `TS-007`  
> **Название**: Синтез скилла по компонентной архитектуре Android и обновление glance-скилла  
> **Домен**: `Управление` / `Android`  
> **Исполнитель**: `Skill-Engineer`  
> **Архитектор**: `Architect`  
> **Куратор**: `Skill-Engineer`  

---

## 1. Цель и контекст
Большие монолитные файлы `MainActivity.kt` (~980 строк) и `ScheduleWidget.kt` (~830 строк) замедляют работу агентов, перегружают контекст и повышают риск коллизий. Вводится компонентная структура для Compose UI и Glance Widget. Куратор должен формализовать эти правила в виде скилла `android-compose-architecture` и обновить `android-glance-widget`, чтобы последующие агенты мгновенно определяли нужный файл и не искали код по всему проекту.

## 2. Архитектурные требования и контракты
* Не менять контракты `ScheduleModels.kt`, `overrides.json`, `schedule.json`.
* Закрепить строгие границы файлов для UI компонентов:
  - `ui/MainActivity.kt` — только жизненный цикл Activity и регистрация фонового воркера.
  - `ui/ScheduleScreen.kt` — верхнеуровневый экран и состояние.
  - `ui/components/` — изолированные Composable-компоненты (`ScheduleTopBar`, `DateStrip`, `LiveDayBanner`, `LessonCard`, `UpdateDialog`).
* Закрепить строгие границы файлов для Glance виджета:
  - `widget/ScheduleWidget.kt` — только GlanceAppWidget, провайдер контента и ресивер.
  - `widget/ScheduleWidgetModels.kt` — модели HeroInfo, MergedLessonItem.
  - `widget/ScheduleWidgetUtils.kt` — чистые функции калькуляции и форматирования.
  - `widget/components/` — UI-компоненты виджета (`WidgetHeader`, `WidgetHeroCard`, `WidgetDayHeader`, `WidgetLessonCard`).

## 3. Рекомендуемые скиллы (Prepared Skills)
- [x] `.agents/skills/task-accelerator/SKILL.md`
- [x] `.agents/skills/skill-creator-meta/SKILL.md`

## 4. План реализации
1. Создать `.agents/skills/android-compose-architecture/SKILL.md` с описанием карты компонентов, правил state-hoisting и запретом на раздувание файлов сверх 200 строк.
2. Обновить `.agents/skills/android-glance-widget/SKILL.md` с актуальными путями к `widget/components/` и `widget/ScheduleWidgetUtils.kt`.
3. Добавить скилл в `TASK_BOARD.md` в реестр скиллов.

## 5. Критерии приемки (DoD)
- [x] Файл `.agents/skills/android-compose-architecture/SKILL.md` создан и содержит валидный YAML frontmatter.
- [x] `.agents/skills/android-glance-widget/SKILL.md` актуализирован.
- [x] Описаны правила точечного пиннинга файлов для агентов-подрядчиков.
