package doa.ink.workbench.web.identity

import doa.ink.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import doa.ink.workbench.security.identity.auth.support.AuthIntegrationFixtures
import doa.ink.workbench.security.identity.auth.support.FederatedAuthFixture
import doa.ink.workbench.security.identity.auth.support.KeycloakTestContainer
import doa.ink.workbench.security.identity.auth.support.OAuthAuthorizationCodeClient
import doa.ink.workbench.web.identity.support.AuthIntegrationContainers
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = ["spring.profiles.active=integration"],
)
@AutoConfigureMockMvc
@Import(AuthIntegrationContainers.AuthIntegrationTestConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class AuthOAuth2IntegrationTest(
  @Autowired private val mockMvc: MockMvc,
  @Autowired private val objectMapper: ObjectMapper,
  @Autowired private val database: Database,
) {
  private lateinit var fixture: FederatedAuthFixture

  @BeforeAll
  fun seedFixture() {
    fixture = runBlocking {
      AuthIntegrationFixtures.seedFederatedFixture(database, AuthIntegrationContainers.keycloak)
    }
  }

  @Test
  fun `oauth2 federated login completes with session cookie`() {
    val authorizeBody =
      mapOf(
        "loginMethodId" to fixture.oauth2LoginMethodApiId,
        "tenantId" to fixture.tenant.tenantApiId,
        "returnUrl" to "/",
        "redirectUri" to KeycloakTestContainer.REDIRECT_URI,
      )
    val authorizeStarted =
      mockMvc
        .perform(
          post("/api/auth/federated/authorize")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(authorizeBody))
        )
        .andExpect(request().asyncStarted())
        .andReturn()
    val authorizeResponse =
      mockMvc.perform(asyncDispatch(authorizeStarted)).andExpect(status().isOk()).andReturn()
    val authorizeJson: JsonNode = objectMapper.readTree(authorizeResponse.response.contentAsString)
    val authorizationUrl = authorizeJson.get("authorizationUrl").asText()
    val state = authorizeJson.get("state").asText()
    val code =
      OAuthAuthorizationCodeClient.obtainAuthorizationCode(
        authorizationUrl = authorizationUrl,
        username = KeycloakTestContainer.OAUTH2_USER,
        password = KeycloakTestContainer.OAUTH2_PASSWORD,
        redirectUri = KeycloakTestContainer.REDIRECT_URI,
      )

    val callbackStarted =
      mockMvc
        .perform(get("/api/auth/oauth2/callback").param("code", code).param("state", state))
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(callbackStarted))
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
