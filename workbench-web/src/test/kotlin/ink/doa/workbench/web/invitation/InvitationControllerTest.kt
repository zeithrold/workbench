package ink.doa.workbench.web.invitation

import ink.doa.workbench.core.common.summary.TenantSummary
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.identity.model.InvitationType
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.security.identity.SessionService
import ink.doa.workbench.security.invitation.InvitationAcceptView
import ink.doa.workbench.security.invitation.InvitationPreviewView
import ink.doa.workbench.security.invitation.InvitationService
import ink.doa.workbench.web.api.GlobalExceptionHandler
import ink.doa.workbench.web.support.ContextWebMvcSupport
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(InvitationController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  ContextWebMvcSupport::class,
  ink.doa.workbench.web.support.ProjectWebMvcSupport::class,
  GlobalExceptionHandler::class,
  InvitationControllerTest.TestBeans::class,
)
class InvitationControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `preview invitation returns tenant summary`() {
    val result =
      mockMvc
        .perform(get("/api/invitations/preview").param("token", "invite-token"))
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.tenant.slug").value("acme"))
  }

  @Test
  fun `accept invitation returns created user`() {
    val result =
      mockMvc
        .perform(
          post("/api/invitations/accept")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "token": "invite-token",
                "displayName": "Acme Admin",
                "password": "SecurePass12345"
              }
              """
                .trimIndent()
            )
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.user.displayName").value("Acme Admin"))
  }

  @TestConfiguration
  class TestBeans {
    @Bean
    fun sessionAuthenticator(): ink.doa.workbench.core.identity.auth.SessionAuthenticator =
      object : ink.doa.workbench.core.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String) = null
      }

    @Bean
    fun bearerTokenAuthenticator(): ink.doa.workbench.core.identity.auth.BearerTokenAuthenticator =
      object : ink.doa.workbench.core.identity.auth.BearerTokenAuthenticator {
        override suspend fun authenticateBearerToken(token: String) = null
      }

    @Bean
    fun clock(): java.time.Clock =
      java.time.Clock.fixed(
        java.time.Instant.parse("2026-07-04T00:00:00Z"),
        java.time.ZoneOffset.UTC,
      )

    @Bean fun sessionService(): SessionService = mockk(relaxed = true)

    @Bean
    fun publicIdResolver(): ink.doa.workbench.security.common.PublicIdResolver =
      mockk(relaxed = true)

    @Bean fun invitationService(): InvitationService = mockk(relaxed = true)

    @Bean
    fun invitationServiceSetup(service: InvitationService): Boolean {
      val tenant =
        TenantSummary(
          id = "ten_01JABCDEFGHJKMNPQRSTVWXYZ0",
          slug = "acme",
          name = "Acme",
        )
      coEvery { service.preview("invite-token") } returns
        InvitationPreviewView(
          type = InvitationType.TENANT_ADMIN,
          tenant = tenant,
          email = "admin@acme.test",
          displayName = "Acme Admin",
        )
      coEvery { service.accept(any()) } returns
        InvitationAcceptView(
          type = InvitationType.TENANT_ADMIN,
          tenant = tenant,
          user =
            UserSummary(
              id = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
              displayName = "Acme Admin",
              primaryEmail = "admin@acme.test",
            ),
        )
      return true
    }
  }
}
