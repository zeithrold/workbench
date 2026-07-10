# Workbench

Workbench is a multi-module Spring Boot 4 and SvelteKit monolith for `ink.doa.workbench`.

## Baseline

- Java 25
- Kotlin 2.4
- Spring Boot 4
- Gradle 9
- PostgreSQL, Valkey, Redpanda, Debezium, Elasticsearch
- SvelteKit, shadcn-svelte foundation, Antfu ESLint, Orval

## Local Development

Start infrastructure:

```bash
docker compose up -d
```

Start the backend:

```bash
./gradlew :workbench-web:bootRun --args='--spring.profiles.active=local'
```

Start the worker:

```bash
./gradlew :workbench-worker:bootRun --args='--spring.profiles.active=local,worker'
```

Start the frontend:

```bash
./gradlew :workbench-frontend:pnpmDev
```

OpenAPI is available at `http://localhost:8080/api/openapi`. Scalar is available at `http://localhost:8080/api/scalar`.

## Verification

Three tiers (see [AGENTS.md](AGENTS.md) for Cloud caveats):

```bash
./gradlew quickCheck                      # local: unit tests + static analysis
./gradlew check                           # pre-PR / CI: + integration tests + full Kover (90%)
./gradlew extendedCheck                   # full + fuzz + mutation verification
./gradlew :workbench-core:ciNightlyCheck  # internal per-module Nightly task
./gradlew koverUnitXmlReport              # unit-only coverage report (soft warnings)
```

Focused verification and reporting:

```bash
./gradlew mutationTest
./gradlew :workbench-core:pitest
./gradlew frontendUnitCoverage
./gradlew frontendFullCoverage
./gradlew :workbench-frontend:e2eCheck
```

**Diff coverage** (same gate as CI; requires [uv](https://docs.astral.sh/uv/)):

```bash
./gradlew check
./gradlew frontendFullCoverage
git fetch origin main
uv run --directory scripts/ci check-diff-coverage
```

Thresholds: backend changed lines ≥ 90%, frontend changed lines ≥ 70%. See [`.agents/skills/workbench-development/SKILL.md`](.agents/skills/workbench-development/SKILL.md) for details.

Qodana can run locally without Docker when the Qodana CLI is installed:

```bash
qodana scan --within-docker false
```

The CI workflow uses the same non-Docker mode for both token-authenticated Cloud scans and Fork PR local scans.

Unit tests live under `src/test` and must not start a full Spring context. Integration tests live under `src/integrationTest`, use the `*IntegrationTest` suffix, and may use Testcontainers. Run them with `integrationTest`; standard `check` runs both suites. See [test governance](docs/testing-governance.md) for principles, naming, layering, and framework rules.

## Architecture Notes

The monolith is split by responsibility-oriented Gradle modules: `workbench-core`, `workbench-service`, `workbench-data`, `workbench-security`, `workbench-web`, `workbench-worker`, and `workbench-frontend`. Controllers stay thin and call services, services call repository ports, and Exposed persistence remains behind repository interfaces. Public API names avoid ambiguous suffixes such as BO, PO, VO, or DTO; use Request, Response, Command, Query, Event, Record, Snapshot, View, or Projection instead.
