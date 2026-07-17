package one.ztd.workbench.web.identity

import io.mockk.coEvery
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.agile.project.ProjectRepository
import one.ztd.workbench.identity.LoginCompletionService
import one.ztd.workbench.identity.LoginMethodRepository
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.TenantMemberRepository
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.auth.AuthSessionRepository
import one.ztd.workbench.identity.auth.BearerTokenAuthenticator
import one.ztd.workbench.identity.auth.BearerTokenRepository
import one.ztd.workbench.identity.auth.SessionAuthenticator
import one.ztd.workbench.identity.common.IdentityPublicIdResolver
import one.ztd.workbench.identity.model.AuthSessionRecord
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.CreateAuthSessionCommand
import one.ztd.workbench.identity.model.CreateTenantMemberCommand
import one.ztd.workbench.identity.model.TenantMemberRecord
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.identity.permission.AccessGrantRepository
import one.ztd.workbench.identity.permission.AdminUserQueryRepository
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.security.SecurityConfiguration
import one.ztd.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import one.ztd.workbench.security.WorkbenchAuthenticationFilter
import one.ztd.workbench.tenant.TenantRepository
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.tenant.TenantOperationalGuard
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
  one.ztd.workbench.web.support.ContextWebMvcSupport::class,
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
    fun publicIdResolver(): IdentityPublicIdResolver =
      IdentityPublicIdResolver(TestTenants, UnusedBearerTokenRepository)

    @Bean
    fun loginCompletionService(): LoginCompletionService =
      mockk<LoginCompletionService>(relaxed = true).also {
        coEvery { it.adminScopes(any()) } returns emptyList()
      }

    @Bean
    fun tenantOperationalGuard(): TenantOperationalGuard =
      TenantOperationalGuard(tenants = TestTenants)

    @Bean
    fun projectOperationalGuard(): one.ztd.workbench.agile.project.ProjectOperationalGuard =
      one.ztd.workbench.agile.project.ProjectOperationalGuard(UnusedProjectRepository)

    @Bean
    fun sessionService(
      clock: Clock,
      publicIdResolver: IdentityPublicIdResolver,
      loginCompletionService: LoginCompletionService,
    ): SessionService =
      SessionService(
        sessions = TestAuthSessions,
        tenantMembers = TestTenantMembers,
        tenants = TestTenants,
        publicIds = publicIdResolver,
        loginCompletionService = loginCompletionService,
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

    override suspend fun revokeByActiveTenant(tenantId: UUID, revokedAt: OffsetDateTime): Int = 0

    override suspend fun touch(id: UUID, usedAt: OffsetDateTime): Boolean = false
  }

  private object TestTenantMembers : TenantMemberRepository {
    override suspend fun create(command: CreateTenantMemberCommand): TenantMemberRecord =
      error("Not used")

    override suspend fun findByTenantAndUser(tenantId: UUID, userId: UUID): TenantMemberRecord? =
      null

    override suspend fun findByApiId(tenantId: UUID, apiId: String): TenantMemberRecord? = null

    override suspend fun listByTenant(tenantId: UUID): List<TenantMemberRecord> = emptyList()

    override suspend fun listByUser(userId: UUID): List<TenantMemberRecord> = emptyList()

    override suspend fun updateStatus(
      id: UUID,
      status: one.ztd.workbench.identity.model.TenantMemberStatus,
      updatedAt: OffsetDateTime,
    ): TenantMemberRecord? = null
  }

  private object TestTenants : TenantRepository {
    override suspend fun create(command: one.ztd.workbench.tenant.model.CreateTenantCommand) =
      error("unused")

    override suspend fun update(command: one.ztd.workbench.tenant.model.UpdateTenantCommand) =
      error("unused")

    override suspend fun markDestroying(tenantId: UUID) = error("unused")

    override suspend fun requestDestroy(
      tenantId: UUID,
      tenantApiId: String,
      payload: one.ztd.workbench.tenant.tenant.events.TenantDestroyRequestedEvent,
    ) = error("unused")

    override suspend fun finalizeDestroy(
      command: one.ztd.workbench.tenant.model.FinalizeTenantDestroyCommand
    ) = false

    override suspend fun findById(id: UUID): TenantRecord? = null

    override suspend fun findByIdForDestruction(id: UUID): TenantRecord? = null

    override suspend fun findByApiId(apiId: String): TenantRecord? = null

    override suspend fun findByApiIdForAdmin(apiId: String): TenantRecord? = null

    override suspend fun findBySlug(slug: String): TenantRecord? = null

    override suspend fun existsBySlug(slug: String): Boolean = false

    override suspend fun findByIds(ids: Collection<UUID>): List<TenantRecord> = emptyList()

    override suspend fun list(slug: String?): List<TenantRecord> = emptyList()

    override suspend fun listForAdmin(slug: String?): List<TenantRecord> = emptyList()
  }

  private object UnusedUserRepository : UserRepository {
    override suspend fun create(command: one.ztd.workbench.identity.model.CreateUserCommand) =
      error("unused")

    override suspend fun findById(id: UUID) = null

    override suspend fun findByApiId(apiId: String) = null

    override suspend fun findByPrimaryEmail(primaryEmail: String) = null
  }

  private object UnusedLoginMethodRepository : LoginMethodRepository {
    override suspend fun createLoginMethod(
      command: one.ztd.workbench.identity.model.CreateLoginMethodDefinitionCommand
    ) = error("unused")

    override suspend fun findLoginMethodByCode(code: String) = null

    override suspend fun findLoginMethodByApiId(apiId: String) = null

    override suspend fun findLoginMethodById(id: UUID) = null
  }

  private object UnusedBearerTokenRepository : BearerTokenRepository {
    override suspend fun create(
      command: one.ztd.workbench.identity.model.CreateBearerTokenCommand
    ) = error("unused")

    override suspend fun findById(id: UUID) = null

    override suspend fun findByApiId(apiId: String) = null

    override suspend fun findActiveByHash(tokenHash: String, now: OffsetDateTime) = null

    override suspend fun revoke(id: UUID, revokedAt: OffsetDateTime) = false

    override suspend fun revokeByTenant(tenantId: UUID, revokedAt: OffsetDateTime) = 0

    override suspend fun touch(id: UUID, usedAt: OffsetDateTime) = false
  }

  private object UnusedAdminUserQueryRepository : AdminUserQueryRepository {
    override suspend fun findById(id: UUID) = null

    override suspend fun findByApiId(apiId: String) = null

    override suspend fun findActiveInstanceAdmin(userId: UUID, at: OffsetDateTime) = null

    override suspend fun findActiveTenantAdmin(tenantId: UUID, userId: UUID, at: OffsetDateTime) =
      null

    override suspend fun existsActiveInstanceAdmin() = false

    override suspend fun isActiveInstanceAdmin(userId: UUID, at: OffsetDateTime) = false

    override suspend fun isActiveTenantAdmin(tenantId: UUID, userId: UUID, at: OffsetDateTime) =
      false

    override suspend fun listByUser(userId: UUID) =
      emptyList<one.ztd.workbench.identity.permission.AdminUserRecord>()

    override suspend fun listInstanceAdmins() =
      emptyList<one.ztd.workbench.identity.permission.AdminUserRecord>()

    override suspend fun listTenantAdmins(tenantId: UUID) =
      emptyList<one.ztd.workbench.identity.permission.AdminUserRecord>()
  }

  private object UnusedAccessGrantRepository : AccessGrantRepository {
    override suspend fun create(
      command: one.ztd.workbench.identity.permission.CreateAccessGrantCommand
    ) = error("unused")

    override suspend fun findById(id: UUID) = null

    override suspend fun findByApiId(apiId: String) = null

    override suspend fun listBySubject(
      subjectUserId: UUID,
      scope: one.ztd.workbench.identity.permission.GrantScope?,
      tenantId: UUID?,
      projectId: UUID?,
    ) = emptyList<one.ztd.workbench.identity.permission.AccessGrantRecord>()

    override suspend fun listActiveForSubject(
      subjectUserId: UUID,
      scope: one.ztd.workbench.identity.permission.GrantScope,
      tenantId: UUID?,
      projectId: UUID?,
      at: OffsetDateTime,
    ) = emptyList<one.ztd.workbench.identity.permission.AccessGrantRecord>()

    override suspend fun listByTenant(tenantId: UUID) =
      emptyList<one.ztd.workbench.identity.permission.AccessGrantRecord>()

    override suspend fun listInstanceGrants() =
      emptyList<one.ztd.workbench.identity.permission.AccessGrantRecord>()

    override suspend fun expire(id: UUID, validTo: OffsetDateTime) = false

    override suspend fun expireByTenant(tenantId: UUID, expiredAt: OffsetDateTime) = 0
  }

  private object UnusedProjectRepository : ProjectRepository {
    override suspend fun create(
      command: one.ztd.workbench.agile.project.model.CreateProjectCommand
    ) = error("unused")

    override suspend fun findByApiId(tenantId: UUID, apiId: String) = null

    override suspend fun findById(tenantId: UUID, id: UUID) = null

    override suspend fun list(tenantId: UUID, identifier: String?) =
      emptyList<one.ztd.workbench.agile.project.model.ProjectRecord>()

    override suspend fun update(
      command: one.ztd.workbench.agile.project.model.UpdateProjectCommand
    ) = error("unused")

    override suspend fun markArchived(
      tenantId: UUID,
      projectId: UUID,
      archivedAt: OffsetDateTime,
      archivedBy: UUID,
    ) = error("unused")

    override suspend fun markActive(tenantId: UUID, projectId: UUID) = error("unused")

    override suspend fun markDestroying(
      tenantId: UUID,
      projectId: UUID,
      deletedBy: UUID,
      deleteReason: String?,
    ) = error("unused")

    override suspend fun requestDestroy(
      request: one.ztd.workbench.agile.project.ProjectDestroyRequest
    ) = error("unused")

    override suspend fun finalizeDestroy(
      tenantId: UUID,
      projectId: UUID,
      deletedAt: OffsetDateTime,
      deletedBy: UUID,
      deleteReason: String?,
    ) = false

    override suspend fun updateStatus(
      tenantId: UUID,
      projectId: UUID,
      status: one.ztd.workbench.agile.project.model.ProjectStatus,
    ) = false
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
