# AGENTS.md

## Cursor Cloud specific instructions

Durable notes for running/developing Workbench (multi-module Spring Boot 4 + SvelteKit monolith) in the Cursor Cloud VM. Standard commands live in `README.md`; only non-obvious caveats are captured here.

### Toolchain
- The build requires **JDK 25** (Gradle `jvmToolchain(25)`), but the base image's default `java` is 21. `JAVA_HOME=/usr/lib/jvm/temurin-25` is exported in `~/.bashrc`, and `~/.gradle/gradle.properties` points `org.gradle.java.installations.paths` at it so Gradle finds the toolchain without network provisioning. If a Gradle command fails with a toolchain/`release 25` error, run it with `JAVA_HOME=/usr/lib/jvm/temurin-25` explicitly.
- Node and pnpm are preinstalled; the `com.github.gradle.node` plugin has `download=false` and reuses the system Node.

### Infrastructure (Docker)
- The Docker daemon is not started automatically. Start it once per VM with `sudo dockerd` (it's configured for `fuse-overlayfs` with the containerd snapshotter disabled in `/etc/docker/daemon.json`; the user is in the `docker` group). 
- Start backing services with `docker compose up -d postgres valkey redpanda minio` for the minimal backend stack, or `docker compose up -d` for the full local stack including Debezium and Elasticsearch.
- Postgres/Valkey/Redpanda must be up before the backend/worker start (Flyway migrates Postgres at boot; Redisson connects to Valkey at startup).

### Services
- Backend API (`workbench-web`, port 8080), worker (`workbench-worker`, no web server), and frontend (`workbench-frontend`, Vite port 5173). Run commands are in `README.md`. Prefix backend/worker Gradle runs with the JDK-25 `JAVA_HOME` if it is not already active.
- OpenAPI: `http://localhost:8080/api/openapi`; Scalar UI: `http://localhost:8080/api/scalar`; both plus `/api/actuator/health` are public (`permitAll`).
- The frontend's `pnpm openapi` (Orval) reads `http://localhost:8080/api/openapi`, so the backend must be running to regenerate the API client.

### Verification tiers

| Tier | Command | Scope |
|------|---------|--------|
| **Quick** (local loop) | `./gradlew quickCheck` | Spotless + Detekt + **unit tests**; frontend ESLint only |
| **Full** (pre-PR / CI) | `./gradlew check` | Quick scope + **integration tests** + Kover **full** coverage gate (90%) + frontend Vitest |
| **Extended** (Nightly CI) | `./gradlew extendedCheck` | Full + fuzz + mutation; also generates **unit-only** Kover XML |

Backend test tasks: `unitTest` (excludes integration and fuzz tags), `integrationTest` (integration-tagged only), `test` (runs both). Kotest specs use `@Tags("integration")`; JUnit `@Test` classes use `@Tag("integration")`. Integration tests need Docker.

**Coverage (two metrics):**
- **Full** — `build/reports/kover/report.xml` — unit + integration; gated at 90% on `check`.
- **Unit** — `build/reports/kover/unit/report.xml` — unit tests only; report-only for now (soft target 70%+). Generate with `./gradlew koverUnitCoverage -Pkover.unitOnly`.

Unit vs integration responsibilities and thresholds: [`.agents/skills/workbench-development/SKILL.md`](.agents/skills/workbench-development/SKILL.md).

### Lint / test caveats
- Run `./gradlew quickCheck` during local iteration; `./gradlew check` before push; `./gradlew extendedCheck` matches Nightly.
- **Diff coverage:** after `./gradlew check`, run `./gradlew :workbench-frontend:pnpmCoverage` then `uv run --directory scripts/ci check-diff-coverage`. CI runs this on push/PR (not nightly) when `check` succeeds. See [`.agents/skills/workbench-development/SKILL.md`](.agents/skills/workbench-development/SKILL.md).
- JVM integration tests use Testcontainers, so the Docker daemon must be running.
- Mutation testing: `./gradlew mutationTest --no-parallel --no-configuration-cache` (nightly / extended CI); config in `config/pitest/pitest.properties`. Per-module debug: `./gradlew :workbench-core:pitest`.

### Dependency notes
- Redisson is pinned to `4.6.1` (3.x's `RedissonAutoConfigurationV2` is incompatible with Spring Boot 4's package relocation), and `workbench-web` includes `kotlinx-coroutines-reactor` at runtime (required for Spring MVC to invoke `suspend` handler functions). Both are needed for the apps to boot and serve requests on Spring Boot 4.

### workbench-data package layout
- `data.persistence.{tech}.{domain}` — low-level storage access (Exposed tables, SQL/query builders, future ES/ClickHouse clients); no `@Repository`.
- `data.repository.{domain}` — Spring beans implementing `workbench-core` ports; orchestrate persistence.
- `data.storage.{blob|config}` — object/blob storage (S3, in-memory).
- `data.messaging`, `data.locking`, `data.migration` — infrastructure adapters at the `data` root.
