package one.ztd.workbench.web.sprint

import java.time.OffsetDateTime
import one.ztd.workbench.agile.sprint.SprintView
import one.ztd.workbench.identity.common.summary.UserSummary

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
