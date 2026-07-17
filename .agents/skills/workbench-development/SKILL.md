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
- [ ] Infrastructure requirement classified (none, Testcontainers, or ephemeral lease)
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
| Business logic | workbench-application and the owning domain module | Unit tests without Spring; changed Kotlin lines ≥ 90% diff coverage | [kotlin-patterns](../kotlin-patterns/SKILL.md), [kotlin-testing](../kotlin-testing/SKILL.md) |
| Frontend UI | workbench-frontend | Lint + Vitest (in `check`); changed TS/JS lines ≥ 70% diff coverage | [svelte](../svelte/SKILL.md) |
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
| `workbench-kernel` | Pure shared types, event protocol, lock and blob ports |
| `workbench-tenant`, `workbench-identity`, `workbench-agile`, `workbench-notification` | Domain models, ports, events, and domain services |
| `workbench-application` | Cross-domain use cases, asynchronous handlers, and Outbox execution |
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

### Agent ephemeral Infra

Agents do not reuse the developer Compose project. Code analysis, unit tests, `quickCheck`, and
ordinary builds require no Compose resources; integration tests use Testcontainers. When a real
application stack is necessary, use the lease tool:

```bash
uv run --directory scripts/dev ephemeral-infra run --profile compact -- <command>
uv run --directory scripts/dev ephemeral-infra up --profile compact --ttl 2h --json
uv run --directory scripts/dev ephemeral-infra exec <lease-id> -- <command>
uv run --directory scripts/dev ephemeral-infra down <lease-id>
```

The lease manager is a uv-managed Python CLI. `compact` is the default and contains
PostgreSQL, Valkey, Elasticsearch, and MinIO. Select
`distributed` only for Redpanda/Debezium behavior. Local leases use dynamic loopback ports, a
fresh database, a unique `workbench-agent-*` Compose project, and automatic expiry. Non-local
Docker contexts require explicit user approval before passing `--allow-remote`; remote mode
publishes the allocated ports on that host and should use the shortest practical TTL.

**Cursor Cloud VM:** JDK 25 `JAVA_HOME`, Docker daemon, and Orval caveats — see [AGENTS.md](../../../AGENTS.md).

## Verification

### Tiers

| Tier | Command | When | Includes |
|------|---------|------|----------|
| **Quick** | `./gradlew quickCheck` | Local edit loop | Spotless, Detekt, backend `test`, frontend lint + unit tests |
| **Full** | `./gradlew check` | Pre-PR, CI push/PR | Quick + `integrationTest`, full Kover gate (90%), frontend Vitest |
| **Extended** | `./gradlew extendedCheck` | Large local verification | Full + `fuzzTest` + `mutationTest` |

Python tooling: `./gradlew pythonToolingCheck` validates Ruff for `scripts/ci` and `scripts/dev`,
plus the non-Docker lease-tool unit tests, and is included in both `quickCheck` and `check`.
`./gradlew agentInfraCheck` is the focused `scripts/dev` subset; `./gradlew agentInfraSmokeTest`
performs an isolated local-Docker lifecycle check.

Module tasks: `test` runs unit tests from `src/test`; `integrationTest` runs integration tests from `src/integrationTest`; `check` runs both. Integration tests require Docker.

**Classification:** Integration tests use the `src/integrationTest` source set and `*IntegrationTest` suffix; integration tags are retired. Fuzz tests remain under `src/test` and use `@Tags("fuzz")` for Kotest or `@Tag("fuzz")` for JUnit.

### Unit vs integration responsibilities

| | Unit (`test`) | Integration (`integrationTest`) |
|--|-------------------|----------------------|
| **Purpose** | Business rules, pure logic, HTTP slice (`@WebMvcTest`) | SQL/Exposed semantics, Testcontainers, Kafka/S3/auth wiring |
| **Dependencies** | Fake/Recording ports or MockK; **no Spring full context** | Real Postgres/Valkey/Kafka/Keycloak via Testcontainers |
| **Naming** | `src/test/**/*Test.kt` | `src/integrationTest/**/*IntegrationTest.kt` |
| **PIT** | Included | Excluded (`config/pitest/pitest.properties`) |
| **Prefer** | State/result assertions over `coVerify`-only | Scenarios Fake cannot model (transactions, JSONB, migrations) |

**Consistency:** share fixtures (`workbench-test-support` / module `testFixtures`); use Fake implementations of ports from their owning domain or application module in unit tests; reserve integration tests for adapter/wiring proof. Do not duplicate the same business matrix in both layers.

### Testing principles

- Prioritize test correctness over speed. If writing a test exposes unreasonable code or architecture (e.g., untestable coupling, missing ports, logic in the wrong layer), **stop** and propose design changes before continuing with workaround tests. Canonical reference: [testing-governance.md](../../../docs/testing-governance.md).

### Coverage standards (two metrics)

| Metric | Report | Gate | Target |
|--------|--------|------|--------|
| **Full** (unit + integration) | `build/reports/kover/report.xml` | `./gradlew check` | **90%** line (aggregate + per backend module) |
| **Unit** (unit tests only) | `build/reports/kover/unit/report.xml` | Soft warning | **70%+** line; warns when full-unit line delta > **15pp** |
| **Diff** (full Kover / Vitest LCOV) | `scripts/ci/diff-cover-*.html` | CI after `check` | Backend changed lines **90%**, frontend **70%** |
| **Storybook components** | `workbench-frontend/coverage/storybook-components/component-coverage.json` | `./gradlew check` | New/changed eligible production-reachable Svelte components **100% mounted**; aggregate may not regress (`components/ui/**` shadcn source excluded) |
| **Mutation / Strength** | `build/reports/pitest/` | Nightly report | Track via Quality Report; no hard gate yet |

If full line coverage exceeds unit by **> 15%** for a module, add unit tests or contract tests — do not compensate with more integration tests alone.

Generate unit-only coverage:

```bash
./gradlew koverUnitXmlReport
```

### Standard gate (matches CI)

```bash
./gradlew check --no-daemon
./gradlew :workbench-web:bootJar :workbench-worker:bootJar --no-daemon
```

`check` is the shared local/CI gate: Spotless, Detekt, unit + integration tests, Kover full verify, and frontend ESLint + Vitest.

### Quick local loop

```bash
./gradlew quickCheck --no-daemon
```

**Pre-PR diff coverage (matches CI quality gate):**

CI enforces **changed-line** coverage after the full `./gradlew check` gate succeeds. Nightly skips diff coverage (fuzz/mutation only).

| Stack | Report | Threshold | When skipped |
|-------|--------|-----------|--------------|
| Backend Kotlin | Kover XML (`build/reports/kover/report.xml`) | 90% | No `workbench-*/src/main/**/*.kt` changes vs base |
| Frontend | Vitest LCOV (`workbench-frontend/coverage/lcov.info`) | 70% | No `workbench-frontend/src/**/*.{ts,js}` changes vs base |

Install [uv](https://docs.astral.sh/uv/) once (`curl -LsSf https://astral.sh/uv/install.sh | sh`). Python tooling in `scripts/ci/` is limited to the `diff-cover` wrapper (Ruff lint/format included in CI).

```bash
./gradlew check --no-daemon
./gradlew frontendFullCoverage --no-daemon
./gradlew frontendStorybookComponentCoverage --no-daemon
git fetch origin main
uv run --directory scripts/ci check-diff-coverage              # both stacks
uv run --directory scripts/ci check-diff-coverage --target backend
uv run --directory scripts/ci check-diff-coverage --target frontend
```

Lint/format CI Python scripts:

```bash
uv run --directory scripts/ci ruff check .
uv run --directory scripts/ci ruff format --check .
```

Lint/format agent Infra Python scripts:

```bash
uv run --directory scripts/dev ruff check .
uv run --directory scripts/dev ruff format --check .
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
./gradlew frontendUnitCoverage
./gradlew :workbench-frontend:pnpmE2e
```

| Test kind | Rule | Command |
|-----------|------|---------|
| Unit | `src/test`; must **not** start a full Spring context | `./gradlew :workbench-*:test` |
| Integration | `src/integrationTest`; `*IntegrationTest`; needs Docker | `./gradlew :workbench-*:integrationTest` |
| All backend tests | Unit + integration | `./gradlew :workbench-*:check` |
| Fuzz | Fuzz tag (`@Tags("fuzz")` / `@Tag("fuzz")`) | `./gradlew fuzzTest` |
| Mutation | Extended/Nightly | `./gradlew mutationTest --no-parallel --no-configuration-cache` |

Generic verification patterns: [springboot-verification](../springboot-verification/SKILL.md) (use Gradle commands above for this repo).

## CI Mapping

| Trigger | Workflow | What runs |
|---------|----------|-----------|
| Push / PR | [ci.yml](../../../.github/workflows/ci.yml) → [quality-gate.yml](../../../.github/workflows/quality-gate.yml) | `check` (full), unit coverage, bootJar, diff coverage, Docker build (no push on PR) |
| Nightly 02:00 Asia/Shanghai | [nightly.yml](../../../.github/workflows/nightly.yml) | Per-module `ciNightlyCheck` (parallel) + `test-support:check` + frontend `check` + unit Kover → aggregate PIT/Kover report (no diff coverage, no Docker) |

Reports: `uv run check-diff-coverage` writes diff coverage to GitHub Step Summary (Quality Gate only); `./gradlew ciRenderQualitySummary` writes Kover/PIT and unit/full delta summaries.

## PR Checklist

- [ ] `./gradlew quickCheck` during iteration; `./gradlew check` before push locally
- [ ] Diff coverage passes locally when Kotlin or frontend source changed (`uv run --directory scripts/ci check-diff-coverage`)
- [ ] Kotlin changes: unit/integration tests added; backend diff ≥ 90%
- [ ] Frontend changes: Vitest tests added; frontend diff ≥ 70%
- [ ] Eligible production-reachable Svelte components are mounted in Storybook; new/changed components have 100% mount coverage and the baseline only shrinks (`components/ui/**` shadcn source excluded)
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
- PR with only a module `test` — skips Spotless, Detekt, integration tests, and frontend verification
- Opening a PR after only `./gradlew check` when source changed but diff coverage was not run locally
- Changing Kotlin or frontend business logic without tests for the new/changed lines
- Adding or changing an eligible production-reachable Svelte component without mounting it in a meaningful Storybook scenario
- Writing contorted or shallow tests to work around bad design instead of flagging the design issue

## Additional Resources

- [git-commit](../git-commit/SKILL.md) — commit message format and agent workflow
- [reference.md](reference.md) — command cheat sheet, paths, CI stages
- [README.md](../../../README.md) — baseline stack and architecture
- [AGENTS.md](../../../AGENTS.md) — Cursor Cloud toolchain notes
