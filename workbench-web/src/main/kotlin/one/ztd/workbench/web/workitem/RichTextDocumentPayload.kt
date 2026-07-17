package one.ztd.workbench.web.workitem

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import one.ztd.workbench.agile.workitem.richtext.RichTextDocument
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

data class RichTextDocumentPayload(
  val format: String,
  val schemaVersion: Int,
  val content: JsonNode,
) {
  fun toDomain(): RichTextDocument =
    RichTextDocument(
      format = format,
      schemaVersion = schemaVersion,
      content = Json.parseToJsonElement(content.toString()).jsonObject,
    )

  companion object {
    private val mapper = ObjectMapper()

    fun from(document: RichTextDocument): RichTextDocumentPayload =
      RichTextDocumentPayload(
        format = document.format,
        schemaVersion = document.schemaVersion,
        content = mapper.readTree(document.content.toString()),
      )
  }
}
