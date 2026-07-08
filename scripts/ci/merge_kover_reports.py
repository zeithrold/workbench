#!/usr/bin/env python3
"""Merge per-module Kover XML reports into a root aggregate without running Gradle tests."""

from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

BACKEND_MODULE_PREFIX = "workbench-"
COUNTER_TYPES = ("INSTRUCTION", "BRANCH", "LINE", "METHOD", "CLASS")


def repo_root() -> Path:
    env = os.environ.get("REPO_ROOT")
    if env:
        return Path(env).resolve()
    return Path(".").resolve()


def discover_module_reports(root: Path) -> list[Path]:
    reports: list[Path] = []
    for child in sorted(root.iterdir()):
        if not child.is_dir() or not child.name.startswith(BACKEND_MODULE_PREFIX):
            continue
        report = child / "build" / "reports" / "kover" / "report.xml"
        if report.is_file():
            reports.append(report)
    return reports


def report_level_counters(path: Path) -> dict[str, tuple[int, int]]:
    try:
        document = ET.parse(path)
    except ET.ParseError:
        return {}

    totals: dict[str, tuple[int, int]] = {}
    for counter in document.getroot().findall("counter"):
        counter_type = counter.get("type")
        if counter_type is None:
            continue
        missed = int(counter.get("missed", "0"))
        covered = int(counter.get("covered", "0"))
        if counter_type in totals:
            prev_missed, prev_covered = totals[counter_type]
            totals[counter_type] = (prev_missed + missed, prev_covered + covered)
        else:
            totals[counter_type] = (missed, covered)
    return totals


def merge_counters(reports: list[Path]) -> dict[str, tuple[int, int]]:
    merged: dict[str, tuple[int, int]] = {}
    for report in reports:
        for counter_type, (missed, covered) in report_level_counters(report).items():
            if counter_type in merged:
                prev_missed, prev_covered = merged[counter_type]
                merged[counter_type] = (prev_missed + missed, prev_covered + covered)
            else:
                merged[counter_type] = (missed, covered)
    return merged


def write_aggregate_report(output: Path, counters: dict[str, tuple[int, int]]) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    report = ET.Element(
        "report",
        name="Kover aggregated report (Nightly CI module merge)",
    )
    for counter_type in COUNTER_TYPES:
        if counter_type not in counters:
            continue
        missed, covered = counters[counter_type]
        ET.SubElement(
            report,
            "counter",
            type=counter_type,
            missed=str(missed),
            covered=str(covered),
        )
    ET.ElementTree(report).write(output, encoding="unicode", xml_declaration=True)


def main() -> int:
    root = repo_root()
    module_reports = discover_module_reports(root)
    if not module_reports:
        print("No module Kover reports found under workbench-*/build/reports/kover/", file=sys.stderr)
        return 1

    counters = merge_counters(module_reports)
    if not counters:
        print("Module Kover reports contained no aggregate counters.", file=sys.stderr)
        return 1

    output = root / "build" / "reports" / "kover" / "report.xml"
    write_aggregate_report(output, counters)
    print(f"Wrote aggregated Kover report for {len(module_reports)} modules to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
