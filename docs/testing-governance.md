# Test Governance

## Principles

- Prioritize test correctness over speed. If writing a test exposes unreasonable code or architecture (e.g., untestable coupling, missing ports, logic in the wrong layer), stop and propose design changes before continuing with workaround tests.

Workbench uses four complementary test types. Each test should cover one boundary and use the matching framework, tag, and name.

| Type | Purpose | Framework | Name and tag |
|---|---|---|---|
| Domain/service unit | Business rules and pure transformations, with Fakes, Recording ports, or MockK | Kotest + MockK | `*Test` without a tag |
| Web MVC slice | Routes, JSON, authorization, filters, and exception mapping | JUnit 5 + MockMvc + `@WebMvcTest` | `*ControllerTest` without a tag |
| Non-Spring integration | Real adapters, database semantics, migrations, Kafka/S3/Keycloak interactions | Kotest + Testcontainers | `src/integrationTest` + `*IntegrationTest` |
| Spring Boot integration | Spring wiring and configuration with real infrastructure | JUnit 5 + `@SpringBootTest` | `src/integrationTest` + `*IntegrationTest` |

## Boundaries

- Unit tests must not start a full Spring context. `@WebMvcTest` is an HTTP slice unit test, not an integration test.
- Direct controller tests use `*ControllerUnitTest` and may only verify request/context conversion and service delegation. Keep HTTP status, serialization, security, filters, and exception mapping in MVC slice tests.
- `@SpringBootTest` is always an integration test. Tests using MockMvc must use `WebEnvironment.MOCK`; reserve `RANDOM_PORT` for tests that issue real HTTP requests.
- Do not duplicate the same business decision matrix in unit and integration tests. Use integration tests only for behavior a Fake cannot prove.
- Reuse stable cross-cutting fixtures from `workbench-test-support` or module `testFixtures`; keep domain-specific test data local to the test class.

## Enforcement

`./gradlew testArchitectureCheck` validates both `src/test` and `src/integrationTest` across all backend modules and `workbench-test-support`. It is included in both `quickCheck` and `check`.

The check rejects retired integration tags, `*IntegrationTest` files outside `src/integrationTest`, ordinary test classes inside the integration source set without the required suffix, `@SpringBootTest` outside the integration source set, `@WebMvcTest` inside it, retired `*DirectTest` names, Spring tests using Kotest Specs, and MockMvc tests configured with `RANDOM_PORT`.
