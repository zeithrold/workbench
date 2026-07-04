package ink.doa.workbench.core.workitem.richtext

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class RichTextProcessorTest {
  @Test
  fun `sanitizeHtml strips script tags`() {
    val sanitized = RichTextProcessor.sanitizeHtml("<p>Hello</p><script>alert(1)</script>")
    sanitized shouldContain "Hello"
    sanitized shouldNotContain "script"
  }

  @Test
  fun `processDescriptionInput converts plain text to html and plain search text`() {
    val processed = RichTextProcessor.processDescriptionInput("Acceptance criteria")
    processed?.html shouldBe "<p>Acceptance criteria</p>"
    processed?.plainText shouldBe "Acceptance criteria"
  }

  @Test
  fun `processDescriptionInput sanitizes html and extracts plain text`() {
    val processed = RichTextProcessor.processDescriptionInput("<p>Hello <strong>world</strong></p>")
    processed?.html shouldContain "Hello"
    processed?.plainText shouldBe "Hello world"
  }

  @Test
  fun `processCommentInput mirrors description input processing`() {
    val processed = RichTextProcessor.processCommentInput("Looks good")
    processed?.html shouldBe "<p>Looks good</p>"
    processed?.plainText shouldBe "Looks good"
  }

  @Test
  fun `processCommentInput sanitizes html`() {
    val processed =
      RichTextProcessor.processCommentInput("<p>Hello <strong>world</strong></p><script>x</script>")
    processed?.html shouldContain "Hello"
    processed?.plainText shouldBe "Hello world"
  }
}
