package ink.doa.workbench.security.common

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.LoginMethodRepository
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.auth.BearerTokenRepository
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.permission.AccessGrantRepository
import ink.doa.workbench.core.permission.AdminUserQueryRepository
import ink.doa.workbench.core.project.ProjectRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class PublicIdResolverTest :
  StringSpec({
    val tenants = mockk<TenantRepository>()
    val users = mockk<UserRepository>()
    val loginMethods = mockk<LoginMethodRepository>()
    val bearerTokens = mockk<BearerTokenRepository>()
    val adminUsers = mockk<AdminUserQueryRepository>()
    val accessGrants = mockk<AccessGrantRepository>()
    val projects = mockk<ProjectRepository>()
    val resolver =
      PublicIdResolver(
        PublicIdIdentitySupport(tenants, users, loginMethods, bearerTokens),
        PublicIdPermissionSupport(adminUsers, accessGrants),
        projects,
      )
    val now = OffsetDateTime.now(ZoneOffset.UTC)

    "resolveTenant returns tenant record" {
      val tenant =
        TenantRecord(
          id = UUID.randomUUID(),
          apiId = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          slug = "acme",
          name = "Acme",
          timezone = "UTC",
          locale = "en-US",
          createdAt = now,
          updatedAt = now,
        )
      coEvery { tenants.findByApiId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0") } returns tenant

      resolver.resolveTenant("ten_01JABCDEFGHJKMNPQRSTVWXYZ0").slug shouldBe "acme"
    }

    "resolveTenant throws when tenant is missing" {
      coEvery { tenants.findByApiId("ten_missing") } returns null

      shouldThrow<ResourceNotFoundException> { resolver.resolveTenant("ten_missing") }
    }

    "resolveUser returns user record" {
      val user =
        UserRecord(
          id = UUID.randomUUID(),
          apiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
          displayName = "Ada",
          primaryEmail = "ada@example.test",
        )
      coEvery { users.findByApiId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1") } returns user

      resolver.resolveUser("usr_01JABCDEFGHJKMNPQRSTVWXYZ1").displayName shouldBe "Ada"
    }

    "resolveUser throws when user is missing" {
      coEvery { users.findByApiId("usr_missing") } returns null

      shouldThrow<ResourceNotFoundException> { resolver.resolveUser("usr_missing") }
    }

    "resolveProject returns project for tenant" {
      val projectId = UUID.randomUUID()
      val project =
        ink.doa.workbench.core.project.model.ProjectRecord(
          id = projectId,
          apiId = PublicId.new("prj"),
          tenantId = UUID.randomUUID(),
          identifier = "CORE",
          name = "Core",
          description = null,
          leadUserId = UUID.randomUUID(),
          createdBy = UUID.randomUUID(),
        )
      coEvery { projects.findByApiId(project.tenantId, project.apiId.value) } returns project

      resolver.resolveProject(project.tenantId, project.apiId.value).identifier shouldBe "CORE"
    }

    "resolveTenantForAdmin returns tenant record" {
      val tenant =
        TenantRecord(
          id = UUID.randomUUID(),
          apiId = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ2"),
          slug = "admin",
          name = "Admin",
          timezone = "UTC",
          locale = "en-US",
          createdAt = now,
          updatedAt = now,
        )
      coEvery { tenants.findByApiIdForAdmin("ten_01JABCDEFGHJKMNPQRSTVWXYZ2") } returns tenant

      resolver.resolveTenantForAdmin("ten_01JABCDEFGHJKMNPQRSTVWXYZ2").slug shouldBe "admin"
    }

    "resolveLoginMethod returns login method record" {
      val method =
        ink.doa.workbench.core.identity.model.LoginMethodDefinitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId("lmd_01JABCDEFGHJKMNPQRSTVWXYZ3"),
          code = "password",
          kind = ink.doa.workbench.core.identity.model.LoginMethodKind.PASSWORD,
          name = "Password",
          isBuiltin = true,
          isEnabledGlobally = true,
          createdAt = now,
          updatedAt = now,
        )
      coEvery { loginMethods.findLoginMethodByApiId("lmd_01JABCDEFGHJKMNPQRSTVWXYZ3") } returns
        method

      resolver.resolveLoginMethod("lmd_01JABCDEFGHJKMNPQRSTVWXYZ3").code shouldBe "password"
    }

    "resolveBearerToken returns token record" {
      val token =
        ink.doa.workbench.core.identity.model.BearerTokenRecord(
          id = UUID.randomUUID(),
          apiId = PublicId("btk_01JABCDEFGHJKMNPQRSTVWXYZ4"),
          tokenHash = "hash",
          userId = UUID.randomUUID(),
          loginAccountId = UUID.randomUUID(),
          tenantId = null,
          name = null,
          scopes = emptySet(),
          createdBy = null,
          expiresAt = now.plusDays(1),
          revokedAt = null,
          lastUsedAt = null,
          createdAt = now,
          updatedAt = now,
        )
      coEvery { bearerTokens.findByApiId("btk_01JABCDEFGHJKMNPQRSTVWXYZ4") } returns token

      resolver.resolveBearerToken("btk_01JABCDEFGHJKMNPQRSTVWXYZ4").tokenHash shouldBe "hash"
    }

    "resolveAdminUser and resolveAccessGrant return records" {
      val adminUser =
        ink.doa.workbench.core.permission.AdminUserRecord(
          id = UUID.randomUUID(),
          apiId = PublicId("adm_01JABCDEFGHJKMNPQRSTVWXYZ5"),
          userId = UUID.randomUUID(),
          scope = ink.doa.workbench.core.permission.AdminScope.TENANT,
          tenantId = UUID.randomUUID(),
          status = ink.doa.workbench.core.permission.AdminUserStatus.ACTIVE,
          grantedBy = UUID.randomUUID(),
          validFrom = now,
          validTo = null,
          createdAt = now,
          updatedAt = now,
        )
      val grant =
        ink.doa.workbench.core.permission.AccessGrantRecord(
          id = UUID.randomUUID(),
          apiId = PublicId("grt_01JABCDEFGHJKMNPQRSTVWXYZ6"),
          scope = ink.doa.workbench.core.permission.GrantScope.TENANT,
          tenantId = UUID.randomUUID(),
          projectId = null,
          subjectUserId = UUID.randomUUID(),
          action = ink.doa.workbench.core.permission.model.AuthorizationAction("project.read"),
          resourcePattern = "project:*",
          effect = ink.doa.workbench.core.permission.model.PermissionEffect.ALLOW,
          validFrom = now,
          validTo = null,
          grantedBy = UUID.randomUUID(),
          createdAt = now,
        )
      coEvery { adminUsers.findByApiId("adm_01JABCDEFGHJKMNPQRSTVWXYZ5") } returns adminUser
      coEvery { accessGrants.findByApiId("grt_01JABCDEFGHJKMNPQRSTVWXYZ6") } returns grant

      resolver.resolveAdminUser("adm_01JABCDEFGHJKMNPQRSTVWXYZ5").userId shouldBe adminUser.userId
      resolver.resolveAccessGrant("grt_01JABCDEFGHJKMNPQRSTVWXYZ6").resourcePattern shouldBe
        "project:*"
    }
  })
