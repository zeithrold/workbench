package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.model.WorkItemRecord
import java.util.UUID
import kotlinx.serialization.json.JsonElement

data class WorkItemTemplateContextRequest(
  val tenantId: UUID,
  val projectId: UUID,
  val actorUserId: UUID,
  val reporterUserId: UUID? = null,
  val workItem: WorkItemRecord? = null,
  val currentProperties: Map<String, JsonElement> = emptyMap(),
)
