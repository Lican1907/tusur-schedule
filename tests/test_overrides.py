"""Unit tests for teacher overrides application (cancel, set, room preservation)."""

import unittest
from zoneinfo import ZoneInfo

from src.core.models import Lesson
from src.overrides.engine import apply_overrides_to_lessons

TZ = ZoneInfo("Asia/Tomsk")


class TestOverridesEngine(unittest.TestCase):
    def setUp(self):
        self.sample_lessons = [
            Lesson(
                id="test-1",
                date="2026-10-15",
                pair_num=1,
                time_start="08:50",
                time_end="10:25",
                subject="Дискретная математика",
                short_subject="Дискретка",
                lesson_type="ПРАК",
                type_marker="ПРАК 🔵",
                teacher="Иванов И.И.",
                room="418",
                building="ФЭТ",
                location="📍 ФЭТ 418",
                color_id="9",
                status="ACTIVE",
                summary="ПРАК 🔵 Дискретка",
                description="Практическое занятие по дискретной математике",
            ),
            Lesson(
                id="test-2",
                date="2026-10-15",
                pair_num=2,
                time_start="10:40",
                time_end="12:15",
                subject="Математика",
                short_subject="Математика",
                lesson_type="ПРАК",
                type_marker="ПРАК 🔵",
                teacher="Петров П.П.",
                room="310",
                building="РК",
                location="📍 РК 310",
                color_id="9",
                status="ACTIVE",
                summary="ПРАК 🔵 Математика",
                description="Практика по математике",
            ),
        ]

    def test_cancel_override(self):
        overrides = [
            {
                "id": "ov-cancel-disc",
                "date": "2026-10-15",
                "action": "cancel",
                "time_start": "08:50",
                "type": "ПРАК",
                "subject": "Дискретка",
                "comment": "Болезнь преподавателя",
            }
        ]

        result = apply_overrides_to_lessons(self.sample_lessons, overrides, group_name="426топ-4", tz=TZ)
        cancelled = result[0]
        self.assertEqual(cancelled.status, "CANCELLED")
        self.assertEqual(cancelled.summary, "❌ [ОТМЕНА] Дискретка")
        self.assertEqual(cancelled.color_id, "8")
        self.assertIn("❌ Занятие отменено преподавателем: Болезнь преподавателя", cancelled.description)
        # Location and original time preserved
        self.assertEqual(cancelled.location, "📍 ФЭТ 418")
        self.assertEqual(cancelled.time_start, "08:50")

    def test_set_replacement_with_room_preservation(self):
        # Teacher replaces practice with lecture but keeps original room
        overrides = [
            {
                "id": "ov-replace-math",
                "date": "2026-10-15",
                "action": "set",
                "time_start": "10:40",
                "type": "ЛЕК",
                "subject": "Математика",
                "keep_original_location": True,
                "location": None,
                "comment": "Вместо практики будет лекция в той же аудитории",
            }
        ]

        result = apply_overrides_to_lessons(self.sample_lessons, overrides, group_name="426топ-4", tz=TZ)
        replaced = result[1]
        self.assertEqual(replaced.status, "CHANGED")
        self.assertEqual(replaced.summary, "⚡ ЛЕК 🟢 Математика")
        self.assertEqual(replaced.color_id, "10")  # Green Basil
        self.assertEqual(replaced.location, "📍 РК 310")  # Preserved original room
        self.assertIn("⚡ Изменение в расписании от преподавателя", replaced.description)

    def test_set_with_new_location(self):
        overrides = [
            {
                "id": "ov-change-loc",
                "date": "2026-10-15",
                "action": "set",
                "time_start": "10:40",
                "type": "ЛЕК",
                "subject": "Математика",
                "keep_original_location": False,
                "location": "ГК 411",
                "comment": "Перенос в актовый зал",
            }
        ]

        result = apply_overrides_to_lessons(self.sample_lessons, overrides, group_name="426топ-4", tz=TZ)
        replaced = result[1]
        self.assertEqual(replaced.location, "📍 ГК 411")
        self.assertEqual(replaced.room, "411")

    def test_group_filtering(self):
        # Override targeted for 426-3 should NOT affect 426топ-4
        overrides = [
            {
                "id": "ov-other-group",
                "group": "426-3",
                "date": "2026-10-15",
                "action": "cancel",
                "subject": "Дискретка",
                "comment": "Отмена только для 426-3",
            }
        ]

        result = apply_overrides_to_lessons(self.sample_lessons, overrides, group_name="426топ-4", tz=TZ)
        self.assertEqual(result[0].status, "ACTIVE")
        self.assertFalse(result[0].summary.startswith("❌"))


if __name__ == "__main__":
    unittest.main()
