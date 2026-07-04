package ink.doa.workbench.core.permission

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class AdminRepositoriesTest :
  StringSpec({
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val userId = UUID.randomUUID()

    "admin scopes map to database values" {
      AdminScope.INSTANCE.dbValue shouldBe "instance"
      AdminScope.TENANT.dbValue shouldBe "tenant"
      GrantScope.PROJECT.dbValue shouldBe "project"
      AdminUserStatus.ACTIVE.dbValue shouldBe "active"
    }

    "create admin user command stores scope metadata" {
      val command =
        CreateAdminUserCommand(
          userId = userId,
          scope = AdminScope.TENANT,
          tenantId = UUID.randomUUID(),
          grantedBy = userId,
          validFrom = now,
        )

      command.scope shouldBe AdminScope.TENANT
      command.tenantId.shouldNotBeNull()
    }

    "access grant record stores action and effect" {
      val record =
        AccessGrantRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("agr"),
          scope = GrantScope.TENANT,
          tenantId = UUID.randomUUID(),
          projectId = null,
          subjectUserId = userId,
          action = AuthorizationAction("project.read"),
          resourcePattern = "project:*",
          effect = PermissionEffect.ALLOW,
          validFrom = now,
          validTo = null,
          grantedBy = userId,
          createdAt = now,
        )

      record.action.code shouldBe "project.read"
      record.effect shouldBe PermissionEffect.ALLOW
    }

    "create access grant command defaults to allow effect" {
      CreateAccessGrantCommand(
          scope = GrantScope.INSTANCE,
          subjectUserId = userId,
          action = AuthorizationAction("instance.manage"),
          validFrom = now,
        )
        .effect shouldBe PermissionEffect.ALLOW
    }
  })
