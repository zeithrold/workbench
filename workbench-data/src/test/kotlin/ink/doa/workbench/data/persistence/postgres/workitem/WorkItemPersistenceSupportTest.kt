package ink.doa.workbench.data.persistence.postgres.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.core.workitem.query.WorkItemQueryFieldType
import ink.doa.workbench.data.persistence.postgres.workitem.query.toWorkItemFieldType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class WorkItemPersistenceSupportTest :
  StringSpec({
    "asObject wraps non-object json as empty object" {
      JsonPrimitive("text").asObject() shouldBe JsonObject(emptyMap())
      JsonObject(mapOf("a" to JsonPrimitive(1))).asObject() shouldBe
        JsonObject(mapOf("a" to JsonPrimitive(1)))
    }

    "snapshot builds property code map" {
      val propertyId = UUID.randomUUID()
      snapshot(
        listOf(
          WorkItemPropertyValue(
            propertyId = propertyId,
            propertyApiId = PublicId.new("fld"),
            code = "points",
            dataType = WorkItemPropertyDataType.NUMBER,
            value = JsonPrimitive(5),
          )
        )
      ) shouldBe JsonObject(mapOf("points" to JsonPrimitive(5)))
    }

    "maps database property types to query field types" {
      "text".toWorkItemFieldType() shouldBe WorkItemQueryFieldType.TEXT
      "multi_select".toWorkItemFieldType() shouldBe WorkItemQueryFieldType.MULTI_SELECT
      "datetime".toWorkItemFieldType() shouldBe WorkItemQueryFieldType.DATETIME
    }

    "rejects unsupported property types" {
      shouldThrow<InvalidRequestException> { "currency".toWorkItemFieldType() }
    }
  })
