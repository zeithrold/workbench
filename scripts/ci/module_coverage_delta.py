#!/usr/bin/env python3
"""Compute per-module unit vs full coverage deltas and emit warnings for large integration lift."""

from __future__ import annotations

import json
import sys
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

from coverage_metrics import (
    FRONTEND_MODULE,
    INTEGRATION_LIFT_WARN_THRESHOLD,
    CoverageMetrics,
    aggregate_kover_metrics,
    discover_backend_modules,
    frontend_full_lcov,
    frontend_unit_lcov,
    has_coverage_data,
    integration_lift,
    module_full_kover_report,
    module_unit_kover_report,
    parse_kover_report,
    parse_lcov_report,
    repo_root,
)

RESULTS_JSON = Path("scripts/ci/module-coverage-delta.json")


@dataclass(frozen=True)
class ModuleDelta:
    unit: CoverageMetrics | None
    full: CoverageMetrics | None
    delta_line: float | None
    warn_integration_lift: bool = False
    note: str | None = None


def metrics_payload(metrics: CoverageMetrics | None) -> dict[str, float | None] | None:
    if metrics is None or not has_coverage_data(metrics):
        return None
    return {
        "line": metrics.line,
        "branch": metrics.branch,
        "instruction": metrics.instruction,
    }


def module_delta(
    unit: CoverageMetrics | None,
    full: CoverageMetrics | None,
    *,
    note: str | None = None,
) -> ModuleDelta:
    delta_line = integration_lift(
        full.line if full else None,
        unit.line if unit else None,
    )
    warn = delta_line is not None and delta_line > INTEGRATION_LIFT_WARN_THRESHOLD
    return ModuleDelta(
        unit=unit,
        full=full,
        delta_line=delta_line,
        warn_integration_lift=warn,
        note=note,
    )


def collect_module_deltas(root: Path) -> dict[str, ModuleDelta]:
    deltas: dict[str, ModuleDelta] = {}

    for module in discover_backend_modules(root):
        unit = parse_kover_report(module_unit_kover_report(root, module))
        full = parse_kover_report(module_full_kover_report(root, module))
        deltas[module] = module_delta(unit, full)

    frontend_unit = parse_lcov_report(frontend_unit_lcov(root))
    frontend_full = parse_lcov_report(frontend_full_lcov(root))
    if frontend_unit is None and frontend_full is not None:
        frontend_unit = frontend_full
    if frontend_full is None and frontend_unit is not None:
        frontend_full = frontend_unit
    if has_coverage_data(frontend_unit) or has_coverage_data(frontend_full):
        deltas[FRONTEND_MODULE] = module_delta(
            frontend_unit,
            frontend_full,
            note="Vitest unit tests only; no integration test layer.",
        )

    return deltas


def collect_totals(root: Path, modules: list[str]) -> ModuleDelta:
    backend_modules = [module for module in modules if module != FRONTEND_MODULE]
    unit_total_path = root / "build" / "reports" / "kover" / "unit" / "report.xml"
    full_total_path = root / "build" / "reports" / "kover" / "report.xml"

    unit = parse_kover_report(unit_total_path)
    full = parse_kover_report(full_total_path)
    if unit is None and backend_modules:
        unit = aggregate_kover_metrics(root, backend_modules, unit=True)
    if full is None and backend_modules:
        full = aggregate_kover_metrics(root, backend_modules, unit=False)

    return module_delta(unit, full)


def build_payload(root: Path) -> dict:
    modules = collect_module_deltas(root)
    module_names = sorted(modules.keys())
    totals = collect_totals(root, module_names)

    warnings = [
        module
        for module, delta in modules.items()
        if delta.warn_integration_lift and delta.delta_line is not None
    ]
    if totals.warn_integration_lift:
        warnings.append("**Total**")

    return {
        "generated_at": datetime.now(UTC).isoformat(),
        "integration_lift_warn_threshold": INTEGRATION_LIFT_WARN_THRESHOLD,
        "modules": {
            module: {
                "unit": metrics_payload(delta.unit),
                "full": metrics_payload(delta.full),
                "delta": {"line": delta.delta_line},
                "warn_integration_lift": delta.warn_integration_lift,
                "note": delta.note,
            }
            for module, delta in modules.items()
        },
        "totals": {
            "unit": metrics_payload(totals.unit),
            "full": metrics_payload(totals.full),
            "delta": {"line": totals.delta_line},
            "warn_integration_lift": totals.warn_integration_lift,
        },
        "warnings": warnings,
    }


def emit_github_warnings(payload: dict) -> None:
    threshold = payload.get("integration_lift_warn_threshold", INTEGRATION_LIFT_WARN_THRESHOLD)
    for module, data in payload.get("modules", {}).items():
        if not data.get("warn_integration_lift"):
            continue
        delta = data.get("delta", {}).get("line")
        delta_text = f"{delta:.1f}" if isinstance(delta, (int, float)) else "N/A"
        print(
            f"::warning title=Integration lift > {threshold:g}%::"
            f"{module}: full-unit line delta is {delta_text}% "
            f"(consider adding unit or contract tests)",
            file=sys.stderr,
        )

    totals = payload.get("totals", {})
    if totals.get("warn_integration_lift"):
        delta = totals.get("delta", {}).get("line")
        delta_text = f"{delta:.1f}" if isinstance(delta, (int, float)) else "N/A"
        print(
            f"::warning title=Integration lift > {threshold:g}%::"
            f"Backend total: full-unit line delta is {delta_text}%",
            file=sys.stderr,
        )


def write_results(root: Path, payload: dict) -> Path:
    path = root / RESULTS_JSON
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    return path


def main() -> int:
    root = repo_root()
    payload = build_payload(root)
    output = write_results(root, payload)
    emit_github_warnings(payload)
    print(f"Wrote module coverage delta report to {output}")
    if payload["warnings"]:
        print(
            "Integration lift warnings (non-blocking): " + ", ".join(payload["warnings"]),
            file=sys.stderr,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
