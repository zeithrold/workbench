package ink.doa.workbench.core.workitem.richtext

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
data class RichTextDocument(
  val format: String = FORMAT,
  val schemaVersion: Int = SCHEMA_VERSION,
  val content: JsonObject,
) {
  companion object {
    const val FORMAT = "tiptap"
    const val SCHEMA_VERSION = 1
  }
}

data class ProcessedRichText(
  val document: RichTextDocument,
  val plainText: String,
)

object RichTextProcessor {
  val supportedCodeLanguages =
    setOf(
      "plaintext",
      "kotlin",
      "java",
      "typescript",
      "javascript",
      "json",
      "sql",
      "bash",
      "yaml",
      "xml",
      "html",
      "css",
      "markdown",
    )

  private val containerNodes =
    setOf(
      "doc",
      "paragraph",
      "heading",
      "bulletList",
      "orderedList",
      "listItem",
      "blockquote",
      "codeBlock",
    )
  private val leafNodes = setOf("text", "horizontalRule", "hardBreak")
  private val marks = setOf("bold", "italic", "strike", "code", "link")
  private const val MAX_DEPTH = 32
  private const val MAX_NODES = 10_000
  private const val MAX_TEXT_LENGTH = 200_000

  fun process(document: RichTextDocument?): ProcessedRichText? {
    if (document == null) return null
    require(document.format == RichTextDocument.FORMAT) {
      "Unsupported rich-text format: ${document.format}"
    }
    require(document.schemaVersion == RichTextDocument.SCHEMA_VERSION) {
      "Unsupported rich-text schema version: ${document.schemaVersion}"
    }
    val state = ValidationState()
    validateNode(document.content, parent = null, depth = 0, state)
    val plainText = state.text.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    return if (plainText.isEmpty() && !state.hasNonTextContent) null
    else ProcessedRichText(document, plainText)
  }

  fun fromPlainText(value: String?): RichTextDocument? {
    if (value.isNullOrBlank()) return null
    return RichTextDocument(
      content =
        buildJsonObject {
          put("type", JsonPrimitive("doc"))
          put(
            "content",
            buildJsonArray {
              add(
                buildJsonObject {
                  put("type", JsonPrimitive("paragraph"))
                  put(
                    "content",
                    buildJsonArray {
                      add(
                        buildJsonObject {
                          put("type", JsonPrimitive("text"))
                          put("text", JsonPrimitive(value))
                        }
                      )
                    },
                  )
                }
              )
            },
          )
        }
    )
  }

  fun isPlainText(value: String): Boolean = !Regex("<[^>]+>").containsMatchIn(value)

  private fun validateNode(node: JsonObject, parent: String?, depth: Int, state: ValidationState) {
    require(depth <= MAX_DEPTH) { "Rich-text document exceeds maximum depth" }
    require(++state.nodeCount <= MAX_NODES) { "Rich-text document exceeds maximum node count" }
    val type = node.string("type")
    require(type in containerNodes || type in leafNodes) { "Unsupported rich-text node: $type" }
    require(node.keys.all { it in setOf("type", "attrs", "content", "text", "marks") }) {
      "Unsupported attribute on rich-text node: $type"
    }
    validateParent(type, parent)
    validateAttributes(type, node["attrs"])
    validateMarks(type, node["marks"])

    if (type == "text") {
      val text = node.string("text")
      state.textLength += text.length
      require(state.textLength <= MAX_TEXT_LENGTH) {
        "Rich-text document exceeds maximum text length"
      }
      if (text.isNotBlank()) state.text += text
      require(node["content"] == null) { "Text nodes cannot contain child nodes" }
    } else {
      require(node["text"] == null) { "$type nodes cannot contain a text property" }
      val children = node["content"]
      if (children != null) {
        require(children is JsonArray) { "Rich-text node content must be an array" }
        children.forEach { child ->
          require(child is JsonObject) { "Rich-text child must be an object" }
          validateNode(child, type, depth + 1, state)
        }
      }
      if (type in setOf("horizontalRule", "hardBreak")) state.hasNonTextContent = true
    }
  }

  private fun validateParent(type: String, parent: String?) {
    val allowed =
      when (parent) {
        null -> setOf("doc")
        "doc",
        "blockquote" ->
          setOf(
            "paragraph",
            "heading",
            "bulletList",
            "orderedList",
            "blockquote",
            "codeBlock",
            "horizontalRule",
          )
        "paragraph",
        "heading" -> setOf("text", "hardBreak")
        "codeBlock" -> setOf("text")
        "bulletList",
        "orderedList" -> setOf("listItem")
        "listItem" -> setOf("paragraph", "bulletList", "orderedList", "blockquote", "codeBlock")
        else -> emptySet()
      }
    require(type in allowed) { "$type has an invalid parent" }
  }

  private fun validateAttributes(type: String, element: JsonElement?) {
    val attrs = element as? JsonObject
    when (type) {
      "heading" -> {
        require(attrs?.keys == setOf("level") && attrs["level"]?.primitive()?.intOrNull in 1..3) {
          "heading requires level 1, 2, or 3"
        }
      }
      "codeBlock" -> {
        val language = attrs?.get("language")?.primitive()?.contentOrNull ?: "plaintext"
        require(attrs == null || attrs.keys == setOf("language")) {
          "codeBlock has unsupported attributes"
        }
        require(language in supportedCodeLanguages) { "Unsupported code language: $language" }
      }
      else -> require(attrs == null || attrs.isEmpty()) { "$type does not support attributes" }
    }
  }

  private fun validateMarks(type: String, element: JsonElement?) {
    if (element == null) return
    require(type == "text" && element is JsonArray) { "Only text nodes support marks" }
    element.forEach { value ->
      val mark = value as? JsonObject ?: error("Rich-text mark must be an object")
      val markType = mark.string("type")
      require(markType in marks) { "Unsupported rich-text mark: $markType" }
      require(mark.keys.all { it in setOf("type", "attrs") }) {
        "Unsupported mark attribute: $markType"
      }
      val attrs = mark["attrs"] as? JsonObject
      if (markType == "link") {
        require(attrs?.keys == setOf("href")) { "link requires an href" }
        val href = attrs.string("href")
        require(isSafeLink(href)) { "Unsupported link protocol" }
      } else {
        require(attrs == null || attrs.isEmpty()) { "$markType does not support attributes" }
      }
    }
  }

  private fun isSafeLink(href: String): Boolean =
    href.startsWith("https://") ||
      href.startsWith("http://") ||
      href.startsWith("mailto:") ||
      href.startsWith("/") ||
      href.startsWith("#")

  private fun JsonObject.string(key: String): String =
    this[key]?.primitive()?.contentOrNull
      ?: throw IllegalArgumentException("Missing rich-text property: $key")

  private fun JsonElement.primitive(): JsonPrimitive =
    this as? JsonPrimitive
      ?: throw IllegalArgumentException("Rich-text property must be a primitive")

  private data class ValidationState(
    var nodeCount: Int = 0,
    var textLength: Int = 0,
    var hasNonTextContent: Boolean = false,
    val text: MutableList<String> = mutableListOf(),
  )
}
