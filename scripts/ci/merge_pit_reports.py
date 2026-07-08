#!/usr/bin/env python3
"""Merge per-module PIT XML reports into a root aggregate without running Gradle."""

from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

BACKEND_MODULE_PREFIX = "workbench-"


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
        report = child / "build" / "reports" / "pitest" / "mutations.xml"
        if report.is_file():
            reports.append(report)
    return reports


def parse_mutations_document(path: Path) -> tuple[ET.Element, list[ET.Element], bool]:
    document = ET.parse(path)
    root = document.getroot()
    if root.tag != "mutations":
        msg = f"Expected <mutations> root in {path}, found <{root.tag}>"
        raise ValueError(msg)

    partial = root.get("partial", "false").lower() == "true"
    mutations = list(root.findall("mutation"))
    return root, mutations, partial


def merge_mutations(reports: list[Path]) -> ET.Element:
    merged = ET.Element("mutations")
    partial = False

    for report in reports:
        _, mutations, report_partial = parse_mutations_document(report)
        partial = partial or report_partial
        for mutation in mutations:
            merged.append(mutation)

    if partial:
        merged.set("partial", "true")

    return merged


def write_aggregate_report(output: Path, merged: ET.Element) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(merged).write(output, encoding="unicode", xml_declaration=True)


def main() -> int:
    root = repo_root()
    module_reports = discover_module_reports(root)
    if not module_reports:
        print(
            "No module PIT reports found under workbench-*/build/reports/pitest/mutations.xml",
            file=sys.stderr,
        )
        return 1

    try:
        merged = merge_mutations(module_reports)
    except (ET.ParseError, ValueError) as error:
        print(f"Failed to merge PIT reports: {error}", file=sys.stderr)
        return 1

    mutation_count = len(list(merged.findall("mutation")))
    if mutation_count == 0:
        print("Module PIT reports contained no mutations.", file=sys.stderr)
        return 1

    output = root / "build" / "reports" / "pitest" / "mutations.xml"
    write_aggregate_report(output, merged)
    print(
        f"Wrote aggregated PIT report for {len(module_reports)} modules "
        f"({mutation_count} mutations) to {output}",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
