package one.ztd.workbench.data.repository.project

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import one.ztd.workbench.agile.workitem.CreateWorkItemPersistenceCommand
import one.ztd.workbench.agile.workitem.model.CreateWorkItemCommand
import one.ztd.workbench.data.repository.permission.ExposedPermissionBindingRepository
import one.ztd.workbench.data.repository.permission.ExposedPermissionGroupRepository
import one.ztd.workbench.data.repository.permission.ExposedPermissionPolicyRepository
import one.ztd.workbench.data.support.seedWorkItemStack
import one.ztd.workbench.data.support.withPostgresDatabase
import one.ztd.workbench.data.support.workItemRepository
import one.ztd.workbench.identity.permission.CreatePermissionBindingCommand
import one.ztd.workbench.identity.permission.CreatePermissionGroupCommand
import one.ztd.workbench.identity.permission.CreatePermissionPolicyCommand
import one.ztd.workbench.identity.permission.CreatePermissionPolicyRuleCommand
import one.ztd.workbench.identity.permission.PermissionPrincipalType
import one.ztd.workbench.identity.permission.model.AuthorizationAction

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
