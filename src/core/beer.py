"""'Можно пить пиво!' leisure event logic."""

from datetime import date, datetime, timedelta
from typing import Any, Dict, List, Optional, Union
from zoneinfo import ZoneInfo

from icalendar import Alarm, Event

from src.config import TIMEZONE
from src.core.models import Lesson


def get_beer_info_for_day(
    target_date: date,
    lessons_today: List[Union[Event, Lesson, Dict[str, Any]]],
    tz: ZoneInfo = TIMEZONE,
    today: Optional[date] = None,
) -> Optional[Dict[str, Any]]:
    """
    Computes beer event parameters strictly for the current day
    if active (non-cancelled) lessons took place.
    """
    if today is None:
        today = datetime.now(tz).date()
    if target_date != today:
        return None

    # Filter out cancelled lessons
    active_end_times: List[datetime] = []
    for item in lessons_today:
        if isinstance(item, Event):
            summary = str(item.get("summary", ""))
            status = str(item.get("x-status", "")).upper()
            if summary.startswith("❌") or status == "CANCELLED":
                continue
            dtend_prop = item.get("dtend")
            if dtend_prop and hasattr(dtend_prop, "dt"):
                dt = dtend_prop.dt
                if isinstance(dt, datetime):
                    active_end_times.append(dt)
        elif isinstance(item, Lesson):
            if item.status == "CANCELLED" or item.summary.startswith("❌"):
                continue
            eh, em = map(int, item.time_end.split(":"))
            y, m, d = map(int, item.date.split("-"))
            active_end_times.append(datetime(y, m, d, eh, em, tzinfo=tz))
        elif isinstance(item, dict):
            status = item.get("status", "").upper()
            summary = item.get("summary", "")
            if status == "CANCELLED" or summary.startswith("❌"):
                continue
            t_end = item.get("time_end")
            d_str = item.get("date", target_date.strftime("%Y-%m-%d"))
            if t_end:
                eh, em = map(int, t_end.split(":"))
                y, m, d = map(int, d_str.split("-"))
                active_end_times.append(datetime(y, m, d, eh, em, tzinfo=tz))

    if not active_end_times:
        return None

    start_dt = max(active_end_times)
    end_dt = start_dt + timedelta(hours=2)

    return {
        "summary": "🍺 Можно пить пиво!",
        "location": "🎉 Свобода",
        "description": "Все пары на сегодня закончились. Время заслуженного отдыха.",
        "start_dt": start_dt,
        "end_dt": end_dt,
        "time_start": start_dt.strftime("%H:%M"),
        "time_end": end_dt.strftime("%H:%M"),
        "color_id": "5",
        "uid": f"beer_{target_date.strftime('%Y%m%d')}@tusur",
    }


def build_beer_event(
    target_date: date,
    lessons_today: List[Event],
    tz: ZoneInfo = TIMEZONE,
) -> Optional[Event]:
    """
    Creates an RFC 5545 VEVENT '🍺 Можно пить пиво!' with VALARM strictly for today.
    Maintains exact backward compatibility with parser.py.
    """
    info = get_beer_info_for_day(target_date, lessons_today, tz=tz)
    if not info:
        return None

    beer_ev = Event()
    beer_ev.add("summary", info["summary"])
    beer_ev.add("dtstart", info["start_dt"])
    beer_ev.add("dtend", info["end_dt"])
    beer_ev.add("location", info["location"])
    beer_ev.add("description", info["description"])
    beer_ev.add("uid", info["uid"])
    beer_ev.add("dtstamp", datetime.now(tz))
    beer_ev.add("x-color-id", info["color_id"])

    alarm = Alarm()
    alarm.add("action", "DISPLAY")
    alarm.add("description", info["summary"])
    alarm.add("trigger", timedelta(minutes=0))
    beer_ev.add_component(alarm)

    return beer_ev


def build_beer_lesson(
    target_date: date,
    lessons_today: List[Lesson],
    tz: ZoneInfo = TIMEZONE,
) -> Optional[Lesson]:
    """Creates a Lesson object for the beer event."""
    info = get_beer_info_for_day(target_date, lessons_today, tz=tz)
    if not info:
        return None

    return Lesson(
        id=f"beer-{target_date.strftime('%Y%m%d')}",
        date=target_date.strftime("%Y-%m-%d"),
        pair_num=0,
        time_start=info["time_start"],
        time_end=info["time_end"],
        subject="Можно пить пиво!",
        short_subject="Можно пить пиво!",
        lesson_type="ОТДЫХ",
        type_marker="🍺",
        location=info["location"],
        color_id=info["color_id"],
        status="ACTIVE",
        summary=info["summary"],
        description=info["description"],
        uid=info["uid"],
    )
