package ink.doa.workbench.core.workitem.richtext

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class AttachmentReferenceParserTest :
  StringSpec({
    "extractContentReferences parses attachment content urls from html" {
      val html = """<img src="/api/projects/prj_01/work-items/iss_01/attachments/att_01/content">"""
      AttachmentReferenceParser.extractContentReferences(html).shouldHaveSize(1).first().apply {
        projectApiId shouldBe "prj_01"
        workItemApiId shouldBe "iss_01"
        attachmentApiId shouldBe "att_01"
      }
    }

    "isAllowedAttachmentContentUrl rejects external urls" {
      AttachmentReferenceParser.isAllowedAttachmentContentUrl("https://evil.test/x.png") shouldBe
        false
    }
  })
