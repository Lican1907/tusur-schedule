"""Domain models for schedule entities."""

from dataclasses import asdict, dataclass, field
from datetime import date, datetime
import re
from typing import Any, Dict, List, Optional
from zoneinfo import ZoneInfo

from icalendar import Event

from src.config import TIMEZONE, TIMEZONE_STR


@dataclass
class TimeSlot:
    """Represents a pair time slot according to bell schedule."""
    pair_num: int
    time_start: str  # "HH:MM"
    time_end: str    # "HH:MM"

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class Lesson:
    """Represents an academic lesson event."""
    id: str
    date: str  # "YYYY-MM-DD"
    pair_num: int
    time_start: str  # "HH:MM"
    time_end: str    # "HH:MM"
    subject: str
    short_subject: str = ""
    lesson_type: str = ""
    type_marker: str = ""
    teacher: str = ""
    room: str = ""
    building: str = ""
    location: str = ""
    raw_location: str = ""
    subgroup: str = ""
    groups: str = ""
    color_id: str = "10"
    status: str = "ACTIVE"  # "ACTIVE", "CANCELLED", "CHANGED"
    is_pe: bool = False
    summary: str = ""
    description: str = ""
    uid: str = ""
    url: str = ""

    def __post_init__(self):
        if not self.short_subject and self.subject:
            self.short_subject = self.subject
        if not self.summary and self.subject:
            marker = self.type_marker or self.lesson_type
            if marker:
                self.summary = f"{marker} {self.short_subject}".strip()
            else:
                self.summary = self.short_subject
        if not self.uid and self.date and self.pair_num:
            d_clean = self.date.replace("-", "")
            self.uid = f"tusur-{self.id or self.pair_num}-{d_clean}@{TIMEZONE_STR}"

    def to_dict(self) -> Dict[str, Any]:
        """Returns JSON-serializable dictionary representation."""
        return {
            "id": self.id,
            "date": self.date,
            "pair_num": self.pair_num,
            "time_start": self.time_start,
            "time_end": self.time_end,
            "subject": self.subject,
            "short_subject": self.short_subject,
            "lesson_type": self.lesson_type,
            "type_marker": self.type_marker,
            "teacher": self.teacher,
            "room": self.room,
            "building": self.building,
            "location": self.location,
            "subgroup": self.subgroup,
            "color_id": self.color_id,
            "status": self.status,
            "is_pe": self.is_pe,
            "summary": self.summary,
            "description": self.description,
            "uid": self.uid,
        }

    def to_vevent(self, tz: ZoneInfo = TIMEZONE) -> Event:
        """Converts to RFC 5545 iCalendar VEVENT."""
        ev = Event()
        ev.add("summary", self.summary)
        if self.description:
            ev.add("description", self.description)
        if self.location:
            ev.add("location", self.location)

        y, m, d = map(int, self.date.split("-"))
        sh, sm = map(int, self.time_start.split(":"))
        eh, em = map(int, self.time_end.split(":"))

        dt_start = datetime(y, m, d, sh, sm, tzinfo=tz)
        dt_end = datetime(y, m, d, eh, em, tzinfo=tz)

        ev.add("dtstart", dt_start)
        ev.add("dtend", dt_end)
        ev.add("dtstamp", datetime.now(tz))
        ev.add("uid", self.uid)
        ev.add("x-color-id", self.color_id)
        if self.status != "ACTIVE":
            ev.add("x-status", self.status)

        return ev

    @classmethod
    def from_vevent(cls, ev: Event, tz: ZoneInfo = TIMEZONE) -> "Lesson":
        """Reconstructs a Lesson from an RFC 5545 iCalendar VEVENT."""
        summary = str(ev.get("summary", ""))
        description = str(ev.get("description", ""))
        location = str(ev.get("location", ""))
        uid = str(ev.get("uid", ""))
        color_id = str(ev.get("x-color-id", "10"))
        status = str(ev.get("x-status", "ACTIVE"))

        dtstart = ev.get("dtstart").dt
        dtend = ev.get("dtend").dt

        if not isinstance(dtstart, datetime):
            dtstart = datetime.combine(dtstart, datetime.min.time(), tzinfo=tz)
        if not isinstance(dtend, datetime):
            dtend = datetime.combine(dtend, datetime.min.time(), tzinfo=tz)

        date_str = dtstart.strftime("%Y-%m-%d")
        time_start = dtstart.strftime("%H:%M")
        time_end = dtend.strftime("%H:%M")

        # Extract teacher, room, subgroup from description if available
        teacher = ""
        room = ""
        building = ""
        subgroup = ""
        subject = ""
        lesson_type = ""

        m_subj = re.search(r"Предмет:\s*(.+)", description)
        if m_subj:
            subject = m_subj.group(1).strip()
        m_type = re.search(r"Вид:\s*(.+)", description)
        if m_type:
            lesson_type = m_type.group(1).strip()
        m_teacher = re.search(r"Преподаватель:\s*(.+)", description)
        if m_teacher:
            teacher = m_teacher.group(1).strip()
        m_sub = re.search(r"Подгруппа:\s*(.+)", description)
        if m_sub:
            subgroup = m_sub.group(1).strip()

        # Parse location building and room
        clean_loc = location.replace("📍", "").strip()
        parts = clean_loc.split(maxsplit=1)
        if len(parts) == 2:
            building, room = parts[0], parts[1]
        elif len(parts) == 1:
            if parts[0] in ("ГК", "РК", "ФЭТ"):
                building = parts[0]
            else:
                room = parts[0]

        # Determine pair number by start time
        pair_num = 1
        hour_start = dtstart.hour * 60 + dtstart.minute
        if hour_start < 10 * 60 + 30:
            pair_num = 1
        elif hour_start < 12 * 60 + 30:
            pair_num = 2
        elif hour_start < 14 * 60 + 55:
            pair_num = 3
        elif hour_start < 16 * 60 + 40:
            pair_num = 4
        elif hour_start < 18 * 60 + 25:
            pair_num = 5
        elif hour_start < 20 * 60 + 10:
            pair_num = 6
        else:
            pair_num = 7

        m_uid = re.search(r"tusur-(?:override-)?([^-@]+)", uid)
        lid = m_uid.group(1) if m_uid else str(pair_num)

        return cls(
            id=lid,
            date=date_str,
            pair_num=pair_num,
            time_start=time_start,
            time_end=time_end,
            subject=subject or summary,
            short_subject=subject or summary,
            lesson_type=lesson_type,
            teacher=teacher,
            room=room,
            building=building,
            location=location,
            subgroup=subgroup,
            color_id=color_id,
            status=status,
            summary=summary,
            description=description,
            uid=uid,
        )


@dataclass
class DaySchedule:
    """Represents a scheduled day with ordered lessons."""
    date: str  # "YYYY-MM-DD"
    weekday: int  # 1 (Monday) to 7 (Sunday)
    lessons: List[Lesson] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "date": self.date,
            "weekday": self.weekday,
            "lessons": [l.to_dict() for l in self.lessons],
        }


@dataclass
class Group:
    """Represents an academic group configuration."""
    id: str
    name: str
    url: str
    output_files: List[str] = field(default_factory=list)
    sync_gcal: bool = False
    pe_teacher: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)
