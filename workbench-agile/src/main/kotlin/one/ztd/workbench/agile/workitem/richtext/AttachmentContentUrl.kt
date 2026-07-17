package one.ztd.workbench.agile.workitem.richtext

object AttachmentContentUrl {
  fun build(
    projectApiId: String,
    workItemApiId: String,
    attachmentApiId: String,
  ): String =
    "/api/projects/$projectApiId/work-items/$workItemApiId/attachments/$attachmentApiId/content"
}
