package one.ztd.workbench.agile.workitem

import java.util.UUID
import kotlinx.serialization.json.JsonElement
import one.ztd.workbench.agile.workitem.model.WorkItemRecord

data class WorkItemTemplateContextRequest(
  val tenantId: UUID,
  val projectId: UUID,
  val actorUserId: UUID,
  val reporterUserId: UUID? = null,
  val workItem: WorkItemRecord? = null,
  val currentProperties: Map<String, JsonElement> = emptyMap(),
)
