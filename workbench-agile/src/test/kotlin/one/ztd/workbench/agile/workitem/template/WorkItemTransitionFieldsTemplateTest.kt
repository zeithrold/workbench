package one.ztd.workbench.agile.workitem.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class WorkItemTransitionFieldsTemplateTest :
  StringSpec({
    "fieldPathFromPropertyKey prefixes bare property keys" {
      fieldPathFromPropertyKey("resolution") shouldBe
        TemplateField.Property(apiId = null, code = "resolution")
    }

    "fieldPathFromPropertyKey preserves explicit property paths" {
      fieldPathFromPropertyKey("property.dueDate") shouldBe
        TemplateField.Property(apiId = null, code = "dueDate")
    }

    "fieldPathFromPropertyKey recognizes system field names" {
      fieldPathFromPropertyKey("assignee") shouldBe TemplateField.System("assignee")
      fieldPathFromPropertyKey("title") shouldBe TemplateField.System("title")
    }

    "toPermissionResourceId maps system and property fields" {
      TemplateField.System("title").toPermissionResourceId() shouldBe "issue:field:system.title"
      TemplateField.Property(apiId = null, code = "resolution").toPermissionResourceId() shouldBe
        "issue:field:property.resolution"
      TemplateField.Property(apiId = "fld_123", code = null).toPermissionResourceId() shouldBe
        "issue:field:property.fld_123"
    }

    "toWirePath maps system and property fields" {
      TemplateField.System("assignee").toWirePath() shouldBe "assignee"
      TemplateField.Property(apiId = null, code = "resolution").toWirePath() shouldBe
        "property.resolution"
      TemplateField.Property(apiId = "fld_123", code = null).toWirePath() shouldBe
        "property.fld_123"
    }

    "isNonNullValue distinguishes null json values" {
      (null as kotlinx.serialization.json.JsonElement?).isNonNullValue() shouldBe false
      JsonNull.isNonNullValue() shouldBe false
      JsonPrimitive("value").isNonNullValue() shouldBe true
    }

    "property field requires apiId or code" {
      shouldThrow<IllegalArgumentException> { TemplateField.Property(apiId = null, code = null) }
      shouldThrow<IllegalArgumentException> { TemplateField.Property(apiId = "  ", code = "  ") }
    }

    "transition fields template defaults version and resource" {
      WorkItemTransitionFieldsTemplate().version shouldBe
        WorkItemTransitionFieldsTemplate.CURRENT_VERSION
      WorkItemTransitionFieldsTemplate().resource shouldBe WorkItemTransitionFieldsTemplate.RESOURCE
    }
  })
