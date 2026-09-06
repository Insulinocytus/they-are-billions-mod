#!/usr/bin/env python3
"""Summarize a PresentMon CSV into average FPS and P95 frame time."""

from __future__ import annotations

import csv
import json
import math
import sys
from pathlib import Path


def percentile(sorted_values: list[float], p: float) -> float:
    if not sorted_values:
        return 0.0
    index = math.ceil(p / 100.0 * len(sorted_values)) - 1
    index = min(max(index, 0), len(sorted_values) - 1)
    return sorted_values[index]


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: summarize_presentmon.py <presentmon.csv>")
    path = Path(sys.argv[1])
    times: list[float] = []
    with path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        field = next(
            (name for name in (reader.fieldnames or []) if name.lower() in {"msbetweenpresents", "msbetweenppresents"}),
            None,
        )
        if field is None:
            raise SystemExit("PresentMon CSV missing msBetweenPresents")
        for row in reader:
            value = row.get(field, "").strip()
            if value:
                times.append(float(value))
    if not times:
        raise SystemExit("no PresentMon frame samples")
    average = sum(times) / len(times)
    summary = {
        "samples": len(times),
        "averageFrameTimeMs": average,
        "averageFps": 1000.0 / average if average else 0.0,
        "p95FrameTimeMs": percentile(sorted(times), 95.0),
        "source": str(path),
    }
    print(json.dumps(summary, indent=2))
    path.with_suffix(".summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
