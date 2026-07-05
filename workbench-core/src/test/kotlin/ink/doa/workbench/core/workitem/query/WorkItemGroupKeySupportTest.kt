package ink.doa.workbench.core.workitem.query

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class WorkItemGroupKeySupportTest :
  StringSpec({
    val field = QueryField.System("statusGroup")

    "maps null bucket value to is_empty group key" {
      WorkItemGroupKeySupport.keyFromBucketValue(field, null) shouldBe
        ConditionNode.Predicate(field = field, op = QueryOperator.IS_EMPTY, value = null)
    }

    "maps non-null bucket value to eq group key" {
      WorkItemGroupKeySupport.keyFromBucketValue(field, "todo") shouldBe
        ConditionNode.Predicate(
          field = field,
          op = QueryOperator.EQ,
          value = QueryValue.Literal(JsonPrimitive("todo")),
        )
    }

    "extracts bucket value from eq group key" {
      val key =
        ConditionNode.Predicate(
          field = field,
          op = QueryOperator.EQ,
          value = QueryValue.Literal(JsonPrimitive("todo")),
        )

      WorkItemGroupKeySupport.bucketValueFromKey(key) shouldBe "todo"
    }

    "extracts null bucket value from is_empty group key" {
      val key = ConditionNode.Predicate(field = field, op = QueryOperator.IS_EMPTY, value = null)

      WorkItemGroupKeySupport.bucketValueFromKey(key) shouldBe null
    }

    "returns null bucket value for unsupported operators" {
      val key =
        ConditionNode.Predicate(
          field = field,
          op = QueryOperator.IN,
          value = QueryValue.Literal(JsonPrimitive("todo")),
        )

      WorkItemGroupKeySupport.bucketValueFromKey(key) shouldBe null
    }

    "converts jdbc values to json elements" {
      WorkItemGroupKeySupport.toJsonElement(null) shouldBe JsonNull
      WorkItemGroupKeySupport.toJsonElement("todo") shouldBe JsonPrimitive("todo")
      WorkItemGroupKeySupport.toJsonElement(42L) shouldBe JsonPrimitive(42L)
      WorkItemGroupKeySupport.toJsonElement(3.5) shouldBe JsonPrimitive(3.5)
      WorkItemGroupKeySupport.toJsonElement(true) shouldBe JsonPrimitive(true)
      WorkItemGroupKeySupport.toJsonElement(listOf("x")) shouldBe JsonPrimitive("[x]")
    }

    "converts json elements to jdbc values" {
      WorkItemGroupKeySupport.toJdbcValue(JsonNull) shouldBe null
      WorkItemGroupKeySupport.toJdbcValue(JsonPrimitive(true)) shouldBe true
      WorkItemGroupKeySupport.toJdbcValue(JsonPrimitive(42)) shouldBe 42L
      WorkItemGroupKeySupport.toJdbcValue(JsonPrimitive(3.5)) shouldBe 3.5
      WorkItemGroupKeySupport.toJdbcValue(JsonPrimitive("todo")) shouldBe "todo"
    }
  })
