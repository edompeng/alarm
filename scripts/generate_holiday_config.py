#!/usr/bin/env python3
"""
scripts/generate_holiday_config.py

One-click generator for China statutory holidays and compensatory workdays configuration.
Generates validated JSON conforming to the Smart Alarm HolidaySyncModel schema.
Supports current year and next year (if officially published by General Office of State Council).
"""

import argparse
import datetime
import json
import os
import sys

# Official baseline arrangements published by the General Office of the State Council of the PRC
OFFICIAL_CALENDAR_DATA = {
    2025: {
        "holidays": [
            "2025-01-01",
            "2025-01-28", "2025-01-29", "2025-01-30", "2025-01-31",
            "2025-02-01", "2025-02-02", "2025-02-03", "2025-02-04",
            "2025-04-04", "2025-04-05", "2025-04-06",
            "2025-05-01", "2025-05-02", "2025-05-03", "2025-05-04", "2025-05-05",
            "2025-05-31", "2025-06-01", "2025-06-02",
            "2025-10-01", "2025-10-02", "2025-10-03", "2025-10-04",
            "2025-10-05", "2025-10-06", "2025-10-07", "2025-10-08"
        ],
        "workdays": [
            "2025-01-26", "2025-02-08",
            "2025-04-27", "2025-09-28", "2025-10-11"
        ]
    },
    2026: {
        "holidays": [
            "2026-01-01", "2026-01-02", "2026-01-03",
            "2026-02-16", "2026-02-17", "2026-02-18", "2026-02-19", "2026-02-20", "2026-02-21", "2026-02-22", "2026-02-23",
            "2026-04-04", "2026-04-05", "2026-04-06",
            "2026-05-01", "2026-05-02", "2026-05-03", "2026-05-04", "2026-05-05",
            "2026-06-19", "2026-06-20", "2026-06-21",
            "2026-09-25", "2026-09-26", "2026-09-27",
            "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-05", "2026-10-06", "2026-10-07"
        ],
        "workdays": [
            "2026-02-15", "2026-02-28",
            "2026-04-26", "2026-05-09",
            "2026-09-20", "2026-10-10"
        ]
    }
}


def validate_config(config: dict) -> None:
    """Validates holiday configuration conforming to HolidaySyncModel."""
    year = config.get("year")
    if not isinstance(year, int) or year < 2020 or year > 2099:
        raise ValueError(f"Invalid year: {year}")

    holidays = config.get("holidays")
    workdays = config.get("workdays")

    if not isinstance(holidays, list):
        raise ValueError("'holidays' must be a JSON array")
    if not isinstance(workdays, list):
        raise ValueError("'workdays' must be a JSON array")

    h_set = set()
    for h in holidays:
        datetime.date.fromisoformat(h)
        if not h.startswith(str(year)):
            raise ValueError(f"Holiday date {h} does not match year {year}")
        if h in h_set:
            raise ValueError(f"Duplicate holiday date: {h}")
        h_set.add(h)

    w_set = set()
    for w in workdays:
        datetime.date.fromisoformat(w)
        if not w.startswith(str(year)):
            raise ValueError(f"Workday date {w} does not match year {year}")
        if w in w_set:
            raise ValueError(f"Duplicate workday date: {w}")
        w_set.add(w)

    overlap = h_set.intersection(w_set)
    if overlap:
        raise ValueError(f"Overlap between holidays and workdays: {overlap}")


def generate_year_config(year: int) -> dict:
    """Generates and validates configuration dictionary for a given year."""
    if year in OFFICIAL_CALENDAR_DATA:
        data = OFFICIAL_CALENDAR_DATA[year]
        config = {
            "year": year,
            "authority": "General Office of the State Council",
            "holidays": sorted(data["holidays"]),
            "workdays": sorted(data["workdays"])
        }
        validate_config(config)
        return config
    return None


def main():
    parser = argparse.ArgumentParser(description="Generate statutory holiday & compensatory workday JSON config.")
    parser.add_argument("--year", type=int, help="Specify single target year (e.g. 2026)")
    parser.add_argument("--output", "-o", type=str, help="Output file path (default: stdout)")
    parser.add_argument("--include-next", action="store_true", default=True,
                        help="Include next year if officially announced")
    args = parser.parse_args()

    current_year = datetime.datetime.now().year
    target_years = []

    if args.year:
        target_years = [args.year]
    else:
        target_years = [current_year]
        next_year = current_year + 1
        if next_year in OFFICIAL_CALENDAR_DATA:
            target_years.append(next_year)
        else:
            print(f"[INFO] Next year ({next_year}) statutory holiday arrangement has not been announced yet. Omitting.",
                  file=sys.stderr)

    configs = []
    for y in target_years:
        cfg = generate_year_config(y)
        if cfg:
            configs.append(cfg)
        else:
            print(f"[WARN] No official holiday arrangement available for year {y}.", file=sys.stderr)

    if not configs:
        print("[ERROR] No valid holiday configurations could be generated.", file=sys.stderr)
        sys.exit(1)

    result = configs[0] if len(configs) == 1 else configs

    formatted_json = json.dumps(result, ensure_ascii=False, indent=2)

    if args.output:
        os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(formatted_json)
            f.write("\n")
        print(f"[SUCCESS] Holiday configuration written to {args.output} ({len(configs)} year(s))")
    else:
        print(formatted_json)


if __name__ == "__main__":
    main()
