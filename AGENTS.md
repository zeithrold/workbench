# AGENTS.md

## Cursor Cloud specific instructions

Durable notes for running/developing Workbench (multi-module Spring Boot 4 + SvelteKit monolith) in the Cursor Cloud VM. Standard commands live in `README.md`; only non-obvious caveats are captured here.

### Toolchain
- The build requires **JDK 25** (Gradle `jvmToolchain(25)`), but the base image's default `java` is 21. `JAVA_HOME=/usr/lib/jvm/temurin-25` is exported in `~/.bashrc`, and `~/.gradle/gradle.properties` points `org.gradle.java.installations.paths` at it so Gradle finds the toolchain without network provisioning. If a Gradle command fails with a toolchain/`release 25` error, run it with `JAVA_HOME=/usr/lib/jvm/temurin-25` explicitly.
- Node and pnpm are preinstalled; the `com.github.gradle.node` plugin has `download=false` and reuses the system Node.

### Infrastructure (Docker)
- The Docker daemon is not started automatically. Start it once per VM with `sudo dockerd` (it's configured for `fuse-overlayfs` with the containerd snapshotter disabled in `/etc/docker/daemon.json`; the user is in the `docker` group). 
- Start the compact local stack with `docker compose up -d`. Use `docker compose --profile distributed up -d` only when Redpanda, Debezium, and the standalone Worker are required.
- Postgres/Valkey/Redpanda must be up before the backend/worker start (Flyway migrates Postgres at boot; Redisson connects to Valkey at startup).

### Agent-owned ephemeral Infra
- Agents must not start, stop, migrate, repair, or clean the developer-owned default Compose project unless the user explicitly asks for that environment to be diagnosed.
- Code inspection, `quickCheck`, unit tests, builds, and ordinary integration tests do not start Compose. Integration tests use Testcontainers and only require the Docker daemon.
- When a real application stack is required for OpenAPI generation, browser verification, or runtime debugging, use `uv run --directory scripts/dev ephemeral-infra`. The default `compact` lease starts PostgreSQL, Valkey, Elasticsearch, and MinIO; use `distributed` only for Redpanda/Debezium behavior.
- Prefer `uv run --directory scripts/dev ephemeral-infra run -- <command>` so cleanup happens on exit. For multi-step work, use `up --json`, run commands through `exec <lease-id> -- ...`, and call `down <lease-id>` when finished; a TTL reaper and `gc` provide crash recovery.
- The tool only operates on manifests under `.gradle/agent-infra` and Compose projects named `workbench-agent-*`. Local leases bind dynamic loopback ports and always start a fresh PostgreSQL database.
- Local Unix/npipe Docker endpoints may be used autonomously when the task requires Infra. SSH/TCP contexts are rejected; `--allow-remote` may be used only after explicit user approval because it publishes the allocated ports on the remote host. Keep an approved remote lease's TTL as short as practical.

### Services
- Backend API (`workbench-web`, port 8080), worker (`workbench-worker`, no web server), and frontend (`workbench-frontend`, Vite port 5173). Run commands are in `README.md`. Prefix backend/worker Gradle runs with the JDK-25 `JAVA_HOME` if it is not already active.
- OpenAPI: `http://localhost:8080/api/openapi`; Scalar UI: `http://localhost:8080/api/scalar`; both plus `/api/actuator/health` are public (`permitAll`).
- The frontend's `pnpm openapi` (Orval) reads `http://localhost:8080/api/openapi`, so the backend must be running to regenerate the API client.

### Verification tiers

| Tier | Command | Scope |
|------|---------|--------|
| **Quick** (local loop) | `./gradlew quickCheck` | Spotless + Detekt + **unit tests**; frontend lint + unit tests |
| **Full** (pre-PR / CI) | `./gradlew check` | Quick scope + **integration tests** + Kover **full** coverage gate (90%) + frontend Vitest |
| **Extended** (large local changes) | `./gradlew extendedCheck` | Full + `fuzzTest` + `mutationTest` |

Infra-tool checks: `./gradlew agentInfraCheck` is non-Docker and part of `quickCheck`;
`./gradlew agentInfraSmokeTest` creates and destroys one isolated compact lease.

Backend test tasks: `test` runs unit tests from `src/test`; `integrationTest` runs integration tests from `src/integrationTest`; `check` runs both. Fuzz tests remain tagged under `src/test`; integration tests need Docker.

**Coverage (two metrics):**
- **Full** — `build/reports/kover/report.xml` — unit + integration; gated at 90% by standard `check`.
- **Unit** — `build/reports/kover/unit/report.xml` — unit tests only; soft warnings at 70% and full-unit delta >15pp. Generate with `./gradlew koverUnitXmlReport`.

Unit vs integration responsibilities and thresholds: [`.agents/skills/workbench-development/SKILL.md`](.agents/skills/workbench-development/SKILL.md).

### Lint / test caveats
- Run `./gradlew quickCheck` during local iteration; `./gradlew check` before push; use `./gradlew extendedCheck` for fuzz and mutation verification.
- **Diff coverage:** after `./gradlew check`, run `./gradlew frontendFullCoverage` then `uv run --directory scripts/ci check-diff-coverage`. CI runs this in the `quality-gate-finalize` job (not nightly) after parallel `frontend-e2e` completes. See [`.agents/skills/workbench-development/SKILL.md`](.agents/skills/workbench-development/SKILL.md).
- **Frontend coverage tiers:** Unit (`coverage/unit/lcov.info`), Full (`coverage/full/lcov.info` = unit + storybook), E2E (`coverage/e2e/lcov.info`). Commands: `./gradlew frontendUnitCoverage`, `frontendFullCoverage`, `:workbench-frontend:e2eCheck`. LCOV measures `src/**/*.{ts,js}` only (`.svelte` components are tested but not counted).
- JVM integration tests use Testcontainers, so the Docker daemon must be running.
- Mutation testing: `./gradlew mutationTest --no-parallel --no-configuration-cache` (nightly / extended CI); config in `config/pitest/pitest.properties`. Per-module debug: `./gradlew :workbench-application:pitest`.

### Dependency notes
- Redisson is pinned to `4.6.1` (3.x's `RedissonAutoConfigurationV2` is incompatible with Spring Boot 4's package relocation), and `workbench-web` includes `kotlinx-coroutines-reactor` at runtime (required for Spring MVC to invoke `suspend` handler functions). Both are needed for the apps to boot and serve requests on Spring Boot 4.

### workbench-data package layout
- `data.persistence.{tech}.{domain}` — low-level storage access (Exposed tables, SQL/query builders, future ES/ClickHouse clients); no `@Repository`.
- `data.repository.{domain}` — Spring beans implementing ports owned by the corresponding domain or application module; orchestrate persistence.
- `data.storage.{blob|config}` — object/blob storage (S3, in-memory).
- `data.messaging`, `data.locking`, `data.migration` — infrastructure adapters at the `data` root.
