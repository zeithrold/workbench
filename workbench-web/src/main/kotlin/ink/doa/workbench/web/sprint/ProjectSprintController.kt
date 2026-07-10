package ink.doa.workbench.web.sprint

import ink.doa.workbench.agile.sprint.SprintService
import ink.doa.workbench.core.common.context.ProjectRequestContext
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.sprint.model.ArchiveSprintCommand
import ink.doa.workbench.core.sprint.model.CloseSprintCommand
import ink.doa.workbench.core.sprint.model.CreateSprintCommand
import ink.doa.workbench.core.sprint.model.DeleteSprintCommand
import ink.doa.workbench.core.sprint.model.StartSprintCommand
import ink.doa.workbench.core.sprint.model.UpdateSprintCommand
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.AuthorizeAll
import ink.doa.workbench.web.api.Idempotent
import ink.doa.workbench.web.api.ProjectScoped
import ink.doa.workbench.web.api.ResourceId
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import java.net.URI
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/projects/{id}/sprints")
@Tag(name = "Project Sprints", description = "Project-scoped sprint APIs.")
@SessionSecured
@StandardErrorResponses
class ProjectSprintController(private val sprintService: SprintService) {
  @GetMapping
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "sprint.read", resource = "sprint")
  @Operation(summary = "List project sprints")
  suspend fun list(
    @RequestParam(required = false) status: String?,
    projectContext: ProjectRequestContext,
  ): List<SprintResponse> {
    val parsedStatus = status?.let(::parseSprintStatus)
    return sprintService
      .list(projectContext.tenant.id, projectContext.project.id, parsedStatus)
      .map(SprintResponse::from)
  }

  @PostMapping
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "sprint.create", resource = "sprint")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a sprint")
  suspend fun create(
    @Valid @RequestBody request: CreateSprintRequest,
    projectContext: ProjectRequestContext,
  ): ResponseEntity<SprintResponse> {
    val view =
      sprintService.create(
        CreateSprintCommand(
          tenantId = projectContext.tenant.id,
          projectId = projectContext.project.id,
          name = request.name,
          goal = request.goal,
          startAt = request.startAt,
          endAt = request.endAt,
          createdBy = actorUserId(projectContext),
        )
      )
    val response = SprintResponse.from(view)
    return ResponseEntity.created(
        URI.create("/api/projects/${projectContext.project.publicId.value}/sprints/${response.id}")
      )
      .body(response)
  }

  @GetMapping("/{sprintId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "sprint.read", resource = "sprint")
  @Operation(summary = "Get a sprint")
  suspend fun get(
    @PathVariable @ResourceId sprintId: String,
    projectContext: ProjectRequestContext,
  ): SprintResponse =
    SprintResponse.from(
      sprintService.get(projectContext.tenant.id, projectContext.project.id, sprintId)
    )

  @PatchMapping("/{sprintId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "sprint.manage", resource = "sprint")
  @Operation(summary = "Update a sprint")
  suspend fun update(
    @PathVariable @ResourceId sprintId: String,
    @Valid @RequestBody request: PatchSprintRequest,
    projectContext: ProjectRequestContext,
  ): SprintResponse =
    SprintResponse.from(
      sprintService.update(
        UpdateSprintCommand(
          tenantId = projectContext.tenant.id,
          projectId = projectContext.project.id,
          sprintApiId = sprintId,
          name = request.name,
          goal = request.goal,
          startAt = request.startAt,
          endAt = request.endAt,
          actorUserId = actorUserId(projectContext),
        )
      )
    )

  @PostMapping("/{sprintId}/start")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "sprint.manage", resource = "sprint")
  @Operation(summary = "Start a sprint")
  suspend fun start(
    @PathVariable @ResourceId sprintId: String,
    projectContext: ProjectRequestContext,
  ): SprintResponse =
    SprintResponse.from(
      sprintService.start(
        StartSprintCommand(
          tenantId = projectContext.tenant.id,
          projectId = projectContext.project.id,
          sprintApiId = sprintId,
          actorUserId = actorUserId(projectContext),
        )
      )
    )

  @PostMapping("/{sprintId}/close")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @AuthorizeAll(
    actions = ["sprint.manage", "sprint.workitem.disposition"],
    resource = "sprint",
  )
  @Idempotent
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Close a sprint")
  suspend fun close(
    @PathVariable @ResourceId sprintId: String,
    @Valid @RequestBody request: CloseSprintRequest,
    @RequestHeader(name = "Idempotency-Key") idempotencyKey: String,
    projectContext: ProjectRequestContext,
  ): SprintCloseOperationResponse =
    SprintCloseOperationResponse.from(
      sprintService.close(
        CloseSprintCommand(
          tenantId = projectContext.tenant.id,
          projectId = projectContext.project.id,
          sprintApiId = sprintId,
          actorUserId = actorUserId(projectContext),
          disposition = request.disposition,
          targetSprintApiId = request.targetSprintId,
          idempotencyKey = idempotencyKey,
        )
      )
    )

  @GetMapping("/{sprintId}/close-operations/{operationId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "sprint.read", resource = "sprint")
  @Operation(summary = "Get a sprint close operation")
  suspend fun closeOperation(
    @PathVariable @ResourceId sprintId: String,
    @PathVariable operationId: String,
    projectContext: ProjectRequestContext,
  ): SprintCloseOperationResponse =
    SprintCloseOperationResponse.from(
      sprintService.closeOperation(
        projectContext.tenant.id,
        projectContext.project.id,
        sprintId,
        operationId,
      )
    )

  @PostMapping("/{sprintId}/close-operations/{operationId}/retry")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @AuthorizeAll(
    actions = ["sprint.manage", "sprint.workitem.disposition"],
    resource = "sprint",
  )
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Retry a failed sprint close operation")
  suspend fun retryCloseOperation(
    @PathVariable @ResourceId sprintId: String,
    @PathVariable operationId: String,
    projectContext: ProjectRequestContext,
  ): SprintCloseOperationResponse =
    SprintCloseOperationResponse.from(
      sprintService.retryCloseOperation(
        projectContext.tenant.id,
        projectContext.project.id,
        sprintId,
        operationId,
      )
    )

  @PostMapping("/{sprintId}/archive")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "sprint.manage", resource = "sprint")
  @Operation(summary = "Archive a sprint")
  suspend fun archive(
    @PathVariable @ResourceId sprintId: String,
    projectContext: ProjectRequestContext,
  ): SprintResponse =
    SprintResponse.from(
      sprintService.archive(
        ArchiveSprintCommand(
          tenantId = projectContext.tenant.id,
          projectId = projectContext.project.id,
          sprintApiId = sprintId,
          actorUserId = actorUserId(projectContext),
        )
      )
    )

  @DeleteMapping("/{sprintId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "sprint.manage", resource = "sprint")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a sprint")
  suspend fun delete(
    @PathVariable @ResourceId sprintId: String,
    @RequestBody(required = false) request: DeleteSprintRequest?,
    projectContext: ProjectRequestContext,
  ) {
    sprintService.delete(
      DeleteSprintCommand(
        tenantId = projectContext.tenant.id,
        projectId = projectContext.project.id,
        sprintApiId = sprintId,
        actorUserId = actorUserId(projectContext),
        deleteReason = request?.deleteReason,
      )
    )
  }
}

private fun actorUserId(projectContext: ProjectRequestContext) =
  projectContext.actor?.id
    ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
