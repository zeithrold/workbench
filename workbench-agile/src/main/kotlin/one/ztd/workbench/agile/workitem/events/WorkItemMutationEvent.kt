package one.ztd.workbench.agile.workitem.events

import kotlinx.serialization.Serializable
import one.ztd.workbench.agile.workitem.model.WorkItemRecord

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
