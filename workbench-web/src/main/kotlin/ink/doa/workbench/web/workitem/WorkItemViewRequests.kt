package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.databind.JsonNode
import ink.doa.workbench.core.workitem.view.WorkItemViewVisibility
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

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
