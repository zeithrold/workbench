package ink.doa.workbench.core.workitem.richtext

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class RichTextProcessorTest :
  StringSpec({
    "sanitizeHtml strips script tags" {
      val sanitized = RichTextProcessor.sanitizeHtml("<p>Hello</p><script>alert(1)</script>")
      sanitized shouldContain "Hello"
      sanitized shouldNotContain "script"
    }

    "processDescriptionInput converts plain text to html and plain search text" {
      val processed = RichTextProcessor.processDescriptionInput("Acceptance criteria")
      processed?.html shouldBe "<p>Acceptance criteria</p>"
      processed?.plainText shouldBe "Acceptance criteria"
    }

    "processDescriptionInput returns null for null input" {
      RichTextProcessor.processDescriptionInput(null).shouldBeNull()
    }

    "processDescriptionInput sanitizes html and extracts plain text" {
      val processed =
        RichTextProcessor.processDescriptionInput("<p>Hello <strong>world</strong></p>")
      processed?.html shouldContain "Hello"
      processed?.plainText shouldBe "Hello world"
    }

    "processCommentInput mirrors description input processing" {
      val processed = RichTextProcessor.processCommentInput("Looks good")
      processed?.html shouldBe "<p>Looks good</p>"
      processed?.plainText shouldBe "Looks good"
    }

    "processCommentInput sanitizes html" {
      val processed =
        RichTextProcessor.processCommentInput(
          "<p>Hello <strong>world</strong></p><script>x</script>"
        )
      processed?.html shouldContain "Hello"
      processed?.plainText shouldBe "Hello world"
    }

    "processDescription returns null for null input" {
      RichTextProcessor.processDescription(null).shouldBeNull()
    }

    "processDescription returns nulls for blank sanitized html" {
      val processed = RichTextProcessor.processDescription("<script>alert(1)</script>")
      processed?.html.shouldBeNull()
      processed?.plainText.shouldBeNull()
    }

    "plainTextToHtml escapes unsafe content and sanitizes output" {
      val html = RichTextProcessor.plainTextToHtml("Hello <script>alert(1)</script>")
      html shouldContain "Hello"
      html shouldNotContain "script"
    }

    "toPlainText collapses whitespace" {
      RichTextProcessor.toPlainText("<p>Hello   <strong>world</strong></p>") shouldBe "Hello world"
    }

    "isPlainText detects html tags" {
      RichTextProcessor.isPlainText("plain") shouldBe true
      RichTextProcessor.isPlainText("<p>html</p>") shouldBe false
    }

    "sanitizeDescriptionHtml keeps valid attachment images and strips external images" {
      val validSrc = "/api/projects/prj_01/work-items/iss_01/attachments/att_01/content"
      val sanitized =
        RichTextProcessor.sanitizeDescriptionHtml(
          """<p>See image</p><img src="$validSrc" alt="diagram"><img src="https://evil.test/x.png">"""
        )
      sanitized shouldContain validSrc
      sanitized shouldNotContain "evil.test"
    }

    "processCommentInput strips image tags" {
      val processed =
        RichTextProcessor.processCommentInput(
          """<p>Comment</p><img src="/api/projects/prj_01/work-items/iss_01/attachments/att_01/content">"""
        )
      processed?.html shouldNotContain "<img"
    }
  })
