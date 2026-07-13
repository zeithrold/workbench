package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.richtext.RichTextDocument
import ink.doa.workbench.agile.workitem.richtext.RichTextProcessor
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun richText(value: String): RichTextDocument =
  requireNotNull(RichTextProcessor.fromPlainText(value))

internal fun emptyRichText(): RichTextDocument =
  RichTextDocument(
    content =
      buildJsonObject {
        put("type", "doc")
        put(
          "content",
          buildJsonArray {
            add(buildJsonObject { put("type", "paragraph") })
          },
        )
      }
  )
