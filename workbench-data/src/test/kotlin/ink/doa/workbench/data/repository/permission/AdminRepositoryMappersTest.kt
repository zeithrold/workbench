package ink.doa.workbench.data.repository.permission

import ink.doa.workbench.identity.permission.AdminScope
import ink.doa.workbench.identity.permission.AdminUserStatus
import ink.doa.workbench.identity.permission.GrantScope
import ink.doa.workbench.identity.permission.GroupMemberStatus
import ink.doa.workbench.identity.permission.PermissionPrincipalType
import ink.doa.workbench.identity.permission.model.PermissionEffect
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.ZoneOffset

class AdminRepositoryMappersTest :
  StringSpec({
    "maps admin scopes and statuses from database values" {
      adminScopeOf("instance") shouldBe AdminScope.INSTANCE
      adminScopeOf("tenant") shouldBe AdminScope.TENANT
      adminUserStatusOf("active") shouldBe AdminUserStatus.ACTIVE
      adminUserStatusOf("revoked") shouldBe AdminUserStatus.REVOKED
      grantScopeOf("project") shouldBe GrantScope.PROJECT
      groupMemberStatusOf("active") shouldBe GroupMemberStatus.ACTIVE
      permissionPrincipalTypeOf("group") shouldBe PermissionPrincipalType.GROUP
    }

    "maps permission effects case-insensitively" {
      permissionEffectOf("allow") shouldBe PermissionEffect.ALLOW
      permissionEffectOf("DENY") shouldBe PermissionEffect.DENY
    }

    "rejects unknown permission effect" {
      shouldThrow<IllegalStateException> { permissionEffectOf("maybe") }
    }

    "permission effect db value is lowercase" {
      PermissionEffect.ALLOW.dbValue shouldBe "allow"
      PermissionEffect.DENY.dbValue shouldBe "deny"
    }

    "nowUtc returns UTC offset" {
      AdminRepositoryMappers.nowUtc().offset shouldBe ZoneOffset.UTC
    }
  })
