package ink.doa.workbench.core.workitem.richtext

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RichTextProcessorTest :
  StringSpec({
    "normalizes a valid document and extracts plain text" {
      val document =
        richTextDocument(
          node("heading", attrs = buildJsonObject { put("level", 2) }, text = "Title"),
          node("paragraph", text = "Hello world"),
          node(
            "codeBlock",
            attrs = buildJsonObject { put("language", "kotlin") },
            text = "val x = 1",
          ),
        )

      val processed = RichTextProcessor.process(document)

      processed?.document shouldBe document
      processed?.plainText shouldBe "Title Hello world val x = 1"
    }

    "normalizes an empty document to null" {
      RichTextProcessor.process(richTextDocument(node("paragraph"))) shouldBe null
    }

    "rejects unsupported schema versions" {
      shouldThrow<IllegalArgumentException> {
        RichTextProcessor.process(
          richTextDocument(node("paragraph", text = "x")).copy(schemaVersion = 2)
        )
      }
    }

    "rejects unsupported nodes marks attributes and languages" {
      listOf(
          richTextDocument(node("table")),
          richTextDocument(
            node("heading", attrs = buildJsonObject { put("level", 4) }, text = "x")
          ),
          richTextDocument(
            node("codeBlock", attrs = buildJsonObject { put("language", "brainfuck") }, text = "x")
          ),
          richTextDocument(
            buildJsonObject {
              put("type", "paragraph")
              put(
                "content",
                buildJsonArray {
                  add(
                    buildJsonObject {
                      put("type", "text")
                      put("text", "x")
                      put(
                        "marks",
                        buildJsonArray { add(buildJsonObject { put("type", "underline") }) },
                      )
                    }
                  )
                },
              )
            }
          ),
        )
        .forEach { document ->
          shouldThrow<IllegalArgumentException> { RichTextProcessor.process(document) }
        }
    }

    "accepts safe links and rejects unsafe links" {
      fun link(href: String) =
        richTextDocument(
          buildJsonObject {
            put("type", "paragraph")
            put(
              "content",
              buildJsonArray {
                add(
                  buildJsonObject {
                    put("type", "text")
                    put("text", "link")
                    put(
                      "marks",
                      buildJsonArray {
                        add(
                          buildJsonObject {
                            put("type", "link")
                            put("attrs", buildJsonObject { put("href", href) })
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

      RichTextProcessor.process(link("https://example.com"))?.plainText shouldBe "link"
      shouldThrow<IllegalArgumentException> {
        RichTextProcessor.process(link("javascript:alert(1)"))
      }
    }
  })

private fun richTextDocument(vararg children: kotlinx.serialization.json.JsonObject) =
  RichTextDocument(
    content =
      buildJsonObject {
        put("type", "doc")
        put("content", buildJsonArray { children.forEach(::add) })
      }
  )

private fun node(
  type: String,
  attrs: kotlinx.serialization.json.JsonObject? = null,
  text: String? = null,
) = buildJsonObject {
  put("type", type)
  attrs?.let { put("attrs", it) }
  text?.let {
    put(
      "content",
      buildJsonArray {
        add(
          buildJsonObject {
            put("type", "text")
            put("text", it)
          }
        )
      },
    )
  }
}
