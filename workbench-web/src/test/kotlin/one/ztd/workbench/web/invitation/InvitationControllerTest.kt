package one.ztd.workbench.web.invitation

import io.mockk.coEvery
import io.mockk.mockk
import one.ztd.workbench.application.invitation.InvitationAcceptView
import one.ztd.workbench.application.invitation.InvitationAcceptanceApplicationService
import one.ztd.workbench.application.invitation.InvitationAcceptanceWithSession
import one.ztd.workbench.application.invitation.InvitationPreviewView
import one.ztd.workbench.application.invitation.InvitationService
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.identity.model.InvitationType
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.security.SecurityConfiguration
import one.ztd.workbench.security.WorkbenchAuthenticationFilter
import one.ztd.workbench.tenant.common.summary.TenantSummary
import one.ztd.workbench.web.api.GlobalExceptionHandler
import one.ztd.workbench.web.support.ContextWebMvcSupport
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(InvitationController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  ContextWebMvcSupport::class,
  one.ztd.workbench.web.support.ProjectWebMvcSupport::class,
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
      .andExpect(
        header()
          .string(
            "Set-Cookie",
            org.hamcrest.Matchers.containsString("WORKBENCH_SESSION=session-secret"),
          )
      )
  }

  @TestConfiguration
  class TestBeans {
    @Bean
    fun sessionAuthenticator(): one.ztd.workbench.identity.auth.SessionAuthenticator =
      object : one.ztd.workbench.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String) = null
      }

    @Bean
    fun bearerTokenAuthenticator(): one.ztd.workbench.identity.auth.BearerTokenAuthenticator =
      object : one.ztd.workbench.identity.auth.BearerTokenAuthenticator {
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
    fun publicIdResolver(): one.ztd.workbench.application.identity.PublicIdResolver =
      mockk(relaxed = true)

    @Bean fun invitationService(): InvitationService = mockk(relaxed = true)

    @Bean
    fun invitationAcceptanceApplicationService(): InvitationAcceptanceApplicationService =
      mockk(relaxed = true)

    @Bean
    fun sessionCookieWriter(clock: java.time.Clock) =
      one.ztd.workbench.web.api.http.SessionCookieWriter(clock, secure = false)

    @Bean
    fun invitationServiceSetup(
      service: InvitationService,
      acceptance: InvitationAcceptanceApplicationService,
    ): Boolean {
      val tenant =
        TenantSummary(
          id = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
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
      val accepted =
        InvitationAcceptView(
          type = InvitationType.TENANT_ADMIN,
          tenant = tenant,
          user =
            UserSummary(
              id = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
              displayName = "Acme Admin",
              primaryEmail = "admin@acme.test",
            ),
        )
      coEvery { acceptance.acceptNew(any(), any()) } returns
        InvitationAcceptanceWithSession(
          acceptance = accepted,
          sessionSecret = "session-secret",
          sessionExpiresAt = java.time.OffsetDateTime.parse("2026-07-05T00:00:00Z"),
        )
      return true
    }
  }
}
