package ink.doa.workbench.data.repository.permission

import ink.doa.workbench.agile.project.model.CreateProjectCommand
import ink.doa.workbench.data.persistence.postgres.identity.TenantsTable
import ink.doa.workbench.data.repository.identity.ExposedUserRepository
import ink.doa.workbench.data.repository.project.ExposedProjectRepository
import ink.doa.workbench.data.support.withCorePostgresDatabase
import ink.doa.workbench.identity.model.CreateUserCommand
import ink.doa.workbench.identity.permission.AddGroupMemberCommand
import ink.doa.workbench.identity.permission.CreatePermissionBindingCommand
import ink.doa.workbench.identity.permission.CreatePermissionGroupCommand
import ink.doa.workbench.identity.permission.CreatePermissionPolicyCommand
import ink.doa.workbench.identity.permission.CreatePermissionPolicyRuleCommand
import ink.doa.workbench.identity.permission.PermissionPrincipalType
import ink.doa.workbench.identity.permission.UpdatePermissionGroupCommand
import ink.doa.workbench.identity.permission.UpdatePermissionPolicyCommand
import ink.doa.workbench.identity.permission.model.AuthorizationAction
import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ExposedPermissionManagementRepositoriesIntegrationTest :
  StringSpec({
    "group bindings resolve active member policy rules" {
      withCorePostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val users = ExposedUserRepository(database)
        val groups = ExposedPermissionGroupRepository(database)
        val policies = ExposedPermissionPolicyRepository(database)
        val bindings = ExposedPermissionBindingRepository(database)
        val user = users.create(CreateUserCommand("Ada", "ada-permissions@example.test"))
        val group =
          groups.create(
            CreatePermissionGroupCommand(
              tenantId = tenantId,
              code = "developers",
              name = "Developers",
              description = null,
            )
          )
        groups.addMember(AddGroupMemberCommand(group.id, user.id))
        val policy =
          policies.create(
            CreatePermissionPolicyCommand(
              tenantId = tenantId,
              code = "project-reader",
              name = "Project Reader",
              description = null,
            )
          )
        policies.addRule(
          CreatePermissionPolicyRuleCommand(
            policyId = policy.id,
            action = AuthorizationAction("project.read"),
            resourcePattern = "project:*",
          )
        )
        bindings.create(
          CreatePermissionBindingCommand(
            tenantId = tenantId,
            projectId = null,
            principalType = PermissionPrincipalType.GROUP,
            principalUserId = null,
            principalGroupId = group.id,
            policyId = policy.id,
            validFrom = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
            createdBy = user.id,
          )
        )

        val rules =
          bindings.listActiveRulesForSubject(
            subjectUserId = user.id,
            tenantId = tenantId,
            projectId = null,
            at = OffsetDateTime.now(ZoneOffset.UTC),
          )
        rules.shouldHaveSize(1)
        rules.single().action.code shouldBe "project.read"

        groups.removeMember(group.id, user.id, OffsetDateTime.now(ZoneOffset.UTC))
        bindings
          .listActiveRulesForSubject(
            subjectUserId = user.id,
            tenantId = tenantId,
            projectId = null,
            at = OffsetDateTime.now(ZoneOffset.UTC),
          )
          .shouldBeEmpty()
      }
    }

    "listActiveGroupIdsForUser returns only active memberships" {
      withCorePostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val users = ExposedUserRepository(database)
        val groups = ExposedPermissionGroupRepository(database)
        val user = users.create(CreateUserCommand("Cara", "cara-permissions@example.test"))
        val group =
          groups.create(
            CreatePermissionGroupCommand(
              tenantId = tenantId,
              code = "qa",
              name = "QA",
              description = null,
            )
          )
        groups.addMember(AddGroupMemberCommand(group.id, user.id))

        groups.listActiveGroupIdsForUser(tenantId, user.id) shouldBe setOf(group.id)

        groups.removeMember(group.id, user.id, OffsetDateTime.now(ZoneOffset.UTC))
        groups.listActiveGroupIdsForUser(tenantId, user.id).shouldBeEmpty()
      }
    }

    "user bindings resolve direct policy rules" {
      withCorePostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val users = ExposedUserRepository(database)
        val policies = ExposedPermissionPolicyRepository(database)
        val bindings = ExposedPermissionBindingRepository(database)
        val user = users.create(CreateUserCommand("Bob", "bob-permissions@example.test"))
        val policy =
          policies.create(
            CreatePermissionPolicyCommand(
              tenantId = tenantId,
              code = "tenant-admin",
              name = "Tenant Admin",
              description = null,
            )
          )
        policies.addRule(
          CreatePermissionPolicyRuleCommand(
            policyId = policy.id,
            action = AuthorizationAction("project.read"),
            resourcePattern = "project:*",
          )
        )
        bindings.create(
          CreatePermissionBindingCommand(
            tenantId = tenantId,
            projectId = null,
            principalType = PermissionPrincipalType.USER,
            principalUserId = user.id,
            principalGroupId = null,
            policyId = policy.id,
            validFrom = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
            createdBy = user.id,
          )
        )

        val rules =
          bindings.listActiveRulesForSubject(
            subjectUserId = user.id,
            tenantId = tenantId,
            projectId = null,
            at = OffsetDateTime.now(ZoneOffset.UTC),
          )

        rules.single().action.code shouldBe "project.read"
      }
    }

    "policy rules persist condition json through active bindings" {
      withCorePostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val users = ExposedUserRepository(database)
        val policies = ExposedPermissionPolicyRepository(database)
        val bindings = ExposedPermissionBindingRepository(database)
        val user = users.create(CreateUserCommand("Finn", "finn-permissions@example.test"))
        val policy =
          policies.create(
            CreatePermissionPolicyCommand(
              tenantId = tenantId,
              code = "conditional-reader",
              name = "Conditional Reader",
              description = null,
            )
          )
        val conditionJson =
          """
          {"field":"statusGroup","op":"eq","value":"todo"}
          """
            .trimIndent()
        policies.addRule(
          CreatePermissionPolicyRuleCommand(
            policyId = policy.id,
            action = AuthorizationAction("issue.view"),
            resourcePattern = "issue:*",
            conditionJson = conditionJson,
          )
        )
        bindings.create(
          CreatePermissionBindingCommand(
            tenantId = tenantId,
            projectId = null,
            principalType = PermissionPrincipalType.USER,
            principalUserId = user.id,
            principalGroupId = null,
            policyId = policy.id,
            validFrom = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
            createdBy = user.id,
          )
        )

        val rules =
          bindings.listActiveRulesForSubject(
            subjectUserId = user.id,
            tenantId = tenantId,
            projectId = null,
            at = OffsetDateTime.now(ZoneOffset.UTC),
          )

        rules.single().conditionJson shouldBe conditionJson
      }
    }

    "project bindings resolve project ids for subject" {
      withCorePostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val users = ExposedUserRepository(database)
        val projects = ExposedProjectRepository(database)
        val policies = ExposedPermissionPolicyRepository(database)
        val bindings = ExposedPermissionBindingRepository(database)
        val user = users.create(CreateUserCommand("Cara", "cara-permissions@example.test"))
        val project =
          projects.create(
            CreateProjectCommand(
              tenantId = tenantId,
              identifier = "WB",
              name = "Workbench",
              description = null,
              createdBy = user.id,
              leadUserId = user.id,
            )
          )
        val policy =
          policies.create(
            CreatePermissionPolicyCommand(
              tenantId = tenantId,
              code = "project-member",
              name = "Project Member",
              description = null,
            )
          )
        policies.addRule(
          CreatePermissionPolicyRuleCommand(
            policyId = policy.id,
            action = AuthorizationAction("project.read"),
            resourcePattern = "project:*",
          )
        )
        bindings.create(
          CreatePermissionBindingCommand(
            tenantId = tenantId,
            projectId = project.id,
            principalType = PermissionPrincipalType.USER,
            principalUserId = user.id,
            principalGroupId = null,
            policyId = policy.id,
            validFrom = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
            createdBy = user.id,
          )
        )

        bindings.listProjectIdsForSubject(
          tenantId = tenantId,
          subjectUserId = user.id,
          at = OffsetDateTime.now(ZoneOffset.UTC),
        ) shouldBe setOf(project.id)
      }
    }

    "policy and group update persist renamed metadata" {
      withCorePostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val groups = ExposedPermissionGroupRepository(database)
        val policies = ExposedPermissionPolicyRepository(database)
        val group =
          groups.create(
            CreatePermissionGroupCommand(
              tenantId = tenantId,
              code = "qa",
              name = "QA",
              description = "Quality",
            )
          )
        val policy =
          policies.create(
            CreatePermissionPolicyCommand(
              tenantId = tenantId,
              code = "reader",
              name = "Reader",
              description = "Read access",
            )
          )

        val updatedGroup =
          groups.update(UpdatePermissionGroupCommand(group.id, "QA Team", "Quality assurance"))
        val updatedPolicy =
          policies.update(UpdatePermissionPolicyCommand(policy.id, "Readers", "Read-only"))
        updatedGroup.name shouldBe "QA Team"
        updatedPolicy.name shouldBe "Readers"
        groups.findById(tenantId, group.id)?.name shouldBe "QA Team"
        policies.findById(tenantId, policy.id)?.name shouldBe "Readers"
      }
    }

    "binding expire and listByProject return project scoped bindings" {
      withCorePostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val users = ExposedUserRepository(database)
        val projects = ExposedProjectRepository(database)
        val policies = ExposedPermissionPolicyRepository(database)
        val bindings = ExposedPermissionBindingRepository(database)
        val user = users.create(CreateUserCommand("Eli", "eli-permissions@example.test"))
        val project =
          projects.create(
            CreateProjectCommand(
              tenantId = tenantId,
              identifier = "OPS",
              name = "Operations",
              description = null,
              createdBy = user.id,
              leadUserId = user.id,
            )
          )
        val policy =
          policies.create(
            CreatePermissionPolicyCommand(
              tenantId = tenantId,
              code = "ops-reader",
              name = "Ops Reader",
              description = null,
            )
          )
        val binding =
          bindings.create(
            CreatePermissionBindingCommand(
              tenantId = tenantId,
              projectId = project.id,
              principalType = PermissionPrincipalType.USER,
              principalUserId = user.id,
              principalGroupId = null,
              policyId = policy.id,
              validFrom = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
              createdBy = user.id,
            )
          )

        bindings.listByProject(tenantId, project.id).single().id shouldBe binding.id
        bindings.expire(tenantId, binding.id, OffsetDateTime.now(ZoneOffset.UTC)) shouldBe true
        bindings
          .listActiveRulesForSubject(
            subjectUserId = user.id,
            tenantId = tenantId,
            projectId = project.id,
            at = OffsetDateTime.now(ZoneOffset.UTC),
          )
          .shouldBeEmpty()
      }
    }
  })

private fun seedTenant(database: Database): UUID {
  val tenantId = UUID.randomUUID()
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  transaction(database) {
    TenantsTable.insert {
      it[id] = tenantId.toKotlinUuid()
      it[apiId] = PublicId.new("ten").value
      it[name] = "Test Tenant"
      it[slug] = "permission-${tenantId.toString().take(8)}"
      it[timezone] = "UTC"
      it[locale] = "en-US"
      it[createdAt] = now
      it[updatedAt] = now
    }
  }
  return tenantId
}
