---
name: workbench-development
description: >-
  End-to-end Workbench development workflow: environment setup, module layering,
  local dev loop, verification, and PR/CI. Use when starting a new feature, changing
  API/DB/frontend, local debugging, pre-PR verification, or when the user asks how
  to develop or submit changes in this monolith.
---

# Workbench Development

Orchestrator for the Spring Boot 4 + SvelteKit monolith. Domain details live in linked skills — do not duplicate them here.

## When to Activate

- Starting a new feature or bugfix
- Changing API, database schema, frontend, or worker jobs
- Local environment setup or debugging
- Pre-PR verification or CI failure triage
- Questions about where code goes or which commands to run

## Quick Checklist

Copy and track progress:

```
- [ ] Infrastructure up (Docker: postgres, valkey, redpanda)
- [ ] Change type identified (see decision tree)
- [ ] Code in correct modules with tests
- [ ] API changes: OpenAPI annotated + `pnpm openapi` (backend running)
- [ ] Schema changes: Flyway migration + Exposed tables + migration test count
- [ ] `./gradlew quickCheck` during iteration; `./gradlew check` before push
- [ ] Diff coverage passes (Kotlin → unit/integration tests; frontend → Vitest; see Verification)
- [ ] PR opened; CI quality gate green
```

## Change-Type Decision Tree

| Change | Modules (order) | Required steps | Skill |
|--------|-----------------|----------------|-------|
| New/changed API | core → service → web | OpenAPI; regen client; changed Kotlin lines ≥ 90% diff coverage | [api-design](../api-design/SKILL.md) |
| Schema / persistence | workbench-data | `V{n}__*.sql`; Exposed; bump migration test | [kotlin-exposed-patterns](../kotlin-exposed-patterns/SKILL.md) |
| Business logic | workbench-service, workbench-agile | Unit tests without Spring; changed Kotlin lines ≥ 90% diff coverage | [kotlin-patterns](../kotlin-patterns/SKILL.md), [kotlin-testing](../kotlin-testing/SKILL.md) |
| Frontend UI | workbench-frontend | Lint + Vitest (in `check`); changed TS/Svelte lines ≥ 70% diff coverage | [svelte](../svelte/SKILL.md) |
| Background / Kafka | workbench-worker | integration profile + Testcontainers; changed Kotlin lines ≥ 90% diff coverage | [springboot-patterns](../springboot-patterns/SKILL.md) |

**API + frontend:** implement backend first → start web → `cd workbench-frontend && pnpm openapi` → wire UI to generated client.

**Schema only:** migration + Exposed + repositories; no web change unless exposing new fields.

**Frontend only:** skip Orval unless consuming new/changed endpoints.

## Module Placement

```
Client → Controller (web) → Service → Repository port (core) → Exposed (data)
```

| Module | Responsibility |
|--------|----------------|
| `workbench-core` | Domain models, ports (`*Record`, `*Query`, `*Summary`) |
| `workbench-service` | Business logic (`*Command`, `*View`) |
| `workbench-agile` | Project/work-item domain services |
| `workbench-data` | Exposed tables, repository impls, Flyway SQL |
| `workbench-security` | Spring Security integration |
| `workbench-web` | Controllers, `*Request`/`*Response`, OpenAPI |
| `workbench-worker` | Background jobs (no HTTP) |
| `workbench-frontend` | SvelteKit UI, Orval-generated API client |

Avoid `BO`/`PO`/`VO`/`DTO` suffixes. HTTP shape: [api-design](../api-design/SKILL.md).

## Local Dev Loop

**Prerequisites:** JDK 25; Docker running.

```bash
docker compose up -d
# minimal backend stack:
# docker compose up -d postgres valkey redpanda
```

Run in separate terminals:

```bash
./gradlew :workbench-web:bootRun --args='--spring.profiles.active=local'
./gradlew :workbench-worker:bootRun --args='--spring.profiles.active=local,worker'
./gradlew :workbench-frontend:pnpmDev
```

`./gradlew dev` prints the same commands. OpenAPI: `http://localhost:8080/api/openapi`; Scalar: `/api/scalar`.

**Cursor Cloud VM:** JDK 25 `JAVA_HOME`, Docker daemon, and Orval caveats — see [AGENTS.md](../../../AGENTS.md).

## Verification

### Tiers

| Tier | Command | When | Includes |
|------|---------|------|----------|
| **Quick** | `./gradlew quickCheck` | Local edit loop | Spotless, Detekt, backend `unitTest`, frontend `pnpmLint` |
| **Full** | `./gradlew check` | Pre-PR, CI push/PR | Quick + `integrationTest`, full Kover gate (90%), frontend Vitest |
| **Extended** | `./gradlew extendedCheck` | Nightly CI | Full + `fuzzTest` + `mutationTest` + unit Kover XML |

Module tasks: `unitTest` (no integration/fuzz tags), `integrationTest` (integration-tagged only), `test` (both). Integration tests require Docker.

**Tags:** Kotest specs (`StringSpec`, etc.) — `@Tags("integration")` / `@Tags("fuzz")` from `io.kotest.core.annotation.Tags` (filtered via Gradle `kotest.tags`). JUnit `@Test` classes — `@Tag("integration")` / `@Tag("fuzz")` from `org.junit.jupiter.api.Tag` (filtered via JUnit Platform `includeTags` / `excludeTags`). Both are required where applicable; JUnit `@Tag` on Kotest specs is not honored.

### Unit vs integration responsibilities

| | Unit (`unitTest`) | Integration (tagged) |
|--|-------------------|----------------------|
| **Purpose** | Business rules, pure logic, HTTP slice (`@WebMvcTest`) | SQL/Exposed semantics, Testcontainers, Kafka/S3/auth wiring |
| **Dependencies** | Fake/Recording ports or MockK; **no Spring full context** | Real Postgres/Valkey/Kafka/Keycloak via Testcontainers |
| **Naming** | `*Test.kt` | `*IntegrationTest.kt` + integration tag (see Tags above) |
| **PIT** | Included | Excluded (`config/pitest/pitest.properties`) |
| **Prefer** | State/result assertions over `coVerify`-only | Scenarios Fake cannot model (transactions, JSONB, migrations) |

**Consistency:** share fixtures (`workbench-test-support` / module `testFixtures`); use Fake implementations of `workbench-core` ports in unit tests; reserve integration tests for adapter/wiring proof. Do not duplicate the same business matrix in both layers.

### Coverage standards (two metrics)

| Metric | Report | Gate | Target |
|--------|--------|------|--------|
| **Full** (unit + integration) | `build/reports/kover/report.xml` | `./gradlew check` | **90%** line (aggregate + per backend module) |
| **Unit** (unit tests only) | `build/reports/kover/unit/report.xml` | Report only (for now) | **70%+** line; core/agile/service **80%+** aspirational |
| **Diff** (full Kover / Vitest LCOV) | `scripts/ci/diff-cover-*.html` | CI after `check` | Backend changed lines **90%**, frontend **70%** |
| **Mutation / Strength** | `build/reports/pitest/` | Nightly report | Track via Quality Report; no hard gate yet |

If full line coverage exceeds unit by **> 15%** for a module, add unit tests or contract tests — do not compensate with more integration tests alone.

Generate unit-only coverage:

```bash
./gradlew koverUnitCoverage -Pkover.unitOnly
```

### Standard gate (matches CI)

```bash
./gradlew check --no-daemon
./gradlew :workbench-web:bootJar :workbench-worker:bootJar --no-daemon
```

`check` includes Spotless, Detekt, unit + integration tests, Kover full verify, and frontend ESLint + Vitest.

### Quick local loop

```bash
./gradlew quickCheck --no-daemon
```

**Pre-PR diff coverage (matches CI quality gate):**

CI enforces **changed-line** coverage (in addition to the full `./gradlew check` gate) on push and PR when `check` succeeds. Nightly skips diff coverage (fuzz/mutation only).

| Stack | Report | Threshold | When skipped |
|-------|--------|-----------|--------------|
| Backend Kotlin | Kover XML (`build/reports/kover/report.xml`) | 90% | No `workbench-*/src/main/**/*.kt` changes vs base |
| Frontend | Vitest LCOV (`workbench-frontend/coverage/lcov.info`) | 70% | No `workbench-frontend/src/**/*.{ts,js,svelte}` changes vs base |

Install [uv](https://docs.astral.sh/uv/) once (`curl -LsSf https://astral.sh/uv/install.sh | sh`). Python tooling lives in `scripts/ci/` (Ruff lint/format included in CI).

```bash
./gradlew check --no-daemon
./gradlew :workbench-frontend:pnpmCoverage --no-daemon
git fetch origin main
uv run --directory scripts/ci check-diff-coverage              # both stacks
uv run --directory scripts/ci check-diff-coverage --target backend
uv run --directory scripts/ci check-diff-coverage --target frontend
```

Lint/format CI Python scripts:

```bash
uv run --directory scripts/ci ruff check .
uv run --directory scripts/ci ruff format .
```

Override compare branch or thresholds: `COMPARE_BRANCH=origin/develop FAIL_UNDER_BACKEND=90 FAIL_UNDER_FRONTEND=70`.

HTML reports: `scripts/ci/diff-cover-backend.html`, `scripts/ci/diff-cover-frontend.html`.

**Fix formatting locally:**

```bash
./gradlew spotlessApply
```

**Extended (nightly CI / large changes):**

```bash
./gradlew extendedCheck --no-parallel --no-configuration-cache
./gradlew :workbench-frontend:pnpmCoverage
./gradlew :workbench-frontend:pnpmE2e
```

| Test kind | Rule | Command |
|-----------|------|---------|
| Unit | Must **not** start Spring; no integration tag | `./gradlew :workbench-*:unitTest` |
| Integration | Integration tag (`@Tags` on Kotest, `@Tag` on JUnit); needs Docker | `./gradlew :workbench-*:integrationTest` |
| All backend tests | Unit + integration | `./gradlew :workbench-*:test` |
| Fuzz | Fuzz tag (`@Tags("fuzz")` / `@Tag("fuzz")`) | `./gradlew fuzzTest` |
| Mutation | nightly (`extendedCheck`) | `./gradlew mutationTest --no-parallel --no-configuration-cache` |

Generic verification patterns: [springboot-verification](../springboot-verification/SKILL.md) (use Gradle commands above for this repo).

## CI Mapping

| Trigger | Workflow | What runs |
|---------|----------|-----------|
| Push / PR | [ci.yml](../../../.github/workflows/ci.yml) → quality-gate | `check` (full), bootJar, diff coverage, Docker build (no push on PR) |
| Nightly 02:00 Asia/Shanghai | [nightly.yml](../../../.github/workflows/nightly.yml) | `check` + fuzz + mutation + `koverUnitCoverage -Pkover.unitOnly` (no diff coverage) |

Reports: `uv run check-diff-coverage` writes diff coverage to GitHub Step Summary; `uv run render-quality-summary` writes Kover/PIT. Diff coverage runs on CI push/PR when `./gradlew check` succeeds.

## PR Checklist

- [ ] `./gradlew quickCheck` during iteration; `./gradlew check` before push locally
- [ ] Diff coverage passes locally when Kotlin or frontend source changed (`uv run --directory scripts/ci check-diff-coverage`)
- [ ] Kotlin changes: unit/integration tests added; backend diff ≥ 90%
- [ ] Frontend changes: Vitest tests added; frontend diff ≥ 70%
- [ ] API changes: `pnpm openapi` run; generated client committed
- [ ] New Flyway version: `PostgresMigrationIntegrationTest` expected count updated
- [ ] No secrets or debug logging left in diff
- [ ] Commit messages follow [git-commit](../git-commit/SKILL.md) (Conventional Commits)
- [ ] If CI diff coverage fails: download `quality-reports-*` artifact and open `scripts/ci/diff-cover-*.html` to find uncovered changed lines

## Anti-patterns

- Business logic in controllers → [api-design](../api-design/SKILL.md)
- Unit tests starting Spring context
- OpenAPI field changes without regen frontend client
- New migration without updating integration test count
- PR with only module `unitTest` — skips Spotless, Detekt, integration tests, frontend lint
- Opening a PR after only `./gradlew check` when source changed but diff coverage was not run locally
- Changing Kotlin or frontend business logic without tests for the new/changed lines

## Additional Resources

- [git-commit](../git-commit/SKILL.md) — commit message format and agent workflow
- [reference.md](reference.md) — command cheat sheet, paths, CI stages
- [README.md](../../../README.md) — baseline stack and architecture
- [AGENTS.md](../../../AGENTS.md) — Cursor Cloud toolchain notes
