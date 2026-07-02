package doa.ink.workbench.web.project

import doa.ink.workbench.core.project.model.ProjectRecord
import doa.ink.workbench.web.api.OpenApiExamples
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Tenant-scoped project resource.")
data class ProjectResponse(
  @field:Schema(description = "Public project id.", example = OpenApiExamples.PROJECT_ID)
  val id: String,
  @field:Schema(
    description = "Display key prefix used for work item keys.",
    example = "CORE",
  )
  val identifier: String,
  @field:Schema(description = "Human-readable project name.", example = "Core Platform")
  val name: String,
  @field:Schema(description = "Optional project description.", example = "Platform engineering.")
  val description: String?,
) {
  companion object {
    fun from(record: ProjectRecord): ProjectResponse =
      ProjectResponse(
        id = record.apiId.value,
        identifier = record.identifier,
        name = record.name,
        description = record.description,
      )
  }
}
