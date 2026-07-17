package one.ztd.workbench.web.support

import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.agile.project.model.ProjectRecord
import one.ztd.workbench.agile.project.model.ProjectStatus
import one.ztd.workbench.application.identity.PublicIdResolver
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.identity.permission.model.AuthorizationDecision
import one.ztd.workbench.identity.permission.model.DecisionReason
import one.ztd.workbench.identity.permission.model.PermissionService
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.tenant.TenantOperationalGuard
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

object TenantWebMvcFixtures {
  const val SESSION = "tenant-session"
  val TENANT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000010")
  val USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
  val TENANT_RECORD =
    TenantRecord(
      id = TENANT_ID,
      apiId = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
      slug = "acme",
      name = "Acme",
      timezone = "UTC",
      locale = "en-US",
      createdAt = OffsetDateTime.parse("2026-07-03T00:00:00Z"),
      updatedAt = OffsetDateTime.parse("2026-07-03T00:00:00Z"),
    )
  val PRINCIPAL =
    AuthenticatedPrincipal(
      user =
        UserRecord(
          id = USER_ID,
          apiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
          displayName = "Ada",
          primaryEmail = "ada@example.test",
        ),
      loginAccountId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
      sessionId = "session-id",
      bearerTokenId = null,
      tenantId = TENANT_ID,
    )
  const val PROJECT_PUBLIC_ID = "prj_01JABCDEFGHJKMNPQRSTVWXYZ0"
  val PROJECT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000020")
  val PROJECT_RECORD =
    ProjectRecord(
      id = PROJECT_ID,
      apiId = PublicId(PROJECT_PUBLIC_ID),
      tenantId = TENANT_ID,
      identifier = "CORE",
      name = "Core Platform",
      description = "Platform engineering",
      status = ProjectStatus.ACTIVE,
      leadUserId = USER_ID,
      createdBy = USER_ID,
    )
}

@TestConfiguration
class TenantScopedWebMvcSupport {
  @Bean
  fun tenantOperationalGuard(): TenantOperationalGuard = mockk {
    coEvery { ensureOperational(any()) } returns Unit
  }

  @Bean fun publicIdResolver(): PublicIdResolver = mockk(relaxed = true)

  @Bean
  fun permissionService(): PermissionService =
    object : PermissionService {
      override suspend fun decide(
        request: one.ztd.workbench.identity.permission.model.AuthorizationRequest
      ): AuthorizationDecision =
        AuthorizationDecision.Allow(DecisionReason("grant_allowed", "allowed"))
    }

  @Bean
  fun tenantSessionService(): SessionService = mockk {
    coEvery { requireActiveTenant(any()) } returns TenantWebMvcFixtures.TENANT_RECORD
    coEvery { requireActiveTenantId(any()) } returns TenantWebMvcFixtures.TENANT_ID
  }

  @Bean
  fun clock(): java.time.Clock =
    java.time.Clock.fixed(java.time.Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
}
