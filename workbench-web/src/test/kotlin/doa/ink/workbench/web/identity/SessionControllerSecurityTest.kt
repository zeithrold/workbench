package doa.ink.workbench.web.identity

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.identity.TenantMemberRepository
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.auth.AuthSessionRepository
import doa.ink.workbench.core.identity.auth.BearerTokenAuthenticator
import doa.ink.workbench.core.identity.auth.SessionAuthenticator
import doa.ink.workbench.core.identity.model.AuthSessionRecord
import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.core.identity.model.CreateAuthSessionCommand
import doa.ink.workbench.core.identity.model.CreateTenantMemberCommand
import doa.ink.workbench.core.identity.model.TenantMemberRecord
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.UserRecord
import doa.ink.workbench.security.SecurityConfiguration
import doa.ink.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import doa.ink.workbench.security.WorkbenchAuthenticationFilter
import doa.ink.workbench.service.identity.SessionService
import jakarta.servlet.http.Cookie
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(SessionController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  SessionControllerSecurityTest.TestBeans::class,
)
class SessionControllerSecurityTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `authenticated suspend endpoints remain authorized on async dispatch`() {
    val result =
      mockMvc
        .perform(get("/api/session").cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, SESSION_SECRET)))
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.user.displayName").value("Ada Lovelace"))
  }

  @TestConfiguration
  class TestBeans {
    @Bean
    fun sessionAuthenticator(): SessionAuthenticator =
      object : SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String): AuthenticatedPrincipal? =
          if (sessionId == SESSION_SECRET) PRINCIPAL else null
      }

    @Bean
    fun bearerTokenAuthenticator(): BearerTokenAuthenticator =
      object : BearerTokenAuthenticator {
        override suspend fun authenticateBearerToken(token: String): AuthenticatedPrincipal? = null
      }

    @Bean fun clock(): Clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC)

    @Bean
    fun sessionService(clock: Clock): SessionService =
      SessionService(
        sessions = TestAuthSessions,
        tenantMembers = TestTenantMembers,
        tenants = TestTenants,
        clock = clock,
      )
  }

  private object TestAuthSessions : AuthSessionRepository {
    override suspend fun create(command: CreateAuthSessionCommand): AuthSessionRecord =
      error("Not used")

    override suspend fun findById(id: UUID): AuthSessionRecord? =
      if (id == SESSION_ID) {
        AuthSessionRecord(
          id = SESSION_ID,
          sessionHash = "hash",
          userId = USER_ID,
          loginAccountId = LOGIN_ACCOUNT_ID,
          activeTenantId = null,
          expiresAt = OffsetDateTime.parse("2026-07-03T00:00:00Z"),
          revokedAt = null,
          lastUsedAt = null,
          createdAt = OffsetDateTime.parse("2026-07-02T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-02T00:00:00Z"),
        )
      } else {
        null
      }

    override suspend fun findActiveByHash(
      sessionHash: String,
      now: OffsetDateTime,
    ): AuthSessionRecord? = null

    override suspend fun updateActiveTenant(
      id: UUID,
      activeTenantId: UUID?,
      updatedAt: OffsetDateTime,
    ): Boolean = false

    override suspend fun revoke(id: UUID, revokedAt: OffsetDateTime): Boolean = false

    override suspend fun touch(id: UUID, usedAt: OffsetDateTime): Boolean = false
  }

  private object TestTenantMembers : TenantMemberRepository {
    override suspend fun create(command: CreateTenantMemberCommand): TenantMemberRecord =
      error("Not used")

    override suspend fun findByTenantAndUser(tenantId: UUID, userId: UUID): TenantMemberRecord? =
      null

    override suspend fun listByUser(userId: UUID): List<TenantMemberRecord> = emptyList()
  }

  private object TestTenants : TenantRepository {
    override suspend fun findById(id: UUID): TenantRecord? = null

    override suspend fun findByApiId(apiId: String): TenantRecord? = null

    override suspend fun findByIds(ids: Collection<UUID>): List<TenantRecord> = emptyList()
  }

  private companion object {
    val USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val LOGIN_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val SESSION_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
    const val SESSION_SECRET = "session-secret"
    val PRINCIPAL =
      AuthenticatedPrincipal(
        user =
          UserRecord(
            id = USER_ID,
            apiId = PublicId.new("usr"),
            displayName = "Ada Lovelace",
            primaryEmail = "ada@example.test",
          ),
        loginAccountId = LOGIN_ACCOUNT_ID,
        sessionId = SESSION_ID.toString(),
        bearerTokenId = null,
      )
  }
}
