package ink.doa.workbench.core.workitem.richtext

data class AttachmentContentReference(
  val projectApiId: String,
  val workItemApiId: String,
  val attachmentApiId: String,
)

object AttachmentReferenceParser {
  private val contentUrlPattern =
    Regex(
      "^/api/projects/([A-Za-z0-9_]+)/work-items/([A-Za-z0-9_]+)/attachments/([A-Za-z0-9_]+)/content$"
    )

  fun isAllowedAttachmentContentUrl(src: String): Boolean = contentUrlPattern.matches(src.trim())

  fun extractContentReferences(html: String): List<AttachmentContentReference> =
    IMG_SRC_PATTERN.findAll(html)
      .mapNotNull { match ->
        val src = match.groupValues[1].trim()
        contentUrlPattern.matchEntire(src)?.let { result ->
          AttachmentContentReference(
            projectApiId = result.groupValues[1],
            workItemApiId = result.groupValues[2],
            attachmentApiId = result.groupValues[3],
          )
        }
      }
      .toList()

  fun buildContentUrl(
    projectApiId: String,
    workItemApiId: String,
    attachmentApiId: String,
  ): String =
    "/api/projects/$projectApiId/work-items/$workItemApiId/attachments/$attachmentApiId/content"

  private val IMG_SRC_PATTERN =
    Regex("""<img\b[^>]*\bsrc="([^"]+)"[^>]*>""", RegexOption.IGNORE_CASE)
}
