package ink.doa.workbench.web.identity

import ink.doa.workbench.core.identity.model.LoginMethodKind
import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.security.identity.auth.support.AuthIntegrationFixtures
import ink.doa.workbench.security.identity.auth.support.InMemoryLdapTestServer
import ink.doa.workbench.security.identity.auth.support.LdapAuthFixture
import ink.doa.workbench.web.identity.support.AuthIntegrationContainers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = ["spring.profiles.active=integration"],
)
@AutoConfigureMockMvc
@Import(AuthIntegrationContainers.AuthIntegrationTestConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class AuthLdapIntegrationTest(
  @Autowired private val mockMvc: MockMvc,
  @Autowired private val objectMapper: ObjectMapper,
  @Autowired private val database: Database,
) {
  private lateinit var fixture: LdapAuthFixture

  @BeforeAll
  fun seedFixture() {
    fixture = runBlocking {
      AuthIntegrationFixtures.seedLdapFixture(database, AuthIntegrationContainers.ldap)
    }
  }

  @Test
  fun `ldap login returns session cookie`() {
    val body =
      mapOf(
        "method" to LoginMethodKind.LDAP.name,
        "loginMethodId" to fixture.loginMethodApiId,
        "tenantId" to fixture.tenant.tenantApiId,
        "subject" to InMemoryLdapTestServer.TEST_USER,
        "password" to InMemoryLdapTestServer.TEST_PASSWORD,
      )
    val started =
      mockMvc
        .perform(
          post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(started))
      .andExpect(status().isOk())
      .andExpect(cookie().exists(WORKBENCH_SESSION_COOKIE_NAME))
  }

  companion object {
    @JvmStatic
    @DynamicPropertySource
    fun registerProperties(registry: DynamicPropertyRegistry) {
      AuthIntegrationContainers.registerDataSourceProperties(registry)
    }
  }
}
