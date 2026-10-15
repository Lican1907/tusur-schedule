#!/usr/bin/env python3
"""Backward-compatibility CLI shim delegating to src/integrations/gcal.py."""

from src.integrations.gcal import (
    extract_event_payload,
    get_calendar_client,
    main,
    sync_calendar,
    sync_calendar_if_configured,
)

__all__ = [
    "extract_event_payload",
    "get_calendar_client",
    "sync_calendar",
    "sync_calendar_if_configured",
    "main",
]

if __name__ == "__main__":
    main()
