package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.project.ProjectSummary
import ink.doa.workbench.agile.workitem.WorkItemViewView
import ink.doa.workbench.identity.common.summary.UserSummary
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonElement

data class WorkItemViewResponse(
  val id: String,
  val name: String,
  val description: String?,
  val visibility: String,
  val owner: UserSummary,
  val project: ProjectSummary?,
  val query: JsonElement,
  val displayFields: JsonElement,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
) {
  companion object {
    fun from(view: WorkItemViewView): WorkItemViewResponse =
      WorkItemViewResponse(
        id = view.id,
        name = view.name,
        description = view.description,
        visibility = view.visibility.dbValue,
        owner = view.owner,
        project = view.project,
        query = view.query,
        displayFields = view.displayFields,
        createdAt = view.createdAt,
        updatedAt = view.updatedAt,
      )
  }
}
