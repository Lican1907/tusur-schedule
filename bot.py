#!/usr/bin/env python3
"""Backward-compatibility CLI shim delegating to src/integrations/bot/service.py."""

from src.integrations.bot.service import (
    ALLOWED_USERNAMES,
    DAYS_RU,
    OVERRIDES_FILE,
    TIMEZONE,
    TIMEZONE_STR,
    TYPE_EMOJI,
    OverrideItem,
    TeacherMessageAnalysis,
    analyze_message_with_gemini,
    commit_overrides_to_github,
    is_authorized,
    load_current_overrides,
    main,
    save_current_overrides,
    trigger_pipeline_and_sync,
)

__all__ = [
    "TIMEZONE_STR",
    "TIMEZONE",
    "OVERRIDES_FILE",
    "ALLOWED_USERNAMES",
    "TYPE_EMOJI",
    "DAYS_RU",
    "OverrideItem",
    "TeacherMessageAnalysis",
    "is_authorized",
    "analyze_message_with_gemini",
    "load_current_overrides",
    "save_current_overrides",
    "commit_overrides_to_github",
    "trigger_pipeline_and_sync",
    "main",
]

if __name__ == "__main__":
    main()
