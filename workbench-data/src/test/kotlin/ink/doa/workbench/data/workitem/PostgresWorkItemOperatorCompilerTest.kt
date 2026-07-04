package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.workitem.query.DateDirection
import ink.doa.workbench.core.workitem.query.QueryOperator
import ink.doa.workbench.core.workitem.query.QueryValue
import ink.doa.workbench.core.workitem.query.RelativeDateUnit
import ink.doa.workbench.core.workitem.query.WorkItemQueryFieldType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

class PostgresWorkItemOperatorCompilerTest :
  StringSpec({
    val compiler = PostgresWorkItemOperatorCompiler()

    "compile EQ emits equality predicate" {
      val fragment =
        compiler.compile(
          valueSql = "ipv.value_number",
          type = WorkItemQueryFieldType.NUMBER,
          op = QueryOperator.EQ,
          value = QueryValue.Literal(JsonPrimitive(5)),
        )

      fragment.sql shouldBe "ipv.value_number = ?"
      fragment.params shouldBe listOf(5.0)
    }

    "compile BETWEEN emits range predicate" {
      val fragment =
        compiler.compile(
          valueSql = "ipv.value_number",
          type = WorkItemQueryFieldType.NUMBER,
          op = QueryOperator.BETWEEN,
          value = QueryValue.Between(JsonPrimitive(1), JsonPrimitive(10)),
        )

      fragment.sql shouldContain ">="
      fragment.sql shouldContain "<="
      fragment.params shouldBe listOf(1.0, 10.0)
    }

    "compile IS_EMPTY checks null for scalar fields" {
      val fragment =
        compiler.compile(
          valueSql = "ipv.value_text",
          type = WorkItemQueryFieldType.TEXT,
          op = QueryOperator.IS_EMPTY,
          value = null,
        )

      fragment.sql shouldContain "IS NULL"
    }

    "compile BETWEEN rejects non-between value" {
      shouldThrow<ink.doa.workbench.core.common.errors.InvalidRequestException> {
        compiler.compile(
          valueSql = "ipv.value_number",
          type = WorkItemQueryFieldType.NUMBER,
          op = QueryOperator.BETWEEN,
          value = QueryValue.Literal(JsonPrimitive(1)),
        )
      }
    }

    "compile CONTAINS emits ilike predicate for text fields" {
      val fragment =
        compiler.compile(
          valueSql = "ipv.value_text",
          type = WorkItemQueryFieldType.TEXT,
          op = QueryOperator.CONTAINS,
          value = QueryValue.Literal(JsonPrimitive("bug")),
        )

      fragment.sql shouldContain "ILIKE"
      fragment.params shouldBe listOf("%bug%")
    }

    "compile IN expands literal array" {
      val fragment =
        compiler.compile(
          valueSql = "i.status_id",
          type = WorkItemQueryFieldType.TEXT,
          op = QueryOperator.IN,
          value = QueryValue.Literal(JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b")))),
        )

      fragment.sql shouldContain "IN"
      fragment.params shouldBe listOf("a", "b")
    }

    "compile WITHIN emits relative date window" {
      val fragment =
        compiler.compile(
          valueSql = "i.updated_at",
          type = WorkItemQueryFieldType.DATE,
          op = QueryOperator.WITHIN,
          value =
            QueryValue.RelativeDate(
              amount = 7,
              unit = RelativeDateUnit.DAY,
              direction = DateDirection.PAST,
              anchor = "date.now",
            ),
        )

      fragment.sql shouldContain "interval"
    }

    "compile STARTS_WITH escapes like wildcards" {
      val fragment =
        compiler.compile(
          valueSql = "i.title",
          type = WorkItemQueryFieldType.TEXT,
          op = QueryOperator.STARTS_WITH,
          value = QueryValue.Literal(JsonPrimitive("100%")),
        )

      fragment.sql shouldContain "ILIKE"
      fragment.params.single().toString() shouldContain "100"
    }
  })
