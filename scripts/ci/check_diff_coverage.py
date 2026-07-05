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
DIFF_COVER_PATCH = Path("scripts/ci/.diff-cover.patch")
DIFF_COVER_CONFIG = Path("scripts/ci/.diff-cover.toml")


@dataclass(frozen=True)
class StackConfig:
    name: str
    coverage_path: Path
    html_report: Path
    json_report: Path
    include_globs: tuple[str, ...]
    fail_under: float


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
    """List paths that differ from `branch`, including uncommitted and untracked files."""
    files: set[str] = set()
    commands = [
        ["git", "diff", "--name-only", branch],
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
            raise RuntimeError(
                f"Unable to diff against {branch}: {result.stderr.strip() or result.stdout.strip()}"
            )
        files.update(line.strip() for line in result.stdout.splitlines() if line.strip())
    return sorted(files)


def generate_git_diff(root: Path, branch: str) -> str:
    """
    Build a unified diff against `branch` through the current working tree.

    `git diff origin/main` compares the merge base branch to the index and
    working tree, so staged and unstaged local changes are included alongside
    commits already on the current branch.
    """
    result = subprocess.run(
        ["git", "diff", branch, "--no-color", "--no-ext-diff", "-U0"],
        cwd=root,
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"Unable to diff against {branch}: {result.stderr.strip() or result.stdout.strip()}"
        )

    diff_parts = [result.stdout]
    untracked = subprocess.run(
        ["git", "ls-files", "--others", "--exclude-standard"],
        cwd=root,
        check=False,
        capture_output=True,
        text=True,
    )
    if untracked.returncode != 0:
        raise RuntimeError(
            "Unable to list untracked files: "
            f"{untracked.stderr.strip() or untracked.stdout.strip()}"
        )

    for path in untracked.stdout.splitlines():
        normalized = path.strip()
        if not normalized:
            continue
        if not (root / normalized).is_file():
            continue
        untracked_diff = subprocess.run(
            [
                "git",
                "diff",
                "--no-index",
                "--no-color",
                "--no-ext-diff",
                "-U0",
                os.devnull,
                normalized,
            ],
            cwd=root,
            check=False,
            capture_output=True,
            text=True,
        )
        if untracked_diff.stdout:
            diff_parts.append(untracked_diff.stdout)

    return "".join(diff_parts)


def write_diff_cover_config(
    path: Path,
    *,
    include_globs: tuple[str, ...],
    src_roots: tuple[str, ...],
    fail_under: float,
) -> None:
    """Write a diff-cover TOML config so multiple src_roots are preserved."""
    lines = [
        "[tool.diff_cover]",
        f"fail_under = {fail_under}",
        "include = [",
    ]
    lines.extend(f'  "{pattern}",' for pattern in include_globs)
    lines.append("]")
    if src_roots:
        lines.append("src_roots = [")
        lines.extend(f'  "{src_root}",' for src_root in src_roots)
        lines.append("]")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def relevant_backend_src_roots(root: Path, changed_files: list[str]) -> tuple[str, ...]:
    modules: set[str] = set()
    for path in changed_files:
        if not path.startswith("workbench-") or "/src/main/" not in path:
            continue
        module = path.split("/", 1)[0]
        kotlin_root = root / module / "src/main/kotlin"
        if kotlin_root.is_dir():
            modules.add(str(kotlin_root.relative_to(root)))
    return tuple(sorted(modules))


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


def load_diff_cover_json(path: Path) -> dict | None:
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None


def effective_diff_coverage_percent(payload: dict) -> float | None:
    """
    Return diff-cover's coverage for changed lines that appear in the report.

    diff-cover only scores lines present in both the diff and the coverage XML.
    Deleted lines, annotation-only edits, and stale reports can inflate
    `num_changed_lines` without adding rows to `total_num_lines`, so the pass/fail
    threshold must use `total_percent_covered` rather than dividing by
    `num_changed_lines`.
    """
    total_num = payload.get("total_num_lines") or 0
    num_changed = payload.get("num_changed_lines") or 0
    if num_changed <= 0:
        total = payload.get("total_percent_covered")
        return float(total) if total is not None else None
    if total_num <= 0:
        return 0.0
    total = payload.get("total_percent_covered")
    return float(total) if total is not None else None


def unmeasured_changed_source_files(
    root: Path,
    payload: dict,
    changed_files: list[str],
    include_globs: tuple[str, ...],
) -> list[str]:
    src_stats = payload.get("src_stats", {})
    unmeasured: list[str] = []
    for path in changed_files:
        if not matches_any(path, include_globs):
            continue
        if path in src_stats:
            continue
        if not (root / path).is_file():
            continue
        unmeasured.append(path)
    return sorted(unmeasured)


def parse_json_percent(path: Path) -> float | None:
    payload = load_diff_cover_json(path)
    if payload is None:
        return None
    return effective_diff_coverage_percent(payload)


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
    diff_text: str,
    src_roots: tuple[str, ...],
) -> tuple[int, str, float | None, dict | None]:
    coverage_path = root / config.coverage_path
    if not coverage_path.is_file():
        return 1, f"Coverage report not found: {coverage_path}", None, None

    html_path = root / config.html_report
    json_path = root / config.json_report
    html_path.parent.mkdir(parents=True, exist_ok=True)

    diff_path = root / DIFF_COVER_PATCH
    config_path = root / DIFF_COVER_CONFIG
    diff_path.write_text(diff_text, encoding="utf-8")
    write_diff_cover_config(
        config_path,
        include_globs=config.include_globs,
        src_roots=src_roots,
        fail_under=config.fail_under,
    )

    command = [
        "diff-cover",
        str(coverage_path),
        f"--diff-file={diff_path}",
        f"--config-file={config_path}",
        f"--format=html:{html_path},json:{json_path}",
        "--total-percent-float",
    ]

    result = subprocess.run(
        command,
        cwd=root,
        check=False,
        capture_output=True,
        text=True,
    )
    output = "\n".join(part for part in (result.stdout, result.stderr) if part).strip()
    payload = load_diff_cover_json(json_path)
    percent = effective_diff_coverage_percent(payload) if payload else None
    if percent is None:
        percent = parse_stdout_percent(output)
    return result.returncode, output, percent, payload


def evaluate_stack(
    root: Path,
    config: StackConfig,
    branch: str,
    changed_files: list[str],
    diff_text: str,
    src_roots: tuple[str, ...],
) -> StackResult:
    if not has_stack_changes(changed_files, config.include_globs):
        return StackResult(
            name=config.name,
            status="skipped",
            percent=None,
            fail_under=config.fail_under,
            message="No matching source changes in diff; skipped.",
        )

    if not src_roots:
        return StackResult(
            name=config.name,
            status="fail",
            percent=None,
            fail_under=config.fail_under,
            message="Unable to resolve source roots for changed files.",
        )

    exit_code, output, percent, payload = run_diff_cover(
        root, config, branch, diff_text, src_roots
    )
    unmeasured_files = (
        unmeasured_changed_source_files(root, payload, changed_files, config.include_globs)
        if payload
        else []
    )
    unmeasured_lines = 0
    if payload:
        num_changed = payload.get("num_changed_lines") or 0
        total_num = payload.get("total_num_lines") or 0
        unmeasured_lines = max(num_changed - total_num, 0)

    detail = output or "Diff coverage check failed."
    if unmeasured_lines > 0 or unmeasured_files:
        hints = []
        if unmeasured_lines > 0:
            hints.append(
                f"{unmeasured_lines} changed line(s) were not found in the coverage report "
                f"(run `./gradlew check` and, for frontend, "
                f"`./gradlew :workbench-frontend:pnpmCoverage` after your latest edits)"
            )
        if unmeasured_files:
            preview = ", ".join(unmeasured_files[:5])
            suffix = "..." if len(unmeasured_files) > 5 else ""
            hints.append(
                f"{len(unmeasured_files)} changed source file(s) missing from coverage: "
                f"{preview}{suffix}"
            )
        detail = "\n".join([detail, *hints]) if detail else "\n".join(hints)

    if percent is not None and percent >= config.fail_under:
        return StackResult(
            name=config.name,
            status="pass",
            percent=percent,
            fail_under=config.fail_under,
            message=detail or "Diff coverage threshold met.",
        )

    return StackResult(
        name=config.name,
        status="fail",
        percent=percent,
        fail_under=config.fail_under,
        message=detail,
    )


def backend_config(root: Path) -> StackConfig:
    return StackConfig(
        name="backend",
        coverage_path=env_path("BACKEND_COVERAGE_XML", BACKEND_COVERAGE_XML),
        html_report=Path("scripts/ci/diff-cover-backend.html"),
        json_report=Path("scripts/ci/diff-cover-backend.json"),
        include_globs=BACKEND_INCLUDE_GLOBS,
        fail_under=env_float("FAIL_UNDER_BACKEND", 90.0),
    )


def frontend_config(root: Path) -> StackConfig:
    return StackConfig(
        name="frontend",
        coverage_path=env_path("FRONTEND_COVERAGE_LCOV", FRONTEND_COVERAGE_LCOV),
        html_report=Path("scripts/ci/diff-cover-frontend.html"),
        json_report=Path("scripts/ci/diff-cover-frontend.json"),
        include_globs=FRONTEND_INCLUDE_GLOBS,
        fail_under=env_float("FAIL_UNDER_FRONTEND", 70.0),
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


def append_github_step_summary(markdown: str) -> None:
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary_path:
        return
    with open(summary_path, "a", encoding="utf-8") as handle:
        handle.write(markdown)
        if not markdown.endswith("\n"):
            handle.write("\n")


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
    diff_text = generate_git_diff(root, branch)
    backend_src_roots = relevant_backend_src_roots(root, changed_files)
    frontend_src_roots = (str(Path("workbench-frontend/src")),)

    configs: list[StackConfig] = []
    if args.target in ("backend", "all"):
        configs.append(backend_config(root))
    if args.target in ("frontend", "all"):
        configs.append(frontend_config(root))

    src_roots_by_stack = {
        "backend": backend_src_roots,
        "frontend": frontend_src_roots,
    }
    results = [
        evaluate_stack(
            root,
            config,
            branch,
            changed_files,
            diff_text,
            src_roots_by_stack[config.name],
        )
        for config in configs
    ]
    write_results(root, results)

    summary = render_summary(results)
    print(summary)
    append_github_step_summary(summary)

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
