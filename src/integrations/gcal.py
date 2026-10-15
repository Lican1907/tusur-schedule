"""Google Calendar API Direct Synchronization Module.

Idempotently syncs parsed TUSUR schedule events to Google Calendar using Service Account.
"""

import argparse
from datetime import datetime, timedelta
import json
import logging
import os
from pathlib import Path
import sys
from typing import Any, Dict, List, Optional, Set, Tuple
from zoneinfo import ZoneInfo

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from icalendar import Calendar, Event

from src.config import ROOT_DIR, TIMEZONE, TIMEZONE_STR

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


def get_calendar_client() -> Tuple[Optional[Any], Optional[str]]:
    """
    Initializes and returns (google_calendar_service, calendar_id).
    Reads credentials from:
    1. GOOGLE_CREDENTIALS_JSON env var (raw JSON string)
    2. GOOGLE_APPLICATION_CREDENTIALS env var (file path)
    3. service_account.json in the current working directory or ROOT_DIR
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
    else:
        sa_paths = [
            Path("service_account.json"),
            ROOT_DIR / "service_account.json",
        ]
        found_sa = next((p for p in sa_paths if p.exists()), None)
        if found_sa:
            try:
                credentials = service_account.Credentials.from_service_account_file(
                    str(found_sa), scopes=SCOPES
                )
                logger.info("Loaded Google credentials from %s", found_sa)
            except Exception as e:
                logger.error("Failed to load credentials from %s: %s", found_sa, e)
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

    color_id = str(ev.get("x-color-id", "")).strip()
    if not color_id:
        if summary.startswith("❌"):
            color_id = "8"
        elif summary.startswith("🍺"):
            color_id = "5"
        elif "ЛЕК" in summary:
            color_id = "10"
        elif "ПРАК" in summary:
            color_id = "9"
        elif "ЛАБ" in summary:
            color_id = "3"
        elif "СРС" in summary:
            color_id = "8"
        elif "КУРС" in summary:
            color_id = "4"
        elif "ЗАЧ" in summary:
            color_id = "5"
        elif "ЭКЗ" in summary:
            color_id = "11"
        else:
            color_id = "10"

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
    stats = {"created": 0, "updated": 0, "unchanged": 0, "deleted": 0, "failed": 0}

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

    existing_events_by_uid: Dict[str, Dict[str, Any]] = {}
    try:
        lookback_limit = datetime.now(tz) - timedelta(days=30)
        start_bound = min(min_date - timedelta(days=1), lookback_limit) if min_date else lookback_limit
        time_min = start_bound.isoformat()
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

    today_date = datetime.now(tz).date()
    seen_deleted_ids: Set[str] = set()
    for uid_key, item in list(existing_events_by_uid.items()):
        item_id = item.get("id")
        if not item_id or item_id in seen_deleted_ids:
            continue

        item_summary = item.get("summary", "")
        item_uid = item.get("iCalUID", "")
        private_props = item.get("extendedProperties", {}).get("private", {})
        tusur_uid = private_props.get("tusur_uid", "")

        is_beer = (
            item_uid.startswith("beer_")
            or tusur_uid.startswith("beer_")
            or "🍺" in item_summary
            or "пить пиво" in item_summary.lower()
        )
        if not is_beer:
            continue

        item_start_str = item.get("start", {}).get("dateTime") or item.get("start", {}).get("date")
        if not item_start_str:
            continue
        try:
            if "T" in item_start_str:
                item_date = datetime.fromisoformat(item_start_str).date()
            else:
                item_date = datetime.strptime(item_start_str[:10], "%Y-%m-%d").date()
        except Exception:
            continue

        if item_date < today_date:
            try:
                service.events().delete(calendarId=calendar_id, eventId=item_id).execute()
                stats["deleted"] += 1
                seen_deleted_ids.add(item_id)
                logger.info("Deleted past beer event from %s: '%s' (id: %s)", item_date, item_summary, item_id)
                existing_events_by_uid.pop(uid_key, None)
            except Exception as e:
                logger.warning("Failed to delete past beer event %s: %s", item_id, e)

    for payload in events_to_sync:
        uid = payload["iCalUID"]
        existing = existing_events_by_uid.get(uid)

        if existing:
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
        "Google Calendar Sync Summary: Created=%d, Updated=%d, Deleted=%d, Unchanged=%d, Failed=%d",
        stats["created"],
        stats["updated"],
        stats["deleted"],
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
