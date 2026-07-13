package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.workitem.WorkItemCommentService
import ink.doa.workbench.agile.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.agile.workitem.model.DeleteWorkItemCommentCommand
import ink.doa.workbench.agile.workitem.model.UpdateWorkItemCommentCommand
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.ProjectScoped
import ink.doa.workbench.web.api.ResourceId
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import ink.doa.workbench.web.api.context.ProjectRequestContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import java.net.URI
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/projects/{id}/work-items/{workItemId}/comments")
@Tag(name = "Project Work Items", description = "Project-scoped work item runtime APIs.")
@SessionSecured
@StandardErrorResponses
class ProjectWorkItemCommentController(private val commentService: WorkItemCommentService) {
  @PostMapping
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.comment.create", resource = "issue")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a work item comment")
  suspend fun create(
    @PathVariable @ResourceId workItemId: String,
    @Valid @RequestBody request: CreateWorkItemCommentRequest,
    projectContext: ProjectRequestContext,
  ): ResponseEntity<WorkItemCommentResponse> {
    val actorUserId = actorUserId(projectContext)
    val comment =
      commentService.create(
        CreateWorkItemCommentCommand(
          tenantId = projectContext.tenant.id,
          projectId = projectContext.project.id,
          workItemApiId = workItemId,
          authorId = actorUserId,
          body = request.body.toDomain(),
        )
      )
    val response = WorkItemCommentResponse.from(comment)
    return ResponseEntity.created(
        URI.create(
          "/api/projects/${projectContext.project.publicId.value}/work-items/$workItemId/comments/${response.id}"
        )
      )
      .body(response)
  }

  @PatchMapping("/{commentId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.comment.update", resource = "issue")
  @Operation(summary = "Update a work item comment")
  suspend fun update(
    @PathVariable @ResourceId workItemId: String,
    @PathVariable @ResourceId commentId: String,
    @Valid @RequestBody request: UpdateWorkItemCommentRequest,
    projectContext: ProjectRequestContext,
  ): WorkItemCommentResponse =
    WorkItemCommentResponse.from(
      commentService.update(
        UpdateWorkItemCommentCommand(
          tenantId = projectContext.tenant.id,
          projectId = projectContext.project.id,
          workItemApiId = workItemId,
          commentApiId = commentId,
          actorUserId = actorUserId(projectContext),
          body = request.body.toDomain(),
        )
      )
    )

  @DeleteMapping("/{commentId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.comment.delete", resource = "issue")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a work item comment")
  suspend fun delete(
    @PathVariable @ResourceId workItemId: String,
    @PathVariable @ResourceId commentId: String,
    projectContext: ProjectRequestContext,
  ) {
    commentService.delete(
      DeleteWorkItemCommentCommand(
        tenantId = projectContext.tenant.id,
        projectId = projectContext.project.id,
        workItemApiId = workItemId,
        commentApiId = commentId,
        actorUserId = actorUserId(projectContext),
      )
    )
  }

  private fun actorUserId(projectContext: ProjectRequestContext) =
    projectContext.actor?.id
      ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
}
