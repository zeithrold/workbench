package doa.ink.workbench.web.project

import doa.ink.workbench.core.common.context.ProjectRequestContext
import doa.ink.workbench.core.common.context.TenantRequestContext
import doa.ink.workbench.core.project.model.CreateProjectCommand
import doa.ink.workbench.core.project.model.NonMemberJoinPolicy
import doa.ink.workbench.core.project.model.NonMemberVisibility
import doa.ink.workbench.core.project.model.UpdateProjectCommand
import doa.ink.workbench.service.project.ProjectManagementService
import doa.ink.workbench.service.project.ProjectOperationalGuard
import doa.ink.workbench.web.api.Audit
import doa.ink.workbench.web.api.Authenticated
import doa.ink.workbench.web.api.Authorize
import doa.ink.workbench.web.api.ProjectScoped
import doa.ink.workbench.web.api.SessionSecured
import doa.ink.workbench.web.api.StandardErrorResponses
import doa.ink.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Tenant-scoped project management.")
@SessionSecured
@StandardErrorResponses
class ProjectController(
  private val service: ProjectManagementService,
  private val projectOperationalGuard: ProjectOperationalGuard,
) {
  @GetMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "project.read", resource = "project")
  @Operation(summary = "List visible projects")
  suspend fun list(
    @RequestParam(required = false) identifier: String?,
    tenantContext: TenantRequestContext,
  ): List<ProjectResponse> {
    val actorUserId =
      tenantContext.actor?.id
        ?: throw IllegalStateException("Authenticated user is required.")
    return service.list(tenantContext.tenant.id, actorUserId, identifier).map(ProjectResponse::from)
  }

  @PostMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "project.create", resource = "project")
  @Audit("project.create")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a project")
  suspend fun create(
    @Valid @RequestBody request: CreateProjectRequest,
    tenantContext: TenantRequestContext,
  ): ResponseEntity<ProjectResponse> {
    val actorUserId =
      tenantContext.actor?.id
        ?: throw IllegalStateException("Authenticated user is required.")
    val view =
      service.create(
        CreateProjectCommand(
          tenantId = tenantContext.tenant.id,
          identifier = request.identifier,
          name = request.name,
          description = request.description,
          createdBy = actorUserId,
          leadUserId = actorUserId,
        ),
        actorUserId = actorUserId,
      )
    val response = ProjectResponse.from(view)
    return ResponseEntity.created(URI.create("/api/projects/${response.id}")).body(response)
  }

  @GetMapping("/{id}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "project.read", resource = "project")
  @Operation(summary = "Get project")
  suspend fun get(projectContext: ProjectRequestContext): ProjectResponse {
    val actorUserId =
      projectContext.actor?.id
        ?: throw IllegalStateException("Authenticated user is required.")
    return ProjectResponse.from(
      service.get(projectContext.tenant.id, actorUserId, projectContext.project.publicId.value)
    )
  }

  @PatchMapping("/{id}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "project.manage", resource = "project")
  @Operation(summary = "Update project settings")
  suspend fun update(
    @Valid @RequestBody request: PatchProjectRequest,
    projectContext: ProjectRequestContext,
  ): ProjectResponse {
    projectOperationalGuard.ensureWritable(projectContext.tenant.id, projectContext.project.id)
    val record =
      service.update(
        UpdateProjectCommand(
          tenantId = projectContext.tenant.id,
          projectId = projectContext.project.id,
          identifier = request.identifier,
          name = request.name,
          description = request.description,
          nonMemberVisibility = request.nonMemberVisibility?.let(::parseVisibility),
          nonMemberJoinPolicy = request.nonMemberJoinPolicy?.let(::parseJoinPolicy),
          updatedBy = projectContext.actor?.id,
        )
      )
    return ProjectResponse.from(record)
  }

  @PostMapping("/{id}/archive")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "project.archive", resource = "project")
  @Operation(summary = "Archive project")
  suspend fun archive(projectContext: ProjectRequestContext): ProjectResponse {
    val actorUserId =
      projectContext.actor?.id
        ?: throw IllegalStateException("Authenticated user is required.")
    return ProjectResponse.from(
      service.archive(projectContext.tenant.id, projectContext.project.id, actorUserId)
    )
  }

  @PostMapping("/{id}/unarchive")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "project.archive", resource = "project")
  @Operation(summary = "Unarchive project")
  suspend fun unarchive(projectContext: ProjectRequestContext): ProjectResponse =
    ProjectResponse.from(service.unarchive(projectContext.tenant.id, projectContext.project.id))

  @DeleteMapping("/{id}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "project.delete", resource = "project")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Request project deletion")
  suspend fun delete(
    @RequestBody(required = false) request: DestroyProjectRequest?,
    projectContext: ProjectRequestContext,
  ): ProjectResponse {
    val actorUserId =
      projectContext.actor?.id
        ?: throw IllegalStateException("Authenticated user is required.")
    return ProjectResponse.from(
      service.requestDestroy(
        tenantId = projectContext.tenant.id,
        tenantPublicId = projectContext.tenant.publicId,
        projectPublicId = projectContext.project.publicId.value,
        actorUserId = actorUserId,
        deleteReason = request?.deleteReason,
      )
    )
  }
}

data class CreateProjectRequest(
  @field:NotBlank @field:Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$") val identifier: String,
  @field:NotBlank val name: String,
  val description: String?,
)

data class PatchProjectRequest(
  @field:Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$") val identifier: String? = null,
  val name: String? = null,
  val description: String? = null,
  val nonMemberVisibility: String? = null,
  val nonMemberJoinPolicy: String? = null,
)

data class DestroyProjectRequest(val deleteReason: String? = null)

private fun parseVisibility(value: String): NonMemberVisibility =
  NonMemberVisibility.entries.single {
    it.dbValue == value.lowercase() || it.name.equals(value, ignoreCase = true)
  }

private fun parseJoinPolicy(value: String): NonMemberJoinPolicy =
  NonMemberJoinPolicy.entries.single {
    it.dbValue == value.lowercase() || it.name.equals(value, ignoreCase = true)
  }
