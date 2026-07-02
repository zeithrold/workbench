package doa.ink.workbench.web.project

import doa.ink.workbench.core.common.context.TenantRequestContext
import doa.ink.workbench.core.project.model.CreateProjectCommand
import doa.ink.workbench.service.project.ProjectService
import doa.ink.workbench.web.api.Audit
import doa.ink.workbench.web.api.Authenticated
import doa.ink.workbench.web.api.Authorize
import doa.ink.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/projects")
class ProjectController(private val service: ProjectService) {
  @PostMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "project.create", resource = "project")
  @Audit("project.create")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Create a project",
    description = "Creates a project in the active session tenant.",
    responses =
      [
        ApiResponse(
          responseCode = "201",
          description = "Project created",
          content = [Content(schema = Schema(implementation = ProjectResponse::class))],
        )
      ],
  )
  suspend fun create(
    @Valid @RequestBody request: CreateProjectRequest,
    tenantContext: TenantRequestContext,
  ): ProjectResponse {
    val record =
      service.create(
        CreateProjectCommand(
          tenantId = tenantContext.tenantId,
          identifier = request.identifier,
          name = request.name,
          description = request.description,
        )
      )
    return ProjectResponse.from(record)
  }
}

data class CreateProjectRequest(
  @field:NotBlank
  @field:Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$")
  @field:Schema(
    example = "CORE",
    description = "Display key prefix used for work item keys such as CORE-123.",
  )
  val identifier: String,
  @field:NotBlank @field:Schema(example = "Core Platform") val name: String,
  @field:Schema(example = "Platform engineering workbench project.") val description: String?,
)
