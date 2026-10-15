"""Schedule parsing, transformation, and multi-format export pipeline."""

import argparse
from datetime import datetime
import logging
import os
import re
import sys
import time
from typing import Any, Dict, List, Optional
from zoneinfo import ZoneInfo

from bs4 import BeautifulSoup
from icalendar import Calendar

from src.config import (
    DEFAULT_URL,
    OUTPUT_FILE,
    OVERRIDES_FILE,
    SUPPORTED_GROUPS,
    TIMEZONE,
    TIMEZONE_STR,
)
from src.core.models import Lesson
from src.exporters.ics import build_calendar
from src.exporters.json import export_schedule_json, export_today_json
from src.overrides.engine import apply_overrides_to_lessons, load_overrides
from src.scraper.client import create_http_session, fetch_page
from src.scraper.parser import discover_weeks, parse_week_lessons

logger = logging.getLogger("tusur_pipeline")


def parse_semester_lessons(
    url: str = DEFAULT_URL,
    all_weeks: bool = True,
    group_name: Optional[str] = None,
    tz: ZoneInfo = TIMEZONE,
) -> List[Lesson]:
    """
    Scrapes semester schedule, parses into Lesson domain objects,
    and applies teacher overrides.
    """
    session = create_http_session()

    try:
        main_html = fetch_page(url, session=session)

        # Extract group name
        if not group_name:
            soup_main_grp = BeautifulSoup(main_html, "html.parser")
            h1 = soup_main_grp.find("h1")
            if h1:
                m = re.search(r"группы\s+([^\s]+)", h1.get_text())
                if m:
                    group_name = m.group(1)
            if not group_name:
                m_url = re.search(r"/groups/([^/?#]+)", url)
                if m_url and m_url.group(1) in SUPPORTED_GROUPS:
                    group_name = SUPPORTED_GROUPS[m_url.group(1)].get("name", m_url.group(1))
            if not group_name:
                group_name = "426топ-4"

        lessons: List[Lesson] = parse_week_lessons(main_html, group_name=group_name, tz=tz)

        if all_weeks:
            week_urls = discover_weeks(main_html, url)
            logger.info("Discovered %d weeks in carousel for full semester parsing", len(week_urls))

            soup_main = BeautifulSoup(main_html, "html.parser")
            active_tile = soup_main.find(
                "li",
                class_=lambda c: c and "tile" in c.split() and ("current" in c.split() or "current-week" in c.split()),
            )
            active_href = ""
            if active_tile and active_tile.find("a"):
                active_href = active_tile.find("a").get("href", "")

            for idx, w_url in enumerate(week_urls, start=1):
                if w_url == url or (active_href and w_url.endswith(active_href)):
                    logger.info("Skipping already fetched current week (%d/%d): %s", idx, len(week_urls), w_url)
                    continue

                try:
                    time.sleep(0.3)
                    logger.info("Processing week %d/%d: %s", idx, len(week_urls), w_url)
                    w_html = fetch_page(w_url, session=session)
                    w_lessons = parse_week_lessons(w_html, group_name=group_name, tz=tz)
                    if not w_lessons:
                        logger.info("No lessons found for week %s (vacation or session)", w_url)
                    lessons.extend(w_lessons)
                except Exception as e:
                    logger.warning("Failed to fetch/parse week %s: %s (skipping and continuing)", w_url, e)

        # Apply teacher overrides if present
        overrides = load_overrides()
        if overrides:
            logger.info("Applying %d schedule overrides from %s (group: %s)", len(overrides), OVERRIDES_FILE, group_name)
            lessons = apply_overrides_to_lessons(lessons, overrides, group_name=group_name, tz=tz)

        return lessons
    finally:
        session.close()


def parse_semester(
    url: str = DEFAULT_URL,
    all_weeks: bool = True,
    group_name: Optional[str] = None,
    tz: ZoneInfo = TIMEZONE,
) -> Calendar:
    """Legacy compatibility entrypoint returning an RFC 5545 Calendar."""
    lessons = parse_semester_lessons(url=url, all_weeks=all_weeks, group_name=group_name, tz=tz)
    resolved_group = group_name or "426топ-4"
    return build_calendar(lessons, group_name=resolved_group, tz=tz)


def parse_and_save_group(
    group_key: str,
    group_cfg: Dict[str, Any],
    all_weeks: bool = True,
    tz: ZoneInfo = TIMEZONE,
) -> Calendar:
    """
    Parses semester schedule for a configured group and writes:
    - iCalendar .ics files (e.g. schedule-426top-4.ics, schedule.ics)
    - Structured JSON files (e.g. schedule-426top-4.json, schedule.json)
    - Lightweight today.json widget files
    """
    name = group_cfg.get("name", group_key)
    url = group_cfg["url"]
    output_files = group_cfg.get("output_files", [f"schedule-{group_key}.ics"])
    sync_gcal = group_cfg.get("sync_gcal", False)

    logger.info("=== Parsing group: %s (%s) ===", name, group_key)
    lessons = parse_semester_lessons(url=url, all_weeks=all_weeks, group_name=name, tz=tz)

    # 1. Build and export iCalendar (.ics)
    cal = build_calendar(lessons, group_name=name, tz=tz)
    ical_data = cal.to_ical()

    for out_file in output_files:
        with open(out_file, "wb") as f:
            f.write(ical_data)
        event_count = len([c for c in cal.walk("vevent")])
        logger.info("Saved %d events for %s to %s", event_count, name, out_file)

    # 2. Export structured JSON for mobile widget and API
    json_filename = f"schedule-{group_key}.json"
    export_schedule_json(lessons, group_name=name, filepath=json_filename, tz=tz)

    # For default group 426top-4, also export alias schedule.json and today.json
    if group_key == "426top-4" or "schedule.ics" in output_files:
        export_schedule_json(lessons, group_name=name, filepath="schedule.json", tz=tz)
        export_today_json(lessons, group_name=name, filepath="today.json", tz=tz)

    # Also export group-specific today-<group>.json
    export_today_json(lessons, group_name=name, filepath=f"today-{group_key}.json", tz=tz)

    # 3. Google Calendar API direct sync if configured
    if sync_gcal:
        try:
            from src.integrations.gcal import sync_calendar_if_configured
            sync_calendar_if_configured(cal)
        except ImportError:
            logger.debug("gcal module not loaded or dependencies missing")
        except Exception as e:
            logger.warning("Google Calendar sync encountered an issue: %s", e)

    return cal


def parse_all_groups(
    all_weeks: bool = True,
    tz: ZoneInfo = TIMEZONE,
) -> Dict[str, Calendar]:
    """Parses and updates schedules for all supported groups."""
    results: Dict[str, Calendar] = {}
    for group_key, group_cfg in SUPPORTED_GROUPS.items():
        try:
            cal = parse_and_save_group(group_key, group_cfg, all_weeks=all_weeks, tz=tz)
            results[group_key] = cal
            time.sleep(0.3)
        except Exception as e:
            logger.exception("Failed to parse group %s: %s", group_key, e)
    return results


# Backward compatibility alias
parse_schedule = parse_semester


def main() -> None:
    """CLI entrypoint with multi-group and multi-format support."""
    parser = argparse.ArgumentParser(description="Parse TUSUR group schedule into .ics and .json")
    parser.add_argument(
        "--group",
        default="all",
        choices=["all"] + list(SUPPORTED_GROUPS.keys()),
        help="Target group to parse (default: all)",
    )
    parser.add_argument(
        "--url",
        default=None,
        help="Target schedule URL (overrides group settings if specified)",
    )
    parser.add_argument(
        "--output",
        "-o",
        default=None,
        help="Output .ics file path (overrides group settings if specified)",
    )
    parser.add_argument(
        "--all-weeks",
        action="store_true",
        default=os.getenv("PARSE_ALL_WEEKS", "").lower() in ("1", "true", "yes"),
        help="Parse all weeks of the semester instead of only the current week",
    )
    args = parser.parse_args()

    logger.info("Starting TUSUR schedule parser pipeline...")
    logger.info("Mode: %s", "All Semester Weeks" if args.all_weeks else "Current Study Week")
    logger.info("Timezone: %s", TIMEZONE_STR)

    try:
        if args.url:
            output_file = args.output or OUTPUT_FILE
            cal = parse_semester(url=args.url, all_weeks=args.all_weeks)
            ical_data = cal.to_ical()
            with open(output_file, "wb") as f:
                f.write(ical_data)
            event_count = len([c for c in cal.walk("vevent")])
            logger.info("SUCCESS: Wrote %d events to %s", event_count, output_file)
            if args.url == DEFAULT_URL:
                try:
                    from src.integrations.gcal import sync_calendar_if_configured
                    sync_calendar_if_configured(cal)
                except Exception as e:
                    logger.warning("Google Calendar sync encountered an issue: %s", e)
            sys.exit(0)

        if args.group == "all":
            logger.info("Parsing all %d supported groups: %s", len(SUPPORTED_GROUPS), list(SUPPORTED_GROUPS.keys()))
            parse_all_groups(all_weeks=args.all_weeks)
        else:
            group_cfg = dict(SUPPORTED_GROUPS[args.group])
            if args.output:
                group_cfg["output_files"] = [args.output]
            parse_and_save_group(args.group, group_cfg, all_weeks=args.all_weeks)

        logger.info("SUCCESS: Completed schedule parsing and export (.ics & .json)")
        sys.exit(0)
    except Exception as e:
        logger.exception("FATAL: Failed to parse schedule: %s", e)
        sys.exit(1)
