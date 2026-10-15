"""Unit tests for iCalendar and JSON exporters."""

from datetime import datetime
import json
import tempfile
import unittest
from zoneinfo import ZoneInfo

from src.core.models import Lesson
from src.exporters.ics import build_calendar
from src.exporters.json import export_schedule_json, export_today_json

TZ = ZoneInfo("Asia/Tomsk")


class TestExporters(unittest.TestCase):
    def setUp(self):
        self.mock_now = datetime(2026, 10, 15, 12, 0, tzinfo=TZ)
        self.lessons = [
            Lesson(
                id="les-1",
                date="2026-10-15",
                pair_num=1,
                time_start="08:50",
                time_end="10:25",
                subject="Математика",
                short_subject="Математика",
                lesson_type="ЛЕК",
                type_marker="ЛЕК 🟢",
                teacher="Иванов И.И.",
                room="310",
                building="РК",
                location="📍 РК 310",
                color_id="10",
                status="ACTIVE",
                summary="ЛЕК 🟢 Математика",
                description="Лекция по высшей математике",
            ),
            Lesson(
                id="les-2",
                date="2026-10-15",
                pair_num=2,
                time_start="10:40",
                time_end="12:15",
                subject="Дискретная математика",
                short_subject="Дискретка",
                lesson_type="ПРАК",
                type_marker="ПРАК 🔵",
                teacher="Петров П.П.",
                room="418",
                building="ФЭТ",
                location="📍 ФЭТ 418",
                color_id="9",
                status="ACTIVE",
                summary="ПРАК 🔵 Дискретка",
                description="Практика по дискретной математике",
            ),
            Lesson(
                id="les-3",
                date="2026-10-16",
                pair_num=1,
                time_start="08:50",
                time_end="10:25",
                subject="История",
                short_subject="История",
                lesson_type="ЛЕК",
                type_marker="ЛЕК 🟢",
                teacher="Сидоров С.С.",
                room="201",
                building="ГК",
                location="📍 ГК 201",
                color_id="10",
                status="ACTIVE",
                summary="ЛЕК 🟢 История",
            ),
        ]

    def test_ics_export_structure(self):
        cal = build_calendar(self.lessons, group_name="426топ-4", tz=TZ)
        ical_str = cal.to_ical().decode("utf-8")

        self.assertIn("BEGIN:VCALENDAR", ical_str)
        self.assertIn("BEGIN:VTIMEZONE", ical_str)
        self.assertIn("TZID:Asia/Tomsk", ical_str)
        self.assertIn("SUMMARY:ЛЕК 🟢 Математика", ical_str)
        self.assertIn("LOCATION:📍 РК 310", ical_str)
        self.assertIn("X-COLOR-ID:10", ical_str)

    def test_today_json_schema(self):
        with tempfile.NamedTemporaryFile("w+", suffix=".json", delete=False) as tmp:
            tmp_path = tmp.name

        data = export_today_json(
            self.lessons,
            group_name="426топ-4",
            filepath=tmp_path,
            now=self.mock_now,
            tz=TZ,
        )

        self.assertEqual(data["date"], "2026-10-15")
        self.assertEqual(data["group"], "426топ-4")
        self.assertEqual(len(data["lessons"]), 2)
        # Check normalized lesson schema
        first_les = data["lessons"][0]
        self.assertEqual(first_les["pair_num"], 1)
        self.assertEqual(first_les["time_start"], "08:50")
        self.assertEqual(first_les["time_end"], "10:25")
        self.assertEqual(first_les["subject"], "Математика")
        self.assertEqual(first_les["lesson_type"], "ЛЕК")
        self.assertEqual(first_les["teacher"], "Иванов И.И.")
        self.assertEqual(first_les["room"], "310")
        self.assertEqual(first_les["building"], "РК")
        self.assertEqual(first_les["status"], "ACTIVE")
        self.assertEqual(first_les["color_id"], "10")

        # Beer leisure event included for today
        self.assertIsNotNone(data["beer_event"])
        self.assertEqual(data["beer_event"]["summary"], "🍺 Можно пить пиво!")
        self.assertEqual(data["beer_event"]["time_start"], "12:15")

        with open(tmp_path, "r", encoding="utf-8") as f:
            disk_data = json.load(f)
        self.assertEqual(disk_data["date"], "2026-10-15")

    def test_schedule_json_schema(self):
        with tempfile.NamedTemporaryFile("w+", suffix=".json", delete=False) as tmp:
            tmp_path = tmp.name

        data = export_schedule_json(
            self.lessons,
            group_name="426топ-4",
            filepath=tmp_path,
            tz=TZ,
        )

        self.assertEqual(data["group"], "426топ-4")
        self.assertEqual(data["total_lessons"], 3)
        self.assertEqual(data["total_days"], 2)
        self.assertIn("2026-10-15", data["days"])
        self.assertIn("2026-10-16", data["days"])
        self.assertEqual(len(data["days"]["2026-10-15"]), 2)
        self.assertEqual(len(data["days"]["2026-10-16"]), 1)


if __name__ == "__main__":
    unittest.main()
