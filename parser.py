#!/usr/bin/env python3
"""
TUSUR Schedule to iCalendar (.ics) Parser
Fetches timetable from timetable.tusur.ru, parses lessons, and exports valid RFC 5545 iCalendar.
Timezone: Asia/Tomsk (UTC+7)
"""

import argparse
import logging
import os
import re
import sys
import time
from collections import defaultdict
from datetime import date, datetime, timedelta
from typing import Dict, List, Optional, Tuple

from bs4 import BeautifulSoup
from icalendar import Alarm, Calendar, Event, Timezone, TimezoneStandard
import pytz
import requests
from requests.adapters import HTTPAdapter
from urllib3.util import Retry

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("tusur_parser")

DEFAULT_URL = "https://timetable.tusur.ru/faculties/fsu/groups/426top-4"
TIMEZONE_STR = "Asia/Tomsk"
OUTPUT_FILE = "schedule.ics"

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

MONTH_MAP = {
    "янв": 1, "фев": 2, "мар": 3, "апр": 4, "май": 5, "мая": 5,
    "июн": 6, "июл": 7, "авг": 8, "сен": 9, "окт": 10, "ноя": 11, "дек": 12,
}

DEFAULT_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language": "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
}


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
    """Fetches HTML page from the given URL."""
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
    finally:
        if close_session:
            session.close()


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
                    # Extract text removing the key prefix
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


def format_lesson_type(lesson_type: str) -> str:
    """Formats lesson type strictly with specified emoji and short code."""
    lt = lesson_type.strip().lower()
    if not lt:
        return ""
    if "лек" in lt:
        return "🟢 [ЛЕК]"
    if "прак" in lt:
        return "🔵 [ПРАК]"
    if "лаб" in lt:
        return "🟣 [ЛАБ]"
    if "сам" in lt or "срс" in lt:
        return "⚪ [СРС]"
    if "курс" in lt:
        return "🌸 [КУРС]"
    if "зач" in lt:
        return "🟡 [ЗАЧ]"
    if "экз" in lt:
        return "🔴 [ЭКЗ]"
    return f"[{lesson_type.strip()}]"


def shorten_subject(name: str) -> str:
    """Case-insensitive abbreviation of subject names according to rules."""
    n_lower = name.strip().lower()
    if "проектирование индивидуальной траектории" in n_lower or "питр" in n_lower:
        return "ПИТР"
    if "основы российской государственности" in n_lower or n_lower == "орг":
        return "ОРГ"
    if "история россии" in n_lower:
        return "история"
    if "иностранный язык" in n_lower:
        return "английский"
    if "физическая культура" in n_lower or "фкис" in n_lower or "физкультура" in n_lower:
        return "физ-ра"
    if "дискретная математика" in n_lower or n_lower == "дм":
        return "дискретка"
    return name.strip()


def format_location(raw_loc: str, short_aud: str = "") -> str:
    """Formats location strictly as '<КОРПУС> <НОМЕР_АУДИТОРИИ>'."""
    combined = f"{raw_loc} {short_aud}".strip().lower()
    if not combined:
        return ""

    building = ""
    if (
        "главный корпус" in combined
        or "пр. ленина, 40" in combined
        or "ленина, 40" in combined
        or re.search(r"\bгк\b", combined)
    ):
        building = "ГК"
    elif (
        "радиотехнический корпус" in combined
        or "ул. вершинина, 47" in combined
        or "вершинина, 47" in combined
        or re.search(r"\bрк\b", combined)
    ):
        building = "РК"
    elif (
        "корпус фэт" in combined
        or "фэт" in combined
        or "красноармейская, 146" in combined
        or "вершинина, 74" in combined
        or re.search(r"\bф\b", combined)
    ):
        building = "ФЭТ"

    room = ""
    # Extract auditorium number e.g. "ауд. 310" or "ауд 310" or "119а"
    m = re.search(r"ауд\.?\s*([0-9]+[а-яёa-z]?)", combined, re.IGNORECASE)
    if m:
        room = m.group(1).upper()
    else:
        m2 = re.search(r"(?:гк|рк|фэт|ф)?\s*([0-9]{3}[а-яёa-z]?)", combined, re.IGNORECASE)
        if m2:
            room = m2.group(1).upper()

    if building and room:
        return f"{building} {room}"
    if building:
        return building
    if room:
        return room
    return raw_loc.strip()


def parse_week_events(html: str, tz: pytz.BaseTzInfo) -> List[Event]:
    """Parses lessons from a single week HTML page and returns list of Event objects."""
    soup = BeautifulSoup(html, "html.parser")
    modals = parse_modals(soup)
    week_start = determine_week_start(soup, modals)

    desktop_table = soup.find("table", class_="table-lessons")
    if not desktop_table:
        logger.warning("Could not find table.table-lessons on the page")
        return []

    events: List[Event] = []
    rows = desktop_table.find("tbody").find_all("tr")

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

        # Bell call times
        time_th = r.find("th", class_="time")
        start_time_str, end_time_str = BELL_SCHEDULE.get(pair_num, ("08:50", "10:25"))
        if time_th:
            spans = time_th.find_all("span")
            if len(spans) >= 2:
                start_time_str = spans[0].get_text(strip=True)
                end_time_str = spans[1].get_text(strip=True)

        cells = r.find_all("td", class_="lesson_cell")
        for c_idx, cell in enumerate(cells):
            # Day number: 1 = Monday, ..., 6 = Saturday
            day_num = c_idx + 1
            for c in cell.get("class", []):
                m = re.match(r"day_(\d+)", c)
                if m:
                    day_num = int(m.group(1))
                    break

            day_date = None
            if week_start:
                day_date = week_start + timedelta(days=day_num - 1)

            lessons = cell.find_all("div", class_="lesson-cell")
            for l_div in lessons:
                disc_span = l_div.find("span", class_="discipline")
                lesson_id = disc_span.get("data-lesson-id") if disc_span else None
                modal_data = modals.get(lesson_id, {}) if lesson_id else {}

                # Fallback / exact date from modal if available
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
                location = format_location(full_aud, short_aud)

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

                # SUMMARY: <МАРКЕР_И_ТИП> <СОКРАЩЕННЫЙ_ПРЕДМЕТ>
                type_marker = format_lesson_type(lesson_type)
                short_subject = shorten_subject(disc_name)
                summary = f"{type_marker} {short_subject}".strip() if type_marker else short_subject

                # DESCRIPTION: полные данные без сокращений
                desc_parts = []
                if disc_name:
                    desc_parts.append(f"Предмет: {disc_name}")
                if lesson_type:
                    desc_parts.append(f"Вид: {lesson_type}")
                if teacher:
                    desc_parts.append(f"Преподаватель: {teacher}")
                if subgroup:
                    desc_parts.append(f"Подгруппа: {subgroup}")
                if full_aud:
                    desc_parts.append(f"Аудитория: {short_aud} ({full_aud})" if short_aud and short_aud != full_aud else f"Аудитория: {full_aud}")
                elif short_aud:
                    desc_parts.append(f"Аудитория: {short_aud}")
                if groups:
                    desc_parts.append(f"Группы: {groups}")
                if modal_data.get("Ссылка на электронный ресурс"):
                    desc_parts.append(f"Ссылка: {modal_data['Ссылка на электронный ресурс']}")

                description = "\n".join(desc_parts)

                # Lesson times
                try:
                    s_h, s_m = map(int, start_time_str.split(":"))
                    e_h, e_m = map(int, end_time_str.split(":"))
                except ValueError:
                    s_h, s_m = map(int, BELL_SCHEDULE[pair_num][0].split(":"))
                    e_h, e_m = map(int, BELL_SCHEDULE[pair_num][1].split(":"))

                dt_start = tz.localize(datetime(day_date.year, day_date.month, day_date.day, s_h, s_m))
                dt_end = tz.localize(datetime(day_date.year, day_date.month, day_date.day, e_h, e_m))

                event = Event()
                event.add("summary", summary)
                event.add("description", description)
                if location:
                    event.add("location", location)
                event.add("dtstart", dt_start)
                event.add("dtend", dt_end)
                event.add("dtstamp", datetime.now(tz))

                uid_id = lesson_id if lesson_id else f"{pair_num}-{day_num}"
                event.add("uid", f"tusur-{uid_id}-{day_date.strftime('%Y%m%d')}@timetable.tusur.ru")

                events.append(event)

    logger.info("Found %d lessons for week starting %s", len(events), week_start)
    return events


def discover_weeks(html: str, base_url: str) -> List[str]:
    """Discovers all semester week URLs from the carousel navigation."""
    soup = BeautifulSoup(html, "html.parser")
    weeks_ul = soup.find("ul", class_="weeks")
    week_urls = []
    if weeks_ul:
        for li in weeks_ul.find_all("li", class_="tile"):
            a = li.find("a")
            if a and a.get("href"):
                href = a["href"]
                if href.startswith("/"):
                    href = "https://timetable.tusur.ru" + href
                week_urls.append(href)
    return week_urls


def build_calendar(events: List[Event], group_name: str = "426топ-4") -> Calendar:
    """Builds a complete RFC 5545 Calendar with VTIMEZONE, VEVENTs, and beer time events."""
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

    # Group lesson events by date to create dynamic beer events
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

    tz = pytz.timezone(TIMEZONE_STR)
    all_events: List[Event] = list(events)

    for day_date, day_lessons in sorted(lessons_by_day.items()):
        # Exclude Sundays (weekday 6) and days without lessons
        if day_date.weekday() == 6 or not day_lessons:
            continue

        # Find the latest end time among all lessons of this day
        latest_end_dt = max(ev.get("dtend").dt for ev in day_lessons)

        beer_ev = Event()
        beer_ev.add("summary", "🍺 Пить пиво дозволено")
        beer_ev.add("dtstart", latest_end_dt)
        beer_ev.add("dtend", latest_end_dt + timedelta(hours=2))
        beer_ev.add("location", "Свобода")
        beer_ev.add("description", "Все пары на сегодня закончились. Время заслуженного отдыха.")
        beer_ev.add("uid", f"beer_{day_date.strftime('%Y%m%d')}@tusur")
        beer_ev.add("dtstamp", datetime.now(tz))

        # Add embedded VALARM notification
        alarm = Alarm()
        alarm.add("action", "DISPLAY")
        alarm.add("description", "🍺 Пить пиво дозволено!")
        alarm.add("trigger", timedelta(minutes=0))
        beer_ev.add_component(alarm)

        all_events.append(beer_ev)

    # De-duplicate events by UID
    seen_uids = set()
    for ev in all_events:
        uid = str(ev.get("uid"))
        if uid not in seen_uids:
            seen_uids.add(uid)
            cal.add_component(ev)

    return cal


def parse_schedule(url: str = DEFAULT_URL, all_weeks: bool = False) -> Calendar:
    """Main parsing orchestrator. Returns populated Calendar object."""
    tz = pytz.timezone(TIMEZONE_STR)
    session = create_http_session()

    try:
        # Fetch initial (current week) page
        main_html = fetch_page(url, session=session)
        events = parse_week_events(main_html, tz)

        if all_weeks:
            week_urls = discover_weeks(main_html, url)
            logger.info("Discovered %d weeks in carousel for full semester parsing", len(week_urls))

            # Identify the currently viewed week's URL to avoid redundant fetching
            soup_main = BeautifulSoup(main_html, "html.parser")
            active_tile = soup_main.find("li", class_=lambda c: c and "tile" in c.split() and ("current" in c.split() or "current-week" in c.split()))
            active_href = ""
            if active_tile and active_tile.find("a"):
                active_href = active_tile.find("a").get("href", "")

            for idx, w_url in enumerate(week_urls, start=1):
                # Skip if already fetched as main page
                if w_url == url or (active_href and w_url.endswith(active_href)):
                    logger.info("Skipping already fetched current week (%d/%d): %s", idx, len(week_urls), w_url)
                    continue

                try:
                    time.sleep(0.3)  # Polite crawling delay between requests
                    logger.info("Processing week %d/%d: %s", idx, len(week_urls), w_url)
                    w_html = fetch_page(w_url, session=session)
                    w_events = parse_week_events(w_html, tz)
                    if not w_events:
                        logger.info("No lessons found for week %s (vacation or session)", w_url)
                    events.extend(w_events)
                except Exception as e:
                    logger.warning("Failed to fetch/parse week %s: %s (skipping and continuing)", w_url, e)

        # Extract group name from title if possible
        soup = BeautifulSoup(main_html, "html.parser")
        h1 = soup.find("h1")
        group_name = "426топ-4"
        if h1:
            m = re.search(r"группы\s+([^\s]+)", h1.get_text())
            if m:
                group_name = m.group(1)

        cal = build_calendar(events, group_name=group_name)
        return cal
    finally:
        session.close()


def main():
    parser = argparse.ArgumentParser(description="Parse TUSUR group schedule into iCalendar (.ics)")
    parser.add_argument(
        "--url",
        default=DEFAULT_URL,
        help=f"Target schedule URL (default: {DEFAULT_URL})",
    )
    parser.add_argument(
        "--output",
        "-o",
        default=OUTPUT_FILE,
        help=f"Output .ics file path (default: {OUTPUT_FILE})",
    )
    parser.add_argument(
        "--all-weeks",
        action="store_true",
        default=os.getenv("PARSE_ALL_WEEKS", "").lower() in ("1", "true", "yes"),
        help="Parse all weeks of the semester instead of only the current week",
    )
    args = parser.parse_args()

    logger.info("Starting TUSUR schedule parser...")
    logger.info("URL: %s", args.url)
    logger.info("Mode: %s", "All Semester Weeks" if args.all_weeks else "Current Study Week")
    logger.info("Timezone: %s", TIMEZONE_STR)

    try:
        cal = parse_schedule(url=args.url, all_weeks=args.all_weeks)
        ical_data = cal.to_ical()

        with open(args.output, "wb") as f:
            f.write(ical_data)

        event_count = len([c for c in cal.walk("vevent")])
        logger.info("SUCCESS: Wrote %d events to %s", event_count, args.output)
        sys.exit(0)
    except Exception as e:
        logger.exception("FATAL: Failed to parse schedule: %s", e)
        sys.exit(1)


if __name__ == "__main__":
    main()
