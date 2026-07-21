package one.ztd.workbench.agile.workitem

import java.util.UUID
import one.ztd.workbench.agile.workitem.access.WorkItemAccessActor
import one.ztd.workbench.agile.workitem.access.WorkItemAccessRuleRecord
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigDetails
import one.ztd.workbench.agile.workitem.model.TransitionRequest
import one.ztd.workbench.identity.permission.ResolvedPermissionRule
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.springframework.stereotype.Component

@Component
class WorkItemTransitionContextLoader(
  private val repository: WorkItemRepository,
  private val mutationSupport: WorkItemMutationSupport,
  private val transitionValidator: WorkItemTransitionValidator,
  private val accessPolicy: WorkItemAccessPolicyEngine,
  private val bindingPermissions: WorkItemBindingPermissionEvaluator,
) {
  data class Overrides(
    val bindingRules: List<ResolvedPermissionRule>? = null,
    val accessRules: List<WorkItemAccessRuleRecord>? = null,
    val config: IssueTypeConfigDetails? = null,
    val actor: WorkItemAccessActor? = null,
    val projectApiId: String? = null,
  )

  suspend fun loadForIssue(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
    actorUserId: UUID,
    actorUserApiId: String,
  ): WorkItemTransitionContext {
    val issue =
      repository.findByApiId(tenantId, projectId, workItemApiId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)
    return load(issue, actorUserId, actorUserApiId)
  }

  suspend fun load(
    issue: one.ztd.workbench.agile.workitem.model.WorkItemRecord,
    actorUserId: UUID,
    actorUserApiId: String,
    overrides: Overrides = Overrides(),
  ): WorkItemTransitionContext {
    val config =
      overrides.config
        ?: mutationSupport.requireConfig(issue.tenantId, issue.issueTypeConfigApiId.value)
    val currentProperties = repository.listPropertyValues(issue.tenantId, issue.id)
    val projectApiId =
      overrides.projectApiId
        ?: repository.resolveProjectApiId(issue.tenantId, issue.projectId)?.value
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
    val conditionContext =
      transitionValidator.conditionContext(
        issue,
        actorUserApiId,
        projectApiId,
        currentProperties,
      )
    val accessEvaluation =
      transitionValidator.accessEvaluationContext(
        WorkItemTransitionEvaluationRequest(
          issue = issue,
          actorUserId = actorUserId,
          actorUserApiId = actorUserApiId,
          projectApiId = projectApiId,
          properties = currentProperties,
          issueTypeConfigId = config.config.id,
          actor = overrides.actor,
        )
      )
    val permissionContext =
      permissionContext(
        issue,
        config,
        accessEvaluation,
        PermissionRequest(actorUserId, actorUserApiId, projectApiId, overrides),
      )
    val templateContext =
      mutationSupport.templateContext(
        WorkItemTemplateContextRequest(
          tenantId = issue.tenantId,
          projectId = issue.projectId,
          actorUserId = actorUserId,
          workItem = issue,
          currentProperties = currentProperties,
        )
      )
    return WorkItemTransitionContext(
      tenantId = issue.tenantId,
      projectId = issue.projectId,
      actorUserId = actorUserId,
      actorUserApiId = actorUserApiId,
      issue = issue,
      config = config,
      currentProperties = currentProperties,
      conditionContext = conditionContext,
      accessEvaluation = accessEvaluation,
      templateContext = templateContext,
      permissionContext = permissionContext,
    )
  }

  private suspend fun permissionContext(
    issue: one.ztd.workbench.agile.workitem.model.WorkItemRecord,
    config: IssueTypeConfigDetails,
    accessEvaluation: one.ztd.workbench.agile.workitem.access.WorkItemAccessEvaluationContext,
    request: PermissionRequest,
  ): WorkItemFieldPermissionContext =
    WorkItemFieldPermissionContext(
      tenantId = issue.tenantId,
      projectId = issue.projectId,
      actorUserId = request.actorUserId,
      actorUserApiId = request.actorUserApiId,
      operation = FieldPermissionOperation.UPDATE,
      accessEvaluation = accessEvaluation,
      resourceAttributes = workItemResourceAttributes(issue, request.projectApiId),
      bindingRules =
        request.overrides.bindingRules
          ?: bindingPermissions.loadActiveRules(
            issue.tenantId,
            issue.projectId,
            request.actorUserId,
          ),
      accessRules =
        request.overrides.accessRules
          ?: accessPolicy.loadAccessRules(issue.tenantId, config.config.id),
    )

  private data class PermissionRequest(
    val actorUserId: UUID,
    val actorUserApiId: String,
    val projectApiId: String,
    val overrides: Overrides,
  )

  suspend fun load(request: TransitionRequest): WorkItemTransitionContext =
    loadForIssue(
      tenantId = request.tenantId,
      projectId = request.projectId,
      workItemApiId = request.workItemApiId,
      actorUserId = request.actorUserId,
      actorUserApiId = request.actorUserApiId,
    )

  private fun workItemResourceAttributes(
    issue: one.ztd.workbench.agile.workitem.model.WorkItemRecord,
    projectApiId: String,
  ): Map<String, String> =
    mapOf(
      "reporter" to issue.reporterApiId.value,
      "assignee" to issue.assigneeApiId?.value.orEmpty(),
      "status" to issue.statusApiId.value,
      "statusGroup" to issue.statusGroup.dbValue,
      "issueType" to issue.issueTypeApiId.value,
      "issueTypeConfig" to issue.issueTypeConfigApiId.value,
      "project" to projectApiId,
    )
}
