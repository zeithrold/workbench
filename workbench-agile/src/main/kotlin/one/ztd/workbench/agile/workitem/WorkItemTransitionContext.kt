package one.ztd.workbench.agile.workitem

import java.util.UUID
import kotlinx.serialization.json.JsonElement
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigDetails
import one.ztd.workbench.agile.workitem.model.WorkItemRecord
import one.ztd.workbench.agile.workitem.template.WorkItemValueTemplateContext

data class WorkItemTransitionContext(
  val tenantId: UUID,
  val projectId: UUID,
  val actorUserId: UUID,
  val actorUserApiId: String,
  val issue: WorkItemRecord,
  val config: IssueTypeConfigDetails,
  val currentProperties: Map<String, JsonElement>,
  val conditionContext: WorkItemConditionContext,
  val accessEvaluation: one.ztd.workbench.agile.workitem.access.WorkItemAccessEvaluationContext,
  val templateContext: WorkItemValueTemplateContext,
  val permissionContext: WorkItemFieldPermissionContext,
)
