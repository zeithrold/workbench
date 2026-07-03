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
- [ ] `./gradlew check` passes
- [ ] PR opened; CI quality gate green
```

## Change-Type Decision Tree

| Change | Modules (order) | Required steps | Skill |
|--------|-----------------|----------------|-------|
| New/changed API | core → service → web | OpenAPI; regen client | [api-design](../api-design/SKILL.md) |
| Schema / persistence | workbench-data | `V{n}__*.sql`; Exposed; bump migration test | [kotlin-exposed-patterns](../kotlin-exposed-patterns/SKILL.md) |
| Business logic | workbench-service, workbench-agile | Unit tests without Spring | [kotlin-patterns](../kotlin-patterns/SKILL.md), [kotlin-testing](../kotlin-testing/SKILL.md) |
| Frontend UI | workbench-frontend | Lint + Vitest (in `check`) | [svelte](../svelte/SKILL.md) |
| Background / Kafka | workbench-worker | integration profile + Testcontainers | [springboot-patterns](../springboot-patterns/SKILL.md) |

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

`./gradlew dev` prints the same commands. OpenAPI: `http://localhost:8080/v3/api-docs`; Scalar: `/scalar`.

**Cursor Cloud VM:** JDK 25 `JAVA_HOME`, Docker daemon, and Orval caveats — see [AGENTS.md](../../../AGENTS.md).

## Verification

**Standard gate (matches CI):**

```bash
./gradlew check --no-daemon
./gradlew :workbench-web:bootJar :workbench-worker:bootJar --no-daemon
```

`check` includes Spotless, Detekt, backend tests (excludes fuzz), Kover, and frontend ESLint + Vitest.

**Fix formatting locally:**

```bash
./gradlew spotlessApply
```

**Extended (nightly CI / large changes):**

```bash
./gradlew fuzzTest
./gradlew mutationTest --no-parallel --no-configuration-cache
./gradlew :workbench-frontend:pnpmCoverage
./gradlew :workbench-frontend:pnpmE2e
```

| Test kind | Rule | Command |
|-----------|------|---------|
| Unit | Must **not** start Spring | `./gradlew :workbench-*:test` |
| Integration | `@Tag("integration")`, needs Docker | included in `check` |
| Fuzz | `@Tag("fuzz")` | `./gradlew fuzzTest` |
| Mutation | nightly | `./gradlew mutationTest --no-parallel --no-configuration-cache` |

Generic verification patterns: [springboot-verification](../springboot-verification/SKILL.md) (use Gradle commands above for this repo).

## CI Mapping

| Trigger | Workflow | What runs |
|---------|----------|-----------|
| Push / PR | [ci.yml](../../../.github/workflows/ci.yml) → quality-gate | `check`, bootJar, Docker build (no push on PR) |
| Nightly 02:00 Asia/Shanghai | [nightly.yml](../../../.github/workflows/nightly.yml) | quality-gate + fuzz + mutation |

Reports: `scripts/ci/render-quality-summary.py` → GitHub Step Summary (Kover/PIT).

## PR Checklist

- [ ] `./gradlew check` passes locally
- [ ] API changes: `pnpm openapi` run; generated client committed
- [ ] New Flyway version: `PostgresMigrationIntegrationTest` expected count updated
- [ ] No secrets or debug logging left in diff
- [ ] Commit messages follow [git-commit](../git-commit/SKILL.md) (Conventional Commits)

## Anti-patterns

- Business logic in controllers → [api-design](../api-design/SKILL.md)
- Unit tests starting Spring context
- OpenAPI field changes without regen frontend client
- New migration without updating integration test count
- PR with only module `test` — skips Spotless, Detekt, frontend lint

## Additional Resources

- [git-commit](../git-commit/SKILL.md) — commit message format and agent workflow
- [reference.md](reference.md) — command cheat sheet, paths, CI stages
- [README.md](../../../README.md) — baseline stack and architecture
- [AGENTS.md](../../../AGENTS.md) — Cursor Cloud toolchain notes
