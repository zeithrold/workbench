package one.ztd.workbench.web.workitem

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import java.net.URI
import one.ztd.workbench.agile.workitem.ListWorkItemAttachmentsRequest
import one.ztd.workbench.agile.workitem.WorkItemAttachmentService
import one.ztd.workbench.agile.workitem.model.AttachmentPurpose
import one.ztd.workbench.agile.workitem.model.CompleteWorkItemAttachmentUploadCommand
import one.ztd.workbench.agile.workitem.model.DeleteWorkItemAttachmentCommand
import one.ztd.workbench.agile.workitem.model.InitiateWorkItemAttachmentUploadCommand
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.web.api.Authenticated
import one.ztd.workbench.web.api.Authorize
import one.ztd.workbench.web.api.ProjectScoped
import one.ztd.workbench.web.api.ResourceId
import one.ztd.workbench.web.api.SessionSecured
import one.ztd.workbench.web.api.StandardErrorResponses
import one.ztd.workbench.web.api.TenantScoped
import one.ztd.workbench.web.api.context.ProjectRequestContext
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/projects/{id}/work-items/{workItemId}/attachments")
@Tag(name = "Project Work Items", description = "Project-scoped work item runtime APIs.")
@SessionSecured
@StandardErrorResponses
class ProjectWorkItemAttachmentController(
  private val attachmentService: WorkItemAttachmentService
) {
  @GetMapping
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.view", resource = "issue")
  @Operation(summary = "List work item attachments")
  suspend fun list(
    @PathVariable @ResourceId workItemId: String,
    query: ListWorkItemAttachmentsQueryParams,
    projectContext: ProjectRequestContext,
  ): List<WorkItemAttachmentResponse> {
    val parsedPurpose = query.purpose?.let(::parsePurpose)
    return attachmentService
      .list(
        ListWorkItemAttachmentsRequest(
          tenantId = projectContext.tenant.id,
          projectId = projectContext.project.id,
          workItemApiId = workItemId,
          purpose = parsedPurpose,
          commentApiId = query.commentId,
          limit = query.limit,
          offset = query.offset,
        )
      )
      .map { record -> toResponse(record, workItemId, projectContext) }
  }

  @PostMapping("/upload-sessions")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.attachment.create", resource = "issue")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a work item attachment upload session")
  suspend fun createUploadSession(
    @PathVariable @ResourceId workItemId: String,
    @Valid @RequestBody request: InitiateWorkItemAttachmentUploadRequest,
    projectContext: ProjectRequestContext,
  ): ResponseEntity<WorkItemAttachmentUploadSessionResponse> {
    val session =
      attachmentService.initiateUpload(
        InitiateWorkItemAttachmentUploadCommand(
          tenantId = projectContext.tenant.id,
          projectId = projectContext.project.id,
          projectApiId = projectContext.project.publicId.value,
          workItemApiId = workItemId,
          uploadedBy = actorUserId(projectContext),
          filename = request.filename,
          contentType = request.contentType,
          declaredByteSize = request.byteSize,
          purpose = parsePurpose(request.purpose),
          commentApiId = request.commentId,
        )
      )
    val response = WorkItemAttachmentUploadSessionResponse.from(session)
    return ResponseEntity.created(
        URI.create(
          "/api/projects/${projectContext.project.publicId.value}/work-items/$workItemId/attachments/upload-sessions/${response.id}"
        )
      )
      .body(response)
  }

  @PostMapping("/upload-sessions/{sessionId}/complete")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.attachment.create", resource = "issue")
  @Operation(summary = "Complete a work item attachment upload session")
  suspend fun completeUploadSession(
    @PathVariable @ResourceId workItemId: String,
    @PathVariable @ResourceId sessionId: String,
    projectContext: ProjectRequestContext,
  ): WorkItemAttachmentResponse {
    val attachment =
      attachmentService.completeUpload(
        CompleteWorkItemAttachmentUploadCommand(
          tenantId = projectContext.tenant.id,
          projectId = projectContext.project.id,
          workItemApiId = workItemId,
          attachmentApiId = sessionId,
          uploadedBy = actorUserId(projectContext),
        )
      )
    return toResponse(attachment, workItemId, projectContext)
  }

  @GetMapping("/{attachmentId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.view", resource = "issue")
  @Operation(summary = "Get work item attachment metadata")
  suspend fun get(
    @PathVariable @ResourceId workItemId: String,
    @PathVariable @ResourceId attachmentId: String,
    projectContext: ProjectRequestContext,
  ): WorkItemAttachmentResponse {
    val attachment =
      attachmentService.get(
        tenantId = projectContext.tenant.id,
        projectId = projectContext.project.id,
        workItemApiId = workItemId,
        attachmentApiId = attachmentId,
      )
    return toResponse(attachment, workItemId, projectContext)
  }

  @GetMapping("/{attachmentId}/content")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.view", resource = "issue")
  @Operation(summary = "Redirect to work item attachment content")
  suspend fun downloadContent(
    @PathVariable @ResourceId workItemId: String,
    @PathVariable @ResourceId attachmentId: String,
    projectContext: ProjectRequestContext,
  ): ResponseEntity<Unit> {
    val redirectUrl =
      attachmentService.contentRedirectUrl(
        tenantId = projectContext.tenant.id,
        projectId = projectContext.project.id,
        workItemApiId = workItemId,
        attachmentApiId = attachmentId,
      )
    return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, redirectUrl).build()
  }

  @DeleteMapping("/{attachmentId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.attachment.delete", resource = "issue")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a work item attachment")
  suspend fun delete(
    @PathVariable @ResourceId workItemId: String,
    @PathVariable @ResourceId attachmentId: String,
    projectContext: ProjectRequestContext,
  ) {
    attachmentService.delete(
      DeleteWorkItemAttachmentCommand(
        tenantId = projectContext.tenant.id,
        projectId = projectContext.project.id,
        workItemApiId = workItemId,
        attachmentApiId = attachmentId,
        actorUserId = actorUserId(projectContext),
      )
    )
  }

  private suspend fun toResponse(
    record: one.ztd.workbench.agile.workitem.model.WorkItemAttachmentRecord,
    workItemId: String,
    projectContext: ProjectRequestContext,
  ): WorkItemAttachmentResponse {
    val downloadUrl =
      attachmentService
        .presignedDownloadUrl(
          tenantId = projectContext.tenant.id,
          projectId = projectContext.project.id,
          workItemApiId = workItemId,
          attachmentApiId = record.apiId.value,
        )
        .url
    return WorkItemAttachmentResponse.from(record, downloadUrl)
  }

  private fun actorUserId(projectContext: ProjectRequestContext) =
    projectContext.actor?.id
      ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)

  private fun parsePurpose(value: String): AttachmentPurpose =
    AttachmentPurpose.fromWire(value)
      ?: throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_PURPOSE_INVALID)
}
