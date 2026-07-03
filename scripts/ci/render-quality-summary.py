#!/usr/bin/env python3
"""Render Kover coverage and PIT mutation metrics as GitHub Actions job summary Markdown."""

from __future__ import annotations

import os
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

BACKEND_MODULE_PREFIX = "workbench-"


@dataclass(frozen=True)
class CoverageMetrics:
    line: float | None = None
    branch: float | None = None
    instruction: float | None = None


@dataclass(frozen=True)
class MutationMetrics:
    mutation_score: float | None = None
    test_strength: float | None = None
    pit_line: float | None = None
    killed: int = 0
    survived: int = 0
    no_coverage: int = 0
    total: int = 0


def repo_root() -> Path:
    return Path(os.environ.get("REPO_ROOT", ".")).resolve()


def extended_tests_enabled() -> bool:
    return os.environ.get("EXTENDED_TESTS", "false").lower() == "true"


def parse_kover_report(path: Path) -> CoverageMetrics | None:
    if not path.is_file():
        return None
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return None

    counters: dict[str, tuple[int, int]] = {}
    for counter in root.iter("counter"):
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


def discover_backend_modules(root: Path) -> list[str]:
    modules: list[str] = []
    for child in sorted(root.iterdir()):
        if not child.is_dir():
            continue
        if not child.name.startswith(BACKEND_MODULE_PREFIX):
            continue
        report = child / "build" / "reports" / "kover" / "report.xml"
        if report.is_file():
            modules.append(child.name)
    return modules


def parse_pit_mutations(path: Path) -> MutationMetrics | None:
    if not path.is_file():
        return None
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return None

    status_counts: dict[str, int] = {}
    for mutation in root.iter("mutation"):
        status = mutation.get("status", "UNKNOWN")
        status_counts[status] = status_counts.get(status, 0) + 1

    killed = status_counts.get("KILLED", 0)
    survived = status_counts.get("SURVIVED", 0)
    no_coverage = status_counts.get("NO_COVERAGE", 0)
    total = sum(status_counts.values())
    if total == 0:
        return None

    detected = killed + survived
    return MutationMetrics(
        mutation_score=killed / total * 100.0,
        test_strength=(killed / detected * 100.0) if detected > 0 else None,
        killed=killed,
        survived=survived,
        no_coverage=no_coverage,
        total=total,
    )


def parse_pit_line_from_mutations(path: Path) -> float | None:
    if not path.is_file():
        return None
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return None

    covered_lines: set[tuple[str, str]] = set()
    all_lines: set[tuple[str, str]] = set()
    for mutation in root.iter("mutation"):
        source = mutation.findtext("sourceFile")
        line = mutation.findtext("lineNumber")
        if source is None or line is None:
            continue
        key = (source, line)
        all_lines.add(key)
        if mutation.get("status") != "NO_COVERAGE":
            covered_lines.add(key)

    if not all_lines:
        return None
    return len(covered_lines) / len(all_lines) * 100.0


def parse_pit_line_coverage(path: Path) -> float | None:
    mutations_path = path.parent / "mutations.xml"
    line_from_mutations = parse_pit_line_from_mutations(mutations_path)
    if line_from_mutations is not None:
        return line_from_mutations

    if not path.is_file():
        return None
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return None

    total = 0
    covered = 0
    for block in root.iter("block"):
        total += 1
        tests = block.find("tests")
        if tests is not None and len(tests) > 0:
            covered += 1

    if total == 0:
        return None
    return covered / total * 100.0


def parse_pit_index_summary(path: Path) -> MutationMetrics | None:
    if not path.is_file():
        return None

    text = path.read_text(encoding="utf-8", errors="replace")
    summary_start = text.find("<h3>Project Summary</h3>")
    if summary_start < 0:
        return None

    summary_chunk = text[summary_start : summary_start + 1200]
    percentages = re.findall(r"<td>(\d+)%", summary_chunk)
    legends = re.findall(r'class="coverage_legend">(\d+)/(\d+)</div>', summary_chunk)
    if len(percentages) < 3 or len(legends) < 3:
        return None

    pit_line = float(percentages[0])
    mutation_score = float(percentages[1])
    test_strength = float(percentages[2])
    killed = int(legends[1][0])
    mutation_total = int(legends[1][1])
    detected_killed = int(legends[2][0])
    detected_total = int(legends[2][1])
    survived = detected_total - detected_killed
    no_coverage = max(mutation_total - killed - survived, 0)

    return MutationMetrics(
        mutation_score=mutation_score,
        test_strength=test_strength,
        pit_line=pit_line,
        killed=killed,
        survived=survived,
        no_coverage=no_coverage,
        total=mutation_total,
    )


def has_coverage_data(metrics: CoverageMetrics | None) -> bool:
    if metrics is None:
        return False
    return any(value is not None for value in (metrics.line, metrics.branch, metrics.instruction))


def resolve_aggregate_mutation(
    aggregate_dir: Path,
    module_metrics: dict[str, MutationMetrics],
) -> MutationMetrics | None:
    aggregate = load_mutation_metrics(aggregate_dir)
    if aggregate is not None:
        return aggregate

    aggregate = parse_pit_index_summary(aggregate_dir / "index.html")
    if aggregate is not None:
        return aggregate

    return aggregate_mutation_metrics(list(module_metrics.values()))


def aggregate_mutation_metrics(metrics_list: list[MutationMetrics]) -> MutationMetrics | None:
    if not metrics_list:
        return None

    killed = sum(metric.killed for metric in metrics_list)
    survived = sum(metric.survived for metric in metrics_list)
    no_coverage = sum(metric.no_coverage for metric in metrics_list)
    total = sum(metric.total for metric in metrics_list)
    if total == 0:
        return None

    detected = killed + survived
    return MutationMetrics(
        mutation_score=killed / total * 100.0,
        test_strength=(killed / detected * 100.0) if detected > 0 else None,
        pit_line=None,
        killed=killed,
        survived=survived,
        no_coverage=no_coverage,
        total=total,
    )


def load_mutation_metrics(path: Path) -> MutationMetrics | None:
    mutations = parse_pit_mutations(path / "mutations.xml")
    if mutations is None:
        return None
    pit_line = parse_pit_line_coverage(path / "linecoverage.xml")
    return MutationMetrics(
        mutation_score=mutations.mutation_score,
        test_strength=mutations.test_strength,
        pit_line=pit_line,
        killed=mutations.killed,
        survived=mutations.survived,
        no_coverage=mutations.no_coverage,
        total=mutations.total,
    )


def fmt_pct(value: float | None, bold: bool = False) -> str:
    if value is None:
        return "N/A"
    text = f"{value:.1f}%"
    return f"**{text}**" if bold else text


def fmt_delta(kover_line: float | None, pit_line: float | None) -> str:
    if kover_line is None or pit_line is None:
        return "N/A"
    delta = pit_line - kover_line
    sign = "+" if delta > 0 else ""
    return f"{sign}{delta:.1f}%"


def render_coverage_section(root: Path) -> list[str]:
    lines = ["### Coverage (Kover)", ""]
    modules = discover_backend_modules(root)
    total_path = root / "build" / "reports" / "kover" / "report.xml"
    total = parse_kover_report(total_path)

    if not modules and total is None:
        lines.append("_No Kover report found. Run `./gradlew check koverXmlReport` first._")
        lines.append("")
        return lines

    lines.append("| Module | Line | Branch | Instruction |")
    lines.append("|--------|------|--------|-------------|")

    for module in modules:
        metrics = parse_kover_report(root / module / "build" / "reports" / "kover" / "report.xml")
        if not has_coverage_data(metrics):
            continue
        lines.append(
            f"| {module} | {fmt_pct(metrics.line)} | {fmt_pct(metrics.branch)} | {fmt_pct(metrics.instruction)} |"
        )

    if total is not None:
        lines.append(
            f"| **Total** | {fmt_pct(total.line, bold=True)} | {fmt_pct(total.branch, bold=True)} | "
            f"{fmt_pct(total.instruction, bold=True)} |"
        )

    lines.append("")
    return lines


def discover_pit_modules(root: Path) -> list[str]:
    modules: list[str] = []
    for child in sorted(root.iterdir()):
        if not child.is_dir() or not child.name.startswith(BACKEND_MODULE_PREFIX):
            continue
        pit_dir = child / "build" / "reports" / "pitest"
        if (pit_dir / "mutations.xml").is_file():
            modules.append(child.name)
    return modules


def render_mutation_section(root: Path) -> tuple[list[str], dict[str, MutationMetrics], MutationMetrics | None]:
    lines = ["### Mutation Testing (PIT)", ""]

    if not extended_tests_enabled():
        lines.append("_Skipped — mutation tests run only on Nightly (`extended-tests: true`)._")
        lines.append("")
        return lines, {}, None

    aggregate_dir = root / "build" / "reports" / "pitest"
    modules = discover_pit_modules(root)

    module_metrics: dict[str, MutationMetrics] = {}
    for module in modules:
        metrics = load_mutation_metrics(root / module / "build" / "reports" / "pitest")
        if metrics is None:
            continue
        module_metrics[module] = metrics

    aggregate = resolve_aggregate_mutation(aggregate_dir, module_metrics)

    if aggregate is None and not module_metrics:
        lines.append("_No PIT report found. Run `./gradlew mutationTest` first._")
        lines.append("")
        return lines, {}, None

    lines.append("| Module | Mutation | Strength | PIT Line | Killed | Survived | No Cov |")
    lines.append("|--------|----------|----------|----------|--------|----------|--------|")

    for module in sorted(module_metrics.keys()):
        metrics = module_metrics[module]
        lines.append(
            f"| {module} | {fmt_pct(metrics.mutation_score)} | {fmt_pct(metrics.test_strength)} | "
            f"{fmt_pct(metrics.pit_line)} | {metrics.killed} | {metrics.survived} | {metrics.no_coverage} |"
        )

    if aggregate is not None:
        lines.append(
            f"| **Total** | {fmt_pct(aggregate.mutation_score, bold=True)} | "
            f"{fmt_pct(aggregate.test_strength, bold=True)} | {fmt_pct(aggregate.pit_line, bold=True)} | "
            f"**{aggregate.killed}** | **{aggregate.survived}** | **{aggregate.no_coverage}** |"
        )

    lines.append("")
    return lines, module_metrics, aggregate


def render_correlation_section(
    root: Path,
    module_metrics: dict[str, MutationMetrics],
    aggregate: MutationMetrics | None,
) -> list[str]:
    lines = ["### Coverage ↔ Mutation", ""]

    if not extended_tests_enabled():
        return []

    modules = sorted(
        module
        for module in set(discover_backend_modules(root)) | set(module_metrics.keys())
        if has_coverage_data(parse_kover_report(root / module / "build" / "reports" / "kover" / "report.xml"))
        or module in module_metrics
    )
    if not modules:
        lines.append("_No module data available for correlation._")
        lines.append("")
        return lines

    lines.append("| Module | Kover Line | PIT Line | Δ | Mutation | Strength |")
    lines.append("|--------|------------|----------|---|----------|----------|")

    for module in modules:
        kover = parse_kover_report(root / module / "build" / "reports" / "kover" / "report.xml")
        mutation = module_metrics.get(module)
        kover_line = kover.line if kover else None
        pit_line = mutation.pit_line if mutation else None
        lines.append(
            f"| {module} | {fmt_pct(kover_line)} | {fmt_pct(pit_line)} | {fmt_delta(kover_line, pit_line)} | "
            f"{fmt_pct(mutation.mutation_score if mutation else None)} | "
            f"{fmt_pct(mutation.test_strength if mutation else None)} |"
        )

    if aggregate is not None:
        total_kover = parse_kover_report(root / "build" / "reports" / "kover" / "report.xml")
        kover_line = total_kover.line if total_kover else None
        pit_line = aggregate.pit_line
        lines.append(
            f"| **Total** | {fmt_pct(kover_line, bold=True)} | {fmt_pct(pit_line, bold=True)} | "
            f"{fmt_delta(kover_line, pit_line)} | {fmt_pct(aggregate.mutation_score, bold=True)} | "
            f"{fmt_pct(aggregate.test_strength, bold=True)} |"
        )

    lines.append("")
    return lines


def main() -> int:
    root = repo_root()
    output: list[str] = ["## Quality Gate Report", ""]

    output.extend(render_coverage_section(root))

    mutation_lines, module_metrics, aggregate = render_mutation_section(root)
    output.extend(mutation_lines)
    output.extend(render_correlation_section(root, module_metrics, aggregate))

    sys.stdout.write("\n".join(output))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
