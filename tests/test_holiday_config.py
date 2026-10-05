#!/usr/bin/env python3
"""
Unit test verifying statutory holiday configuration generation, schema conformance,
file integrity of core/src/assets/holidays_2026.json, and alignment with HolidaySyncManager.
"""

import json
import os
import re
import sys
import unittest

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, REPO_ROOT)

from scripts.generate_holiday_config import (
    OFFICIAL_CALENDAR_DATA,
    generate_year_config,
    validate_config,
)


class TestHolidayConfig(unittest.TestCase):

    def test_generate_year_config_2026(self):
        config = generate_year_config(2026)
        self.assertIsNotNone(config)
        self.assertEqual(config["year"], 2026)
        self.assertIn("holidays", config)
        self.assertIn("workdays", config)
        self.assertGreater(len(config["holidays"]), 0)
        self.assertGreater(len(config["workdays"]), 0)
        # Ensure validation passes
        validate_config(config)

    def test_committed_assets_holidays_2026(self):
        asset_path = os.path.join(REPO_ROOT, "core", "src", "assets", "holidays_2026.json")
        self.assertTrue(os.path.exists(asset_path), f"Missing asset file: {asset_path}")

        with open(asset_path, "r", encoding="utf-8") as f:
            data = json.load(f)

        self.assertEqual(data.get("year"), 2026)
        validate_config(data)

        # Check National Day and New Year
        self.assertIn("2026-01-01", data["holidays"])
        self.assertIn("2026-10-01", data["holidays"])
        self.assertIn("2026-02-15", data["workdays"])

    def test_holiday_sync_manager_default_url(self):
        manager_path = os.path.join(
            REPO_ROOT, "app", "src", "com", "edom", "alarm", "ui", "HolidaySyncManager.java"
        )
        self.assertTrue(os.path.exists(manager_path))

        with open(manager_path, "r", encoding="utf-8") as f:
            content = f.read()

        expected_url = (
            "https://raw.githubusercontent.com/edompeng/alarm/master/core/src/assets/holidays_2026.json"
        )
        self.assertIn(
            f'DEFAULT_SYNC_URL = "{expected_url}";',
            content,
            "HolidaySyncManager must point to official repo default URL",
        )


if __name__ == "__main__":
    unittest.main()
