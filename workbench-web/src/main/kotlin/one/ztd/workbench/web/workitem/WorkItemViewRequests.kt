package one.ztd.workbench.web.workitem

import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import one.ztd.workbench.agile.workitem.view.WorkItemViewVisibility

data class CreateWorkItemViewRequest(
  @field:NotBlank @field:Size(max = 200) val name: String,
  @field:Size(max = 2000) val description: String? = null,
  @field:NotBlank val visibility: String,
  val query: JsonNode? = null,
  val displayFields: JsonNode? = null,
) {
  fun visibilityEnum(): WorkItemViewVisibility = WorkItemViewVisibility.fromDbValue(visibility)
}

data class UpdateWorkItemViewRequest(
  @field:Size(max = 200) val name: String? = null,
  @field:Size(max = 2000) val description: String? = null,
  val visibility: String? = null,
  val query: JsonNode? = null,
  val displayFields: JsonNode? = null,
) {
  fun visibilityEnum(): WorkItemViewVisibility? =
    visibility?.let(WorkItemViewVisibility::fromDbValue)
}
