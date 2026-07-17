package one.ztd.workbench.web.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import io.mockk.mockk
import java.time.Duration
import one.ztd.workbench.data.storage.blob.InMemoryBlobStorage
import one.ztd.workbench.kernel.port.locking.DistributedLockService
import one.ztd.workbench.kernel.storage.BlobStorage
import one.ztd.workbench.testsupport.postgres.MigrationSpec
import one.ztd.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import one.ztd.workbench.testsupport.postgres.registerWorkbenchDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.MOCK,
  properties =
    [
      "spring.profiles.active=integration,web",
      "management.health.redis.enabled=false",
    ],
)
@AutoConfigureMockMvc
@Import(InfrastructureEndpointIntegrationTest.TestBeans::class)
class InfrastructureEndpointIntegrationTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `infrastructure endpoints are served under api prefix without authentication`() {
    mockMvc.perform(get("/api/actuator/health")).andExpect(status().isOk())
    mockMvc.perform(get("/api/openapi")).andExpect(status().isOk())
    mockMvc.perform(get("/api/scalar")).andExpect(status().is2xxSuccessful())
  }

  @Test
  fun `legacy infrastructure paths are not publicly exposed`() {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized())
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized())
    mockMvc.perform(get("/scalar")).andExpect(status().isUnauthorized())
  }

  @Test
  fun `openapi documents every path template parameter`() {
    val response =
      mockMvc.perform(get("/api/openapi")).andExpect(status().isOk()).andReturn().response
    val document = JsonMapper.builder().build().readTree(response.contentAsByteArray)

    document.path("paths").properties().forEach { (path, pathItem) ->
      val templateParameters = PathParameterPattern.findAll(path).map { it.groupValues[1] }.toSet()
      pathItem
        .properties()
        .filter { it.key in HttpMethods }
        .forEach { (method, operation) ->
          val documentedParameters =
            (pathItem.path("parameters").toList() + operation.path("parameters").toList())
              .filter { it.path("in").asText() == "path" && it.path("required").asBoolean() }
              .map { it.path("name").asText() }
              .toSet()
          assertTrue(
            documentedParameters.containsAll(templateParameters),
            "$method $path is missing required path parameters: " +
              (templateParameters - documentedParameters).joinToString(),
          )
        }
    }

    assertEquals(
      setOf("id", "sprintId"),
      requiredPathParameters(document, "/api/projects/{id}/sprints/{sprintId}", "get"),
    )
    assertEquals(
      setOf("id", "userId", "bindingId"),
      requiredPathParameters(
        document,
        "/api/projects/{id}/members/{userId}/policies/{bindingId}",
        "delete",
      ),
    )
  }

  @Test
  fun `openapi exposes navigation without capability contracts`() {
    val response =
      mockMvc.perform(get("/api/openapi")).andExpect(status().isOk()).andReturn().response
    val document = JsonMapper.builder().build().readTree(response.contentAsByteArray)
    val paths = document.path("paths")
    val schemas = document.path("components").path("schemas")

    assertTrue(paths.has("/api/session/navigation"))
    assertFalse(paths.has("/api/admin/capabilities"))
    assertFalse(paths.has("/api/manage/capabilities"))
    assertFalse(paths.has("/api/projects/capabilities"))
    assertTrue(schemas.has("ManagementNavigationResponse"))
    assertFalse(schemas.properties().asSequence().any { (name) -> name.contains("Capability") })
  }

  @TestConfiguration
  class TestBeans {
    @Bean
    @Primary
    fun noOpDistributedLockService(): DistributedLockService =
      object : DistributedLockService {
        override fun <T> withLock(
          name: String,
          wait: Duration,
          lease: Duration,
          block: () -> T,
        ): T = block()
      }

    @Bean @Primary fun redissonClient(): RedissonClient = mockk(relaxed = true)

    @Bean @Primary fun testBlobStorage(): BlobStorage = InMemoryBlobStorage()
  }

  companion object {
    private val PathParameterPattern = Regex("\\{([^{}]+)}")
    private val HttpMethods =
      setOf("get", "put", "post", "delete", "options", "head", "patch", "trace")
    private val postgresLease = WorkbenchPostgresTestSupport.moduleDatabase(MigrationSpec.Full)

    @JvmStatic
    @DynamicPropertySource
    fun registerProperties(registry: DynamicPropertyRegistry) {
      registry.registerWorkbenchDataSource(postgresLease)
    }
  }
}

private fun requiredPathParameters(
  document: JsonNode,
  path: String,
  method: String,
): Set<String> {
  val pathItem = document.path("paths").path(path)
  return (pathItem.path("parameters").toList() + pathItem.path(method).path("parameters").toList())
    .filter { it.path("in").asText() == "path" && it.path("required").asBoolean() }
    .map { it.path("name").asText() }
    .toSet()
}
