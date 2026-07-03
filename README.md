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

OpenAPI is available at `http://localhost:8080/v3/api-docs`. Scalar is available at `http://localhost:8080/scalar`.

## Verification

```bash
./gradlew check
./gradlew mutationTest
./gradlew :workbench-core:pitest
./gradlew :workbench-frontend:pnpmCoverage
./gradlew :workbench-frontend:pnpmE2e
```

Unit tests must not start Spring. Integration tests may use Testcontainers.

## Architecture Notes

The monolith is split by responsibility-oriented Gradle modules: `workbench-core`, `workbench-service`, `workbench-data`, `workbench-security`, `workbench-web`, `workbench-worker`, and `workbench-frontend`. Controllers stay thin and call services, services call repository ports, and Exposed persistence remains behind repository interfaces. Public API names avoid ambiguous suffixes such as BO, PO, VO, or DTO; use Request, Response, Command, Query, Event, Record, Snapshot, View, or Projection instead.
