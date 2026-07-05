package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.project.ProjectRepository
import ink.doa.workbench.core.workitem.WorkItemAttachmentRepository
import ink.doa.workbench.core.workitem.model.AttachmentPurpose
import ink.doa.workbench.core.workitem.richtext.AttachmentReferenceParser
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class WorkItemDescriptionAttachmentValidator(
  private val attachments: WorkItemAttachmentRepository,
  private val projects: ProjectRepository,
) {
  suspend fun validateReferences(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
    issueId: UUID,
    descriptionHtml: String?,
  ) {
    val error = validateReferenceError(tenantId, projectId, workItemApiId, issueId, descriptionHtml)
    if (error != null) {
      throw InvalidRequestException(error)
    }
  }

  fun rejectCreateDescriptionReferences(descriptionHtml: String?) {
    if (descriptionHtml.isNullOrBlank()) return
    if (AttachmentReferenceParser.extractContentReferences(descriptionHtml).isNotEmpty()) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_DESCRIPTION_ATTACHMENT_CREATE_UNSUPPORTED
      )
    }
  }

  private suspend fun validateReferenceError(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
    issueId: UUID,
    descriptionHtml: String?,
  ): WorkbenchErrorCode? {
    if (descriptionHtml.isNullOrBlank()) return null
    val projectApiId = projects.findById(tenantId, projectId)?.apiId?.value ?: return null
    return invalidDescriptionReference(
      descriptionHtml = descriptionHtml,
      projectApiId = projectApiId,
      workItemApiId = workItemApiId,
      tenantId = tenantId,
      issueId = issueId,
    )
  }

  private suspend fun invalidDescriptionReference(
    descriptionHtml: String,
    projectApiId: String,
    workItemApiId: String,
    tenantId: UUID,
    issueId: UUID,
  ): WorkbenchErrorCode? {
    for (reference in AttachmentReferenceParser.extractContentReferences(descriptionHtml)) {
      if (reference.projectApiId != projectApiId || reference.workItemApiId != workItemApiId) {
        return WorkbenchErrorCode.WORK_ITEM_DESCRIPTION_ATTACHMENT_REFERENCE_INVALID
      }
      val attachment = attachments.findByApiId(tenantId, issueId, reference.attachmentApiId)
      if (attachment == null || attachment.purpose != AttachmentPurpose.DESCRIPTION) {
        return WorkbenchErrorCode.WORK_ITEM_DESCRIPTION_ATTACHMENT_REFERENCE_INVALID
      }
    }
    return null
  }
}
