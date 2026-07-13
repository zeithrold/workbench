# Workbench

> Database migration history was reset to a single V1 baseline before production. Existing
> development databases are incompatible with the new baseline; recreate local volumes with
> `docker compose down -v` before starting the application after this change.

Workbench is a multi-module Spring Boot 4 and SvelteKit monolith for `ink.doa.workbench`.

## Baseline

- Java 25
- Kotlin 2.4
- Spring Boot 4
- Gradle 9
- PostgreSQL; optional Valkey Streams or Redpanda/Kafka messaging; Elasticsearch and MinIO
- SvelteKit, shadcn-svelte foundation, Antfu ESLint, Orval

## Local Development

Start the compact PostgreSQL topology (the default transport embeds background jobs in Web):

```bash
docker compose up -d
```

Start the backend:

```bash
./gradlew :workbench-web:bootRun --args='--spring.profiles.active=local'
```

The standalone worker is only used by the distributed Kafka topology:

```bash
docker compose --profile distributed up -d
WORKBENCH_MESSAGING_TRANSPORT=kafka \
./gradlew :workbench-worker:bootRun --args='--spring.profiles.active=local,worker'
```

The distributed profile starts Redpanda, Debezium Connect, and an idempotent connector
initializer. Debezium is the only publisher from the transactional `domain_outbox` table to
Kafka; the Worker consumes the resulting events and never publishes Outbox rows itself. Before
starting the Worker, verify that both the connector and its task report `RUNNING`:

```bash
curl http://localhost:8083/connectors/workbench-outbox/status
```

Select embedded Redis Streams explicitly with
`WORKBENCH_MESSAGING_TRANSPORT=redis-streams`; it does not require the standalone worker.

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
./gradlew :workbench-application:ciNightlyCheck  # internal per-module Nightly task
./gradlew koverUnitXmlReport              # unit-only coverage report (soft warnings)
```

Focused verification and reporting:

```bash
./gradlew mutationTest
./gradlew :workbench-application:pitest
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

The monolith is split into `workbench-kernel`, the `tenant`, `identity`, `agile`, and `notification` domain modules, `workbench-application`, the `data` and `security` adapters, and the `web`/`worker` composition roots. Transport-neutral background handlers live under `workbench-application`; PostgreSQL and Redis Streams embed them in Web, while Kafka invokes them from the standalone Worker. See [the enforced module architecture](docs/module-architecture.md). Controllers stay thin, application services depend on domain ports, and Exposed persistence remains behind repository interfaces. Public API names avoid ambiguous suffixes such as BO, PO, VO, or DTO; use Request, Response, Command, Query, Event, Record, Snapshot, View, or Projection instead.
