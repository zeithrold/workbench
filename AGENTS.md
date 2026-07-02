# AGENTS.md

## Cursor Cloud specific instructions

Durable notes for running/developing Workbench (multi-module Spring Boot 4 + SvelteKit monolith) in the Cursor Cloud VM. Standard commands live in `README.md`; only non-obvious caveats are captured here.

### Toolchain
- The build requires **JDK 25** (Gradle `jvmToolchain(25)`), but the base image's default `java` is 21. `JAVA_HOME=/usr/lib/jvm/temurin-25` is exported in `~/.bashrc`, and `~/.gradle/gradle.properties` points `org.gradle.java.installations.paths` at it so Gradle finds the toolchain without network provisioning. If a Gradle command fails with a toolchain/`release 25` error, run it with `JAVA_HOME=/usr/lib/jvm/temurin-25` explicitly.
- Node and pnpm are preinstalled; the `com.github.gradle.node` plugin has `download=false` and reuses the system Node.

### Infrastructure (Docker)
- The Docker daemon is not started automatically. Start it once per VM with `sudo dockerd` (it's configured for `fuse-overlayfs` with the containerd snapshotter disabled in `/etc/docker/daemon.json`; the user is in the `docker` group). 
- Start backing services with `docker compose up -d postgres valkey redpanda` for the minimal backend stack, or `docker compose up -d` for the full local stack including Debezium and Elasticsearch.
- Postgres/Valkey/Redpanda must be up before the backend/worker start (Flyway migrates Postgres at boot; Redisson connects to Valkey at startup).

### Services
- Backend API (`workbench-web`, port 8080), worker (`workbench-worker`, no web server), and frontend (`workbench-frontend`, Vite port 5173). Run commands are in `README.md`. Prefix backend/worker Gradle runs with the JDK-25 `JAVA_HOME` if it is not already active.
- OpenAPI: `http://localhost:8080/v3/api-docs`; Scalar UI: `http://localhost:8080/scalar`; both plus `/actuator/health` are public (`permitAll`).
- The frontend's `pnpm openapi` (Orval) reads `http://localhost:8080/v3/api-docs`, so the backend must be running to regenerate the API client.

### Lint / test caveats
- Run `./gradlew check` for the JVM build, static analysis, and tests, and `./gradlew :workbench-frontend:pnpmDev|pnpmCoverage|pnpmE2e` for the frontend.
- JVM integration tests use Testcontainers, so the Docker daemon must be running.

### Dependency notes
- Redisson is pinned to `4.6.1` (3.x's `RedissonAutoConfigurationV2` is incompatible with Spring Boot 4's package relocation), and `workbench-web` includes `kotlinx-coroutines-reactor` at runtime (required for Spring MVC to invoke `suspend` handler functions). Both are needed for the apps to boot and serve requests on Spring Boot 4.
