"""Teacher overrides engine for manual replacements, cancellations, and room preservations."""

from datetime import datetime
import json
import logging
import os
from pathlib import Path
import re
from typing import Any, Dict, List, Optional, Union
from zoneinfo import ZoneInfo

from icalendar import Event

from src.config import (
    BELL_SCHEDULE,
    COLOR_ID_MAPPING,
    DATA_DIR,
    OVERRIDES_FILE,
    ROOT_DIR,
    TIMEZONE,
    TIMEZONE_STR,
    TYPE_EMOJI,
)
from src.core.models import Lesson
from src.scraper.parser import (
    format_pe_room,
    get_pe_room_for_group,
    get_pe_rooms_for_date,
    get_pe_teacher_for_group,
    is_pe_lesson,
    shorten_subject,
)

logger = logging.getLogger("tusur_overrides")


def load_overrides(filepath: Optional[str] = None) -> List[Dict[str, Any]]:
    """
    Loads schedule overrides from JSON file if present.
    Checks explicit filepath, data/overrides.json, and root overrides.json.
    """
    candidates = []
    if filepath:
        candidates.append(Path(filepath))
    candidates.extend([
        DATA_DIR / OVERRIDES_FILE,
        ROOT_DIR / OVERRIDES_FILE,
    ])

    for p in candidates:
        if p.exists():
            try:
                with open(p, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    if isinstance(data, list):
                        return data
                    logger.warning("Overrides file %s is not a list, skipping overrides", p)
            except Exception as e:
                logger.error("Failed to read overrides from %s: %s", p, e)

    return []


def apply_overrides_to_lessons(
    lessons: List[Lesson],
    overrides: List[Dict[str, Any]],
    group_name: str = "426топ-4",
    tz: ZoneInfo = TIMEZONE,
) -> List[Lesson]:
    """Applies manual teacher overrides to Lesson domain models."""
    if not overrides:
        return lessons

    result_lessons: List[Lesson] = list(lessons)

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
        time_start = ov.get("time_start")
        ov_type = ov.get("type", "").strip().upper()
        ov_subject = ov.get("subject", "").strip()
        cap_subject = ov_subject[0].upper() + ov_subject[1:] if ov_subject else ""
        comment = ov.get("comment", "").strip()
        keep_orig_loc = bool(ov.get("keep_original_location", False))
        ov_loc = ov.get("location")

        if action == "cancel":
            matched = False
            for les in result_lessons:
                if les.date != date_str:
                    continue
                if time_start and les.time_start != time_start:
                    continue

                subj_match = False
                if ov_subject:
                    subj_lower = ov_subject.lower()
                    if (
                        subj_lower in les.summary.lower()
                        or subj_lower in les.description.lower()
                        or subj_lower in les.subject.lower()
                        or shorten_subject(les.summary).lower() == subj_lower
                    ):
                        subj_match = True
                else:
                    subj_match = True

                type_match = True
                if ov_type:
                    type_clean = re.sub(r"[\[\]\s]", "", ov_type).lower()
                    type_match = type_clean in les.summary.lower() or type_clean in les.description.lower()

                if subj_match and type_match:
                    matched = True
                    les.summary = f"❌ [ОТМЕНА] {cap_subject}"
                    les.color_id = "8"
                    les.status = "CANCELLED"
                    prefix = f"❌ Занятие отменено преподавателем: {comment}" if comment else "❌ Занятие отменено преподавателем"
                    les.description = f"{prefix}\n{les.description}".strip()
                    logger.info("Override %s: Cancelled '%s' on %s", ov_id, cap_subject, target_date)

            if not matched:
                logger.warning("Override %s: No matching lesson found to cancel on %s for %s %s", ov_id, target_date, ov_type, ov_subject)

        elif action == "set":
            matched_les: Optional[Lesson] = None
            if time_start:
                for les in result_lessons:
                    if les.date == date_str and les.time_start == time_start:
                        matched_les = les
                        break

            if matched_les is not None:
                old_loc = matched_les.location
                if keep_orig_loc and old_loc:
                    loc = old_loc
                elif ov_loc:
                    loc = f"📍 {ov_loc}" if not ov_loc.startswith("📍") else ov_loc
                elif is_pe_lesson(ov_subject):
                    pe_room = get_pe_room_for_group(group_name, target_date)
                    loc = f"📍 {pe_room}" if pe_room else "📍 см. описание"
                else:
                    loc = old_loc

                emoji = TYPE_EMOJI.get(ov_type, "")
                marker = f"{ov_type} {emoji}".strip() if emoji else ov_type
                matched_les.summary = f"⚡ {marker} {cap_subject}".strip()
                matched_les.location = loc
                matched_les.color_id = COLOR_ID_MAPPING.get(ov_type, "10")
                matched_les.status = "CHANGED"
                matched_les.lesson_type = ov_type

                clean_loc = loc.replace("📍", "").strip()
                parts = clean_loc.split(maxsplit=1)
                if len(parts) == 2:
                    matched_les.building, matched_les.room = parts[0], parts[1]
                elif len(parts) == 1:
                    matched_les.room = parts[0]

                prefix = f"⚡ Изменение в расписании от преподавателя: {comment}" if comment else "⚡ Изменение в расписании от преподавателя"
                matched_les.description = f"{prefix}\n{matched_les.description}".strip()
                logger.info("Override %s: Replaced event at %s %s with '⚡ %s %s'", ov_id, target_date, time_start, marker, cap_subject)
            else:
                if not time_start:
                    logger.warning("Override %s: Cannot add new lesson without time_start on %s", ov_id, target_date)
                    continue

                s_h, s_m = map(int, time_start.split(":"))
                e_h, e_m = None, None
                pair_num = 1
                for p_num, (s_str, e_str) in BELL_SCHEDULE.items():
                    if s_str == time_start:
                        pair_num = p_num
                        e_h, e_m = map(int, e_str.split(":"))
                        break
                if e_h is None:
                    end_mins = s_h * 60 + s_m + 95
                    e_h = (end_mins // 60) % 24
                    e_m = end_mins % 60

                time_end = f"{e_h:02d}:{e_m:02d}"
                emoji = TYPE_EMOJI.get(ov_type, "")
                marker = f"{ov_type} {emoji}".strip() if emoji else ov_type
                summary = f"⚡ {marker} {cap_subject}".strip()
                desc = f"⚡ Изменение в расписании от преподавателя: {comment}" if comment else "⚡ Изменение в расписании от преподавателя"

                loc = ""
                if ov_loc:
                    loc = f"📍 {ov_loc}" if not ov_loc.startswith("📍") else ov_loc
                elif is_pe_lesson(ov_subject):
                    pe_teacher = get_pe_teacher_for_group(group_name)
                    if pe_teacher:
                        desc += f"\nПреподаватель: {pe_teacher}"
                    pe_room = get_pe_room_for_group(group_name, target_date)
                    if pe_room:
                        desc += f"\nАудитория: {pe_room}"
                        loc = f"📍 {pe_room}"
                    else:
                        loc = "📍 см. описание"
                    pe_rooms = get_pe_rooms_for_date(target_date)
                    if pe_rooms:
                        desc += "\n\n🏟️ Аудитории по преподавателям:\n" + "\n".join(
                            f"• {t_name} — {format_pe_room(r_val)}" for t_name, r_val in pe_rooms.items()
                        )

                clean_loc = loc.replace("📍", "").strip()
                building = ""
                room = ""
                parts = clean_loc.split(maxsplit=1)
                if len(parts) == 2:
                    building, room = parts[0], parts[1]
                elif len(parts) == 1:
                    room = parts[0]

                new_les = Lesson(
                    id=str(ov_id),
                    date=date_str,
                    pair_num=pair_num,
                    time_start=time_start,
                    time_end=time_end,
                    subject=cap_subject,
                    short_subject=cap_subject,
                    lesson_type=ov_type,
                    type_marker=marker,
                    room=room,
                    building=building,
                    location=loc,
                    color_id=COLOR_ID_MAPPING.get(ov_type, "10"),
                    status="CHANGED",
                    summary=summary,
                    description=desc,
                    uid=f"tusur-override-{ov_id}-{target_date.strftime('%Y%m%d')}@{TIMEZONE_STR}",
                )
                result_lessons.append(new_les)
                logger.info("Override %s: Created new lesson on %s at %s: '⚡ %s %s'", ov_id, target_date, time_start, marker, cap_subject)

    return result_lessons


def apply_overrides(
    events: List[Event],
    overrides: List[Dict[str, Any]],
    group_name: str = "426топ-4",
    tz: ZoneInfo = TIMEZONE,
) -> List[Event]:
    """
    Applies overrides to list of icalendar Event objects.
    Preserves 100% backward compatibility with legacy parser.py.
    """
    lessons = [Lesson.from_vevent(ev, tz=tz) for ev in events]
    updated_lessons = apply_overrides_to_lessons(lessons, overrides, group_name=group_name, tz=tz)
    return [l.to_vevent(tz=tz) for l in updated_lessons]
