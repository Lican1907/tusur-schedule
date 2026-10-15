"""JSON Exporters for Mobile Widgets and Web API (today.json and schedule-<group>.json)."""

from collections import defaultdict
from datetime import date, datetime
import json
import logging
from pathlib import Path
from typing import Any, Dict, List, Optional, Union
from zoneinfo import ZoneInfo

from icalendar import Event

from src.config import TIMEZONE
from src.core.beer import get_beer_info_for_day
from src.core.bells import get_current_pair_status
from src.core.models import Lesson

logger = logging.getLogger("tusur_json_exporter")


def normalize_lesson_dict(item: Union[Lesson, Event]) -> Dict[str, Any]:
    """Normalizes a Lesson or Event into a clean widget-ready dictionary."""
    if isinstance(item, Event):
        les = Lesson.from_vevent(item)
    else:
        les = item

    return {
        "pair_num": les.pair_num,
        "time_start": les.time_start,
        "time_end": les.time_end,
        "subject": les.subject,
        "short_subject": les.short_subject,
        "lesson_type": les.lesson_type,
        "teacher": les.teacher,
        "room": les.room,
        "building": les.building,
        "location": les.location,
        "status": les.status,
        "color_id": les.color_id,
        "summary": les.summary,
        "subgroup": les.subgroup,
        "uid": les.uid,
    }


def export_today_json(
    lessons_or_events: List[Union[Lesson, Event]],
    group_name: str = "426топ-4",
    filepath: Optional[Union[str, Path]] = None,
    now: Optional[datetime] = None,
    tz: ZoneInfo = TIMEZONE,
) -> Dict[str, Any]:
    """
    Generates lightweight today.json containing normalized pairs and countdown status for widgets.
    """
    if now is None:
        now = datetime.now(tz)
    elif now.tzinfo is None:
        now = now.replace(tzinfo=tz)

    today = now.date()
    today_str = today.strftime("%Y-%m-%d")

    # Filter lessons for today
    today_items: List[Union[Lesson, Event]] = []
    for item in lessons_or_events:
        uid = str(item.get("uid", "") if isinstance(item, Event) else item.uid)
        if uid.startswith("beer_"):
            continue
        if isinstance(item, Lesson):
            if item.date == today_str:
                today_items.append(item)
        elif isinstance(item, Event):
            dtstart = item.get("dtstart")
            if dtstart and hasattr(dtstart, "dt"):
                d = dtstart.dt
                ev_date = d.date() if isinstance(d, datetime) else d
                if ev_date == today:
                    today_items.append(item)

    # Sort today items by start time / pair_num
    normalized_lessons = [normalize_lesson_dict(i) for i in today_items]
    normalized_lessons.sort(key=lambda x: (x["pair_num"], x["time_start"]))

    # Countdown and active pair metrics
    pair_status = get_current_pair_status(now=now, tz=tz)

    # Beer leisure event for today
    beer_info = get_beer_info_for_day(today, today_items, tz=tz, today=today)
    beer_dict = None
    if beer_info:
        beer_dict = {
            "summary": beer_info["summary"],
            "location": beer_info["location"],
            "time_start": beer_info["time_start"],
            "time_end": beer_info["time_end"],
            "description": beer_info["description"],
            "color_id": beer_info["color_id"],
        }

    is_weekend = today.weekday() >= 6  # Sunday (and Saturday if no classes)

    data: Dict[str, Any] = {
        "generated_at": now.isoformat(),
        "date": today_str,
        "weekday": today.weekday() + 1,
        "group": group_name,
        "is_weekend": is_weekend,
        "pair_status": pair_status["status"],
        "current_pair": pair_status["current_pair"],
        "next_pair": pair_status["next_pair"],
        "minutes_left_in_pair": pair_status["minutes_left_in_pair"],
        "minutes_until_next_pair": pair_status["minutes_until_next_pair"],
        "total_lessons_today": len(normalized_lessons),
        "lessons": normalized_lessons,
        "beer_event": beer_dict,
    }

    if filepath:
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        logger.info("Saved today.json for %s to %s", group_name, filepath)

    return data


def export_schedule_json(
    lessons_or_events: List[Union[Lesson, Event]],
    group_name: str = "426топ-4",
    filepath: Optional[Union[str, Path]] = None,
    tz: ZoneInfo = TIMEZONE,
) -> Dict[str, Any]:
    """
    Generates structured schedule-<group>.json grouping normalized pairs by day.
    """
    now = datetime.now(tz)
    days_dict: Dict[str, List[Dict[str, Any]]] = defaultdict(list)

    for item in lessons_or_events:
        uid = str(item.get("uid", "") if isinstance(item, Event) else item.uid)
        if uid.startswith("beer_"):
            continue

        norm = normalize_lesson_dict(item)
        if isinstance(item, Lesson):
            d_str = item.date
        else:
            dtstart = item.get("dtstart").dt
            d_str = dtstart.strftime("%Y-%m-%d") if isinstance(dtstart, datetime) else dtstart.strftime("%Y-%m-%d")

        days_dict[d_str].append(norm)

    # Sort days and lessons within each day
    sorted_days: Dict[str, List[Dict[str, Any]]] = {}
    for d_str in sorted(days_dict.keys()):
        day_lessons = sorted(days_dict[d_str], key=lambda x: (x["pair_num"], x["time_start"]))
        sorted_days[d_str] = day_lessons

    total_lessons = sum(len(l) for l in sorted_days.values())

    data: Dict[str, Any] = {
        "generated_at": now.isoformat(),
        "group": group_name,
        "total_lessons": total_lessons,
        "total_days": len(sorted_days),
        "days": sorted_days,
    }

    if filepath:
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        logger.info("Saved schedule JSON for %s (%d lessons) to %s", group_name, total_lessons, filepath)

    return data
