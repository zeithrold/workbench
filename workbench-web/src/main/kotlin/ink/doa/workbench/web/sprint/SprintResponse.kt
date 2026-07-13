package ink.doa.workbench.web.sprint

import ink.doa.workbench.agile.sprint.SprintView
import ink.doa.workbench.identity.common.summary.UserSummary
import java.time.OffsetDateTime

data class SprintResponse(
  val id: String,
  val name: String,
  val goal: String?,
  val status: String,
  val startAt: OffsetDateTime?,
  val endAt: OffsetDateTime?,
  val closedAt: OffsetDateTime?,
  val createdBy: UserSummary?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
) {
  companion object {
    fun from(view: SprintView): SprintResponse =
      SprintResponse(
        id = view.id,
        name = view.name,
        goal = view.goal,
        status = view.status.dbValue,
        startAt = view.startAt,
        endAt = view.endAt,
        closedAt = view.closedAt,
        createdBy = view.createdBy,
        createdAt = view.createdAt,
        updatedAt = view.updatedAt,
      )
  }
}
