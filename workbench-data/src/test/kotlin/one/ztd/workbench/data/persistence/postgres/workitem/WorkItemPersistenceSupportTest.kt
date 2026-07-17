package one.ztd.workbench.data.persistence.postgres.workitem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyDataType
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyValue
import one.ztd.workbench.agile.workitem.query.WorkItemQueryFieldType
import one.ztd.workbench.data.persistence.postgres.workitem.query.toWorkItemFieldType
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.ids.PublicId

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
