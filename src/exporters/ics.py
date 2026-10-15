"""RFC 5545 iCalendar (.ics) builder and serializer."""

from collections import defaultdict
from datetime import date, datetime, timedelta
import logging
from typing import Dict, List, Set, Union
from zoneinfo import ZoneInfo

from icalendar import Calendar, Event, Timezone, TimezoneStandard

from src.config import TIMEZONE, TIMEZONE_STR
from src.core.beer import build_beer_event
from src.core.models import Lesson

logger = logging.getLogger("tusur_ics_exporter")


def build_calendar(
    events_or_lessons: List[Union[Event, Lesson]],
    group_name: str = "426топ-4",
    tz: ZoneInfo = TIMEZONE,
) -> Calendar:
    """
    Builds a complete RFC 5545 Calendar with VTIMEZONE, VEVENTs, and beer time event.
    Preserves exact backward compatibility with legacy parser.py.
    """
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

    # Convert lessons to events if needed
    raw_events: List[Event] = []
    for item in events_or_lessons:
        if isinstance(item, Lesson):
            raw_events.append(item.to_vevent(tz=tz))
        else:
            raw_events.append(item)

    # Group lesson events by date to check for today's lessons (excluding beer events)
    lessons_by_day: Dict[date, List[Event]] = defaultdict(list)
    for ev in raw_events:
        uid = str(ev.get("uid", ""))
        if uid.startswith("beer_"):
            continue
        dtstart = ev.get("dtstart")
        if dtstart and hasattr(dtstart, "dt"):
            d = dtstart.dt
            ev_date = d.date() if isinstance(d, datetime) else d
            lessons_by_day[ev_date].append(ev)

    today = datetime.now(tz).date()
    all_events: List[Event] = list(raw_events)

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


def export_ics(
    cal_or_events: Union[Calendar, List[Union[Event, Lesson]]],
    filepath: str,
    group_name: str = "426топ-4",
    tz: ZoneInfo = TIMEZONE,
) -> int:
    """Serializes and writes an .ics calendar file to disk. Returns number of events."""
    if isinstance(cal_or_events, Calendar):
        cal = cal_or_events
    else:
        cal = build_calendar(cal_or_events, group_name=group_name, tz=tz)

    ical_data = cal.to_ical()
    with open(filepath, "wb") as f:
        f.write(ical_data)

    event_count = len([c for c in cal.walk("vevent")])
    logger.info("Wrote %d events to %s", event_count, filepath)
    return event_count
