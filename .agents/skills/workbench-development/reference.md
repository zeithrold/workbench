# Workbench Development Reference

Detailed paths and commands. Read when implementing a specific change type.

## Command Cheat Sheet

| Purpose | Command |
|---------|---------|
| Full stack infra | `docker compose up -d` |
| Minimal backend infra | `docker compose up -d postgres valkey redpanda` |
| Print dev commands | `./gradlew dev` |
| Backend API | `./gradlew :workbench-web:bootRun --args='--spring.profiles.active=local'` |
| Worker | `./gradlew :workbench-worker:bootRun --args='--spring.profiles.active=local,worker'` |
| Frontend dev | `./gradlew :workbench-frontend:pnpmDev` |
| Regenerate API client | `cd workbench-frontend && pnpm openapi` (backend must be running) |
| Main verify (CI gate) | `./gradlew check --no-daemon` |
| Format fix | `./gradlew spotlessApply` |
| Boot jars | `./gradlew :workbench-web:bootJar :workbench-worker:bootJar --no-daemon` |
| Fuzz tests | `./gradlew fuzzTest` |
| Mutation tests | `./gradlew mutationTest --no-parallel --no-configuration-cache` |
| Frontend coverage | `./gradlew :workbench-frontend:pnpmCoverage` |
| Diff coverage (all stacks) | `uv run --directory scripts/ci check-diff-coverage` |
| Diff coverage (backend) | `uv run --directory scripts/ci check-diff-coverage --target backend` |
| Diff coverage (frontend) | `uv run --directory scripts/ci check-diff-coverage --target frontend` |
| Install uv | `curl -LsSf https://astral.sh/uv/install.sh \| sh` |
| Lint CI Python | `uv run --directory scripts/ci ruff check .` |
| Format CI Python | `uv run --directory scripts/ci ruff format .` |
| Frontend E2E | `./gradlew :workbench-frontend:pnpmE2e` |
| Frontend build | `./gradlew :workbench-frontend:pnpmBuild` |
| Module unit tests | `./gradlew :workbench-<module>:test` |

## Toolchain

| Tool | Version / note |
|------|----------------|
| JDK | 25 (`jvmToolchain(25)` in root `build.gradle.kts`) |
| Kotlin | 2.4 |
| Spring Boot | 4 |
| Gradle | 9 |
| Node | 24 (CI) |
| pnpm | 10.33.0 |
| uv | Python ≥ 3.12; deps in `scripts/ci/pyproject.toml` |

Cloud VM: set `JAVA_HOME` to JDK 25 if toolchain resolution fails — see [AGENTS.md](../../../AGENTS.md).

## Module Map

| Module | Path | Key contents |
|--------|------|--------------|
| workbench-core | `workbench-core/` | `*Record`, `*Query`, `*Summary`, repository ports |
| workbench-service | `workbench-service/` | `*Command`, `*View`, application services |
| workbench-agile | `workbench-agile/` | Project/work-item services |
| workbench-data | `workbench-data/` | Exposed tables, repos, Flyway, adapters |
| workbench-security | `workbench-security/` | Security config |
| workbench-web | `workbench-web/` | Controllers, `*Request`/`*Response`, OpenAPI |
| workbench-worker | `workbench-worker/` | Kafka consumers, scheduled jobs |
| workbench-frontend | `workbench-frontend/` | SvelteKit routes, components, generated API |

## Flyway Migrations

| Item | Value |
|------|-------|
| Location | `workbench-data/src/main/resources/db/migration/` |
| Naming | `V{n}__{snake_case_description}.sql` |
| Runtime | Spring Boot Flyway at startup (`classpath:db/migration`) |
| Apps that migrate | `workbench-web`, `workbench-worker` |
| Integration test | `workbench-data/.../PostgresMigrationIntegrationTest.kt` |

**After adding a migration:** update `result.migrationsExecuted shouldBe N` in the integration test to match total migration count.

**Seeds:** use idempotent `ON CONFLICT … DO NOTHING`.

## OpenAPI / Orval

| Item | Value |
|------|-------|
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Scalar UI | `http://localhost:8080/scalar` |
| OpenAPI config | `workbench-web/.../api/OpenApiConfiguration.kt` |
| Examples | `workbench-web/.../api/OpenApiExamples.kt` |
| Orval config | `workbench-frontend/orval.config.ts` |
| Generated client | `workbench-frontend/src/lib/api/generated/` |
| Manual client constants | `workbench-frontend/src/lib/api/client.ts` |

Field renames in OpenAPI are breaking — coordinate backend and frontend in one PR when possible.

## Testing Conventions

| Tag / profile | Use |
|---------------|-----|
| (none) | Unit tests — no Spring context |
| `@Tag("integration")` | Testcontainers, `spring.profiles.active=integration` |
| `@Tag("fuzz")` | Property-based tests; excluded from `check` |
| Docker required | Integration tests and `check` on JVM modules |

**Stacks:** Kotest + MockK + JUnit 5. Integration fixtures in `workbench-service` testFixtures and `config/integration-test/`.

**Static analysis (via `check`):** Spotless (ktfmt Google), Detekt (`config/detekt/detekt.yml`), Kover coverage gate.

**Incremental (diff) coverage (CI gate, local pre-PR):**

| Item | Value |
|------|-------|
| Tool | `diff-cover` via `scripts/ci/check_diff_coverage.py` (UV-managed) |
| Backend report | `build/reports/kover/report.xml` |
| Frontend report | `workbench-frontend/coverage/lcov.info` |
| Backend threshold | 90% changed lines (`FAIL_UNDER_BACKEND`) |
| Frontend threshold | 70% changed lines (`FAIL_UNDER_FRONTEND`) |
| Compare branch | `origin/main` by default (`COMPARE_BRANCH`) |
| HTML reports | `scripts/ci/diff-cover-backend.html`, `scripts/ci/diff-cover-frontend.html` |
| CI results JSON | `scripts/ci/diff-coverage-results.json` |

Full-project Kover (90%) and Vitest thresholds (70%) remain separate from the diff coverage gate. Diff coverage skips a stack when git diff has no matching source changes.

**Mutation testing:** PIT config in `config/pitest/pitest.properties`; per-module debug: `./gradlew :workbench-core:pitest`.

## CI Pipeline Stages

Workflow: [.github/workflows/quality-gate.yml](../../../.github/workflows/quality-gate.yml)

### Job: quality-gate

1. Checkout (`fetch-depth: 0`), fetch PR base branch, JDK 25, pnpm 10.33, Node 24, Gradle, uv
2. `uv run ruff check` + `uv run ruff format --check` in `scripts/ci/`
3. `./gradlew check --no-daemon`
4. `./gradlew :workbench-web:bootJar :workbench-worker:bootJar --no-daemon`
5. Upload boot jars artifact
6. (nightly only) `fuzzTest`, `mutationTest`
7. `koverXmlReport`
8. (CI push/PR only, after successful `check`) `:workbench-frontend:pnpmCoverage`, `uv run check-diff-coverage`
9. `uv run check-diff-coverage` → Step Summary (diff coverage); `uv run render-quality-summary` → Step Summary (Kover, PIT)
10. Upload artifacts: Kover, PIT, diff-cover HTML, `diff-coverage-results.json`, frontend `coverage/` (14-day retention)

### Job: docker (after quality-gate)

- Matrix: amd64 + arm64
- Build `workbench-web` and `workbench-worker` images
- Push to GHCR **only when not a PR**

### Job: docker-manifest (non-PR)

- Multi-arch manifest for web and worker images on default branch

### Triggers

| Workflow | When | Extended tests |
|----------|------|----------------|
| `ci.yml` | push to any branch, all PRs | no |
| `nightly.yml` | cron 02:00 Asia/Shanghai, manual | yes (fuzz + mutation) |

## Related Skills

| Topic | Skill |
|-------|-------|
| HTTP API shape | [api-design](../api-design/SKILL.md) |
| Exposed + repositories | [kotlin-exposed-patterns](../kotlin-exposed-patterns/SKILL.md) |
| Kotlin idioms | [kotlin-patterns](../kotlin-patterns/SKILL.md) |
| Testing / TDD | [kotlin-testing](../kotlin-testing/SKILL.md) |
| Spring architecture | [springboot-patterns](../springboot-patterns/SKILL.md) |
| Verification patterns | [springboot-verification](../springboot-verification/SKILL.md) |
| Svelte UI | [svelte](../svelte/SKILL.md) |
