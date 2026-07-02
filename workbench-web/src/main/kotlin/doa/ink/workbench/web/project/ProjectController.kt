package doa.ink.workbench.web.project

import doa.ink.workbench.core.common.context.TenantRequestContext
import doa.ink.workbench.core.project.model.CreateProjectCommand
import doa.ink.workbench.core.project.model.UpdateProjectCommand
import doa.ink.workbench.service.project.ProjectService
import doa.ink.workbench.web.api.Audit
import doa.ink.workbench.web.api.Authenticated
import doa.ink.workbench.web.api.Authorize
import doa.ink.workbench.web.api.SessionSecured
import doa.ink.workbench.web.api.StandardErrorResponses
import doa.ink.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.net.URI
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Tenant-scoped project management")
@SessionSecured
@StandardErrorResponses
class ProjectController(private val service: ProjectService) {
  @GetMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "project.read", resource = "project")
  @Operation(summary = "List projects")
  suspend fun list(
    @RequestParam(required = false) identifier: String?,
    tenantContext: TenantRequestContext,
  ): List<ProjectResponse> =
    service.list(tenantContext.tenantId, identifier).map { ProjectResponse.from(it) }

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
  ): ResponseEntity<ProjectResponse> {
    val record =
      service.create(
        CreateProjectCommand(
          tenantId = tenantContext.tenantId,
          identifier = request.identifier,
          name = request.name,
          description = request.description,
        )
      )
    val response = ProjectResponse.from(record)
    return ResponseEntity.created(URI.create("/api/projects/${response.id}")).body(response)
  }

  @GetMapping("/{id}")
  @Authenticated
  @TenantScoped
  @Authorize(action = "project.read", resource = "project")
  @Operation(summary = "Get project")
  suspend fun get(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ): ProjectResponse = ProjectResponse.from(service.get(tenantContext.tenantId, id))

  @PatchMapping("/{id}")
  @Authenticated
  @TenantScoped
  @Authorize(action = "project.update", resource = "project")
  @Operation(summary = "Update project")
  suspend fun update(
    @PathVariable id: String,
    @Valid @RequestBody request: PatchProjectRequest,
    tenantContext: TenantRequestContext,
  ): ProjectResponse {
    val project = service.get(tenantContext.tenantId, id)
    val record =
      service.update(
        UpdateProjectCommand(
          tenantId = tenantContext.tenantId,
          projectId = project.id,
          identifier = request.identifier,
          name = request.name,
          description = request.description,
        )
      )
    return ProjectResponse.from(record)
  }

  @DeleteMapping("/{id}")
  @Authenticated
  @TenantScoped
  @Authorize(action = "project.delete", resource = "project")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete project")
  suspend fun delete(@PathVariable id: String, tenantContext: TenantRequestContext) {
    service.delete(tenantContext.tenantId, id)
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

data class PatchProjectRequest(
  @field:Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$") val identifier: String? = null,
  val name: String? = null,
  val description: String? = null,
)
