#!/usr/bin/env python3
"""
Google Calendar API Direct Synchronization Module
Idempotently syncs parsed TUSUR schedule events to Google Calendar using Service Account.
"""

import argparse
import json
import logging
import os
import sys
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional, Tuple
from zoneinfo import ZoneInfo

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from icalendar import Calendar, Event

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

logger = logging.getLogger("tusur_gcal_sync")
if not logger.handlers:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )

SCOPES = ["https://www.googleapis.com/auth/calendar"]
TIMEZONE_STR = "Asia/Tomsk"
TIMEZONE = ZoneInfo(TIMEZONE_STR)


def get_calendar_client() -> Tuple[Optional[Any], Optional[str]]:
    """
    Initializes and returns (google_calendar_service, calendar_id).
    Reads credentials from:
    1. GOOGLE_CREDENTIALS_JSON env var (raw JSON string)
    2. GOOGLE_APPLICATION_CREDENTIALS env var (file path)
    3. service_account.json in the current working directory
    """
    calendar_id = os.getenv("GOOGLE_CALENDAR_ID")
    if not calendar_id:
        logger.info("GOOGLE_CALENDAR_ID environment variable is not set; skipping Google Calendar sync")
        return None, None

    credentials = None
    creds_json = os.getenv("GOOGLE_CREDENTIALS_JSON")

    if creds_json:
        try:
            info = json.loads(creds_json)
            credentials = service_account.Credentials.from_service_account_info(
                info, scopes=SCOPES
            )
            logger.info("Loaded Google credentials from GOOGLE_CREDENTIALS_JSON environment variable")
        except Exception as e:
            logger.error("Failed to parse GOOGLE_CREDENTIALS_JSON: %s", e)
            return None, None
    elif os.getenv("GOOGLE_APPLICATION_CREDENTIALS") and os.path.exists(os.getenv("GOOGLE_APPLICATION_CREDENTIALS", "")):
        creds_path = os.environ["GOOGLE_APPLICATION_CREDENTIALS"]
        try:
            credentials = service_account.Credentials.from_service_account_file(
                creds_path, scopes=SCOPES
            )
            logger.info("Loaded Google credentials from %s", creds_path)
        except Exception as e:
            logger.error("Failed to load credentials from %s: %s", creds_path, e)
            return None, None
    elif os.path.exists("service_account.json"):
        try:
            credentials = service_account.Credentials.from_service_account_file(
                "service_account.json", scopes=SCOPES
            )
            logger.info("Loaded Google credentials from service_account.json")
        except Exception as e:
            logger.error("Failed to load credentials from service_account.json: %s", e)
            return None, None
    else:
        logger.info("No Google Calendar credentials found (checked env var and service_account.json)")
        return None, None

    try:
        service = build("calendar", "v3", credentials=credentials, cache_discovery=False)
        return service, calendar_id
    except Exception as e:
        logger.error("Failed to initialize Google Calendar client: %s", e)
        return None, None


def extract_event_payload(ev: Event, tz: ZoneInfo = TIMEZONE) -> Optional[Dict[str, Any]]:
    """Converts an icalendar Event into a Google Calendar API resource dictionary."""
    uid = str(ev.get("uid", "")).strip()
    if not uid:
        return None

    summary = str(ev.get("summary", "")).strip()
    location = str(ev.get("location", "")).strip() or None
    description = str(ev.get("description", "")).strip() or None

    dtstart_prop = ev.get("dtstart")
    dtend_prop = ev.get("dtend")

    if not dtstart_prop or not dtend_prop:
        return None

    dt_start = dtstart_prop.dt
    dt_end = dtend_prop.dt

    # Ensure timezone awareness
    if isinstance(dt_start, datetime):
        if dt_start.tzinfo is None:
            dt_start = dt_start.replace(tzinfo=tz)
    else:
        dt_start = datetime(dt_start.year, dt_start.month, dt_start.day, 0, 0, tzinfo=tz)

    if isinstance(dt_end, datetime):
        if dt_end.tzinfo is None:
            dt_end = dt_end.replace(tzinfo=tz)
    else:
        dt_end = datetime(dt_end.year, dt_end.month, dt_end.day, 23, 59, tzinfo=tz)

    # Determine colorId
    color_id = str(ev.get("x-color-id", "")).strip()
    if not color_id:
        if summary.startswith("❌"):
            color_id = "8"  # Graphite
        elif summary.startswith("🍺"):
            color_id = "5"  # Banana
        elif "[ЛЕК]" in summary:
            color_id = "10"  # Basil
        elif "[ПРАК]" in summary:
            color_id = "9"  # Blueberry
        elif "[ЛАБ]" in summary:
            color_id = "3"  # Grape
        elif "[СРС]" in summary:
            color_id = "8"  # Graphite
        elif "[КУРС]" in summary:
            color_id = "4"  # Flamingo
        elif "[ЗАЧ]" in summary:
            color_id = "5"  # Banana
        elif "[ЭКЗ]" in summary:
            color_id = "11"  # Tomato
        else:
            color_id = "10"

    # Reminders: popup notification for beer event, default for pairs
    if summary.startswith("🍺"):
        reminders = {
            "useDefault": False,
            "overrides": [{"method": "popup", "minutes": 0}],
        }
    else:
        reminders = {"useDefault": True}

    payload = {
        "iCalUID": uid,
        "summary": summary,
        "description": description,
        "location": location,
        "start": {
            "dateTime": dt_start.isoformat(),
            "timeZone": TIMEZONE_STR,
        },
        "end": {
            "dateTime": dt_end.isoformat(),
            "timeZone": TIMEZONE_STR,
        },
        "colorId": color_id,
        "reminders": reminders,
        "extendedProperties": {
            "private": {
                "tusur_uid": uid,
                "source": "tusur_schedule_sync",
            }
        },
    }
    return payload


def sync_calendar(
    cal: Calendar,
    service: Optional[Any] = None,
    calendar_id: Optional[str] = None,
    tz: ZoneInfo = TIMEZONE,
) -> Dict[str, int]:
    """
    Synchronizes all events from an icalendar Calendar object to Google Calendar.
    Performs idempotent upsert matching by iCalUID / tusur_uid.
    """
    stats = {"created": 0, "updated": 0, "unchanged": 0, "failed": 0}

    if service is None or calendar_id is None:
        service, calendar_id = get_calendar_client()

    if not service or not calendar_id:
        logger.info("Google Calendar client not available; sync skipped")
        return stats

    events_to_sync: List[Dict[str, Any]] = []
    min_date = None
    max_date = None

    for ev in cal.walk("vevent"):
        payload = extract_event_payload(ev, tz=tz)
        if not payload:
            continue
        events_to_sync.append(payload)

        dt_start_str = payload["start"]["dateTime"]
        dt_end_str = payload["end"]["dateTime"]
        dt_start = datetime.fromisoformat(dt_start_str)
        dt_end = datetime.fromisoformat(dt_end_str)

        if min_date is None or dt_start < min_date:
            min_date = dt_start
        if max_date is None or dt_end > max_date:
            max_date = dt_end

    if not events_to_sync:
        logger.info("No events to synchronize to Google Calendar")
        return stats

    logger.info("Found %d local events to sync (Range: %s to %s)", len(events_to_sync), min_date, max_date)

    # Fetch existing events from Google Calendar in the date window
    existing_events_by_uid: Dict[str, Dict[str, Any]] = {}
    try:
        # Buffer range by 1 day
        time_min = (min_date - timedelta(days=1)).isoformat() if min_date else None
        time_max = (max_date + timedelta(days=1)).isoformat() if max_date else None

        page_token = None
        while True:
            response = service.events().list(
                calendarId=calendar_id,
                timeMin=time_min,
                timeMax=time_max,
                maxResults=2500,
                singleEvents=True,
                pageToken=page_token,
            ).execute()

            for item in response.get("items", []):
                # Check iCalUID or private extended property
                item_uid = item.get("iCalUID")
                private_props = item.get("extendedProperties", {}).get("private", {})
                tusur_uid = private_props.get("tusur_uid") or item_uid
                if tusur_uid:
                    existing_events_by_uid[tusur_uid] = item
                if item_uid:
                    existing_events_by_uid[item_uid] = item

            page_token = response.get("nextPageToken")
            if not page_token:
                break

        logger.info("Retrieved %d existing events from Google Calendar in target date window", len(existing_events_by_uid))
    except Exception as e:
        logger.error("Failed to query existing Google Calendar events: %s", e)

    # Process each event: insert or update
    for payload in events_to_sync:
        uid = payload["iCalUID"]
        existing = existing_events_by_uid.get(uid)

        if existing:
            # Check if update is needed
            gcal_id = existing["id"]
            needs_update = False

            if existing.get("summary") != payload["summary"]:
                needs_update = True
            if (existing.get("location") or None) != payload["location"]:
                needs_update = True
            if (existing.get("description") or None) != payload["description"]:
                needs_update = True
            if str(existing.get("colorId", "")) != str(payload["colorId"]):
                needs_update = True

            # Compare start/end datetime
            ex_start = existing.get("start", {}).get("dateTime", "")
            if ex_start and payload["start"]["dateTime"]:
                try:
                    if datetime.fromisoformat(ex_start) != datetime.fromisoformat(payload["start"]["dateTime"]):
                        needs_update = True
                except ValueError:
                    needs_update = True

            if needs_update:
                try:
                    service.events().update(
                        calendarId=calendar_id,
                        eventId=gcal_id,
                        body=payload,
                    ).execute()
                    stats["updated"] += 1
                except Exception as e:
                    logger.warning("Failed to update event '%s' (%s): %s", payload["summary"], uid, e)
                    stats["failed"] += 1
            else:
                stats["unchanged"] += 1
        else:
            # Create new event
            try:
                service.events().insert(
                    calendarId=calendar_id,
                    body=payload,
                ).execute()
                stats["created"] += 1
            except Exception as e:
                logger.warning("Failed to insert event '%s' (%s): %s", payload["summary"], uid, e)
                stats["failed"] += 1

    logger.info(
        "Google Calendar Sync Summary: Created=%d, Updated=%d, Unchanged=%d, Failed=%d",
        stats["created"],
        stats["updated"],
        stats["unchanged"],
        stats["failed"],
    )
    return stats


def sync_calendar_if_configured(cal: Calendar) -> Optional[Dict[str, int]]:
    """Safe helper to sync if credentials exist without failing if unconfigured."""
    service, calendar_id = get_calendar_client()
    if service and calendar_id:
        return sync_calendar(cal, service=service, calendar_id=calendar_id)
    return None


def main() -> None:
    parser = argparse.ArgumentParser(description="Synchronize TUSUR schedule to Google Calendar")
    parser.add_argument(
        "--ics",
        default="schedule.ics",
        help="Path to .ics file to sync (default: schedule.ics)",
    )
    args = parser.parse_args()

    if not os.path.exists(args.ics):
        logger.error("ICS file not found: %s", args.ics)
        sys.exit(1)

    with open(args.ics, "rb") as f:
        cal = Calendar.from_ical(f.read())

    stats = sync_calendar(cal)
    logger.info("Done syncing %s: %s", args.ics, stats)


if __name__ == "__main__":
    main()
