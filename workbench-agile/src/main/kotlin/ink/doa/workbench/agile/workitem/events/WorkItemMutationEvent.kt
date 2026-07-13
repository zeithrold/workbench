package ink.doa.workbench.agile.workitem.events

import ink.doa.workbench.agile.workitem.model.WorkItemRecord
import kotlinx.serialization.Serializable

@Serializable
data class WorkItemMutationEvent(
  val tenantId: String,
  val projectId: String,
  val workItemId: String,
  val key: String,
  val statusId: String,
  val statusGroup: String,
) {
  companion object {
    fun from(record: WorkItemRecord): WorkItemMutationEvent =
      WorkItemMutationEvent(
        tenantId = record.tenantId.toString(),
        projectId = record.projectId.toString(),
        workItemId = record.apiId.value,
        key = record.key,
        statusId = record.statusApiId.value,
        statusGroup = record.statusGroup.dbValue,
      )
  }
}
