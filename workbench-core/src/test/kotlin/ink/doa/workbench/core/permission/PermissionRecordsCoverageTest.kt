package ink.doa.workbench.core.permission

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.model.CredentialType
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.AuthorizationEnvironment
import ink.doa.workbench.core.permission.model.AuthorizationRequest
import ink.doa.workbench.core.permission.model.AuthorizationResource
import ink.doa.workbench.core.permission.model.AuthorizationScope
import ink.doa.workbench.core.permission.model.AuthorizationSubject
import ink.doa.workbench.core.permission.model.DecisionReason
import ink.doa.workbench.core.permission.model.PermissionAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.permission.model.PermissionRule
import ink.doa.workbench.core.permission.model.ResourcePattern
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class PermissionRecordsCoverageTest :
  StringSpec({
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val tenantId = UUID.randomUUID()
    val userId = UUID.randomUUID()

    "permission management records expose policy metadata" {
      val groupId = UUID.randomUUID()
      PermissionGroupRecord(
          id = groupId,
          apiId = PublicId.new("pgr"),
          tenantId = tenantId,
          code = "admins",
          name = "Admins",
          description = null,
          builtin = false,
          createdAt = now,
          updatedAt = now,
        )
        .code shouldBe "admins"

      GroupMemberRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("gmr"),
          groupId = groupId,
          userId = userId,
          status = GroupMemberStatus.ACTIVE,
          createdAt = now,
          updatedAt = now,
        )
        .status shouldBe GroupMemberStatus.ACTIVE

      val policyId = UUID.randomUUID()
      PermissionPolicyRecord(
          id = policyId,
          apiId = PublicId.new("pol"),
          tenantId = tenantId,
          code = "project-admin",
          name = "Project Admin",
          description = null,
          builtin = false,
          createdAt = now,
          updatedAt = now,
        )
        .code shouldBe "project-admin"

      PermissionPolicyRuleRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("prr"),
          policyId = policyId,
          action = AuthorizationAction("project.manage"),
          resourcePattern = "project:*",
          effect = PermissionEffect.ALLOW,
          conditionJson = null,
          createdAt = now,
        )
        .effect shouldBe PermissionEffect.ALLOW

      PermissionBindingRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("pbd"),
          tenantId = tenantId,
          projectId = null,
          principalType = PermissionPrincipalType.GROUP,
          principalUserId = null,
          principalGroupId = groupId,
          policyId = policyId,
          validFrom = now,
          validTo = null,
          createdBy = userId,
          createdAt = now,
        )
        .principalType shouldBe PermissionPrincipalType.GROUP
    }

    "authorization models carry request context" {
      AuthorizationRequest(
          scope = AuthorizationScope.TENANT,
          subject =
            AuthorizationSubject(
              userId = userId,
              userApiId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
              loginAccountId = UUID.randomUUID(),
              credentialType = CredentialType.SESSION,
              credentialId = "sess",
              credentialTenantId = tenantId,
              credentialScopes = emptySet(),
            ),
          tenantId = tenantId,
          action = AuthorizationAction("project.read"),
          resource = AuthorizationResource(type = "project", id = "prj_abc", tenantId = tenantId),
          environment = AuthorizationEnvironment(requestId = "req-1", occurredAt = Instant.now()),
        )
        .action
        .code shouldBe "project.read"

      PermissionRule(
          effect = PermissionEffect.ALLOW,
          actions = setOf(PermissionAction("project.read")),
          resource = ResourcePattern("project:*"),
          condition = null,
        )
        .effect shouldBe PermissionEffect.ALLOW

      DecisionReason(code = "allowed", message = "matched policy").code shouldBe "allowed"
    }

    "admin repositories models expose grant metadata" {
      AdminUserRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("adm"),
          userId = userId,
          scope = AdminScope.TENANT,
          tenantId = tenantId,
          status = AdminUserStatus.ACTIVE,
          grantedBy = null,
          validFrom = now,
          validTo = null,
          createdAt = now,
          updatedAt = now,
        )
        .scope shouldBe AdminScope.TENANT

      AccessGrantRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("grt"),
          scope = GrantScope.TENANT,
          tenantId = tenantId,
          projectId = null,
          subjectUserId = userId,
          action = AuthorizationAction("tenant.manage"),
          resourcePattern = "tenant:*",
          effect = PermissionEffect.ALLOW,
          validFrom = now,
          validTo = null,
          grantedBy = null,
          createdAt = now,
        )
        .action
        .code shouldBe "tenant.manage"

      PermissionActionRecord(
          id = UUID.randomUUID(),
          code = AuthorizationAction("project.read"),
          description = "Read Project",
          createdAt = now,
        )
        .code
        .code shouldBe "project.read"
    }
  })
