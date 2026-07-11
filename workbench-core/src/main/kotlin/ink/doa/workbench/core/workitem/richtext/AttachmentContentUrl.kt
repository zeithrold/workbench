package ink.doa.workbench.core.workitem.richtext

object AttachmentContentUrl {
  fun build(
    projectApiId: String,
    workItemApiId: String,
    attachmentApiId: String,
  ): String =
    "/api/projects/$projectApiId/work-items/$workItemApiId/attachments/$attachmentApiId/content"
}
