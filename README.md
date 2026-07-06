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
./gradlew quickCheck                              # local: unit tests + static analysis
./gradlew check                                   # pre-PR / CI: + integration tests + full Kover (90%)
./gradlew extendedCheck                           # Nightly: + fuzz + mutation
./gradlew koverUnitCoverage -Pkover.unitOnly      # unit-only coverage report (no gate)
```

Legacy per-module debug:

```bash
./gradlew mutationTest
./gradlew :workbench-core:pitest
./gradlew :workbench-frontend:pnpmCoverage
./gradlew :workbench-frontend:pnpmE2e
```

**Diff coverage** (same gate as CI; requires [uv](https://docs.astral.sh/uv/)):

```bash
./gradlew check
./gradlew :workbench-frontend:pnpmCoverage
git fetch origin main
uv run --directory scripts/ci check-diff-coverage
```

Thresholds: backend changed lines ≥ 90%, frontend changed lines ≥ 70%. See [`.agents/skills/workbench-development/SKILL.md`](.agents/skills/workbench-development/SKILL.md) for details.

Unit tests must not start Spring. Integration tests may use Testcontainers. Tag Kotest specs with `@Tags("integration")`; tag JUnit `@Test` classes with `@Tag("integration")`.

## Architecture Notes

The monolith is split by responsibility-oriented Gradle modules: `workbench-core`, `workbench-service`, `workbench-data`, `workbench-security`, `workbench-web`, `workbench-worker`, and `workbench-frontend`. Controllers stay thin and call services, services call repository ports, and Exposed persistence remains behind repository interfaces. Public API names avoid ambiguous suffixes such as BO, PO, VO, or DTO; use Request, Response, Command, Query, Event, Record, Snapshot, View, or Projection instead.
