"""Unit tests for bell timings and active pair status detection."""

from datetime import datetime
import unittest
from zoneinfo import ZoneInfo

from src.core.bells import get_bell_schedule, get_current_pair_status, get_pair_times

TZ = ZoneInfo("Asia/Tomsk")


class TestBellSchedule(unittest.TestCase):
    def test_schedule_pairs_count(self):
        bells = get_bell_schedule()
        self.assertEqual(len(bells), 7)
        self.assertIn(1, bells)
        self.assertIn(7, bells)

    def test_pair_timings(self):
        self.assertEqual(get_pair_times(1), ("08:50", "10:25"))
        self.assertEqual(get_pair_times(2), ("10:40", "12:15"))
        self.assertEqual(get_pair_times(3), ("13:15", "14:50"))
        self.assertEqual(get_pair_times(4), ("15:00", "16:35"))

    def test_saturday_shifted_pair(self):
        # Saturday pair 3 shifted start: 12:30 - 14:05
        self.assertEqual(get_pair_times(3, is_saturday_shifted=True), ("12:30", "14:05"))
        self.assertEqual(get_pair_times(3, custom_shift="с 12:30"), ("12:30", "14:05"))

    def test_custom_shift(self):
        self.assertEqual(get_pair_times(2, custom_shift="с 11:00"), ("11:00", "12:35"))

    def test_status_before_classes(self):
        mock_time = datetime(2026, 10, 15, 8, 0, tzinfo=TZ)
        status = get_current_pair_status(now=mock_time, tz=TZ)
        self.assertEqual(status["status"], "before_classes")
        self.assertEqual(status["next_pair"], 1)
        self.assertEqual(status["minutes_until_next_pair"], 50)
        self.assertIsNone(status["current_pair"])

    def test_status_in_pair(self):
        # 09:15 is inside pair 1 (08:50 - 10:25)
        mock_time = datetime(2026, 10, 15, 9, 15, tzinfo=TZ)
        status = get_current_pair_status(now=mock_time, tz=TZ)
        self.assertEqual(status["status"], "in_pair")
        self.assertEqual(status["current_pair"], 1)
        self.assertEqual(status["next_pair"], 2)
        self.assertEqual(status["minutes_left_in_pair"], 70)

    def test_status_in_break(self):
        # 10:30 is between pair 1 (ends 10:25) and pair 2 (starts 10:40)
        mock_time = datetime(2026, 10, 15, 10, 30, tzinfo=TZ)
        status = get_current_pair_status(now=mock_time, tz=TZ)
        self.assertEqual(status["status"], "in_break")
        self.assertIsNone(status["current_pair"])
        self.assertEqual(status["next_pair"], 2)
        self.assertEqual(status["minutes_until_next_pair"], 10)

    def test_status_after_classes(self):
        mock_time = datetime(2026, 10, 15, 22, 0, tzinfo=TZ)
        status = get_current_pair_status(now=mock_time, tz=TZ)
        self.assertEqual(status["status"], "after_classes")
        self.assertIsNone(status["current_pair"])
        self.assertIsNone(status["next_pair"])


if __name__ == "__main__":
    unittest.main()
