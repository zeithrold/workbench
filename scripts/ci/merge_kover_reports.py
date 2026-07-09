#!/usr/bin/env python3
"""Merge per-module Kover XML reports into a root aggregate without running Gradle tests."""

from __future__ import annotations

import argparse
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


def discover_module_reports(root: Path, *, unit: bool) -> list[Path]:
    reports: list[Path] = []
    for child in sorted(root.iterdir()):
        if not child.is_dir() or not child.name.startswith(BACKEND_MODULE_PREFIX):
            continue
        if child.name == "workbench-frontend":
            continue
        report = (
            child / "build" / "reports" / "kover" / "unit" / "report.xml"
            if unit
            else child / "build" / "reports" / "kover" / "report.xml"
        )
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


def write_aggregate_report(
    output: Path, counters: dict[str, tuple[int, int]], *, unit: bool
) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    label = "unit" if unit else "full"
    report = ET.Element(
        "report",
        name=f"Kover aggregated report (Nightly CI module merge, {label})",
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


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--scope",
        choices=("full", "unit"),
        default="full",
        help="Merge full (default) or unit-only per-module Kover reports",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    unit = args.scope == "unit"
    root = repo_root()
    module_reports = discover_module_reports(root, unit=unit)
    if not module_reports:
        suffix = "unit/" if unit else ""
        print(
            f"No module Kover reports found under workbench-*/build/reports/kover/{suffix}",
            file=sys.stderr,
        )
        return 1

    counters = merge_counters(module_reports)
    if not counters:
        print("Module Kover reports contained no aggregate counters.", file=sys.stderr)
        return 1

    output = (
        root / "build" / "reports" / "kover" / "unit" / "report.xml"
        if unit
        else root / "build" / "reports" / "kover" / "report.xml"
    )
    write_aggregate_report(output, counters, unit=unit)
    scope_label = "unit" if unit else "full"
    print(
        f"Wrote aggregated {scope_label} Kover report for {len(module_reports)} modules to {output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
