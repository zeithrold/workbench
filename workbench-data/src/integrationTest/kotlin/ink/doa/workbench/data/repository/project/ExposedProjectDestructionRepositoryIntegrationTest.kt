package ink.doa.workbench.data.repository.project

import ink.doa.workbench.core.permission.CreatePermissionBindingCommand
import ink.doa.workbench.core.permission.CreatePermissionGroupCommand
import ink.doa.workbench.core.permission.CreatePermissionPolicyCommand
import ink.doa.workbench.core.permission.CreatePermissionPolicyRuleCommand
import ink.doa.workbench.core.permission.PermissionPrincipalType
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.workitem.CreateWorkItemPersistenceCommand
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.data.repository.permission.ExposedPermissionBindingRepository
import ink.doa.workbench.data.repository.permission.ExposedPermissionGroupRepository
import ink.doa.workbench.data.repository.permission.ExposedPermissionPolicyRepository
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import ink.doa.workbench.data.support.workItemRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedProjectDestructionRepositoryIntegrationTest :
  StringSpec({
    "expireBindingsByProject and softDeleteProjectScopedData mark project data deleted" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val destruction = ExposedProjectDestructionRepository(database)
        val workItems = workItemRepository(database)
        val groups = ExposedPermissionGroupRepository(database)
        val policies = ExposedPermissionPolicyRepository(database)
        val bindings = ExposedPermissionBindingRepository(database)
        val deletedAt = OffsetDateTime.now(ZoneOffset.UTC)

        workItems.create(
          CreateWorkItemPersistenceCommand(
            command =
              CreateWorkItemCommand(
                tenantId = stack.tenantId,
                projectId = stack.projectId,
                issueTypeApiId = stack.issueType.apiId.value,
                title = "To delete",
                description = null,
                reporterId = stack.actorId,
                actorUserId = stack.actorId,
              ),
            issueTypeId = stack.issueType.id,
            issueTypeConfigId = stack.config.config.id,
            initialStatusId = stack.todoStatus.id,
            propertyValues = emptyList(),
          )
        )

        val group =
          groups.create(
            CreatePermissionGroupCommand(
              tenantId = stack.tenantId,
              code = "project-team",
              name = "Project Team",
              description = null,
            )
          )
        val policy =
          policies.create(
            CreatePermissionPolicyCommand(
              tenantId = stack.tenantId,
              code = "project-access",
              name = "Project Access",
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
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            principalType = PermissionPrincipalType.GROUP,
            principalUserId = null,
            principalGroupId = group.id,
            policyId = policy.id,
            validFrom = deletedAt.minusMinutes(1),
            createdBy = stack.actorId,
          )
        )

        destruction.expireBindingsByProject(stack.tenantId, stack.projectId, deletedAt) shouldBe 1
        destruction.softDeleteProjectScopedData(
          tenantId = stack.tenantId,
          projectId = stack.projectId,
          deletedAt = deletedAt,
          deletedBy = stack.actorId,
          deleteReason = "project_destroyed",
        )
      }
    }
  })
