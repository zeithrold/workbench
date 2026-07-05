package ink.doa.workbench.core.workitem.query

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive

class WorkItemSearchGroupScopeTest :
  StringSpec({
    "rejects include and exclude keys together" {
      val key =
        ConditionNode.Predicate(
          field = QueryField.System("statusGroup"),
          op = QueryOperator.EQ,
          value = QueryValue.Literal(JsonPrimitive("todo")),
        )

      shouldThrow<IllegalArgumentException> {
        WorkItemSearchGroupScope(includeGroupKeys = listOf(key), excludeGroupKeys = listOf(key))
      }
    }

    "accepts include-only scope" {
      val key =
        ConditionNode.Predicate(
          field = QueryField.System("statusGroup"),
          op = QueryOperator.EQ,
          value = QueryValue.Literal(JsonPrimitive("todo")),
        )

      WorkItemSearchGroupScope(includeGroupKeys = listOf(key)).includeGroupKeys.single() shouldBe
        key
    }
  })
