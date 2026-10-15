"""Bell timings, current pair detection, and countdown math for TUSUR."""

from datetime import datetime, time
from typing import Any, Dict, Optional, Tuple
from zoneinfo import ZoneInfo

from src.config import BELL_SCHEDULE, TIMEZONE


def get_bell_schedule() -> Dict[int, Tuple[str, str]]:
    """Returns the university bell schedule mapping pair number to (start_time, end_time)."""
    return dict(BELL_SCHEDULE)


def get_pair_times(
    pair_num: int,
    is_saturday_shifted: bool = False,
    custom_shift: Optional[str] = None,
) -> Tuple[str, str]:
    """
    Returns (start_str, end_str) for a given pair number.
    Handles Saturday 3rd pair shift ('с 12:30' -> strictly 12:30-14:05) and custom shifts.
    """
    if pair_num == 3 and (is_saturday_shifted or (custom_shift and "12:30" in custom_shift)):
        return ("12:30", "14:05")

    if custom_shift:
        import re
        m = re.search(r"с\s*(\d{1,2})[:.](\d{2})", custom_shift, re.IGNORECASE)
        if m:
            s_h = int(m.group(1))
            s_m = int(m.group(2))
            start_mins = s_h * 60 + s_m
            end_mins = start_mins + 95  # Standard lesson duration: 95 minutes
            e_h = (end_mins // 60) % 24
            e_m = end_mins % 60
            return (f"{s_h:02d}:{s_m:02d}", f"{e_h:02d}:{e_m:02d}")

    return BELL_SCHEDULE.get(pair_num, ("08:50", "10:25"))


def _parse_time_str(t_str: str) -> time:
    h, m = map(int, t_str.split(":"))
    return time(h, m)


def get_current_pair_status(
    now: Optional[datetime] = None,
    tz: ZoneInfo = TIMEZONE,
    is_saturday_shifted: bool = False,
) -> Dict[str, Any]:
    """
    Calculates active pair status and countdown metrics for widgets.
    Returns:
        status: "before_classes" | "in_pair" | "in_break" | "after_classes"
        current_pair: int | None
        next_pair: int | None
        minutes_left_in_pair: int | None
        minutes_until_next_pair: int | None
    """
    if now is None:
        now = datetime.now(tz)
    elif now.tzinfo is None:
        now = now.replace(tzinfo=tz)

    cur_time = now.time()
    cur_minutes = cur_time.hour * 60 + cur_time.minute

    bells = []
    for p_num in sorted(BELL_SCHEDULE.keys()):
        s_str, e_str = get_pair_times(p_num, is_saturday_shifted=(is_saturday_shifted and p_num == 3))
        sh, sm = map(int, s_str.split(":"))
        eh, em = map(int, e_str.split(":"))
        bells.append((p_num, sh * 60 + sm, eh * 60 + em, s_str, e_str))

    first_start = bells[0][1]
    last_end = bells[-1][2]

    if cur_minutes < first_start:
        return {
            "status": "before_classes",
            "current_pair": None,
            "next_pair": 1,
            "minutes_left_in_pair": None,
            "minutes_until_next_pair": first_start - cur_minutes,
        }

    if cur_minutes >= last_end:
        return {
            "status": "after_classes",
            "current_pair": None,
            "next_pair": None,
            "minutes_left_in_pair": None,
            "minutes_until_next_pair": None,
        }

    for idx, (p_num, s_min, e_min, _, _) in enumerate(bells):
        if s_min <= cur_minutes < e_min:
            next_p = bells[idx + 1][0] if idx + 1 < len(bells) else None
            return {
                "status": "in_pair",
                "current_pair": p_num,
                "next_pair": next_p,
                "minutes_left_in_pair": e_min - cur_minutes,
                "minutes_until_next_pair": None,
            }
        if idx + 1 < len(bells):
            next_s_min = bells[idx + 1][1]
            if e_min <= cur_minutes < next_s_min:
                return {
                    "status": "in_break",
                    "current_pair": None,
                    "next_pair": bells[idx + 1][0],
                    "minutes_left_in_pair": None,
                    "minutes_until_next_pair": next_s_min - cur_minutes,
                }

    return {
        "status": "after_classes",
        "current_pair": None,
        "next_pair": None,
        "minutes_left_in_pair": None,
        "minutes_until_next_pair": None,
    }
