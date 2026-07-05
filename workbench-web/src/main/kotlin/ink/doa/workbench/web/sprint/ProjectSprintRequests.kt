package ink.doa.workbench.web.sprint

import ink.doa.workbench.core.sprint.model.SprintStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

data class CreateSprintRequest(
  @field:NotBlank @field:Size(max = 200) val name: String,
  @field:Size(max = 2000) val goal: String? = null,
  val startAt: OffsetDateTime? = null,
  val endAt: OffsetDateTime? = null,
)

data class PatchSprintRequest(
  @field:Size(max = 200) val name: String? = null,
  @field:Size(max = 2000) val goal: String? = null,
  val startAt: OffsetDateTime? = null,
  val endAt: OffsetDateTime? = null,
)

data class DeleteSprintRequest(val deleteReason: String? = null)

fun parseSprintStatus(value: String): SprintStatus =
  SprintStatus.entries.single {
    it.dbValue == value.lowercase() || it.name.equals(value, ignoreCase = true)
  }
