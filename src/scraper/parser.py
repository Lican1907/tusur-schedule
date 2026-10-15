"""HTML parsing and entity extraction for timetable.tusur.ru."""

from datetime import date, datetime, timedelta
import logging
import re
from typing import Any, Dict, List, Optional, Tuple
from zoneinfo import ZoneInfo

from bs4 import BeautifulSoup
from bs4.element import Tag
from icalendar import Event

from src.config import (
    BELL_SCHEDULE,
    COLOR_ID_MAPPING,
    GROUP_PE_TEACHERS,
    LOCATION_MAPPING,
    MONTH_MAP,
    PE_ROOMS_SCHEDULE,
    SUBJECT_SHORT_NAMES,
    SUPPORTED_GROUPS,
    TIMEZONE,
    TIMEZONE_STR,
    TYPE_EMOJI,
    TYPE_MAPPING,
)
from src.core.models import Lesson

logger = logging.getLogger("tusur_parser")


def get_color_id_for_type(lesson_type: str) -> str:
    """Returns Google Calendar colorId for a lesson type or code."""
    lt = lesson_type.strip().lower()
    if "лек" in lt:
        return COLOR_ID_MAPPING.get("ЛЕК", "10")
    if "прак" in lt:
        return COLOR_ID_MAPPING.get("ПРАК", "9")
    if "лаб" in lt:
        return COLOR_ID_MAPPING.get("ЛАБ", "3")
    if "сам" in lt or "срс" in lt:
        return COLOR_ID_MAPPING.get("СРС", "8")
    if "курс" in lt:
        return COLOR_ID_MAPPING.get("КУРС", "4")
    if "зач" in lt:
        return COLOR_ID_MAPPING.get("ЗАЧ", "5")
    if "экз" in lt:
        return COLOR_ID_MAPPING.get("ЭКЗ", "11")
    return "10"


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


def format_pe_room(room_val: str) -> str:
    """Formats room name for PE schedule (e.g. 'ауд. 1' or 'Г\\Б')."""
    room_clean = room_val.strip()
    if room_clean.isdigit():
        return f"ауд. {room_clean}"
    return room_clean


def get_pe_rooms_for_date(event_date: date) -> Optional[Dict[str, str]]:
    """Returns PE rooms dictionary for a given date by direct date match or ISO week."""
    date_str = event_date.strftime("%Y-%m-%d")
    if date_str in PE_ROOMS_SCHEDULE:
        return PE_ROOMS_SCHEDULE[date_str]

    ev_iso = event_date.isocalendar()[:2]
    for key_date_str, rooms in PE_ROOMS_SCHEDULE.items():
        try:
            kd = datetime.strptime(key_date_str, "%Y-%m-%d").date()
            if kd.isocalendar()[:2] == ev_iso:
                return rooms
        except ValueError:
            continue
    return None


def get_pe_teacher_for_group(group_name: str) -> Optional[str]:
    """Returns assigned PE teacher for a given group, if configured."""
    if not group_name:
        return None
    norm = group_name.strip().lower()
    norm_latin = norm.replace("топ", "top")
    norm_cyr = norm.replace("top", "топ")

    for grp_key, teacher in GROUP_PE_TEACHERS.items():
        k_norm = grp_key.strip().lower()
        if (
            k_norm == norm
            or k_norm in norm
            or norm in k_norm
            or k_norm in norm_latin
            or k_norm in norm_cyr
        ):
            return teacher

    for g_key, g_cfg in SUPPORTED_GROUPS.items():
        pe_t = g_cfg.get("pe_teacher")
        if not pe_t:
            continue
        k_norm = g_key.strip().lower()
        cfg_name = g_cfg.get("name", "").strip().lower()
        if (
            k_norm in norm
            or norm in k_norm
            or cfg_name in norm
            or norm in cfg_name
            or k_norm in norm_latin
            or k_norm in norm_cyr
        ):
            return pe_t

    return None


def get_pe_room_for_group(group_name: str, event_date: date) -> Optional[str]:
    """Returns the formatted PE room for a group's assigned PE teacher on a given date."""
    teacher = get_pe_teacher_for_group(group_name)
    if not teacher:
        return None
    pe_rooms = get_pe_rooms_for_date(event_date)
    if not pe_rooms:
        return None

    if teacher in pe_rooms:
        return format_pe_room(pe_rooms[teacher])

    surname = teacher.split()[0].lower()
    for t_name, r_val in pe_rooms.items():
        if surname in t_name.lower() or t_name.lower() in teacher.lower():
            return format_pe_room(r_val)

    return None


def format_event_data(
    discipline_name: str,
    lesson_type: str,
    raw_location: str = "",
    short_aud: str = "",
    group_name: str = "",
    event_date: Optional[date] = None,
) -> Tuple[str, str]:
    """
    Formats clean SUMMARY and LOCATION with a pin for a standard lesson event.
    Returns: (summary, clean_location)
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
            type_marker = f"{clean_t} {emoji}".strip() if clean_t else ""

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
        pe_room = get_pe_room_for_group(group_name, event_date) if (group_name and event_date) else None
        if pe_room:
            clean_location = pe_room
        else:
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

    if clean_location:
        if not clean_location.startswith("📍"):
            clean_location = f"📍 {clean_location}"

    return summary, clean_location


def parse_cell_to_lessons(
    cell: Tag,
    pair_num: int,
    default_date: Optional[date],
    modals: Dict[str, Dict[str, str]],
    group_name: str = "426топ-4",
    tz: ZoneInfo = TIMEZONE,
) -> List[Lesson]:
    """Parses a table cell into domain Lesson objects."""
    lessons_list: List[Lesson] = []
    lessons = cell.find_all("div", class_="lesson-cell")

    for l_div in lessons:
        disc_span = l_div.find("span", class_="discipline")
        lesson_id = disc_span.get("data-lesson-id") if disc_span else None
        modal_data = modals.get(lesson_id, {}) if lesson_id else {}

        day_date = default_date
        if "Дата проведения" in modal_data:
            try:
                day_date = datetime.strptime(modal_data["Дата проведения"], "%d.%m.%Y").date()
            except ValueError:
                pass

        if not day_date:
            logger.warning("Could not determine date for lesson %s, skipping", lesson_id)
            continue

        disc_name = modal_data.get("title") or (disc_span.get_text(strip=True) if disc_span else "Занятие")
        kind_span = l_div.find("span", class_="kind")
        abbr = kind_span.find("abbr") if kind_span else None
        cell_kind = (abbr.get("title") if abbr and abbr.get("title") else kind_span.get_text(strip=True)) if kind_span else ""
        lesson_type = modal_data.get("Вид занятия") or cell_kind

        aud_span = l_div.find("span", class_="auditoriums")
        tooltip_i = aud_span.find("i", class_="js-tooltip") if aud_span else None
        full_aud = tooltip_i.get("title") if tooltip_i and tooltip_i.get("title") else ""
        short_aud = modal_data.get("Место проведения") or (aud_span.get_text(strip=True) if aud_span else "")

        teacher = modal_data.get("Преподаватель")
        if not teacher:
            grp_span = l_div.find("span", class_="group")
            teacher = grp_span.get_text(strip=True) if grp_span else ""

        summary, location = format_event_data(
            disc_name,
            lesson_type,
            raw_location=full_aud,
            short_aud=short_aud,
            group_name=group_name,
            event_date=day_date,
        )

        pe_flag = is_pe_lesson(disc_name, summary)
        if pe_flag:
            pe_teacher = get_pe_teacher_for_group(group_name)
            if pe_teacher:
                teacher = pe_teacher
            pe_room = get_pe_room_for_group(group_name, day_date)
            if pe_room:
                short_aud = pe_room
                full_aud = pe_room
                location = f"📍 {pe_room}"
            else:
                location = "📍 см. описание"

        note_span = l_div.find("span", class_="note")
        subgroup = (
            modal_data.get("Подгруппа")
            or modal_data.get("Комментарий")
            or (note_span.get_text(strip=True) if note_span else "")
        )

        groups = modal_data.get("Совместно с группами") or modal_data.get("Группы") or ""

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

        if pe_flag:
            pe_rooms = get_pe_rooms_for_date(day_date)
            if pe_rooms:
                desc_parts.append("")
                desc_parts.append("🏟️ Аудитории по преподавателям:")
                for teacher_name, room_val in pe_rooms.items():
                    desc_parts.append(f"• {teacher_name} — {format_pe_room(room_val)}")

        description = "\n".join(desc_parts)

        shift_search = f"{str(cell)} {modal_data.get('Комментарий', '')} {modal_data.get('Подгруппа', '')} {subgroup}"
        m_shift = re.search(r"с\s*(\d{1,2})[:.](\d{2})", shift_search, re.IGNORECASE)

        if (pair_num == 3 and "12:30" in shift_search) or (
            m_shift and pair_num == 3 and m_shift.group(1) == "12" and m_shift.group(2) == "30"
        ):
            s_h, s_m = 12, 30
            e_h, e_m = 14, 5
        elif m_shift:
            s_h = int(m_shift.group(1))
            s_m = int(m_shift.group(2))
            start_mins = s_h * 60 + s_m
            end_mins = start_mins + 95
            e_h = (end_mins // 60) % 24
            e_m = end_mins % 60
        else:
            start_time_str, end_time_str = BELL_SCHEDULE.get(pair_num, ("08:50", "10:25"))
            s_h, s_m = map(int, start_time_str.split(":"))
            e_h, e_m = map(int, end_time_str.split(":"))

        time_start = f"{s_h:02d}:{s_m:02d}"
        time_end = f"{e_h:02d}:{e_m:02d}"
        color_id = get_color_id_for_type(lesson_type or cell_kind)
        day_num = day_date.weekday() + 1
        uid_id = lesson_id if lesson_id else f"{pair_num}-{day_num}"
        uid = f"tusur-{uid_id}-{day_date.strftime('%Y%m%d')}@timetable.tusur.ru"

        clean_loc = location.replace("📍", "").strip()
        building = ""
        room = ""
        parts = clean_loc.split(maxsplit=1)
        if len(parts) == 2:
            building, room = parts[0], parts[1]
        elif len(parts) == 1:
            if parts[0] in ("ГК", "РК", "ФЭТ"):
                building = parts[0]
            else:
                room = parts[0]

        lesson = Lesson(
            id=str(uid_id),
            date=day_date.strftime("%Y-%m-%d"),
            pair_num=pair_num,
            time_start=time_start,
            time_end=time_end,
            subject=disc_name,
            short_subject=shorten_subject(disc_name),
            lesson_type=lesson_type,
            type_marker="",
            teacher=teacher,
            room=room or short_aud,
            building=building,
            location=location,
            raw_location=full_aud or short_aud,
            subgroup=subgroup,
            groups=groups,
            color_id=color_id,
            status="ACTIVE",
            is_pe=pe_flag,
            summary=summary,
            description=description,
            uid=uid,
        )
        lessons_list.append(lesson)

    return lessons_list


def parse_cell(
    cell: Tag,
    pair_num: int,
    default_date: Optional[date],
    modals: Dict[str, Dict[str, str]],
    group_name: str = "426топ-4",
    tz: ZoneInfo = TIMEZONE,
) -> List[Event]:
    """Legacy parser returning icalendar Event objects."""
    lessons = parse_cell_to_lessons(cell, pair_num, default_date, modals, group_name, tz)
    return [l.to_vevent(tz=tz) for l in lessons]


def parse_week_lessons(
    html: str,
    group_name: str = "426топ-4",
    tz: ZoneInfo = TIMEZONE,
) -> List[Lesson]:
    """Parses a single week HTML and returns a list of domain Lesson objects."""
    soup = BeautifulSoup(html, "html.parser")
    if not group_name or group_name == "426топ-4":
        h1 = soup.find("h1")
        if h1:
            m = re.search(r"группы\s+([^\s]+)", h1.get_text())
            if m:
                group_name = m.group(1)

    modals = parse_modals(soup)
    week_start = determine_week_start(soup, modals)

    desktop_table = soup.find("table", class_="table-lessons")
    if not desktop_table:
        logger.warning("Could not find table.table-lessons on the page")
        return []

    lessons: List[Lesson] = []
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

            cell_lessons = parse_cell_to_lessons(
                cell=cell,
                pair_num=pair_num,
                default_date=default_date,
                modals=modals,
                group_name=group_name,
                tz=tz,
            )
            lessons.extend(cell_lessons)

    logger.info("Found %d lessons for week starting %s", len(lessons), week_start)
    return lessons


def parse_week_events(
    html: str,
    group_name: str = "426топ-4",
    tz: ZoneInfo = TIMEZONE,
) -> List[Event]:
    """Legacy parser returning icalendar Event objects."""
    lessons = parse_week_lessons(html, group_name=group_name, tz=tz)
    return [l.to_vevent(tz=tz) for l in lessons]
