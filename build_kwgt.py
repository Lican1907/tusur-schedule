#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_kwgt.py — Programmatic generator for the TUSUR Schedule KWGT preset.

Generates schedule_widget.kwgt containing preset.json and preview metadata.
Complies with Kustom Widget (KWGT) 4x2 grid specifications, glassmorphism design,
dynamic multi-state switching, live progress bar, and touch actions.
"""

import os
import sys
import json
import base64
import zipfile
import argparse

if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

# 1x1 24-bit JPEG image used as valid preview placeholder in KWGT preset picker
THUMBNAIL_BYTES = base64.b64decode(
    '/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////'
    'wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA='
)


def create_preset_data() -> dict:
    """Creates the full Kustom preset data structure."""
    
    # Mathematical and logic formulas for states and progress
    formula_state = (
        '$if('
        'ci(ecount, a0d) > 0 & df(D, ci(start, 0)) = df(D) & df(S) >= df(S, ci(start, 0)) & df(S) < df(S, ci(end, 0)), 2, '
        'ci(ecount, a0d) > 0 & df(D, ci(start, 0)) = df(D) & df(S) < df(S, ci(start, 0)), 3, '
        '(ci(ecount, a0d) > 0 & df(D, ci(start, 0)) != df(D)) | (ci(ecount, a0d) = 0 & df(f) < 6 & df(H) >= 14), 1, '
        '4)$'
    )
    
    formula_progress = (
        '$mu(min, 100, mu(max, 0, ((df(S) - ci(start, 0, S)) / (ci(end, 0, S) - ci(start, 0, S))) * 100))$'
    )

    preset_info = {
        "version": 13,
        "title": "ТУСУР Расписание 426топ-4",
        "description": "Премиальный виджет расписания ТУСУР с живым прогрессом пары, перемены и статусом отдыха.",
        "author": "Lican1907",
        "email": "",
        "width": 720,
        "height": 340,
        "features": "",
        "release": 382610318,
        "locked": False,
        "pflags": 0,
    }

    # Root widget container
    preset_root = {
        "internal_type": "RootLayerModule",
        "internal_events": [
            {
                "action": "KUSTOM_ACTION",
                "kustom_action": "ADVANCED_EDITOR"
            }
        ],
        "globals_list": {
            "w": {
                "index": 1,
                "type": "NUMBER",
                "title": "Widget Width",
                "global_formula": "$si(rwidth)$",
                "min": 200,
                "max": 1080,
                "value": 720.0,
            },
            "h": {
                "index": 2,
                "type": "NUMBER",
                "title": "Widget Height",
                "global_formula": "$si(rheight)$",
                "min": 100,
                "max": 1080,
                "value": 340.0,
            },
            "state": {
                "index": 3,
                "type": "TEXT",
                "title": "Widget State (1=Beer, 2=Class, 3=Break, 4=Off)",
                "global_formula": formula_state,
                "value": "1",
                "toggles": 10,
            },
            "group": {
                "index": 4,
                "type": "TEXT",
                "title": "Group Name",
                "value": "426топ-4",
            }
        },
        "config_scale_value": 100.0,
        "viewgroup_items": [
            # Main Overlap wrapper for the 4x2 card
            {
                "internal_type": "OverlapLayerModule",
                "internal_title": "Schedule Widget Card (4x2)",
                "position_anchor": "CENTER",
                # Touch anywhere on card opens default calendar
                "internal_events": [
                    {
                        "type": "SINGLE_TAP",
                        "action": "LAUNCH_APP",
                        "intent": "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.APP_CALENDAR;end"
                    }
                ],
                "viewgroup_items": [
                    # 1. Base Glassmorphism Background (#D9161622 with 28dp radius)
                    {
                        "internal_type": "ShapeModule",
                        "internal_title": "Glass Background",
                        "shape_type": "RECT",
                        "shape_width": 720.0,
                        "shape_height": 340.0,
                        "shape_corners": 28.0,
                        "paint_color": "#D9161622",
                        "internal_toggles": {
                            "shape_width": 10,
                            "shape_height": 10
                        },
                        "internal_formulas": {
                            "shape_width": "$si(rwidth)$",
                            "shape_height": "$si(rheight)$"
                        }
                    },
                    # 2. Glass Border Stroke (#2AFFFFFF, 1.5dp, 28dp radius)
                    {
                        "internal_type": "ShapeModule",
                        "internal_title": "Glass Border",
                        "shape_type": "RECT",
                        "shape_width": 720.0,
                        "shape_height": 340.0,
                        "shape_corners": 28.0,
                        "paint_style": "STROKE",
                        "paint_stroke": 1.5,
                        "paint_color": "#2AFFFFFF",
                        "internal_toggles": {
                            "shape_width": 10,
                            "shape_height": 10
                        },
                        "internal_formulas": {
                            "shape_width": "$si(rwidth)$",
                            "shape_height": "$si(rheight)$"
                        }
                    },
                    # 3. Top Header Bar (Group Badge + Live Date)
                    {
                        "internal_type": "OverlapLayerModule",
                        "internal_title": "Header Bar",
                        "position_anchor": "TOPLEFT",
                        "position_offset_x": 28.0,
                        "position_offset_y": 24.0,
                        "viewgroup_items": [
                            # Group Pill Badge Background
                            {
                                "internal_type": "ShapeModule",
                                "internal_title": "Badge BG",
                                "shape_type": "RECT",
                                "shape_width": 142.0,
                                "shape_height": 28.0,
                                "shape_corners": 14.0,
                                "paint_color": "#20FFFFFF",
                                "position_anchor": "CENTERLEFT"
                            },
                            # Group Pill Badge Text
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Badge Text",
                                "text_expression": "ТУСУР • 426топ-4",
                                "text_size": 11.5,
                                "paint_color": "#CCFFFFFF",
                                "position_anchor": "CENTERLEFT",
                                "position_padding_left": 14.0
                            },
                            # Date Display
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Current Date",
                                "text_expression": "$df(\"EEEE, d MMMM\")$",
                                "internal_toggles": {
                                    "text_expression": 10
                                },
                                "internal_formulas": {
                                    "text_expression": "$tc(cap, df(\"EEEE, d MMMM\"))$"
                                },
                                "text_size": 12.0,
                                "paint_color": "#75FFFFFF",
                                "position_anchor": "CENTERLEFT",
                                "position_padding_left": 156.0
                            }
                        ]
                    },
                    # 4. Corner Refresh Button (Action: FORCE_WEATHER_OR_CALENDAR_UPDATE)
                    {
                        "internal_type": "OverlapLayerModule",
                        "internal_title": "Refresh Button",
                        "position_anchor": "TOPRIGHT",
                        "position_offset_x": 24.0,
                        "position_offset_y": 20.0,
                        "internal_events": [
                            {
                                "type": "SINGLE_TAP",
                                "action": "KUSTOM_ACTION",
                                "kustom_action": "FORCE_WEATHER_OR_CALENDAR_UPDATE"
                            }
                        ],
                        "viewgroup_items": [
                            # Circular button backdrop
                            {
                                "internal_type": "ShapeModule",
                                "internal_title": "Refresh Button BG",
                                "shape_type": "CIRCLE",
                                "shape_width": 34.0,
                                "shape_height": 34.0,
                                "paint_color": "#1AFFFFFF"
                            },
                            # Refresh icon symbol
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Refresh Icon",
                                "text_expression": "↻",
                                "text_size": 16.0,
                                "paint_color": "#BBFFFFFF"
                            }
                        ]
                    },
                    # 5. Content Container: State 1 (Beer Time / Пары закончились)
                    {
                        "internal_type": "OverlapLayerModule",
                        "internal_title": "State 1: Beer Time (Пары закончились)",
                        "position_anchor": "TOPLEFT",
                        "position_offset_x": 28.0,
                        "position_offset_y": 74.0,
                        "config_visible": "ALWAYS",
                        "internal_toggles": {
                            "config_visible": 10
                        },
                        "internal_formulas": {
                            "config_visible": (
                                '$if((ci(ecount, a0d) > 0 & df(D, ci(start, 0)) != df(D)) | '
                                '(ci(ecount, a0d) = 0 & df(f) < 6 & df(H) >= 14), ALWAYS, REMOVE)$'
                            )
                        },
                        "viewgroup_items": [
                            # Status Tag Pill Background
                            {
                                "internal_type": "ShapeModule",
                                "internal_title": "Tag BG",
                                "shape_type": "RECT",
                                "shape_width": 136.0,
                                "shape_height": 22.0,
                                "shape_corners": 11.0,
                                "paint_color": "#25FFD166",
                                "position_anchor": "TOPLEFT"
                            },
                            # Status Tag Text
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Tag Text",
                                "text_expression": "✨ ДЕНЬ ЗАКРЫТ",
                                "text_size": 10.5,
                                "paint_color": "#FFD166",
                                "position_anchor": "TOPLEFT",
                                "position_padding_left": 10.0,
                                "position_padding_top": 4.0
                            },
                            # Main Headline
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Beer Headline",
                                "text_expression": "[b]🍺 Пить пиво дозволено[/b]",
                                "text_size": 24.0,
                                "paint_color": "#FFD166",
                                "position_anchor": "TOPLEFT",
                                "position_padding_top": 32.0
                            },
                            # Subtitle
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Subtitle",
                                "text_expression": "Все пары закрыты • Свободен до завтра",
                                "text_size": 14.5,
                                "paint_color": "#EEFFFFFF",
                                "position_anchor": "TOPLEFT",
                                "position_padding_top": 72.0
                            },
                            # Tomorrow's First Class Info
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Tomorrow Info",
                                "text_expression": "$if(ci(ecount, a1d) > 0, \"Завтра первая: \" + df(\"HH:mm\", ci(start, 0, a1d)) + \" • \" + tc(ell, ci(title, 0, a1d), 32), \"Завтра пар нет • Отдыхай!\")$",
                                "internal_toggles": {
                                    "text_expression": 10
                                },
                                "internal_formulas": {
                                    "text_expression": (
                                        '$if(ci(ecount, a1d) > 0, '
                                        '"Завтра первая: " + df("HH:mm", ci(start, 0, a1d)) + " • " + tc(ell, ci(title, 0, a1d), 32), '
                                        '"Завтра пар нет • Отдыхай!")$'
                                    )
                                },
                                "text_size": 12.5,
                                "paint_color": "#75FFFFFF",
                                "position_anchor": "TOPLEFT",
                                "position_padding_top": 108.0
                            }
                        ]
                    },
                    # 6. Content Container: State 2 (Идёт пара прямо сейчас + Живой прогресс-бар)
                    {
                        "internal_type": "OverlapLayerModule",
                        "internal_title": "State 2: In Class (Идёт пара)",
                        "position_anchor": "TOPLEFT",
                        "position_offset_x": 28.0,
                        "position_offset_y": 74.0,
                        "config_visible": "REMOVE",
                        "internal_toggles": {
                            "config_visible": 10
                        },
                        "internal_formulas": {
                            "config_visible": (
                                '$if(ci(ecount, a0d) > 0 & df(D, ci(start, 0)) = df(D) & '
                                'df(S) >= df(S, ci(start, 0)) & df(S) < df(S, ci(end, 0)), ALWAYS, REMOVE)$'
                            )
                        },
                        "viewgroup_items": [
                            # Status Tag Pill Background
                            {
                                "internal_type": "ShapeModule",
                                "internal_title": "Tag BG",
                                "shape_type": "RECT",
                                "shape_width": 128.0,
                                "shape_height": 22.0,
                                "shape_corners": 11.0,
                                "paint_color": "#2506D6A0",
                                "position_anchor": "TOPLEFT"
                            },
                            # Status Tag Text
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Tag Text",
                                "text_expression": "● ИДЁТ ЗАНЯТИЕ",
                                "text_size": 10.5,
                                "paint_color": "#06D6A0",
                                "position_anchor": "TOPLEFT",
                                "position_padding_left": 10.0,
                                "position_padding_top": 4.0
                            },
                            # Class Title Headline
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Class Title",
                                "text_expression": "[b]$tc(ell, ci(title, 0), 38)$[/b]",
                                "internal_toggles": {
                                    "text_expression": 10
                                },
                                "internal_formulas": {
                                    "text_expression": "[b]$tc(ell, ci(title, 0), 38)$[/b]"
                                },
                                "text_size": 20.0,
                                "paint_color": "#FFFFFFFF",
                                "position_anchor": "TOPLEFT",
                                "position_padding_top": 32.0
                            },
                            # Location and Time Range
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Location and Time",
                                "text_expression": "$ci(loc, 0)$ • $df(\"HH:mm\", ci(start, 0))$-$df(\"HH:mm\", ci(end, 0))$",
                                "internal_toggles": {
                                    "text_expression": 10
                                },
                                "internal_formulas": {
                                    "text_expression": "$tc(ell, ci(loc, 0), 36)$ • $df(\"HH:mm\", ci(start, 0))$-$df(\"HH:mm\", ci(end, 0))$"
                                },
                                "text_size": 14.0,
                                "paint_color": "#EEFFFFFF",
                                "position_anchor": "TOPLEFT",
                                "position_padding_top": 68.0
                            },
                            # Live Progress Bar element (Kustom ProgressModule)
                            {
                                "internal_type": "ProgressModule",
                                "internal_title": "Live Class Progress",
                                "progress_progress": "CUSTOM",
                                "progress_level": 50.0,
                                "color_fgcolor": "#4CC9F0",
                                "color_bgcolor": "#20FFFFFF",
                                "style_style": "FLAT",
                                "style_size": 660.0,
                                "style_height": 6.5,
                                "style_corners": 3.25,
                                "position_anchor": "TOPLEFT",
                                "position_padding_top": 104.0,
                                "internal_toggles": {
                                    "progress_level": 10,
                                    "color_fgcolor": 10,
                                    "style_size": 10
                                },
                                "internal_formulas": {
                                    "progress_level": formula_progress,
                                    "color_fgcolor": '$if(si(sysca1)!="", si(sysca1), "#4CC9F0")$',
                                    "style_size": "$mu(min, 664, si(rwidth) - 56)$"
                                }
                            },
                            # Timer Remaining Status
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Remaining Timer",
                                "text_expression": "Осталось: $tf(ci(end, 0), \"m 'мин'\") + \" • \" + mu(floor, " + formula_progress[1:-1] + ") + \"%\"$",
                                "internal_toggles": {
                                    "text_expression": 10,
                                    "paint_color": 10
                                },
                                "internal_formulas": {
                                    "text_expression": (
                                        '"Осталось: " + tf(ci(end, 0), "m \'мин\'") + " • " + '
                                        'mu(floor, ' + formula_progress[1:-1] + ') + "%"'
                                    ),
                                    "paint_color": '$if(si(sysca1)!="", si(sysca1), "#06D6A0")$'
                                },
                                "text_size": 12.5,
                                "paint_color": "#06D6A0",
                                "position_anchor": "TOPLEFT",
                                "position_padding_top": 122.0
                            }
                        ]
                    },
                    # 7. Content Container: State 3 (Перемена между парами)
                    {
                        "internal_type": "OverlapLayerModule",
                        "internal_title": "State 3: Break (Перемена)",
                        "position_anchor": "TOPLEFT",
                        "position_offset_x": 28.0,
                        "position_offset_y": 74.0,
                        "config_visible": "REMOVE",
                        "internal_toggles": {
                            "config_visible": 10
                        },
                        "internal_formulas": {
                            "config_visible": (
                                '$if(ci(ecount, a0d) > 0 & df(D, ci(start, 0)) = df(D) & '
                                'df(S) < df(S, ci(start, 0)), ALWAYS, REMOVE)$'
                            )
                        },
                        "viewgroup_items": [
                            # Status Tag Pill Background
                            {
                                "internal_type": "ShapeModule",
                                "internal_title": "Tag BG",
                                "shape_type": "RECT",
                                "shape_width": 118.0,
                                "shape_height": 22.0,
                                "shape_corners": 11.0,
                                "paint_color": "#254CC9F0",
                                "position_anchor": "TOPLEFT"
                            },
                            # Status Tag Text
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Tag Text",
                                "text_expression": "☕ ПЕРЕМЕНА",
                                "text_size": 10.5,
                                "paint_color": "#4CC9F0",
                                "position_anchor": "TOPLEFT",
                                "position_padding_left": 10.0,
                                "position_padding_top": 4.0
                            },
                            # Break Headline (Next class)
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Break Headline",
                                "text_expression": "Перерыв • Следующая: $ci(title, 0)$",
                                "internal_toggles": {
                                    "text_expression": 10
                                },
                                "internal_formulas": {
                                    "text_expression": '"Перерыв • Следующая: " + tc(ell, ci(title, 0), 28)'
                                },
                                "text_size": 20.0,
                                "paint_color": "#FFFFFFFF",
                                "position_anchor": "TOPLEFT",
                                "position_padding_top": 32.0
                            },
                            # Call timer and classroom location
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Countdown and Location",
                                "text_expression": "Звонок через $tf(ci(start, 0), \"m 'мин'\") • Кабинет: $ci(loc, 0)$",
                                "internal_toggles": {
                                    "text_expression": 10
                                },
                                "internal_formulas": {
                                    "text_expression": (
                                        '"Звонок через " + tf(ci(start, 0), "m \'мин\'") + '
                                        '" • Кабинет: " + tc(ell, ci(loc, 0), 28)'
                                    )
                                },
                                "text_size": 14.5,
                                "paint_color": "#EEFFFFFF",
                                "position_anchor": "TOPLEFT",
                                "position_padding_top": 68.0
                            },
                            # Time of next class
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Next Class Time",
                                "text_expression": "Время пары: $df(\"HH:mm\", ci(start, 0))$-$df(\"HH:mm\", ci(end, 0))$",
                                "internal_toggles": {
                                    "text_expression": 10
                                },
                                "internal_formulas": {
                                    "text_expression": (
                                        '"Время пары: " + df("HH:mm", ci(start, 0)) + '
                                        '" – " + df("HH:mm", ci(end, 0))'
                                    )
                                },
                                "text_size": 12.5,
                                "paint_color": "#75FFFFFF",
                                "position_anchor": "TOPLEFT",
                                "position_padding_top": 108.0
                            }
                        ]
                    },
                    # 8. Content Container: State 4 (Выходной день / Пар сегодня нет)
                    {
                        "internal_type": "OverlapLayerModule",
                        "internal_title": "State 4: Day Off (Выходной)",
                        "position_anchor": "TOPLEFT",
                        "position_offset_x": 28.0,
                        "position_offset_y": 74.0,
                        "config_visible": "REMOVE",
                        "internal_toggles": {
                            "config_visible": 10
                        },
                        "internal_formulas": {
                            "config_visible": (
                                '$if(ci(ecount, a0d) = 0 & (df(f) >= 6 | df(H) < 14), ALWAYS, REMOVE)$'
                            )
                        },
                        "viewgroup_items": [
                            # Status Tag Pill Background
                            {
                                "internal_type": "ShapeModule",
                                "internal_title": "Tag BG",
                                "shape_type": "RECT",
                                "shape_width": 118.0,
                                "shape_height": 22.0,
                                "shape_corners": 11.0,
                                "paint_color": "#25FFD166",
                                "position_anchor": "TOPLEFT"
                            },
                            # Status Tag Text
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Tag Text",
                                "text_expression": "🎉 ВЫХОДНОЙ",
                                "text_size": 10.5,
                                "paint_color": "#FFD166",
                                "position_anchor": "TOPLEFT",
                                "position_padding_left": 10.0,
                                "position_padding_top": 4.0
                            },
                            # Main Headline
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Weekend Headline",
                                "text_expression": "[b]🎉 Выходной день[/b]",
                                "text_size": 24.0,
                                "paint_color": "#FFD166",
                                "position_anchor": "TOPLEFT",
                                "position_padding_top": 32.0
                            },
                            # Subtitle
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Subtitle",
                                "text_expression": "Пар нет, отдых продолжается",
                                "text_size": 14.5,
                                "paint_color": "#EEFFFFFF",
                                "position_anchor": "TOPLEFT",
                                "position_padding_top": 72.0
                            },
                            # Upcoming Schedule Hint
                            {
                                "internal_type": "TextModule",
                                "internal_title": "Upcoming Hint",
                                "text_expression": "$if(ci(ecount, a1d) > 0, \"Завтра первая пара: \" + df(\"HH:mm\", ci(start, 0, a1d)) + \" • \" + tc(ell, ci(title, 0, a1d), 30), \"Свободное время • Отличных выходных!\")$",
                                "internal_toggles": {
                                    "text_expression": 10
                                },
                                "internal_formulas": {
                                    "text_expression": (
                                        '$if(ci(ecount, a1d) > 0, '
                                        '"Завтра первая пара: " + df("HH:mm", ci(start, 0, a1d)) + " • " + tc(ell, ci(title, 0, a1d), 30), '
                                        '"Свободное время • Отличных выходных!")$'
                                    )
                                },
                                "text_size": 12.5,
                                "paint_color": "#75FFFFFF",
                                "position_anchor": "TOPLEFT",
                                "position_padding_top": 108.0
                            }
                        ]
                    }
                ]
            }
        ]
    }

    return {
        "preset_info": preset_info,
        "preset_root": preset_root
    }


def build_kwgt(output_path: str = "schedule_widget.kwgt"):
    """Compiles the preset JSON and packages it into a valid .kwgt zip archive."""
    preset = create_preset_data()
    preset_json_str = json.dumps(preset, ensure_ascii=False, indent=2)

    with zipfile.ZipFile(output_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        # preset.json (core Kustom structure)
        zf.writestr("preset.json", preset_json_str.encode("utf-8"))
        # Thumbnail previews for KWGT preset browser / gallery
        zf.writestr("preset_thumb_portrait.jpg", THUMBNAIL_BYTES)
        zf.writestr("preset_thumb_landscape.jpg", THUMBNAIL_BYTES)

    file_size = os.path.getsize(output_path)
    print(f"✅ Successfully built KWGT preset: {output_path} ({file_size} bytes)")
    
    # Self-verification of output
    with zipfile.ZipFile(output_path, "r") as check_zf:
        namelist = check_zf.namelist()
        assert "preset.json" in namelist, "Verification failed: preset.json missing"
        parsed = json.loads(check_zf.read("preset.json").decode("utf-8"))
        assert "preset_info" in parsed, "Verification failed: preset_info missing"
        assert "preset_root" in parsed, "Verification failed: preset_root missing"
        items = parsed["preset_root"]["viewgroup_items"]
        print(f"📦 Archive contents: {namelist}")
        print(f"🔎 Root hierarchy verified: {len(items)} top-level card(s)")


def main():
    parser = argparse.ArgumentParser(description="Generate KWGT schedule preset file")
    parser.add_argument("-o", "--output", default="schedule_widget.kwgt", help="Output file path (.kwgt)")
    args = parser.parse_args()
    build_kwgt(args.output)


if __name__ == "__main__":
    main()
