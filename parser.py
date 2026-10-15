#!/usr/bin/env python3
"""
TUSUR Schedule Parser & Exporter (Backward-Compatibility Shim)

Delegates all parsing, overrides, and multi-format exports (.ics, .json)
to the modular `src/` architecture.
"""

from src.config import (
    BELL_SCHEDULE,
    COLOR_ID_MAPPING,
    DEFAULT_HEADERS,
    DEFAULT_URL,
    GROUP_PE_TEACHERS,
    LOCATION_MAPPING,
    MONTH_MAP,
    OUTPUT_FILE,
    OVERRIDES_FILE,
    PE_ROOMS_SCHEDULE,
    SUBJECT_SHORT_NAMES,
    SUPPORTED_GROUPS,
    TIMEZONE,
    TIMEZONE_STR,
    TYPE_EMOJI,
    TYPE_MAPPING,
)
from src.core.beer import build_beer_event
from src.exporters.ics import build_calendar
from src.overrides.engine import apply_overrides, load_overrides
from src.pipeline import (
    main,
    parse_all_groups,
    parse_and_save_group,
    parse_schedule,
    parse_semester,
)
from src.scraper.client import create_http_session, fetch_page
from src.scraper.parser import (
    capitalize_first,
    determine_week_start,
    discover_weeks,
    format_event_data,
    format_pe_room,
    get_color_id_for_type,
    get_pe_room_for_group,
    get_pe_rooms_for_date,
    get_pe_teacher_for_group,
    is_pe_lesson,
    parse_cell,
    parse_modals,
    parse_week_events,
    shorten_subject,
)

__all__ = [
    # Constants
    "TIMEZONE_STR",
    "TIMEZONE",
    "DEFAULT_URL",
    "OUTPUT_FILE",
    "OVERRIDES_FILE",
    "SUPPORTED_GROUPS",
    "GROUP_PE_TEACHERS",
    "BELL_SCHEDULE",
    "TYPE_MAPPING",
    "TYPE_EMOJI",
    "COLOR_ID_MAPPING",
    "SUBJECT_SHORT_NAMES",
    "LOCATION_MAPPING",
    "MONTH_MAP",
    "PE_ROOMS_SCHEDULE",
    "DEFAULT_HEADERS",
    # Functions
    "get_color_id_for_type",
    "create_http_session",
    "fetch_page",
    "parse_modals",
    "determine_week_start",
    "discover_weeks",
    "capitalize_first",
    "shorten_subject",
    "is_pe_lesson",
    "format_event_data",
    "format_pe_room",
    "get_pe_rooms_for_date",
    "get_pe_teacher_for_group",
    "get_pe_room_for_group",
    "parse_cell",
    "parse_week_events",
    "load_overrides",
    "apply_overrides",
    "build_beer_event",
    "build_calendar",
    "parse_semester",
    "parse_and_save_group",
    "parse_all_groups",
    "parse_schedule",
    "main",
]

if __name__ == "__main__":
    main()
