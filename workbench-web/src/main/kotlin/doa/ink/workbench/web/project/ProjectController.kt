package doa.ink.workbench.web.project

import doa.ink.workbench.core.common.context.TenantRequestContext
import doa.ink.workbench.core.project.model.CreateProjectCommand
import doa.ink.workbench.core.project.model.UpdateProjectCommand
import doa.ink.workbench.agile.project.ProjectService
import doa.ink.workbench.web.api.Audit
import doa.ink.workbench.web.api.Authenticated
import doa.ink.workbench.web.api.Authorize
import doa.ink.workbench.web.api.OpenApiExamples
import doa.ink.workbench.web.api.SessionSecured
import doa.ink.workbench.web.api.StandardErrorResponses
import doa.ink.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.net.URI
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
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
@Tag(
  name = "Projects",
  description =
    "Tenant-scoped project management. Requires session auth and an active tenant in the session.",
)
@SessionSecured
@StandardErrorResponses
class ProjectController(private val service: ProjectService) {
  @GetMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "project.read", resource = "project")
  @Operation(
    summary = "List projects",
    description =
      "Returns projects in the active session tenant. Optionally filter by work-item key prefix using the identifier query parameter.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Matching projects",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = ProjectResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      summary = "Projects in tenant",
                      value = OpenApiExamples.PROJECT_LIST,
                    )
                  ],
              )
            ],
        )
      ],
  )
  suspend fun list(
    @Parameter(
      description = "Filter by project identifier (work-item key prefix).",
      example = "CORE",
    )
    @RequestParam(required = false)
    identifier: String?,
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
    description =
      "Creates a project in the active session tenant. Returns 201 with a Location header pointing to the new resource.",
    responses =
      [
        ApiResponse(
          responseCode = "201",
          description = "Project created",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = ProjectResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "created",
                      summary = "New project",
                      value = OpenApiExamples.PROJECT_CREATED,
                    )
                  ],
              )
            ],
        ),
        ApiResponse(
          responseCode = "400",
          description = "Validation failed",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "invalidIdentifier",
                      summary = "Identifier does not match required pattern",
                      value = OpenApiExamples.VALIDATION_FAILED,
                    )
                  ],
              )
            ],
        ),
        ApiResponse(
          responseCode = "403",
          description = "Permission denied",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "permissionDenied",
                      value = OpenApiExamples.PERMISSION_DENIED,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun create(
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description = "Project fields for the new resource.",
      content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = CreateProjectRequest::class),
            examples =
              [
                ExampleObject(
                  name = "valid",
                  summary = "Valid create request",
                  value = OpenApiExamples.CREATE_PROJECT_REQUEST,
                ),
                ExampleObject(
                  name = "invalidIdentifier",
                  summary = "Invalid identifier (lowercase)",
                  value = OpenApiExamples.CREATE_PROJECT_REQUEST_INVALID,
                ),
              ],
          )
        ],
    )
    @Valid
    @RequestBody
    request: CreateProjectRequest,
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
  @Operation(
    summary = "Get project",
    description = "Returns a single project by public id within the active session tenant.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Project found",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = ProjectResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.PROJECT_CREATED,
                    )
                  ],
              )
            ],
        ),
        ApiResponse(
          responseCode = "404",
          description = "Project not found",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "notFound",
                      value = OpenApiExamples.RESOURCE_NOT_FOUND,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun get(
    @Parameter(description = "Public project id.", example = OpenApiExamples.PROJECT_ID)
    @PathVariable
    id: String,
    tenantContext: TenantRequestContext,
  ): ProjectResponse = ProjectResponse.from(service.get(tenantContext.tenantId, id))

  @PatchMapping("/{id}")
  @Authenticated
  @TenantScoped
  @Authorize(action = "project.update", resource = "project")
  @Operation(
    summary = "Update project",
    description =
      "Partially updates mutable project fields. Omitted fields are left unchanged; " +
        "explicit null clears nullable fields such as description.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Updated project",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = ProjectResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.PROJECT_CREATED,
                    )
                  ],
              )
            ],
        ),
        ApiResponse(
          responseCode = "404",
          description = "Project not found",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "notFound",
                      value = OpenApiExamples.RESOURCE_NOT_FOUND,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun update(
    @Parameter(description = "Public project id.", example = OpenApiExamples.PROJECT_ID)
    @PathVariable
    id: String,
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
  @Operation(
    summary = "Delete project",
    description = "Permanently deletes a project in the active session tenant. Returns 204 with an empty body.",
    responses =
      [
        ApiResponse(responseCode = "204", description = "Project deleted"),
        ApiResponse(
          responseCode = "404",
          description = "Project not found",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "notFound",
                      value = OpenApiExamples.RESOURCE_NOT_FOUND,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun delete(
    @Parameter(description = "Public project id.", example = OpenApiExamples.PROJECT_ID)
    @PathVariable
    id: String,
    tenantContext: TenantRequestContext,
  ) {
    service.delete(tenantContext.tenantId, id)
  }
}

@Schema(description = "Fields for creating a new project.")
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

@Schema(description = "Partial project update. Omitted fields are unchanged; null clears nullable fields.")
data class PatchProjectRequest(
  @field:Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$")
  @field:Schema(description = "New work-item key prefix.", example = "CORE")
  val identifier: String? = null,
  @field:Schema(description = "New display name.", example = "Core Platform")
  val name: String? = null,
  @field:Schema(description = "New description. Set to null to clear.", example = "Updated scope.")
  val description: String? = null,
)
