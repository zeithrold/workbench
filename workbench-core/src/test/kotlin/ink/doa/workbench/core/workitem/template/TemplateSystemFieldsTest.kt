package ink.doa.workbench.core.workitem.template

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TemplateSystemFieldsTest :
  StringSpec({
    "writable system fields match template validator allowlist" {
      TemplateSystemFields.WRITABLE shouldBe
        setOf("title", "description", "assignee", "priority", "sprint")
    }

    "isWritableSystemField recognizes known fields" {
      TemplateSystemFields.isWritableSystemField("title") shouldBe true
      TemplateSystemFields.isWritableSystemField("custom") shouldBe false
    }
  })
