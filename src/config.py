"""Configuration and centralized data loading for TUSUR schedule service."""

import json
import logging
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from zoneinfo import ZoneInfo

logger = logging.getLogger("tusur_config")

# Project directories
SRC_DIR = Path(__file__).resolve().parent
ROOT_DIR = SRC_DIR.parent
DATA_DIR = ROOT_DIR / "data"

TIMEZONE_STR = "Asia/Tomsk"
TIMEZONE = ZoneInfo(TIMEZONE_STR)

DEFAULT_URL = "https://timetable.tusur.ru/faculties/fsu/groups/426top-4"
OUTPUT_FILE = "schedule.ics"
OVERRIDES_FILE = "overrides.json"

DEFAULT_HEADERS: Dict[str, str] = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language": "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
}


def _load_json_file(filename: str, fallback: Any) -> Any:
    """Helper to load JSON file from data directory or root with fallback."""
    paths_to_check = [
        DATA_DIR / filename,
        ROOT_DIR / filename,
    ]
    for p in paths_to_check:
        if p.exists():
            try:
                with open(p, "r", encoding="utf-8") as f:
                    return json.load(f)
            except Exception as e:
                logger.error("Failed to read JSON from %s: %s", p, e)
    return fallback


# Bell Schedule
_raw_bells = _load_json_file("bell_schedule.json", {})
if _raw_bells:
    BELL_SCHEDULE: Dict[int, Tuple[str, str]] = {
        int(k): (v[0], v[1]) for k, v in _raw_bells.items()
    }
else:
    BELL_SCHEDULE = {
        1: ("08:50", "10:25"),
        2: ("10:40", "12:15"),
        3: ("13:15", "14:50"),
        4: ("15:00", "16:35"),
        5: ("16:45", "18:20"),
        6: ("18:30", "20:05"),
        7: ("20:15", "21:50"),
    }

# PE Schedule & Teachers
_raw_pe = _load_json_file("pe_schedule.json", {})
GROUP_PE_TEACHERS: Dict[str, str] = _raw_pe.get("group_teachers", {
    "426топ-4": "Тарасов А.В.",
    "426top-4": "Тарасов А.В.",
})
PE_ROOMS_SCHEDULE: Dict[str, Dict[str, str]] = _raw_pe.get("rooms_schedule", {})

# Mappings
_raw_mappings = _load_json_file("mappings.json", {})
SUBJECT_SHORT_NAMES: Dict[str, str] = _raw_mappings.get("subject_short_names", {})
TYPE_MAPPING: Dict[str, str] = _raw_mappings.get("type_mapping", {})
TYPE_EMOJI: Dict[str, str] = _raw_mappings.get("type_emoji", {})
COLOR_ID_MAPPING: Dict[str, str] = _raw_mappings.get("color_id_mapping", {
    "ЛЕК": "10",
    "ПРАК": "9",
    "ЛАБ": "3",
    "СРС": "8",
    "КУРС": "4",
    "ЗАЧ": "5",
    "ЭКЗ": "11",
    "CANCELLED": "8",
    "BEER": "5",
})
LOCATION_MAPPING: Dict[str, str] = _raw_mappings.get("location_mapping", {})
MONTH_MAP: Dict[str, int] = _raw_mappings.get("month_map", {})
SUPPORTED_GROUPS: Dict[str, Dict[str, Any]] = _raw_mappings.get("supported_groups", {})
