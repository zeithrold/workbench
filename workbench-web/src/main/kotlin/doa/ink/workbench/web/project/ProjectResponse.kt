package doa.ink.workbench.web.project

import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.service.project.ProjectView
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
  @field:Schema(description = "Lifecycle status.", example = "active") val status: String,
  @field:Schema(description = "Non-member visibility policy.", example = "invisible")
  val nonMemberVisibility: String,
  @field:Schema(description = "Non-member join policy.", example = "admin_only")
  val nonMemberJoinPolicy: String,
  @field:Schema(description = "Project lead user.") val lead: UserSummary?,
  @field:Schema(description = "When the project was archived, if applicable.")
  val archivedAt: String?,
) {
  companion object {
    fun from(view: ProjectView): ProjectResponse =
      ProjectResponse(
        id = view.id,
        identifier = view.identifier,
        name = view.name,
        description = view.description,
        status = view.status,
        nonMemberVisibility = view.nonMemberVisibility,
        nonMemberJoinPolicy = view.nonMemberJoinPolicy,
        lead = view.lead,
        archivedAt = view.archivedAt,
      )
  }
}
