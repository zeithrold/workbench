package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.agile.workitem.model.WorkItemRecord
import ink.doa.workbench.agile.workitem.template.WorkItemValueTemplateContext
import java.util.UUID
import kotlinx.serialization.json.JsonElement

data class WorkItemTransitionContext(
  val tenantId: UUID,
  val projectId: UUID,
  val actorUserId: UUID,
  val actorUserApiId: String,
  val issue: WorkItemRecord,
  val config: IssueTypeConfigDetails,
  val currentProperties: Map<String, JsonElement>,
  val conditionContext: WorkItemConditionContext,
  val accessEvaluation: ink.doa.workbench.agile.workitem.access.WorkItemAccessEvaluationContext,
  val templateContext: WorkItemValueTemplateContext,
  val permissionContext: WorkItemFieldPermissionContext,
)
