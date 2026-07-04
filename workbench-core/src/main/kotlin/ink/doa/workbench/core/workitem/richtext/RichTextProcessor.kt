package ink.doa.workbench.core.workitem.richtext

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.PolicyFactory

data class ProcessedRichText(
  val html: String?,
  val plainText: String?,
)

object RichTextProcessor {
  private val htmlPolicy: PolicyFactory =
    HtmlPolicyBuilder()
      .allowElements(
        "p",
        "br",
        "strong",
        "b",
        "em",
        "i",
        "u",
        "ul",
        "ol",
        "li",
        "a",
        "code",
        "pre",
        "h1",
        "h2",
        "h3",
        "h4",
        "blockquote",
      )
      .allowAttributes("href")
      .onElements("a")
      .allowStandardUrlProtocols()
      .toFactory()

  private val plainTextTagPattern = Regex("<[^>]+>")

  fun processDescriptionInput(input: String?): ProcessedRichText? {
    if (input == null) return null
    val htmlInput = if (isPlainText(input)) plainTextToHtml(input) else input
    return processDescription(htmlInput)
  }

  fun processCommentInput(input: String?): ProcessedRichText? = processDescriptionInput(input)

  fun processDescription(input: String?): ProcessedRichText? {
    if (input == null) return null
    val sanitized = sanitizeHtml(input)
    return if (sanitized.isBlank()) {
      ProcessedRichText(html = null, plainText = null)
    } else {
      ProcessedRichText(html = sanitized, plainText = toPlainText(sanitized))
    }
  }

  fun sanitizeHtml(input: String): String = htmlPolicy.sanitize(input).trim()

  fun toPlainText(html: String): String {
    val text = Jsoup.parse(html).text().replace(Regex("\\s+"), " ").trim()
    return text
  }

  fun plainTextToHtml(plain: String): String {
    val escaped = Document.OutputSettings().escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
    val safePlain = Jsoup.clean(plain, "", Safelist.none(), escaped)
    return sanitizeHtml("<p>$safePlain</p>")
  }

  fun isPlainText(value: String): Boolean = !plainTextTagPattern.containsMatchIn(value)
}
