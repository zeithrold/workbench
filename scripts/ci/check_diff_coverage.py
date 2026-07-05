#!/usr/bin/env python3
"""Check incremental (diff) code coverage for backend and frontend stacks."""

from __future__ import annotations

import argparse
import fnmatch
import json
import os
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path

BACKEND_INCLUDE_GLOBS = ("workbench-*/src/main/**/*.kt",)
FRONTEND_INCLUDE_GLOBS = (
    "workbench-frontend/src/**/*.ts",
    "workbench-frontend/src/**/*.js",
    "workbench-frontend/src/**/*.svelte",
)

BACKEND_COVERAGE_XML = Path("build/reports/kover/report.xml")
FRONTEND_COVERAGE_LCOV = Path("workbench-frontend/coverage/lcov.info")
RESULTS_JSON = Path("scripts/ci/diff-coverage-results.json")


@dataclass(frozen=True)
class StackConfig:
    name: str
    coverage_path: Path
    html_report: Path
    json_report: Path
    include_globs: tuple[str, ...]
    fail_under: float
    src_roots: tuple[str, ...] = ()


@dataclass
class StackResult:
    name: str
    status: str
    percent: float | None
    fail_under: float
    message: str


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


def env_float(name: str, default: float) -> float:
    raw = os.environ.get(name)
    if raw is None:
        return default
    return float(raw)


def env_path(name: str, default: Path) -> Path:
    raw = os.environ.get(name)
    if raw is None:
        return default
    return Path(raw)


def compare_branch() -> str:
    return os.environ.get("COMPARE_BRANCH", "origin/main")


def git_diff_names(root: Path, branch: str) -> list[str]:
    files: set[str] = set()
    commands = [
        ["git", "diff", "--name-only", f"{branch}...HEAD"],
        ["git", "diff", "--name-only", f"{branch}..HEAD"],
        ["git", "diff", "--name-only"],
        ["git", "diff", "--name-only", "--cached"],
        ["git", "ls-files", "--others", "--exclude-standard"],
    ]
    for command in commands:
        result = subprocess.run(
            command,
            cwd=root,
            check=False,
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            continue
        files.update(line.strip() for line in result.stdout.splitlines() if line.strip())
    if files:
        return sorted(files)

    result = subprocess.run(
        ["git", "diff", "--name-only", f"{branch}...HEAD"],
        cwd=root,
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"Unable to diff against {branch}: {result.stderr.strip() or result.stdout.strip()}"
        )
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def matches_any(path: str, globs: tuple[str, ...]) -> bool:
    normalized = path.replace("\\", "/")
    return any(fnmatch.fnmatch(normalized, pattern) for pattern in globs)


def has_stack_changes(changed_files: list[str], globs: tuple[str, ...]) -> bool:
    return any(matches_any(path, globs) for path in changed_files)


def ensure_compare_branch(root: Path, branch: str) -> None:
    result = subprocess.run(
        ["git", "rev-parse", "--verify", branch],
        cwd=root,
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"Compare branch {branch!r} is unavailable. "
            f"Run `git fetch origin` before checking diff coverage."
        )


def parse_json_percent(path: Path) -> float | None:
    if not path.is_file():
        return None
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None
    total = payload.get("total_percent_covered")
    if total is None:
        return None
    return float(total)


def parse_stdout_percent(output: str) -> float | None:
    match = re.search(r"Coverage:\s*([\d.]+)%", output)
    if match:
        return float(match.group(1))
    match = re.search(r"Total coverage:\s*([\d.]+)%", output, re.IGNORECASE)
    if match:
        return float(match.group(1))
    return None


def run_diff_cover(
    root: Path,
    config: StackConfig,
    branch: str,
) -> tuple[int, str, float | None]:
    coverage_path = root / config.coverage_path
    if not coverage_path.is_file():
        return 1, f"Coverage report not found: {coverage_path}", None

    html_path = root / config.html_report
    json_path = root / config.json_report
    html_path.parent.mkdir(parents=True, exist_ok=True)

    command = [
        "diff-cover",
        str(coverage_path),
        f"--compare-branch={branch}",
        f"--fail-under={config.fail_under}",
        f"--format=html:{html_path},json:{json_path}",
        "--total-percent-float",
    ]
    for pattern in config.include_globs:
        command.extend(["--include", pattern])
    for src_root in config.src_roots:
        command.extend(["--src-roots", src_root])

    result = subprocess.run(
        command,
        cwd=root,
        check=False,
        capture_output=True,
        text=True,
    )
    output = "\n".join(part for part in (result.stdout, result.stderr) if part).strip()
    percent = parse_json_percent(json_path)
    if percent is None:
        percent = parse_stdout_percent(output)
    return result.returncode, output, percent


def evaluate_stack(
    root: Path,
    config: StackConfig,
    branch: str,
    changed_files: list[str],
) -> StackResult:
    if not has_stack_changes(changed_files, config.include_globs):
        return StackResult(
            name=config.name,
            status="skipped",
            percent=None,
            fail_under=config.fail_under,
            message="No matching source changes in diff; skipped.",
        )

    exit_code, output, percent = run_diff_cover(root, config, branch)
    if exit_code == 0:
        return StackResult(
            name=config.name,
            status="pass",
            percent=percent,
            fail_under=config.fail_under,
            message=output or "Diff coverage threshold met.",
        )

    detail = output or "Diff coverage check failed."
    if percent is not None and percent >= config.fail_under:
        return StackResult(
            name=config.name,
            status="pass",
            percent=percent,
            fail_under=config.fail_under,
            message=detail,
        )

    return StackResult(
        name=config.name,
        status="fail",
        percent=percent,
        fail_under=config.fail_under,
        message=detail,
    )


def backend_config(root: Path) -> StackConfig:
    src_roots = tuple(
        str(path.relative_to(root))
        for path in sorted(root.glob("workbench-*/src/main/kotlin"))
        if path.is_dir()
    )
    return StackConfig(
        name="backend",
        coverage_path=env_path("BACKEND_COVERAGE_XML", BACKEND_COVERAGE_XML),
        html_report=Path("scripts/ci/diff-cover-backend.html"),
        json_report=Path("scripts/ci/diff-cover-backend.json"),
        include_globs=BACKEND_INCLUDE_GLOBS,
        fail_under=env_float("FAIL_UNDER_BACKEND", 90.0),
        src_roots=src_roots,
    )


def frontend_config(root: Path) -> StackConfig:
    return StackConfig(
        name="frontend",
        coverage_path=env_path("FRONTEND_COVERAGE_LCOV", FRONTEND_COVERAGE_LCOV),
        html_report=Path("scripts/ci/diff-cover-frontend.html"),
        json_report=Path("scripts/ci/diff-cover-frontend.json"),
        include_globs=FRONTEND_INCLUDE_GLOBS,
        fail_under=env_float("FAIL_UNDER_FRONTEND", 70.0),
        src_roots=(str(Path("workbench-frontend/src")),),
    )


def render_summary(results: list[StackResult]) -> str:
    lines = ["### Diff Coverage (changed lines)", ""]
    lines.append("| Stack | Status | Changed-line % | Threshold |")
    lines.append("|-------|--------|------------------|-----------|")
    for result in results:
        percent = "N/A" if result.percent is None else f"{result.percent:.1f}%"
        lines.append(f"| {result.name} | {result.status} | {percent} | {result.fail_under:.0f}% |")
    lines.append("")
    for result in results:
        if result.status == "skipped":
            continue
        lines.append(f"**{result.name}** report: `scripts/ci/diff-cover-{result.name}.html`")
    lines.append("")
    return "\n".join(lines)


def write_results(root: Path, results: list[StackResult]) -> None:
    payload = {
        result.name: {
            "status": result.status,
            "percent": result.percent,
            "fail_under": result.fail_under,
            "message": result.message,
        }
        for result in results
    }
    path = root / RESULTS_JSON
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--target",
        choices=("backend", "frontend", "all"),
        default="all",
        help="Which stack to check (default: all)",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    root = repo_root()
    branch = compare_branch()

    ensure_compare_branch(root, branch)
    changed_files = git_diff_names(root, branch)

    configs: list[StackConfig] = []
    if args.target in ("backend", "all"):
        configs.append(backend_config(root))
    if args.target in ("frontend", "all"):
        configs.append(frontend_config(root))

    results = [evaluate_stack(root, config, branch, changed_files) for config in configs]
    write_results(root, results)

    summary = render_summary(results)
    print(summary)

    failed = [result for result in results if result.status == "fail"]
    for result in results:
        if result.status == "skipped":
            print(f"[{result.name}] skipped: {result.message}")
        elif result.status == "pass":
            percent_text = "N/A" if result.percent is None else f"{result.percent:.1f}%"
            print(f"[{result.name}] pass ({percent_text} >= {result.fail_under:.0f}%)")
        else:
            percent_text = "N/A" if result.percent is None else f"{result.percent:.1f}%"
            print(f"[{result.name}] fail ({percent_text} < {result.fail_under:.0f}%)")
            if result.message:
                print(result.message)

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
