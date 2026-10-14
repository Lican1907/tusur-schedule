#!/usr/bin/env python3
"""
TUSUR Schedule to iCalendar (.ics) Parser and Google Calendar Synchronizer
Fetches timetable from timetable.tusur.ru, parses lessons, applies teacher overrides,
and exports valid RFC 5545 iCalendar with native Google Calendar colorId support.
Timezone: Asia/Tomsk (UTC+7)
"""

import argparse
import json
import logging
import os
import re
import sys
import time
from collections import defaultdict
from datetime import date, datetime, timedelta
from typing import Any, Dict, List, Optional, Set, Tuple
from zoneinfo import ZoneInfo

from bs4 import BeautifulSoup, Tag
from icalendar import Alarm, Calendar, Event, Timezone, TimezoneStandard
import requests
from requests.adapters import HTTPAdapter
from urllib3.util import Retry

# ---------------------------------------------------------------------------
# Logging Configuration
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("tusur_parser")

# ---------------------------------------------------------------------------
# Constants & Configuration
# ---------------------------------------------------------------------------
TIMEZONE_STR = "Asia/Tomsk"
TIMEZONE = ZoneInfo(TIMEZONE_STR)

DEFAULT_URL = "https://timetable.tusur.ru/faculties/fsu/groups/426top-4"
OUTPUT_FILE = "schedule.ics"
OVERRIDES_FILE = "overrides.json"

# Supported academic groups registry
SUPPORTED_GROUPS: Dict[str, Dict[str, Any]] = {
    "426top-4": {
        "name": "426топ-4",
        "url": "https://timetable.tusur.ru/faculties/fsu/groups/426top-4",
        "output_files": ["schedule.ics", "schedule-426top-4.ics"],
        "sync_gcal": True,
    },
    "426top-1": {
        "name": "426топ-1",
        "url": "https://timetable.tusur.ru/faculties/fsu/groups/426top-1",
        "output_files": ["schedule-426top-1.ics"],
        "sync_gcal": False,
    },
    "426top-2": {
        "name": "426топ-2",
        "url": "https://timetable.tusur.ru/faculties/fsu/groups/426top-2",
        "output_files": ["schedule-426top-2.ics"],
        "sync_gcal": False,
    },
    "426-3": {
        "name": "426-3",
        "url": "https://timetable.tusur.ru/faculties/fsu/groups/426-3",
        "output_files": ["schedule-426-3.ics"],
        "sync_gcal": False,
    },
}

# Bell schedule of TUSUR (Томский государственный университет систем управления и радиоэлектроники)
BELL_SCHEDULE: Dict[int, Tuple[str, str]] = {
    1: ("08:50", "10:25"),
    2: ("10:40", "12:15"),
    3: ("13:15", "14:50"),
    4: ("15:00", "16:35"),
    5: ("16:45", "18:20"),
    6: ("18:30", "20:05"),
    7: ("20:15", "21:50"),
}

# Lesson types mapping to colored emoji tags
TYPE_MAPPING: Dict[str, str] = {
    "лек": "🟢 [ЛЕК]",
    "лекция": "🟢 [ЛЕК]",
    "прак": "🔵 [ПРАК]",
    "практика": "🔵 [ПРАК]",
    "лаб": "🟣 [ЛАБ]",
    "лабораторная": "🟣 [ЛАБ]",
    "лабораторная работа": "🟣 [ЛАБ]",
    "срс": "⚪ [СРС]",
    "сам": "⚪ [СРС]",
    "самостоятельная работа": "⚪ [СРС]",
    "курс": "🌸 [КУРС]",
    "курсовая": "🌸 [КУРС]",
    "курсовое проектирование": "🌸 [КУРС]",
    "зач": "🟡 [ЗАЧ]",
    "зачет": "🟡 [ЗАЧ]",
    "зачёт": "🟡 [ЗАЧ]",
    "диф. зачет": "🟡 [ЗАЧ]",
    "диф.зачет": "🟡 [ЗАЧ]",
    "экз": "🔴 [ЭКЗ]",
    "экзамен": "🔴 [ЭКЗ]",
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

# Google Calendar API colorId mapping
COLOR_ID_MAPPING: Dict[str, str] = {
    "ЛЕК": "10",       # Basil / Green
    "ПРАК": "9",        # Blueberry / Blue
    "ЛАБ": "3",         # Grape / Purple
    "СРС": "8",         # Graphite / Dark Grey
    "КУРС": "4",        # Flamingo / Pink
    "ЗАЧ": "5",         # Banana / Yellow
    "ЭКЗ": "11",        # Tomato / Red
    "CANCELLED": "8",   # Graphite / Grey (subdued for cancelled classes)
    "BEER": "5",        # Banana / Amber (leisure event)
}

# Abbreviations for disciplines
SUBJECT_SHORT_NAMES: Dict[str, str] = {
    "основы искусственного интеллекта": "ОИИ",
    "основы искуственного интеллекта": "ОИИ",
    "искусственный интеллект": "ОИИ",
    "искуственный интеллект": "ОИИ",
    "оии": "ОИИ",
    "дискретная математика": "Дискретка",
    "дм": "Дискретка",
    "основы российской государственности": "ОРГ",
    "орг": "ОРГ",
    "проектирование индивидуальной траектории развития": "ПИТР",
    "проектирование индивидуальной траектории": "ПИТР",
    "питр": "ПИТР",
    "питрвсит": "ПИТР",
    "иностранный язык": "Английский",
    "английский язык": "Английский",
    "английский": "Английский",
    "физическая культура и спорт": "Физ-ра",
    "физическая культура": "Физ-ра",
    "физкультура и спорт": "Физ-ра",
    "физкультура": "Физ-ра",
    "фкис": "Физ-ра",
    "элективные дисциплины по физической культуре и спорту": "Физ-ра",
    "элективные дисциплины по физической культуре": "Физ-ра",
    "элективные дисциплины по фкис": "Физ-ра",
    "история россии": "История",
    "история": "История",
}

# University campus buildings mapping
LOCATION_MAPPING: Dict[str, str] = {
    "главный корпус": "ГК",
    "пр. ленина, 40": "ГК",
    "ленина, 40": "ГК",
    "гк": "ГК",
    "радиотехнический корпус": "РК",
    "ул. вершинина, 47": "РК",
    "вершинина, 47": "РК",
    "рк": "РК",
    "корпус фэт": "ФЭТ",
    "фэт": "ФЭТ",
    "красноармейская, 146": "ФЭТ",
    "вершинина, 74": "ФЭТ",
}

MONTH_MAP: Dict[str, int] = {
    "янв": 1, "фев": 2, "мар": 3, "апр": 4, "май": 5, "мая": 5,
    "июн": 6, "июл": 7, "авг": 8, "сен": 9, "окт": 10, "ноя": 11, "дек": 12,
}

# Schedule of PE rooms / halls by teacher (Faculty of Control Systems, 1st course)
PE_ROOMS_SCHEDULE: Dict[str, Dict[str, str]] = {
    "2026-09-17": {
        "Тарасов А.В.": "1",
        "Помещикова Н.В.": "8",
        "Гасюль А.В.": "1",
        "Помещиков С.В.": "Г\\Б",
        "Гужов Ф.А.": "ЦОФ",
        "Ахметов Н.Р.": "21",
        "Рибицкий Ю.Ю.": "11",
        "Давыдов А.Д.": "4",
        "Бозриков Д.М.": "25",
        "Иконникова К.Д.": "24",
        "Александров В.А.": "ЦОФ",
    },
    "2026-09-24": {
        "Тарасов А.В.": "ЦОФ",
        "Помещикова Н.В.": "ЦОФ",
        "Гасюль А.В.": "8",
        "Помещиков С.В.": "1",
        "Гужов Ф.А.": "7",
        "Ахметов Н.Р.": "25",
        "Рибицкий Ю.Ю.": "4",
        "Давыдов А.Д.": "21",
        "Бозриков Д.М.": "24",
        "Иконникова К.Д.": "5",
        "Александров В.А.": "Г\\Б",
    },
    "2026-10-01": {
        "Тарасов А.В.": "24",
        "Помещикова Н.В.": "1",
        "Гасюль А.В.": "ЦОФ",
        "Помещиков С.В.": "8",
        "Гужов Ф.А.": "Г\\Б",
        "Ахметов Н.Р.": "ЦОФ",
        "Рибицкий Ю.Ю.": "25",
        "Давыдов А.Д.": "1",
        "Бозриков Д.М.": "7",
        "Иконникова К.Д.": "21",
        "Александров В.А.": "5",
    },
    "2026-10-08": {
        "Тарасов А.В.": "8",
        "Помещикова Н.В.": "Г\\Б",
        "Гасюль А.В.": "25",
        "Помещиков С.В.": "21",
        "Гужов Ф.А.": "1",
        "Ахметов Н.Р.": "4",
        "Рибицкий Ю.Ю.": "ЦОФ",
        "Давыдов А.Д.": "ЦОФ",
        "Бозриков Д.М.": "5",
        "Иконникова К.Д.": "24",
        "Александров В.А.": "7",
    },
    "2026-10-15": {
        "Тарасов А.В.": "Г\\Б",
        "Помещикова Н.В.": "4",
        "Гасюль А.В.": "21",
        "Помещиков С.В.": "25",
        "Гужов Ф.А.": "8",
        "Ахметов Н.Р.": "1",
        "Рибицкий Ю.Ю.": "11",
        "Давыдов А.Д.": "24",
        "Бозриков Д.М.": "ЦОФ",
        "Иконникова К.Д.": "ЦОФ",
        "Александров В.А.": "1",
    },
    "2026-10-22": {
        "Тарасов А.В.": "21",
        "Помещикова Н.В.": "5",
        "Гасюль А.В.": "Г\\Б",
        "Помещиков С.В.": "ЦОФ",
        "Гужов Ф.А.": "ЦОФ",
        "Ахметов Н.Р.": "8",
        "Рибицкий Ю.Ю.": "24",
        "Давыдов А.Д.": "4",
        "Бозриков Д.М.": "1",
        "Иконникова К.Д.": "1",
        "Александров В.А.": "25",
    },
    "2026-10-29": {
        "Тарасов А.В.": "1",
        "Помещикова Н.В.": "25",
        "Гасюль А.В.": "4",
        "Помещиков С.В.": "7",
        "Гужов Ф.А.": "24",
        "Ахметов Н.Р.": "Г\\Б",
        "Рибицкий Ю.Ю.": "1",
        "Давыдов А.Д.": "8",
        "Бозриков Д.М.": "21",
        "Иконникова К.Д.": "Г\\Б",
        "Александров В.А.": "ЦОФ",
    },
    "2026-11-05": {
        "Тарасов А.В.": "25",
        "Помещикова Н.В.": "1",
        "Гасюль А.В.": "1",
        "Помещиков С.В.": "4",
        "Гужов Ф.А.": "7",
        "Ахметов Н.Р.": "24",
        "Рибицкий Ю.Ю.": "8",
        "Давыдов А.Д.": "Г\\Б",
        "Бозриков Д.М.": "Г\\Б",
        "Иконникова К.Д.": "21",
        "Александров В.А.": "5",
    },
}

DEFAULT_HEADERS: Dict[str, str] = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language": "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
}


# ---------------------------------------------------------------------------
# Color Helpers
# ---------------------------------------------------------------------------
def get_color_id_for_type(lesson_type: str) -> str:
    """Returns Google Calendar colorId for a lesson type or code."""
    lt = lesson_type.strip().lower()
    if "лек" in lt:
        return COLOR_ID_MAPPING["ЛЕК"]       # "10" (Basil / Green)
    if "прак" in lt:
        return COLOR_ID_MAPPING["ПРАК"]      # "9" (Blueberry / Blue)
    if "лаб" in lt:
        return COLOR_ID_MAPPING["ЛАБ"]       # "3" (Grape / Purple)
    if "сам" in lt or "срс" in lt:
        return COLOR_ID_MAPPING["СРС"]       # "8" (Graphite / Dark Grey)
    if "курс" in lt:
        return COLOR_ID_MAPPING["КУРС"]      # "4" (Flamingo / Pink)
    if "зач" in lt:
        return COLOR_ID_MAPPING["ЗАЧ"]       # "5" (Banana / Yellow)
    if "экз" in lt:
        return COLOR_ID_MAPPING["ЭКЗ"]       # "11" (Tomato / Red)
    return "10"


# ---------------------------------------------------------------------------
# HTTP Fetching
# ---------------------------------------------------------------------------
def create_http_session() -> requests.Session:
    """Creates a requests session with retry strategy and default headers."""
    session = requests.Session()
    session.headers.update(DEFAULT_HEADERS)
    retries = Retry(
        total=5,
        backoff_factor=1,
        status_forcelist=[429, 500, 502, 503, 504],
        raise_on_status=False,
    )
    adapter = HTTPAdapter(max_retries=retries)
    session.mount("http://", adapter)
    session.mount("https://", adapter)
    return session


def fetch_page(url: str, session: Optional[requests.Session] = None) -> str:
    """Fetches HTML page from the given URL with error handling and timeouts."""
    close_session = False
    if session is None:
        session = create_http_session()
        close_session = True

    try:
        logger.info("Fetching schedule from %s", url)
        response = session.get(url, timeout=20)
        response.raise_for_status()
        response.encoding = response.apparent_encoding or "utf-8"
        return response.text
    except requests.RequestException as e:
        logger.error("Failed to fetch schedule from %s: %s", url, e)
        raise
    finally:
        if close_session:
            session.close()


# ---------------------------------------------------------------------------
# HTML & Modal Parsing Helpers
# ---------------------------------------------------------------------------
def parse_modals(soup: BeautifulSoup) -> Dict[str, Dict[str, str]]:
    """Extracts detailed information from all lesson modals on the page."""
    modals: Dict[str, Dict[str, str]] = {}
    for m in soup.find_all("div", class_="js-lesson-info-modal"):
        m_id = m.get("data-lesson-id")
        if not m_id or m_id in modals:
            continue
        title_el = m.find("h4", class_="modal-title")
        title = title_el.get_text(strip=True) if title_el else ""
        info: Dict[str, str] = {"title": title}
        body = m.find("div", class_="modal-body")
        if body:
            for p in body.find_all("p"):
                strong = p.find("strong")
                if strong:
                    key = strong.get_text(strip=True).rstrip(":")
                    full_p_text = p.get_text(separator=" ", strip=True)
                    val = full_p_text.replace(strong.get_text(strip=True), "", 1).strip()
                    info[key] = val
        modals[m_id] = info
    return modals


def determine_week_start(soup: BeautifulSoup, modals: Dict[str, Dict[str, str]]) -> Optional[date]:
    """Determines the start date (Monday) of the week currently displayed in HTML."""
    # 1. Primary source: modal with 'Дата проведения' of actual rendered lessons
    for m_info in modals.values():
        if "Дата проведения" in m_info:
            try:
                d_obj = datetime.strptime(m_info["Дата проведения"], "%d.%m.%Y").date()
                return d_obj - timedelta(days=d_obj.weekday())
            except ValueError:
                pass

    # 2. Secondary source: active carousel tab (has class 'current')
    for tile in soup.find_all("li", class_="tile"):
        classes = tile.get("class", [])
        if "current" in classes:
            text = tile.get_text(separator=" ", strip=True)
            m = re.search(r"с\s+(\d{1,2})\s+([а-яё]+)\s+(\d{4})", text, re.IGNORECASE)
            if m:
                d_str, m_str, y_str = m.groups()
                for prefix, m_num in MONTH_MAP.items():
                    if m_str.lower().startswith(prefix):
                        return date(int(y_str), m_num, int(d_str))

    # 3. Fallback: 'current-week' tile
    cur_week = soup.find("li", class_="current-week")
    if cur_week:
        text = cur_week.get_text(separator=" ", strip=True)
        m = re.search(r"с\s+(\d{1,2})\s+([а-яё]+)\s+(\d{4})", text, re.IGNORECASE)
        if m:
            d_str, m_str, y_str = m.groups()
            for prefix, m_num in MONTH_MAP.items():
                if m_str.lower().startswith(prefix):
                    return date(int(y_str), m_num, int(d_str))

    return None


def discover_weeks(html: str, base_url: str) -> List[str]:
    """Discovers all semester week URLs from the carousel navigation."""
    soup = BeautifulSoup(html, "html.parser")
    weeks_ul = soup.find("ul", class_="weeks")
    week_urls: List[str] = []
    if weeks_ul:
        for li in weeks_ul.find_all("li", class_="tile"):
            a = li.find("a")
            if a and a.get("href"):
                href = a["href"]
                if href.startswith("/"):
                    href = "https://timetable.tusur.ru" + href
                week_urls.append(href)
    return week_urls


# ---------------------------------------------------------------------------
# Data Formatting
# ---------------------------------------------------------------------------
def capitalize_first(text: str) -> str:
    """Capitalizes the first character of text while preserving the rest."""
    s = text.strip()
    if not s:
        return ""
    return s[0].upper() + s[1:]


def shorten_subject(discipline_name: str) -> str:
    """Abbreviates discipline name according to standard TUSUR abbreviations, capitalized."""
    disc_clean = discipline_name.strip()
    n_lower = disc_clean.lower()
    for key, abbr in SUBJECT_SHORT_NAMES.items():
        if key in n_lower or n_lower == key:
            return capitalize_first(abbr)
    return capitalize_first(disc_clean)


def is_pe_lesson(disc_name: str, summary: str = "") -> bool:
    """Checks whether a lesson is Physical Education."""
    text = f"{disc_name} {summary}".lower()
    return (
        "физ-ра" in text
        or "физическ" in text
        or "физкульт" in text
        or "фкис" in text
    )


def format_event_data(
    discipline_name: str,
    lesson_type: str,
    raw_location: str = "",
    short_aud: str = "",
) -> Tuple[str, str]:
    """
    Formats clean SUMMARY and LOCATION with a pin for a standard lesson event.
    Returns: (summary, clean_location)
    - summary: '[TYPE] Subject' (e.g. '[ЛЕК] Математика', '[ПРАК] физ-ра')
    - location: '📍 BUILDING ROOM', '📍 BUILDING', '📍 ROOM', or clean string with pin
    """
    # 1. Lesson type marker
    lt = lesson_type.strip().lower()
    type_marker = ""
    if lt:
        for key, marker in TYPE_MAPPING.items():
            if key in lt:
                type_marker = marker
                break
        if not type_marker:
            clean_t = re.sub(r"[\[\]\s]", "", lesson_type).upper()
            emoji = TYPE_EMOJI.get(clean_t, "")
            type_marker = f"{emoji} [{clean_t}]".strip() if clean_t else ""

    # 2. Short subject name
    short_subject = shorten_subject(discipline_name)

    if type_marker and short_subject:
        summary = f"{type_marker} {short_subject}"
    elif type_marker:
        summary = type_marker
    else:
        summary = short_subject

    # 3. Clean location
    if is_pe_lesson(discipline_name, short_subject):
        clean_location = "см. описание"
    else:
        combined = f"{raw_location} {short_aud}".strip().lower()
        building = ""
        if any(k in combined for k in ("главный корпус", "пр. ленина, 40", "ленина, 40")) or re.search(r"\bгк\b", combined):
            building = "ГК"
        elif any(k in combined for k in ("радиотехнический корпус", "ул. вершинина, 47", "вершинина, 47")) or re.search(r"\bрк\b", combined):
            building = "РК"
        elif any(k in combined for k in ("корпус фэт", "вершинина, 74", "красноармейская, 146")) or "фэт" in combined or re.search(r"\bф\b", combined):
            building = "ФЭТ"

        room = ""
        m_room = re.search(r"ауд\.?\s*([0-9]+[а-яёa-z]?)", combined, re.IGNORECASE)
        if m_room:
            room = m_room.group(1).upper()
        else:
            m_num = re.search(r"(?:гк|рк|фэт|ф)?\s*([0-9]{3}[а-яёa-z]?)", combined, re.IGNORECASE)
            if m_num:
                room = m_num.group(1).upper()

        if building and room:
            clean_location = f"{building} {room}"
        elif building:
            clean_location = building
        elif room:
            clean_location = room
        else:
            clean = re.sub(r"\bауд\.?\s*", "", raw_location, flags=re.IGNORECASE).strip()
            clean = re.sub(r"^[,\.\s]+|[,\.\s]+$", "", clean)
            clean_location = clean or short_aud.strip()

    # Prepend pin 📍 if location is present
    if clean_location:
        if not clean_location.startswith("📍"):
            clean_location = f"📍 {clean_location}"

    return summary, clean_location


def format_pe_room(room_val: str) -> str:
    """Formats room name for PE schedule (e.g. 'ауд. 1' or 'Г\\Б')."""
    room_clean = room_val.strip()
    if room_clean.isdigit():
        return f"ауд. {room_clean}"
    return room_clean


def get_pe_rooms_for_date(event_date: date) -> Optional[Dict[str, str]]:
    """
    Returns PE rooms dictionary for a given date by direct date match
    or by matching the ISO study week.
    """
    date_str = event_date.strftime("%Y-%m-%d")
    if date_str in PE_ROOMS_SCHEDULE:
        return PE_ROOMS_SCHEDULE[date_str]

    ev_iso = event_date.isocalendar()[:2]  # (year, week)
    for key_date_str, rooms in PE_ROOMS_SCHEDULE.items():
        try:
            kd = datetime.strptime(key_date_str, "%Y-%m-%d").date()
            if kd.isocalendar()[:2] == ev_iso:
                return rooms
        except ValueError:
            continue
    return None


# ---------------------------------------------------------------------------
# Cell & Event Parsing
# ---------------------------------------------------------------------------
def parse_cell(
    cell: Tag,
    pair_num: int,
    default_date: Optional[date],
    modals: Dict[str, Dict[str, str]],
    tz: ZoneInfo = TIMEZONE,
) -> List[Event]:
    """
    Parses a single schedule table cell (td.lesson_cell).
    Extracts discipline, type, teacher, auditorium, subgroup notes,
    detects shifted start times ('с 12:30' for pair 3 -> strictly 12:30-14:05),
    and creates Event objects for all lessons in the cell.
    """
    events: List[Event] = []
    lessons = cell.find_all("div", class_="lesson-cell")

    for l_div in lessons:
        disc_span = l_div.find("span", class_="discipline")
        lesson_id = disc_span.get("data-lesson-id") if disc_span else None
        modal_data = modals.get(lesson_id, {}) if lesson_id else {}

        # Resolve exact date
        day_date = default_date
        if "Дата проведения" in modal_data:
            try:
                day_date = datetime.strptime(modal_data["Дата проведения"], "%d.%m.%Y").date()
            except ValueError:
                pass

        if not day_date:
            logger.warning("Could not determine date for lesson %s, skipping", lesson_id)
            continue

        # Subject title
        disc_name = modal_data.get("title") or (disc_span.get_text(strip=True) if disc_span else "Занятие")

        # Lesson type (e.g. Лекция, Практика, Лабораторная работа)
        kind_span = l_div.find("span", class_="kind")
        abbr = kind_span.find("abbr") if kind_span else None
        cell_kind = (abbr.get("title") if abbr and abbr.get("title") else kind_span.get_text(strip=True)) if kind_span else ""
        lesson_type = modal_data.get("Вид занятия") or cell_kind

        # Auditorium & Location
        aud_span = l_div.find("span", class_="auditoriums")
        tooltip_i = aud_span.find("i", class_="js-tooltip") if aud_span else None
        full_aud = tooltip_i.get("title") if tooltip_i and tooltip_i.get("title") else ""
        short_aud = modal_data.get("Место проведения") or (aud_span.get_text(strip=True) if aud_span else "")

        # Format summary and clean location
        summary, location = format_event_data(disc_name, lesson_type, full_aud, short_aud)

        # Subgroup / comments / notes
        note_span = l_div.find("span", class_="note")
        subgroup = (
            modal_data.get("Подгруппа")
            or modal_data.get("Комментарий")
            or (note_span.get_text(strip=True) if note_span else "")
        )

        # Teacher
        teacher = modal_data.get("Преподаватель")
        if not teacher:
            grp_span = l_div.find("span", class_="group")
            teacher = grp_span.get_text(strip=True) if grp_span else ""

        # Additional groups
        groups = modal_data.get("Совместно с группами") or modal_data.get("Группы") or ""

        # Detailed description
        desc_parts: List[str] = []
        if disc_name:
            desc_parts.append(f"Предмет: {disc_name}")
        if lesson_type:
            desc_parts.append(f"Вид: {lesson_type}")
        if teacher:
            desc_parts.append(f"Преподаватель: {teacher}")
        if subgroup:
            desc_parts.append(f"Подгруппа: {subgroup}")
        if full_aud:
            desc_parts.append(
                f"Аудитория: {short_aud} ({full_aud})"
                if short_aud and short_aud != full_aud
                else f"Аудитория: {full_aud}"
            )
        elif short_aud:
            desc_parts.append(f"Аудитория: {short_aud}")
        if groups:
            desc_parts.append(f"Группы: {groups}")
        if modal_data.get("Ссылка на электронный ресурс"):
            desc_parts.append(f"Ссылка: {modal_data['Ссылка на электронный ресурс']}")

        # Check if lesson is Physical Education and append rooms schedule
        if is_pe_lesson(disc_name, summary):
            pe_rooms = get_pe_rooms_for_date(day_date)
            if pe_rooms:
                desc_parts.append("")
                desc_parts.append("🏟️ Аудитории по преподавателям:")
                for teacher_name, room_val in pe_rooms.items():
                    desc_parts.append(f"• {teacher_name} — {format_pe_room(room_val)}")

        description = "\n".join(desc_parts)

        # Check for shifted start time (e.g. badge 'с 12:30' or modal note)
        shift_search = f"{str(cell)} {modal_data.get('Комментарий', '')} {modal_data.get('Подгруппа', '')} {subgroup}"
        m_shift = re.search(r"с\s*(\d{1,2})[:.](\d{2})", shift_search, re.IGNORECASE)

        if (pair_num == 3 and "12:30" in shift_search) or (
            m_shift and pair_num == 3 and m_shift.group(1) == "12" and m_shift.group(2) == "30"
        ):
            s_h, s_m = 12, 30
            e_h, e_m = 14, 5  # Strictly 12:30–14:05 (95 min) for 3rd pair shift
        elif m_shift:
            s_h = int(m_shift.group(1))
            s_m = int(m_shift.group(2))
            start_mins = s_h * 60 + s_m
            end_mins = start_mins + 95  # Standard lesson duration: 95 minutes
            e_h = (end_mins // 60) % 24
            e_m = end_mins % 60
        else:
            start_time_str, end_time_str = BELL_SCHEDULE.get(pair_num, ("08:50", "10:25"))
            s_h, s_m = map(int, start_time_str.split(":"))
            e_h, e_m = map(int, end_time_str.split(":"))

        dt_start = datetime(day_date.year, day_date.month, day_date.day, s_h, s_m, tzinfo=tz)
        dt_end = datetime(day_date.year, day_date.month, day_date.day, e_h, e_m, tzinfo=tz)

        event = Event()
        event.add("summary", summary)
        event.add("description", description)
        if location:
            event.add("location", location)
        event.add("dtstart", dt_start)
        event.add("dtend", dt_end)
        event.add("dtstamp", datetime.now(tz))

        color_id = get_color_id_for_type(lesson_type or cell_kind)
        event.add("x-color-id", color_id)

        day_num = day_date.weekday() + 1
        uid_id = lesson_id if lesson_id else f"{pair_num}-{day_num}"
        event.add("uid", f"tusur-{uid_id}-{day_date.strftime('%Y%m%d')}@timetable.tusur.ru")

        events.append(event)

    return events


def parse_week_events(html: str, tz: ZoneInfo = TIMEZONE) -> List[Event]:
    """Parses lessons from a single week HTML page and returns list of Event objects."""
    soup = BeautifulSoup(html, "html.parser")
    modals = parse_modals(soup)
    week_start = determine_week_start(soup, modals)

    desktop_table = soup.find("table", class_="table-lessons")
    if not desktop_table:
        logger.warning("Could not find table.table-lessons on the page")
        return []

    events: List[Event] = []
    tbody = desktop_table.find("tbody")
    rows = tbody.find_all("tr") if tbody else desktop_table.find_all("tr")

    for r in rows:
        row_classes = r.get("class", [])
        pair_num = None
        for c in row_classes:
            m = re.match(r"lesson_(\d+)", c)
            if m:
                pair_num = int(m.group(1))
                break
        if not pair_num:
            continue

        cells = r.find_all("td", class_="lesson_cell")
        for c_idx, cell in enumerate(cells):
            day_num = c_idx + 1
            for c in cell.get("class", []):
                m = re.match(r"day_(\d+)", c)
                if m:
                    day_num = int(m.group(1))
                    break

            default_date = None
            if week_start:
                default_date = week_start + timedelta(days=day_num - 1)

            cell_events = parse_cell(
                cell=cell,
                pair_num=pair_num,
                default_date=default_date,
                modals=modals,
                tz=tz,
            )
            events.extend(cell_events)

    logger.info("Found %d lessons for week starting %s", len(events), week_start)
    return events


# ---------------------------------------------------------------------------
# Overrides System
# ---------------------------------------------------------------------------
def load_overrides(filepath: str = OVERRIDES_FILE) -> List[Dict[str, Any]]:
    """Loads schedule overrides from JSON file if present."""
    if not os.path.exists(filepath):
        return []
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            data = json.load(f)
            if isinstance(data, list):
                return data
            logger.warning("Overrides file %s is not a list, skipping overrides", filepath)
            return []
    except Exception as e:
        logger.error("Failed to read overrides from %s: %s", filepath, e)
        return []


def apply_overrides(
    events: List[Event],
    overrides: List[Dict[str, Any]],
    group_name: str = "426топ-4",
    tz: ZoneInfo = TIMEZONE,
) -> List[Event]:
    """
    Applies manual teacher overrides from overrides.json to events:
    - 'cancel': changes summary to '❌ [ОТМЕНА] <Предмет>', sets colorId='8', marks cancelled
    - 'set': replaces or adds lesson with '⚡ [<ТИП>] <Предмет>', sets colorId and location
    Overrides are filtered by group_name (defaulting to 426топ-4 if unspecified in override).
    """
    if not overrides:
        return events

    result_events: List[Event] = list(events)

    for ov in overrides:
        ov_id = ov.get("id", "unknown")

        # Check group applicability
        ov_group = ov.get("group")
        if ov_group:
            ov_group_clean = str(ov_group).strip().lower().replace("топ", "top")
            cur_group_clean = str(group_name).strip().lower().replace("топ", "top")
            if ov_group_clean != "all" and ov_group_clean != cur_group_clean:
                continue
        else:
            # If no group specified, defaults strictly to 426топ-4 / 426top-4 for safety
            cur_group_clean = str(group_name).strip().lower().replace("топ", "top")
            if cur_group_clean not in ("426top-4", "426топ-4"):
                continue

        date_str = ov.get("date")
        if not date_str:
            continue
        try:
            target_date = datetime.strptime(date_str, "%Y-%m-%d").date()
        except ValueError:
            logger.warning("Invalid date format in override %s: %s", ov_id, date_str)
            continue

        action = ov.get("action", "").lower().strip()
        time_start = ov.get("time_start")  # e.g. "10:40" or None
        ov_type = ov.get("type", "").strip().upper()  # e.g. "ЛЕК", "ПРАК"
        ov_subject = ov.get("subject", "").strip()  # e.g. "Дискретка", "Математика"
        cap_subject = ov_subject[0].upper() + ov_subject[1:] if ov_subject else ""
        comment = ov.get("comment", "").strip()
        keep_orig_loc = bool(ov.get("keep_original_location", False))
        ov_loc = ov.get("location")

        if action == "cancel":
            matched = False
            for ev in result_events:
                dtstart_prop = ev.get("dtstart")
                if not dtstart_prop or not hasattr(dtstart_prop, "dt"):
                    continue
                ev_dt = dtstart_prop.dt
                ev_date = ev_dt.date() if isinstance(ev_dt, datetime) else ev_dt
                if ev_date != target_date:
                    continue

                if time_start and isinstance(ev_dt, datetime):
                    if ev_dt.strftime("%H:%M") != time_start:
                        continue

                ev_summary = str(ev.get("summary", ""))
                ev_desc = str(ev.get("description", ""))

                # Check subject match (case-insensitive substring)
                subj_match = False
                if ov_subject:
                    subj_lower = ov_subject.lower()
                    if subj_lower in ev_summary.lower() or subj_lower in ev_desc.lower():
                        subj_match = True
                    elif shorten_subject(ev_summary).lower() == subj_lower:
                        subj_match = True
                else:
                    subj_match = True

                # Check type match if specified
                type_match = True
                if ov_type:
                    type_clean = re.sub(r"[\[\]\s]", "", ov_type).lower()
                    type_match = type_clean in ev_summary.lower() or type_clean in ev_desc.lower()

                if subj_match and type_match:
                    matched = True
                    # Update summary strictly to: ❌ [ОТМЕНА] <Предмет с заглавной буквы>
                    del ev["summary"]
                    ev.add("summary", f"❌ [ОТМЕНА] {cap_subject}")

                    # Update colorId to "8" (Graphite / Grey)
                    if "x-color-id" in ev:
                        del ev["x-color-id"]
                    ev.add("x-color-id", "8")
                    ev.add("x-status", "CANCELLED")

                    # Prepend comment to description
                    prefix = f"❌ Занятие отменено преподавателем: {comment}" if comment else "❌ Занятие отменено преподавателем"
                    old_desc = str(ev.get("description", ""))
                    del ev["description"]
                    ev.add("description", f"{prefix}\n{old_desc}".strip())

                    logger.info("Override %s: Cancelled '%s' on %s", ov_id, cap_subject, target_date)

            if not matched:
                logger.warning("Override %s: No matching lesson found to cancel on %s for %s %s", ov_id, target_date, ov_type, ov_subject)

        elif action == "set":
            matched_ev = None
            if time_start:
                for ev in result_events:
                    dtstart_prop = ev.get("dtstart")
                    if not dtstart_prop or not hasattr(dtstart_prop, "dt"):
                        continue
                    ev_dt = dtstart_prop.dt
                    ev_date = ev_dt.date() if isinstance(ev_dt, datetime) else ev_dt
                    if ev_date == target_date and isinstance(ev_dt, datetime):
                        if ev_dt.strftime("%H:%M") == time_start:
                            matched_ev = ev
                            break

            if matched_ev is not None:
                # Existing event modified/replaced
                old_loc = str(matched_ev.get("location", ""))
                if keep_orig_loc and old_loc:
                    loc = old_loc
                elif ov_loc:
                    loc = f"📍 {ov_loc}" if not ov_loc.startswith("📍") else ov_loc
                elif is_pe_lesson(ov_subject):
                    loc = "📍 см. описание"
                else:
                    loc = old_loc

                del matched_ev["summary"]
                emoji = TYPE_EMOJI.get(ov_type, "")
                marker = f"{emoji} [{ov_type}]".strip() if emoji else f"[{ov_type}]"
                matched_ev.add("summary", f"⚡ {marker} {cap_subject}".strip())

                if "location" in matched_ev:
                    del matched_ev["location"]
                if loc:
                    matched_ev.add("location", loc)

                color_id = COLOR_ID_MAPPING.get(ov_type, "10")
                if "x-color-id" in matched_ev:
                    del matched_ev["x-color-id"]
                matched_ev.add("x-color-id", color_id)

                prefix = f"⚡ Изменение в расписании от преподавателя: {comment}" if comment else "⚡ Изменение в расписании от преподавателя"
                old_desc = str(matched_ev.get("description", ""))
                del matched_ev["description"]
                matched_ev.add("description", f"{prefix}\n{old_desc}".strip())

                logger.info("Override %s: Replaced event at %s %s with '⚡ [%s] %s'", ov_id, target_date, time_start, ov_type, cap_subject)
            else:
                # Add as new event
                if not time_start:
                    logger.warning("Override %s: Cannot add new lesson without time_start on %s", ov_id, target_date)
                    continue

                s_h, s_m = map(int, time_start.split(":"))
                # End time determination from bell schedule or +95m
                e_h, e_m = None, None
                for p_num, (s_str, e_str) in BELL_SCHEDULE.items():
                    if s_str == time_start:
                        e_h, e_m = map(int, e_str.split(":"))
                        break
                if e_h is None:
                    end_mins = s_h * 60 + s_m + 95
                    e_h = (end_mins // 60) % 24
                    e_m = end_mins % 60

                dt_start = datetime(target_date.year, target_date.month, target_date.day, s_h, s_m, tzinfo=tz)
                dt_end = datetime(target_date.year, target_date.month, target_date.day, e_h, e_m, tzinfo=tz)

                new_ev = Event()
                emoji = TYPE_EMOJI.get(ov_type, "")
                marker = f"{emoji} [{ov_type}]".strip() if emoji else f"[{ov_type}]"
                new_ev.add("summary", f"⚡ {marker} {cap_subject}".strip())
                desc = f"⚡ Изменение в расписании от преподавателя: {comment}" if comment else "⚡ Изменение в расписании от преподавателя"
                if is_pe_lesson(ov_subject):
                    pe_rooms = get_pe_rooms_for_date(target_date)
                    if pe_rooms:
                        desc += "\n\n🏟️ Аудитории по преподавателям:\n" + "\n".join(
                            f"• {t_name} — {format_pe_room(r_val)}" for t_name, r_val in pe_rooms.items()
                        )
                new_ev.add("description", desc)
                if ov_loc:
                    loc = f"📍 {ov_loc}" if not ov_loc.startswith("📍") else ov_loc
                    new_ev.add("location", loc)
                elif is_pe_lesson(ov_subject):
                    new_ev.add("location", "📍 см. описание")

                new_ev.add("dtstart", dt_start)
                new_ev.add("dtend", dt_end)
                new_ev.add("dtstamp", datetime.now(tz))
                color_id = COLOR_ID_MAPPING.get(ov_type, "10")
                new_ev.add("x-color-id", color_id)
                new_ev.add("uid", f"tusur-override-{ov_id}-{target_date.strftime('%Y%m%d')}@{TIMEZONE_STR}")

                result_events.append(new_ev)
                logger.info("Override %s: Created new event on %s at %s: '⚡ [%s] %s'", ov_id, target_date, time_start, ov_type, cap_subject)

    return result_events


# ---------------------------------------------------------------------------
# Beer Event Builder
# ---------------------------------------------------------------------------
def build_beer_event(
    target_date: date,
    lessons_today: List[Event],
    tz: ZoneInfo = TIMEZONE,
) -> Optional[Event]:
    """
    Creates a VEVENT '🍺 Можно пить пиво!' with VALARM strictly for the current day
    (target_date == today), provided there were lessons on that day.
    """
    today = datetime.now(tz).date()
    if target_date != today:
        return None

    # Filter out cancelled lessons for beer time calculation!
    active_lessons = [
        ev for ev in lessons_today
        if not str(ev.get("summary", "")).startswith("❌")
        and str(ev.get("x-status", "")).upper() != "CANCELLED"
    ]

    if not active_lessons:
        return None

    # Latest end time among active today's lessons
    end_times: List[datetime] = []
    for ev in active_lessons:
        dtend_prop = ev.get("dtend")
        if dtend_prop and hasattr(dtend_prop, "dt"):
            dt = dtend_prop.dt
            if isinstance(dt, datetime):
                end_times.append(dt)

    if not end_times:
        return None

    latest_end_dt = max(end_times)

    beer_ev = Event()
    beer_ev.add("summary", "🍺 Можно пить пиво!")
    beer_ev.add("dtstart", latest_end_dt)
    beer_ev.add("dtend", latest_end_dt + timedelta(hours=2))
    beer_ev.add("location", "🎉 Свобода")
    beer_ev.add("description", "Все пары на сегодня закончились. Время заслуженного отдыха.")
    beer_ev.add("uid", f"beer_{target_date.strftime('%Y%m%d')}@tusur")
    beer_ev.add("dtstamp", datetime.now(tz))
    beer_ev.add("x-color-id", "5")  # Banana / Amber

    alarm = Alarm()
    alarm.add("action", "DISPLAY")
    alarm.add("description", "🍺 Можно пить пиво!")
    alarm.add("trigger", timedelta(minutes=0))
    beer_ev.add_component(alarm)

    return beer_ev


# ---------------------------------------------------------------------------
# Calendar Builder & Aggregator
# ---------------------------------------------------------------------------
def build_calendar(
    events: List[Event],
    group_name: str = "426топ-4",
    tz: ZoneInfo = TIMEZONE,
) -> Calendar:
    """Builds a complete RFC 5545 Calendar with VTIMEZONE, VEVENTs, and beer time event."""
    cal = Calendar()
    cal.add("prodid", "-//TUSUR Timetable Parser//timetable.tusur.ru//RU")
    cal.add("version", "2.0")
    cal.add("calscale", "GREGORIAN")
    cal.add("method", "PUBLISH")
    cal.add("x-wr-calname", f"Расписание ТУСУР {group_name}")
    cal.add("x-wr-timezone", TIMEZONE_STR)
    cal.add("x-wr-caldesc", f"Расписание учебных занятий группы {group_name} ТУСУР")

    # Add VTIMEZONE for Asia/Tomsk
    tz_comp = Timezone()
    tz_comp.add("tzid", TIMEZONE_STR)
    tz_std = TimezoneStandard()
    tz_std.add("tzname", "+07")
    tz_std.add("dtstart", datetime(1970, 1, 1, 0, 0, 0))
    tz_std.add("tzoffsetfrom", timedelta(hours=7))
    tz_std.add("tzoffsetto", timedelta(hours=7))
    tz_comp.add_component(tz_std)
    cal.add_component(tz_comp)

    # Group lesson events by date to check for today's lessons (excluding beer events)
    lessons_by_day: Dict[date, List[Event]] = defaultdict(list)
    for ev in events:
        uid = str(ev.get("uid", ""))
        if uid.startswith("beer_"):
            continue
        dtstart = ev.get("dtstart")
        if dtstart and hasattr(dtstart, "dt"):
            d = dtstart.dt
            ev_date = d.date() if isinstance(d, datetime) else d
            lessons_by_day[ev_date].append(ev)

    today = datetime.now(tz).date()
    all_events: List[Event] = list(events)

    # Add beer event strictly for today if there were active lessons
    today_lessons = lessons_by_day.get(today, [])
    beer_ev = build_beer_event(target_date=today, lessons_today=today_lessons, tz=tz)
    if beer_ev:
        all_events.append(beer_ev)
        logger.info("Added beer event for today (%s)", today)
    else:
        logger.info("No beer event created for today (%s): no active lessons today or weekend", today)

    # De-duplicate events by UID
    seen_uids: Set[str] = set()
    for ev in all_events:
        uid = str(ev.get("uid"))
        if uid not in seen_uids:
            seen_uids.add(uid)
            cal.add_component(ev)

    return cal


def parse_semester(
    url: str = DEFAULT_URL,
    all_weeks: bool = True,
    group_name: Optional[str] = None,
    tz: ZoneInfo = TIMEZONE,
) -> Calendar:
    """
    Main parsing orchestrator. Aggregates schedule events across the semester
    (or single week), applies overrides, deduplicates by UID, and returns Calendar.
    """
    session = create_http_session()

    try:
        main_html = fetch_page(url, session=session)
        events = parse_week_events(main_html, tz=tz)

        if all_weeks:
            week_urls = discover_weeks(main_html, url)
            logger.info("Discovered %d weeks in carousel for full semester parsing", len(week_urls))

            # Identify the currently viewed week URL to avoid redundant requests
            soup_main = BeautifulSoup(main_html, "html.parser")
            active_tile = soup_main.find(
                "li",
                class_=lambda c: c and "tile" in c.split() and ("current" in c.split() or "current-week" in c.split()),
            )
            active_href = ""
            if active_tile and active_tile.find("a"):
                active_href = active_tile.find("a").get("href", "")

            for idx, w_url in enumerate(week_urls, start=1):
                if w_url == url or (active_href and w_url.endswith(active_href)):
                    logger.info("Skipping already fetched current week (%d/%d): %s", idx, len(week_urls), w_url)
                    continue

                try:
                    time.sleep(0.3)  # Polite crawling delay
                    logger.info("Processing week %d/%d: %s", idx, len(week_urls), w_url)
                    w_html = fetch_page(w_url, session=session)
                    w_events = parse_week_events(w_html, tz=tz)
                    if not w_events:
                        logger.info("No lessons found for week %s (vacation or session)", w_url)
                    events.extend(w_events)
                except Exception as e:
                    logger.warning("Failed to fetch/parse week %s: %s (skipping and continuing)", w_url, e)

        # Extract group name from title if present
        soup = BeautifulSoup(main_html, "html.parser")
        h1 = soup.find("h1")
        if not group_name:
            group_name = "426топ-4"
            if h1:
                m = re.search(r"группы\s+([^\s]+)", h1.get_text())
                if m:
                    group_name = m.group(1)

        # Apply teacher overrides if present
        overrides = load_overrides()
        if overrides:
            logger.info("Applying %d schedule overrides from %s (group: %s)", len(overrides), OVERRIDES_FILE, group_name)
            events = apply_overrides(events, overrides, group_name=group_name, tz=tz)

        cal = build_calendar(events, group_name=group_name, tz=tz)
        return cal
    finally:
        session.close()


def parse_and_save_group(
    group_key: str,
    group_cfg: Dict[str, Any],
    all_weeks: bool = True,
    tz: ZoneInfo = TIMEZONE,
) -> Calendar:
    """Parses semester schedule for a specific configured group and writes its .ics files."""
    name = group_cfg.get("name", group_key)
    url = group_cfg["url"]
    output_files = group_cfg.get("output_files", [f"schedule-{group_key}.ics"])
    sync_gcal = group_cfg.get("sync_gcal", False)

    logger.info("=== Parsing group: %s (%s) ===", name, group_key)
    cal = parse_semester(url=url, all_weeks=all_weeks, group_name=name, tz=tz)
    ical_data = cal.to_ical()

    for out_file in output_files:
        with open(out_file, "wb") as f:
            f.write(ical_data)
        event_count = len([c for c in cal.walk("vevent")])
        logger.info("Saved %d events for %s to %s", event_count, name, out_file)

    if sync_gcal:
        try:
            from gcal_sync import sync_calendar_if_configured
            sync_calendar_if_configured(cal)
        except ImportError:
            logger.debug("gcal_sync module not loaded or dependencies missing")
        except Exception as e:
            logger.warning("Google Calendar sync encountered an issue: %s", e)

    return cal


def parse_all_groups(
    all_weeks: bool = True,
    tz: ZoneInfo = TIMEZONE,
) -> Dict[str, Calendar]:
    """Parses and updates schedules for all supported groups."""
    results: Dict[str, Calendar] = {}
    for group_key, group_cfg in SUPPORTED_GROUPS.items():
        try:
            cal = parse_and_save_group(group_key, group_cfg, all_weeks=all_weeks, tz=tz)
            results[group_key] = cal
            time.sleep(0.3)  # Polite crawling delay between different groups
        except Exception as e:
            logger.exception("Failed to parse group %s: %s", group_key, e)
    return results


# Backward compatibility alias
parse_schedule = parse_semester


# ---------------------------------------------------------------------------
# CLI Entrypoint
# ---------------------------------------------------------------------------
def main() -> None:
    """CLI entrypoint with argument parsing and multi-group support."""
    parser = argparse.ArgumentParser(description="Parse TUSUR group schedule into iCalendar (.ics)")
    parser.add_argument(
        "--group",
        default="all",
        choices=["all"] + list(SUPPORTED_GROUPS.keys()),
        help="Target group to parse (default: all)",
    )
    parser.add_argument(
        "--url",
        default=None,
        help="Target schedule URL (overrides group settings if specified)",
    )
    parser.add_argument(
        "--output",
        "-o",
        default=None,
        help="Output .ics file path (overrides group settings if specified)",
    )
    parser.add_argument(
        "--all-weeks",
        action="store_true",
        default=os.getenv("PARSE_ALL_WEEKS", "").lower() in ("1", "true", "yes"),
        help="Parse all weeks of the semester instead of only the current week",
    )
    args = parser.parse_args()

    logger.info("Starting TUSUR schedule parser...")
    logger.info("Mode: %s", "All Semester Weeks" if args.all_weeks else "Current Study Week")
    logger.info("Timezone: %s", TIMEZONE_STR)

    try:
        # Legacy single-URL explicit invocation
        if args.url:
            output_file = args.output or OUTPUT_FILE
            cal = parse_semester(url=args.url, all_weeks=args.all_weeks)
            ical_data = cal.to_ical()
            with open(output_file, "wb") as f:
                f.write(ical_data)
            event_count = len([c for c in cal.walk("vevent")])
            logger.info("SUCCESS: Wrote %d events to %s", event_count, output_file)
            if args.url == DEFAULT_URL:
                try:
                    from gcal_sync import sync_calendar_if_configured
                    sync_calendar_if_configured(cal)
                except Exception as e:
                    logger.warning("Google Calendar sync encountered an issue: %s", e)
            sys.exit(0)

        # Multi-group parsing
        if args.group == "all":
            logger.info("Parsing all %d supported groups: %s", len(SUPPORTED_GROUPS), list(SUPPORTED_GROUPS.keys()))
            parse_all_groups(all_weeks=args.all_weeks)
        else:
            group_cfg = dict(SUPPORTED_GROUPS[args.group])
            if args.output:
                group_cfg["output_files"] = [args.output]
            parse_and_save_group(args.group, group_cfg, all_weeks=args.all_weeks)

        logger.info("SUCCESS: Completed schedule parsing")
        sys.exit(0)
    except Exception as e:
        logger.exception("FATAL: Failed to parse schedule: %s", e)
        sys.exit(1)


if __name__ == "__main__":
    main()
