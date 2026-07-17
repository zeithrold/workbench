package one.ztd.workbench.web.messaging

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.application.identity.PublicIdResolver
import one.ztd.workbench.application.messaging.OutboxAdminApplicationService
import one.ztd.workbench.application.messaging.OutboxMessageRecord
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.identity.permission.model.AuthorizationDecision
import one.ztd.workbench.identity.permission.model.AuthorizationScope
import one.ztd.workbench.identity.permission.model.DecisionReason
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.security.SecurityConfiguration
import one.ztd.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import one.ztd.workbench.security.WorkbenchAuthenticationFilter
import one.ztd.workbench.tenant.tenant.TenantOperationalGuard
import one.ztd.workbench.web.api.GlobalExceptionHandler
import one.ztd.workbench.web.api.InfrastructureAspect
import one.ztd.workbench.web.api.InstanceRequestContextResolver
import one.ztd.workbench.web.api.RequestContextResolver
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(OutboxAdminController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  AopAutoConfiguration::class,
  InfrastructureAspect::class,
  RequestContextResolver::class,
  InstanceRequestContextResolver::class,
  one.ztd.workbench.web.support.ContextWebMvcSupport::class,
  one.ztd.workbench.web.support.ProjectWebMvcSupport::class,
  GlobalExceptionHandler::class,
  OutboxAdminControllerSecurityTest.TestBeans::class,
)
class OutboxAdminControllerSecurityTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `outbox admin endpoints reject unauthenticated requests`() {
    mockMvc.perform(get("/api/admin/outbox/messages")).andExpect(status().isUnauthorized())
  }

  @Test
  fun `outbox admin endpoints reject users without instance grants`() {
    val result =
      mockMvc
        .perform(
          get("/api/admin/outbox/messages")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, REGULAR_SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isForbidden())
  }

  @Test
  fun `instance administrator can list outbox messages`() {
    val result =
      mockMvc
        .perform(
          get("/api/admin/outbox/messages")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, ADMIN_SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk())
  }

  @TestConfiguration
  class TestBeans {
    @Bean
    fun sessionAuthenticator(): one.ztd.workbench.identity.auth.SessionAuthenticator =
      object : one.ztd.workbench.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String): AuthenticatedPrincipal? =
          when (sessionId) {
            ADMIN_SESSION -> ADMIN_PRINCIPAL
            REGULAR_SESSION -> REGULAR_PRINCIPAL
            else -> null
          }
      }

    @Bean
    fun bearerTokenAuthenticator(): one.ztd.workbench.identity.auth.BearerTokenAuthenticator =
      object : one.ztd.workbench.identity.auth.BearerTokenAuthenticator {
        override suspend fun authenticateBearerToken(token: String): AuthenticatedPrincipal? = null
      }

    @Bean
    fun permissionService(): one.ztd.workbench.identity.permission.model.PermissionService =
      object : one.ztd.workbench.identity.permission.model.PermissionService {
        override suspend fun decide(
          request: one.ztd.workbench.identity.permission.model.AuthorizationRequest
        ): AuthorizationDecision =
          if (
            request.scope == AuthorizationScope.INSTANCE && request.subject.userId == ADMIN_USER_ID
          ) {
            AuthorizationDecision.Allow(DecisionReason("grant_allowed", "allowed"))
          } else {
            AuthorizationDecision.Deny(DecisionReason("grant_denied", "denied"))
          }
      }

    @Bean
    fun clock(): java.time.Clock =
      java.time.Clock.fixed(java.time.Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC)

    @Bean fun tenantOperationalGuard(): TenantOperationalGuard = mockk(relaxed = true)

    @Bean fun sessionService(): SessionService = mockk(relaxed = true)

    @Bean
    fun publicIdResolver(): PublicIdResolver = mockk {
      coEvery { resolveUser(any()) } returns
        UserRecord(
          id = ADMIN_USER_ID,
          apiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
          displayName = "Admin",
          primaryEmail = "admin@example.test",
        )
    }

    @Bean
    fun outboxAdminService(): OutboxAdminApplicationService {
      val service = mockk<OutboxAdminApplicationService>()
      every { service.list(any()) } returns listOf(SAMPLE_MESSAGE)
      return service
    }
  }

  private companion object {
    const val ADMIN_SESSION = "admin-session"
    const val REGULAR_SESSION = "regular-session"
    val ADMIN_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val REGULAR_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000099")
    val SAMPLE_MESSAGE =
      OutboxMessageRecord(
        id = UUID.fromString("00000000-0000-0000-0000-000000000020"),
        eventId = "evt_1",
        eventType = "work_item.updated",
        topic = "workbench.work-item",
        partitionKey = "wki_1",
        tenantId = "ten_1",
        createdAt = OffsetDateTime.parse("2026-07-03T00:00:00Z"),
        retentionUntil = OffsetDateTime.parse("2026-08-02T00:00:00Z"),
      )
    val ADMIN_PRINCIPAL =
      AuthenticatedPrincipal(
        user =
          UserRecord(
            id = ADMIN_USER_ID,
            apiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
            displayName = "Admin",
            primaryEmail = "admin@example.test",
          ),
        loginAccountId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        sessionId = "session-id",
        bearerTokenId = null,
      )
    val REGULAR_PRINCIPAL =
      AuthenticatedPrincipal(
        user =
          UserRecord(
            id = REGULAR_USER_ID,
            apiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ0"),
            displayName = "User",
            primaryEmail = "user@example.test",
          ),
        loginAccountId = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        sessionId = "regular-session-id",
        bearerTokenId = null,
      )
  }
}
