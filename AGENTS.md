# 🤖 Developer & Agent Guide (`AGENTS.md`)

## 📌 Project Overview
`tusur-schedule` — автоматизированный сервис парсинга расписания занятий ТУСУР (группы 426топ-4, 426топ-1, 426топ-2, 426-3).
Поддерживает генерацию iCalendar (`.ics`), прямой синк с Google Calendar API по сервисным аккаунтам, Telegram-бота с Gemini AI для ручных замен от преподавателей, а также JSON API (`today.json`, `schedule-<group>.json`) для мобильных виджетов.

---

## 🏗️ Repository Architecture

```text
tusur-schedule/
├── .github/workflows/
│   └── update.yml          # CI/CD пайплайн (тесты, парсинг, GCal, деплой .ics и .json в gh-pages)
├── data/
│   ├── bell_schedule.json  # Сетка звонков ТУСУР (пары 1-7: 08:50-10:25 и др.)
│   ├── pe_schedule.json    # Матрица залов физкультуры по датам и преподавателям
│   ├── mappings.json       # Сокращения предметов, типы, корпуса (ГК, РК, ФЭТ) и группы
│   └── overrides.json      # База ручных замен и отмен расписания
├── src/
│   ├── config.py           # Загрузка конфигураций и констант из data/
│   ├── pipeline.py         # Основной оркестратор парсинга и экспорта (.ics & .json)
│   ├── core/
│   │   ├── models.py       # Доменные модели: Lesson, TimeSlot, DaySchedule, Group
│   │   ├── bells.py        # Расчёт звонков, сдвиги субботы, текущая пара и обратный отсчёт
│   │   └── beer.py         # Логика события «Можно пить пиво!» на текущий день
│   ├── scraper/
│   │   ├── client.py       # HTTP-клиент с повторными попытками (Retry) и браузерными заголовками
│   │   └── parser.py       # Парсинг HTML timetable.tusur.ru, модалок, дней и аудиторий
│   ├── overrides/
│   │   └── engine.py       # Замены (set), отмены (cancel), сохранение аудиторий преподавателей
│   ├── exporters/
│   │   ├── ics.py          # RFC 5545 iCalendar (.ics) билдер с таймзоной Asia/Tomsk
│   │   └── json.py         # Генератор JSON для виджетов (today.json, schedule-<group>.json)
│   └── integrations/
│       ├── gcal.py         # Прямая синхронизация с Google Calendar API
│       └── bot/            # Telegram-бот с Gemini AI (Structured Outputs)
├── tests/
│   ├── test_bells.py       # Тесты сетки звонков и обратного отсчёта
│   ├── test_overrides.py   # Тесты отмен, замен и сохранения аудиторий
│   └── test_exporters.py   # Тесты генерации .ics и схемы .json
├── parser.py               # Обратная совместимость: корневой CLI-шим -> src/pipeline.py
├── gcal_sync.py            # Обратная совместимость: корневой CLI-шим -> src/integrations/gcal.py
├── bot.py                  # Обратная совместимость: корневой CLI-шим -> src/integrations/bot/
├── overrides.json          # Корневая ссылка на ручные правки (обратная совместимость)
├── index.html              # Интерактивный веб-лендинг для подписки
├── requirements.txt        # Python-зависимости
└── README.md               # Пользовательская документация
```

---

## 🌿 Правила работы с ветками (Branching Workflow)
- **`main`**: релизная ветка. Сюда попадают только протестированные, отполированные решения.
- **`feature/*` / `dev`**: рабочие ветки для экспериментов, добавления фичей и рефакторинга.
- Перед любым слиянием в `main` обязательно запускать unit-тесты.

---

## ⚡ Быстрые команды разработчика

```bash
# 1. Запуск всех тестов (выполняются за ~0.01 сек):
python -m unittest discover tests

# 2. Локальный парсинг текущей недели (быстрая проверка):
python parser.py --group 426top-4

# 3. Полный парсинг всех недель семестра:
python parser.py --all-weeks

# 4. Проверка CLI-шимов:
python gcal_sync.py --help
python bot.py --help
```

---

## 📱 Контракт данных для виджетов

- **`today.json`**:
  - `pair_status`: `"before_classes" | "in_pair" | "in_break" | "after_classes"`
  - `current_pair`, `next_pair`, `minutes_left_in_pair`, `minutes_until_next_pair`
  - `lessons`: массив нормализованных занятий (`pair_num`, `time_start`, `time_end`, `subject`, `lesson_type`, `teacher`, `room`, `building`, `status`, `color_id`)
  - `beer_event`: `{ summary, location, time_start, time_end }` (если сегодня есть/были пары)
- **`schedule-<group>.json`** и **`schedule.json`**:
  - `days`: объект вида `{"YYYY-MM-DD": [LessonDict, ...]}`.
