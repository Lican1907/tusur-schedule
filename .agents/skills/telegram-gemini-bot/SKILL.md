---
name: telegram-gemini-bot
description: >-
  Use this skill when editing bot.py, configuring Google Gemini API structured outputs,
  modifying teacher notification parsing, or managing Telegram inline keyboards.
---

# Telegram Bot & Gemini API Architecture (tusur-schedule)

Скилл описывает работу Telegram-бота `bot.py` для приема и парсинга сообщений преподавателей через Google Gemini API.

---

## 1. Архитектурный поток бота
1. Бот принимает входящее текстовое или пересланное сообщение в Telegram.
2. Проверяет авторизацию пользователя (`@nktatm` или список разрешенных ID).
3. Передает текст сообщения в Gemini API с моделью `gemini-1.5-flash` / `gemini-2.0-flash` и схемой Pydantic (`Structured Outputs`).
4. Формирует карточку предпросмотра пары с инлайн-кнопками «Применить замену» / «Отменить».
5. При подтверждении — добавляет запись в `overrides.json` и инициирует синхронизацию `gcal_sync.py`.

---

## 2. Важные инварианты
* **Часовой пояс**: строго `Asia/Tomsk` (UTC+7). Все относительные даты ("завтра", "в среду") вычисляются относительно текущего времени в Томске.
* **Переменные окружения**: токены `TELEGRAM_BOT_TOKEN`, `GEMINI_API_KEY` и `GOOGLE_SERVICE_ACCOUNT_JSON` читаются из `.env` через `python-dotenv`.

---

## 3. Команда проверки синтаксиса бота
```powershell
.venv\Scripts\python -c "import bot; print('Bot syntax is valid')"
```
