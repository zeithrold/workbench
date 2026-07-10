# Test Governance

Workbench uses four complementary test types. Each test should cover one boundary and use the matching framework, tag, and name.

| Type | Purpose | Framework | Name and tag |
|---|---|---|---|
| Domain/service unit | Business rules and pure transformations, with Fakes, Recording ports, or MockK | Kotest + MockK | `*Test` without a tag |
| Web MVC slice | Routes, JSON, authorization, filters, and exception mapping | JUnit 5 + MockMvc + `@WebMvcTest` | `*ControllerTest` without a tag |
| Non-Spring integration | Real adapters, database semantics, migrations, Kafka/S3/Keycloak interactions | Kotest + Testcontainers | `*IntegrationTest` + `@Tags("integration")` |
| Spring Boot integration | Spring wiring and configuration with real infrastructure | JUnit 5 + `@SpringBootTest` | `*IntegrationTest` + `@Tag("integration")` |

## Boundaries

- Unit tests must not start a full Spring context. `@WebMvcTest` is an HTTP slice unit test, not an integration test.
- Direct controller tests use `*ControllerUnitTest` and may only verify request/context conversion and service delegation. Keep HTTP status, serialization, security, filters, and exception mapping in MVC slice tests.
- `@SpringBootTest` is always an integration test. Tests using MockMvc must use `WebEnvironment.MOCK`; reserve `RANDOM_PORT` for tests that issue real HTTP requests.
- Do not duplicate the same business decision matrix in unit and integration tests. Use integration tests only for behavior a Fake cannot prove.
- Reuse stable cross-cutting fixtures from `workbench-test-support` or module `testFixtures`; keep domain-specific test data local to the test class.

## Enforcement

`./gradlew workbenchTestArchitectureCheck` validates test source conventions across all backend modules and `workbench-test-support`. It is included in both `workbenchQuickCheck` and `workbenchCiCheck`.

The check rejects integration tags without an `*IntegrationTest` suffix, untagged `*IntegrationTest` files, retired `*DirectTest` names, Spring tests using Kotest Specs, untagged `@SpringBootTest` classes, tagged `@WebMvcTest` slices, and MockMvc tests configured with `RANDOM_PORT`.
