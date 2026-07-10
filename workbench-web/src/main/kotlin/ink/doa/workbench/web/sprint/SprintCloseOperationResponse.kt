package ink.doa.workbench.web.sprint

import ink.doa.workbench.agile.sprint.SprintCloseOperationView
import java.time.OffsetDateTime

data class SprintCloseOperationResponse(
  val id: String,
  val sprintId: String,
  val targetSprintId: String?,
  val disposition: String,
  val status: String,
  val totalItems: Int,
  val processedItems: Int,
  val failedItems: Int,
  val lastError: String?,
  val createdAt: OffsetDateTime,
  val startedAt: OffsetDateTime?,
  val completedAt: OffsetDateTime?,
) {
  companion object {
    fun from(view: SprintCloseOperationView) =
      SprintCloseOperationResponse(
        id = view.id,
        sprintId = view.sprintId,
        targetSprintId = view.targetSprintId,
        disposition = view.disposition,
        status = view.status,
        totalItems = view.totalItems,
        processedItems = view.processedItems,
        failedItems = view.failedItems,
        lastError = view.lastError,
        createdAt = view.createdAt,
        startedAt = view.startedAt,
        completedAt = view.completedAt,
      )
  }
}
