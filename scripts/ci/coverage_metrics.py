#!/usr/bin/env python3
"""Shared coverage metric parsing for Kover XML and Vitest LCOV reports."""

from __future__ import annotations

import os
import subprocess
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

BACKEND_MODULE_PREFIX = "workbench-"
FRONTEND_MODULE = "workbench-frontend"
INTEGRATION_LIFT_WARN_THRESHOLD = 15.0


@dataclass(frozen=True)
class CoverageMetrics:
    line: float | None = None
    branch: float | None = None
    instruction: float | None = None


def repo_root() -> Path:
    env = os.environ.get("REPO_ROOT")
    if env:
        path = Path(env).resolve()
        if env != "." or (path / "build.gradle.kts").is_file():
            return path

    result = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode == 0:
        return Path(result.stdout.strip()).resolve()
    return Path(".").resolve()


def parse_kover_report(path: Path) -> CoverageMetrics | None:
    if not path.is_file():
        return None
    try:
        document = ET.parse(path)
    except ET.ParseError:
        return None

    counters: dict[str, tuple[int, int]] = {}
    for counter in document.getroot().iter("counter"):
        counter_type = counter.get("type")
        if counter_type is None:
            continue
        missed = int(counter.get("missed", "0"))
        covered = int(counter.get("covered", "0"))
        if counter_type in counters:
            prev_missed, prev_covered = counters[counter_type]
            counters[counter_type] = (prev_missed + missed, prev_covered + covered)
        else:
            counters[counter_type] = (missed, covered)

    def pct(counter_type: str) -> float | None:
        if counter_type not in counters:
            return None
        missed, covered = counters[counter_type]
        total = missed + covered
        if total == 0:
            return None
        return covered / total * 100.0

    return CoverageMetrics(
        line=pct("LINE"),
        branch=pct("BRANCH"),
        instruction=pct("INSTRUCTION"),
    )


def parse_lcov_report(path: Path) -> CoverageMetrics | None:
    if not path.is_file():
        return None

    line_hits: dict[tuple[str, int], int] = {}
    branch_hits: dict[tuple[str, int, int, int], int] = {}
    current_file: str | None = None

    for raw_line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw_line.strip()
        if line.startswith("SF:"):
            current_file = line[3:]
            continue
        if current_file is None:
            continue
        if line.startswith("DA:"):
            parts = line[3:].split(",")
            if len(parts) != 2:
                continue
            try:
                line_no = int(parts[0])
                hits = int(parts[1])
            except ValueError:
                continue
            line_hits[(current_file, line_no)] = hits
        elif line.startswith("BRDA:"):
            parts = line[5:].split(",")
            if len(parts) != 4:
                continue
            try:
                line_no = int(parts[0])
                block = int(parts[1])
                branch = int(parts[2])
                hits = int(parts[3])
            except ValueError:
                continue
            branch_hits[(current_file, line_no, block, branch)] = hits
        elif line == "end_of_record":
            current_file = None

    line_total = len(line_hits)
    line_covered = sum(1 for hits in line_hits.values() if hits > 0)
    line_pct = (line_covered / line_total * 100.0) if line_total > 0 else None

    branch_total = len(branch_hits)
    branch_covered = sum(1 for hits in branch_hits.values() if hits > 0)
    branch_pct = (branch_covered / branch_total * 100.0) if branch_total > 0 else None

    if line_pct is None and branch_pct is None:
        return None
    return CoverageMetrics(line=line_pct, branch=branch_pct, instruction=None)


def has_coverage_data(metrics: CoverageMetrics | None) -> bool:
    if metrics is None:
        return False
    return any(value is not None for value in (metrics.line, metrics.branch, metrics.instruction))


def integration_lift(full_line: float | None, unit_line: float | None) -> float | None:
    if full_line is None or unit_line is None:
        return None
    return full_line - unit_line


def discover_backend_modules(root: Path) -> list[str]:
    modules: list[str] = []
    for child in sorted(root.iterdir()):
        if not child.is_dir() or not child.name.startswith(BACKEND_MODULE_PREFIX):
            continue
        if child.name == FRONTEND_MODULE:
            continue
        full_report = child / "build" / "reports" / "kover" / "report.xml"
        unit_report = child / "build" / "reports" / "kover" / "unit" / "report.xml"
        if full_report.is_file() or unit_report.is_file():
            modules.append(child.name)
    return modules


def module_full_kover_report(root: Path, module: str) -> Path:
    return root / module / "build" / "reports" / "kover" / "report.xml"


def module_unit_kover_report(root: Path, module: str) -> Path:
    return root / module / "build" / "reports" / "kover" / "unit" / "report.xml"


def frontend_full_lcov(root: Path) -> Path:
    return root / FRONTEND_MODULE / "coverage" / "lcov.info"


def frontend_unit_lcov(root: Path) -> Path:
    return root / FRONTEND_MODULE / "coverage" / "unit" / "lcov.info"


def aggregate_kover_metrics(
    root: Path, modules: list[str], *, unit: bool
) -> CoverageMetrics | None:
    counter_totals: dict[str, tuple[int, int]] = {}
    for module in modules:
        report = (
            module_unit_kover_report(root, module)
            if unit
            else module_full_kover_report(root, module)
        )
        if not report.is_file():
            continue
        try:
            document = ET.parse(report)
        except ET.ParseError:
            continue
        for counter in document.getroot().findall("counter"):
            counter_type = counter.get("type")
            if counter_type is None:
                continue
            missed = int(counter.get("missed", "0"))
            covered = int(counter.get("covered", "0"))
            if counter_type in counter_totals:
                prev_missed, prev_covered = counter_totals[counter_type]
                counter_totals[counter_type] = (prev_missed + missed, prev_covered + covered)
            else:
                counter_totals[counter_type] = (missed, covered)

    def pct(counter_type: str) -> float | None:
        if counter_type not in counter_totals:
            return None
        missed, covered = counter_totals[counter_type]
        total = missed + covered
        if total == 0:
            return None
        return covered / total * 100.0

    metrics = CoverageMetrics(
        line=pct("LINE"),
        branch=pct("BRANCH"),
        instruction=pct("INSTRUCTION"),
    )
    return metrics if has_coverage_data(metrics) else None
