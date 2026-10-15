#!/usr/bin/env python3
"""
Telegram Bot for TUSUR Schedule Overrides Management
Receives teacher messages, extracts schedule adjustments using Gemini API (Structured Outputs),
previews widget cards, and synchronizes to overrides.json and Google Calendar.
Strict authorization: only permitted usernames (default: @nktatm).
"""

import asyncio
import base64
import json
import logging
import os
import re
import sys
import uuid
from datetime import datetime
from typing import Any, Dict, List, Optional
from zoneinfo import ZoneInfo

from pydantic import BaseModel, Field
import requests
from telegram import InlineKeyboardButton, InlineKeyboardMarkup, Update
from telegram.constants import ParseMode
from telegram.ext import (
    ApplicationBuilder,
    CallbackQueryHandler,
    CommandHandler,
    ContextTypes,
    MessageHandler,
    filters,
)

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

# ---------------------------------------------------------------------------
# Logging Configuration
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("tusur_bot")

TIMEZONE_STR = "Asia/Tomsk"
TIMEZONE = ZoneInfo(TIMEZONE_STR)
OVERRIDES_FILE = "overrides.json"

# Authorized usernames (default strictly: @nktatm)
raw_allowed = os.getenv("ALLOWED_USERNAMES", "nktatm")
ALLOWED_USERNAMES = {u.strip().lower().replace("@", "") for u in raw_allowed.split(",") if u.strip()}

COLOR_DESCRIPTIONS: Dict[str, str] = {
    "10": "🟩 Зелёный (Basil / ЛЕК)",
    "9": "🟦 Синий (Blueberry / ПРАК)",
    "3": "🟪 Фиолетовый (Grape / ЛАБ)",
    "8": "⬜ Графит (Graphite / СРС или Отмена)",
    "4": "🌸 Розовый (Flamingo / КУРС)",
    "5": "🟨 Жёлтый (Banana / ЗАЧ или Пиво)",
    "11": "🟥 Красный (Tomato / ЭКЗ)",
}

TYPE_EMOJI: Dict[str, str] = {
    "ЛЕК": "🟢",
    "ПРАК": "🔵",
    "ЛАБ": "🟣",
    "СРС": "⚪",
    "КУРС": "🌸",
    "ЗАЧ": "🟡",
    "ЭКЗ": "🔴",
}

DAYS_RU = ["Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"]


# ---------------------------------------------------------------------------
# Pydantic Schemas for Gemini Structured Outputs
# ---------------------------------------------------------------------------
class OverrideItem(BaseModel):
    date: str = Field(description="Date of lesson in YYYY-MM-DD format (Asia/Tomsk timezone)")
    action: str = Field(description="'set' to add or replace lesson; 'cancel' to cancel lesson")
    time_start: Optional[str] = Field(default=None, description="Start time in HH:MM format (e.g. '10:40'). For 'cancel', null if cancelling entire subject/type on that date")
    type: str = Field(description="Lesson type code: 'ЛЕК', 'ПРАК', 'ЛАБ', 'СРС', 'КУРС', 'ЗАЧ', 'ЭКЗ'")
    subject: str = Field(description="Capitalized subject name, e.g. 'Дискретка', 'Математика', 'ОРГ', 'Информатика', 'ПИТР'")
    location: Optional[str] = Field(default=None, description="Classroom if explicitly mentioned, or null")
    keep_original_location: bool = Field(default=True, description="True if classroom is 'по расписанию' or unspecified for a replacement")
    comment: str = Field(description="Concise teacher reason or quote")


class TeacherMessageAnalysis(BaseModel):
    has_changes: bool = Field(description="True if message contains actual schedule changes/cancellations, False if irrelevant/no changes")
    overrides: List[OverrideItem] = Field(default_factory=list, description="Extracted schedule override items")
    explanation: str = Field(description="Short human-readable summary of what was understood")


# ---------------------------------------------------------------------------
# Authorization Check Helper
# ---------------------------------------------------------------------------
def is_authorized(update: Update) -> bool:
    """Checks if message sender is authorized to manage schedule."""
    user = update.effective_user
    if not user:
        return False
    username = (user.username or "").lower().strip()
    return username in ALLOWED_USERNAMES


async def reject_unauthorized(update: Update) -> None:
    """Sends rejection message to unauthorized users."""
    user = update.effective_user
    username = f"@{user.username}" if user and user.username else f"ID {user.id if user else 'unknown'}"
    logger.warning("Rejected unauthorized access attempt by %s", username)
    if update.effective_message:
        await update.effective_message.reply_text(
            "⛔ *Доступ запрещен.*\n\nЭтот бот предназначен исключительно для авторизованного администратора (@nktatm).",
            parse_mode=ParseMode.MARKDOWN,
        )


# ---------------------------------------------------------------------------
# Gemini AI Analysis
# ---------------------------------------------------------------------------
def analyze_message_with_gemini(text: str) -> Optional[TeacherMessageAnalysis]:
    """Analyzes teacher message text using Gemini 2.5 Flash Structured Outputs."""
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        logger.error("GEMINI_API_KEY is not set in environment")
        return None

    try:
        from google import genai
        from google.genai import types

        client = genai.Client(api_key=api_key)
        now_tomsk = datetime.now(TIMEZONE)
        today_str = now_tomsk.strftime("%Y-%m-%d")
        weekday_name = DAYS_RU[now_tomsk.weekday()]

        system_instruction = f"""
Ты — специализированный ИИ-парсер расписания занятий группы 426топ-4 ТУСУР (Томск).
Твоя задача — анализировать сообщения преподавателей и старосты, извлекая изменения расписания в строгом формате.

Контекст времени и даты:
- Текущая дата: {today_str} ({weekday_name})
- Часовой пояс: Asia/Tomsk (UTC+7)

Сетка звонков ТУСУР:
1 пара: 08:50 - 10:25
2 пара: 10:40 - 12:15
3 пара: 13:15 - 14:50
4 пара: 15:00 - 16:35
5 пара: 16:45 - 18:20
6 пара: 18:30 - 20:05
7 пара: 20:15 - 21:50

Стандартные сокращения предметов:
- Дискретная математика -> Дискретка
- Основы российской государственности -> ОРГ
- Проектирование индивидуальной траектории -> ПИТР
- Иностранный язык -> Английский
- Физическая культура -> Физ-ра
- История России -> История
- Математика -> Математика
- Информатика -> Информатика

Правила извлечения:
1. Вычисляй точные даты YYYY-MM-DD для относительных выражений: "сегодня", "завтра", "в эту среду", "в четверг 15-го" относительно {today_str}.
2. Если занятие отменено: action = "cancel". Если время не указано, time_start = null.
3. Если пара заменяется на другую:
   - Создай action = "set" для новой пары.
   - Если старая пара явно отменяется или переносится, можно также создать action = "cancel" для отменяемой пары.
4. "Аудитории по расписанию" или если аудитория не указана при замене предмета -> keep_original_location = true, location = null.
5. Игнорируй постороннюю информацию: оценки, результаты тестов, дедлайны домашних заданий, вопросы студентов. Если в сообщении нет изменений расписания, ставь has_changes = false.
"""

        response = client.models.generate_content(
            model="gemini-2.5-flash",
            contents=text,
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=TeacherMessageAnalysis,
                system_instruction=system_instruction,
                temperature=0.1,
            ),
        )

        raw_json = response.text
        data = json.loads(raw_json)
        analysis = TeacherMessageAnalysis(**data)
        return analysis
    except Exception as e:
        logger.exception("Failed to analyze message with Gemini: %s", e)
        return None


# ---------------------------------------------------------------------------
# Overrides Storage & Git Commit Helpers
# ---------------------------------------------------------------------------
def load_current_overrides() -> List[Dict[str, Any]]:
    """Loads existing overrides from data/overrides.json or overrides.json."""
    from src.overrides.engine import load_overrides
    return load_overrides()


def save_current_overrides(overrides: List[Dict[str, Any]]) -> None:
    """Saves overrides to both data/overrides.json and overrides.json."""
    from src.config import DATA_DIR, ROOT_DIR
    target_paths = [DATA_DIR / OVERRIDES_FILE, ROOT_DIR / OVERRIDES_FILE]
    for p in target_paths:
        try:
            with open(p, "w", encoding="utf-8") as f:
                json.dump(overrides, f, ensure_ascii=False, indent=2)
            logger.info("Saved %d overrides to %s", len(overrides), p)
        except Exception as e:
            logger.error("Failed to save overrides to %s: %s", p, e)


def commit_overrides_to_github(overrides: List[Dict[str, Any]]) -> bool:
    """Pushes updated overrides.json to GitHub repository via GitHub REST API if token is configured."""
    github_token = os.getenv("GITHUB_TOKEN")
    repo = os.getenv("GITHUB_REPOSITORY", "Lican1907/tusur-schedule")

    if not github_token:
        logger.info("GITHUB_TOKEN not set; skipping remote GitHub commit")
        return False

    url = f"https://api.github.com/repos/{repo}/contents/{OVERRIDES_FILE}"
    headers = {
        "Authorization": f"Bearer {github_token}",
        "Accept": "application/vnd.github.v3+json",
    }

    try:
        # Get current file sha
        sha = None
        get_res = requests.get(url, headers=headers, timeout=10)
        if get_res.status_code == 200:
            sha = get_res.json().get("sha")

        content_bytes = json.dumps(overrides, ensure_ascii=False, indent=2).encode("utf-8")
        content_b64 = base64.b64encode(content_bytes).decode("ascii")

        body: Dict[str, Any] = {
            "message": "fix(schedule): update overrides.json via Telegram bot",
            "content": content_b64,
            "branch": "main",
        }
        if sha:
            body["sha"] = sha

        put_res = requests.put(url, headers=headers, json=body, timeout=15)
        if put_res.status_code in (200, 201):
            logger.info("Successfully committed overrides.json to %s (main)", repo)
            return True
        else:
            logger.warning("GitHub API returned status %s: %s", put_res.status_code, put_res.text)
            return False
    except Exception as e:
        logger.error("Failed to commit overrides to GitHub: %s", e)
        return False


def trigger_pipeline_and_sync() -> Dict[str, Any]:
    """Re-parses the schedule with new overrides and syncs directly to Google Calendar."""
    import parser
    import gcal_sync

    cal = parser.parse_semester(all_weeks=True)
    with open("schedule.ics", "wb") as f:
        f.write(cal.to_ical())

    sync_result = gcal_sync.sync_calendar_if_configured(cal)
    return {
        "events_count": len([c for c in cal.walk("vevent")]),
        "gcal_stats": sync_result,
    }


# ---------------------------------------------------------------------------
# Telegram Bot Handlers
# ---------------------------------------------------------------------------
async def start_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Handles /start command."""
    if not is_authorized(update):
        await reject_unauthorized(update)
        return

    user = update.effective_user
    username = f"@{user.username}" if user and user.username else "Администратор"

    msg = (
        f"👋 *Привет, {username}!*\n\n"
        "Я автономный бот управления расписанием группы *426топ-4* ТУСУР.\n\n"
        "✨ *Что я умею:*\n"
        "• Присылай или пересылай мне сообщения преподавателей/старосты.\n"
        "• ИИ Gemini 2.5 Flash автоматически распознает отмены, замены и временные сдвиги пар.\n"
        "• Я покажу интерактивное превью карточек с цветами для Google Календаря.\n"
        "• По нажатию одной кнопки изменения сохраняются в `overrides.json` и моментально синхронизируются с Google Calendar API!\n\n"
        "Команды:\n"
        "/list — показать активные правки\n"
        "/sync — запустить синхронизацию с Google Calendar\n"
        "/clear — очистить правки\n"
        "/status — статус системы и конфигурации"
    )
    await update.effective_message.reply_text(msg, parse_mode=ParseMode.MARKDOWN)


async def status_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Handles /status command."""
    if not is_authorized(update):
        await reject_unauthorized(update)
        return

    overrides = load_current_overrides()
    has_gcal = bool(os.getenv("GOOGLE_CALENDAR_ID") and (os.getenv("GOOGLE_CREDENTIALS_JSON") or os.path.exists("service_account.json")))
    has_gemini = bool(os.getenv("GEMINI_API_KEY"))
    has_github = bool(os.getenv("GITHUB_TOKEN"))

    msg = (
        "⚙️ *Статус системы:*\n\n"
        f"• Активных правок: *{len(overrides)}*\n"
        f"• Gemini API: {'✅ Подключен' if has_gemini else '❌ Нет ключа GEMINI_API_KEY'}\n"
        f"• Google Calendar API: {'✅ Подключен' if has_gcal else '⚠️ Не настроен'}\n"
        f"• GitHub Token: {'✅ Подключен' if has_github else '⚠️ Не задан (только локальное сохранение)'}\n"
        f"• Авторизованные пользователи: `{', '.join(sorted(ALLOWED_USERNAMES))}`\n"
    )
    await update.effective_message.reply_text(msg, parse_mode=ParseMode.MARKDOWN)


async def list_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Handles /list command."""
    if not is_authorized(update):
        await reject_unauthorized(update)
        return

    overrides = load_current_overrides()
    if not overrides:
        await update.effective_message.reply_text("ℹ️ Список правок `overrides.json` в данный момент пуст.")
        return

    lines = [f"📋 *Список активных правок ({len(overrides)}):*\n"]
    for idx, ov in enumerate(overrides, 1):
        action_sym = "❌ [ОТМЕНА]" if ov.get("action") == "cancel" else "⚡ [ЗАМЕНА]"
        time_info = f" в {ov.get('time_start')}" if ov.get("time_start") else ""
        lines.append(
            f"*{idx}. {action_sym}* `{ov.get('date')}`{time_info}\n"
            f"   • Предмет: *{ov.get('type')} {TYPE_EMOJI.get(ov.get('type', ''), '')} {ov.get('subject')}*\n"
            f"   • Примечание: _{ov.get('comment', 'без комментария')}_\n"
            f"   • ID: `{ov.get('id')}`\n"
        )

    await update.effective_message.reply_text("\n".join(lines), parse_mode=ParseMode.MARKDOWN)


async def sync_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Handles /sync command."""
    if not is_authorized(update):
        await reject_unauthorized(update)
        return

    status_msg = await update.effective_message.reply_text("⏳ Запускаю полный пересчет и синхронизацию с Google Calendar...")
    try:
        res = trigger_pipeline_and_sync()
        gcal_stats = res.get("gcal_stats")
        gcal_info = ""
        if gcal_stats:
            gcal_info = (
                f"\n• Создано: *{gcal_stats['created']}*\n"
                f"• Обновлено: *{gcal_stats['updated']}*\n"
                f"• Без изменений: *{gcal_stats['unchanged']}*"
            )
        else:
            gcal_info = "\n_Google Calendar sync не настроен или пропущен._"

        await status_msg.edit_text(
            f"✅ *Синхронизация завершена!*\n\n"
            f"• Всего событий в расписании: *{res['events_count']}*{gcal_info}",
            parse_mode=ParseMode.MARKDOWN,
        )
    except Exception as e:
        logger.exception("Manual sync failed: %s", e)
        await status_msg.edit_text(f"❌ Ошибка синхронизации: `{e}`", parse_mode=ParseMode.MARKDOWN)


async def message_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Handles incoming text and forwarded teacher messages."""
    if not is_authorized(update):
        await reject_unauthorized(update)
        return

    text = update.effective_message.text
    if not text:
        return

    progress_msg = await update.effective_message.reply_text("🧠 Анализирую сообщение через Gemini API...")

    analysis = analyze_message_with_gemini(text)
    if not analysis:
        await progress_msg.edit_text("❌ Ошибка при обращении к Gemini API. Проверьте логи и ключ GEMINI_API_KEY.")
        return

    if not analysis.has_changes or not analysis.overrides:
        await progress_msg.edit_text(
            f"ℹ️ *В сообщении не обнаружено изменений расписания.*\n\n_{analysis.explanation}_",
            parse_mode=ParseMode.MARKDOWN,
        )
        return

    # Store pending overrides in context
    context.user_data["pending_overrides"] = [ov.model_dump() for ov in analysis.overrides]

    # Build visual preview for widget
    preview_lines = [
        "📋 *Gemini обнаружил следующие изменения в расписании:*\n",
        f"_{analysis.explanation}_\n",
        "Предпросмотр карточек для виджета Google Calendar:\n",
    ]

    for idx, ov in enumerate(analysis.overrides, start=1):
        if ov.action == "cancel":
            title = f"❌ [ОТМЕНА] {ov.subject}"
            color_desc = COLOR_DESCRIPTIONS["8"]
        else:
            emoji = TYPE_EMOJI.get(ov.type, "🟢")
            title = f"⚡ {ov.type} {emoji} {ov.subject}"
            color_id = "10" if "ЛЕК" in ov.type else ("9" if "ПРАК" in ov.type else ("3" if "ЛАБ" in ov.type else "8"))
            color_desc = COLOR_DESCRIPTIONS.get(color_id, "🟩 Зелёный (10)")

        time_line = f"`{ov.time_start}`" if ov.time_start else "_по расписанию / все пары этого типа_"
        loc_line = f"`📍 {ov.location}`" if ov.location else ("_Сохранить исходную аудиторию_" if ov.keep_original_location else "_Не указана_")

        preview_lines.append(
            f"*{idx}. Карточка: {title}*\n"
            f"   • 📅 Дата: `{ov.date}`\n"
            f"   • ⏰ Время: {time_line}\n"
            f"   • 📍 Локация: {loc_line}\n"
            f"   • 🎨 Цвет плашки: {color_desc}\n"
            f"   • 💬 Примечание: _{ov.comment}_\n"
        )

    keyboard = [
        [
            InlineKeyboardButton("✅ Применить в Google Calendar", callback_data="apply_overrides"),
            InlineKeyboardButton("❌ Отмена", callback_data="cancel_overrides"),
        ]
    ]
    reply_markup = InlineKeyboardMarkup(keyboard)

    await progress_msg.edit_text("\n".join(preview_lines), parse_mode=ParseMode.MARKDOWN, reply_markup=reply_markup)


async def callback_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Handles inline button clicks."""
    query = update.callback_query
    await query.answer()

    if not is_authorized(update):
        await query.edit_message_text("⛔ Действие запрещено.")
        return

    data = query.data
    if data == "cancel_overrides":
        context.user_data.pop("pending_overrides", None)
        await query.edit_message_text("❌ Применение изменений отменено пользователем.")
        return

    if data == "apply_overrides":
        pending = context.user_data.get("pending_overrides", [])
        if not pending:
            await query.edit_message_text("⚠️ Нет активных правок для применения.")
            return

        await query.edit_message_text("⏳ Записываю правки, коммичу в репозиторий и синхронизирую с Google Calendar...")

        existing = load_current_overrides()

        # Add unique IDs
        for item in pending:
            item_id = f"ov-{item['date'].replace('-', '')}-{item.get('type', 'les').lower()}-{uuid.uuid4().hex[:6]}"
            item["id"] = item_id
            existing.append(item)

        save_current_overrides(existing)
        github_committed = commit_overrides_to_github(existing)

        # Trigger sync
        try:
            res = trigger_pipeline_and_sync()
            gcal_stats = res.get("gcal_stats")
            stats_str = ""
            if gcal_stats:
                stats_str = f"\nGoogle Calendar Sync: +{gcal_stats['created']} новых, ~{gcal_stats['updated']} обновлено."

            github_str = " (сохранено на GitHub)" if github_committed else " (сохранено локально)"

            await query.edit_message_text(
                f"✅ *Изменения успешно применены!*{github_str}\n\n"
                f"Добавлено новых правок: *{len(pending)}*\n"
                f"Всего событий в календаре: *{res['events_count']}*{stats_str}\n\n"
                "Виджет на телефоне обновится при следующей синхронизации Google Календаря.",
                parse_mode=ParseMode.MARKDOWN,
            )
        except Exception as e:
            logger.exception("Failed to run pipeline after override: %s", e)
            await query.edit_message_text(f"⚠️ Правки записаны, но синхронизация завершилась с ошибкой: `{e}`", parse_mode=ParseMode.MARKDOWN)

        context.user_data.pop("pending_overrides", None)


# ---------------------------------------------------------------------------
# Bot Startup
# ---------------------------------------------------------------------------
def main() -> None:
    token = os.getenv("TELEGRAM_BOT_TOKEN")
    if not token:
        logger.error("TELEGRAM_BOT_TOKEN environment variable is not set!")
        print("Error: TELEGRAM_BOT_TOKEN environment variable is not set.", file=sys.stderr)
        sys.exit(1)

    logger.info("Starting Telegram Bot with authorized users: %s", ALLOWED_USERNAMES)
    app = ApplicationBuilder().token(token).build()

    app.add_handler(CommandHandler("start", start_handler))
    app.add_handler(CommandHandler("status", status_handler))
    app.add_handler(CommandHandler("list", list_handler))
    app.add_handler(CommandHandler("sync", sync_handler))
    app.add_handler(CallbackQueryHandler(callback_handler))
    app.add_handler(MessageHandler(filters.TEXT & (~filters.COMMAND), message_handler))

    app.run_polling()


if __name__ == "__main__":
    main()
