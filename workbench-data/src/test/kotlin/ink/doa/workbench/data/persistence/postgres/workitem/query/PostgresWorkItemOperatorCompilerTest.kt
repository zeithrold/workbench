package ink.doa.workbench.data.persistence.postgres.workitem.query

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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.postgresql.util.PGobject

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

    "compile NEQ NOT_IN and comparison operators" {
      compiler
        .compile(
          "ipv.value_number",
          WorkItemQueryFieldType.NUMBER,
          QueryOperator.NEQ,
          QueryValue.Literal(JsonPrimitive(2)),
        )
        .sql shouldBe "ipv.value_number <> ?"
      compiler
        .compile(
          "i.status_id",
          WorkItemQueryFieldType.TEXT,
          QueryOperator.NOT_IN,
          QueryValue.Literal(JsonArray(listOf(JsonPrimitive("a")))),
        )
        .sql shouldContain "NOT IN"
      compiler
        .compile(
          "ipv.value_number",
          WorkItemQueryFieldType.NUMBER,
          QueryOperator.GT,
          QueryValue.Literal(JsonPrimitive(1)),
        )
        .sql shouldBe "ipv.value_number > ?"
      compiler
        .compile(
          "ipv.value_number",
          WorkItemQueryFieldType.NUMBER,
          QueryOperator.GTE,
          QueryValue.Literal(JsonPrimitive(1)),
        )
        .sql shouldBe "ipv.value_number >= ?"
      compiler
        .compile(
          "ipv.value_number",
          WorkItemQueryFieldType.NUMBER,
          QueryOperator.LT,
          QueryValue.Literal(JsonPrimitive(1)),
        )
        .sql shouldBe "ipv.value_number < ?"
      compiler
        .compile(
          "ipv.value_number",
          WorkItemQueryFieldType.NUMBER,
          QueryOperator.LTE,
          QueryValue.Literal(JsonPrimitive(1)),
        )
        .sql shouldBe "ipv.value_number <= ?"
    }

    "compile ENDS_WITH MATCHES and empty checks" {
      compiler
        .compile(
          "i.title",
          WorkItemQueryFieldType.TEXT,
          QueryOperator.ENDS_WITH,
          QueryValue.Literal(JsonPrimitive("end")),
        )
        .sql shouldContain "ILIKE"
      compiler
        .compile(
          "i.title",
          WorkItemQueryFieldType.TEXT,
          QueryOperator.MATCHES,
          QueryValue.Literal(JsonPrimitive("pat.*")),
        )
        .sql shouldContain "~*"
      compiler
        .compile("ipv.value_text", WorkItemQueryFieldType.TEXT, QueryOperator.IS_NOT_EMPTY, null)
        .sql shouldContain "NOT"
      compiler
        .compile("ipv.value_json", WorkItemQueryFieldType.JSON, QueryOperator.IS_EMPTY, null)
        .sql shouldContain "'[]'::jsonb"
    }

    "compile jsonb membership operators" {
      val array = JsonArray(listOf(JsonPrimitive("x")))
      compiler
        .compile(
          "ipv.value_json",
          WorkItemQueryFieldType.JSON,
          QueryOperator.HAS_ANY,
          QueryValue.Literal(array),
        )
        .sql shouldContain "?|"
      compiler
        .compile(
          "ipv.value_json",
          WorkItemQueryFieldType.JSON,
          QueryOperator.HAS_ALL,
          QueryValue.Literal(array),
        )
        .sql shouldContain "?&"
      compiler
        .compile(
          "ipv.value_json",
          WorkItemQueryFieldType.JSON,
          QueryOperator.HAS_NONE,
          QueryValue.Literal(array),
        )
        .sql shouldContain "NOT"
    }

    "compile NOT_CONTAINS negates predicate" {
      compiler
        .compile(
          "i.title",
          WorkItemQueryFieldType.TEXT,
          QueryOperator.NOT_CONTAINS,
          QueryValue.Literal(JsonPrimitive("bug")),
        )
        .sql shouldContain "NOT"
    }

    "compile date comparison aliases and future relative windows" {
      compiler
        .compile(
          "i.updated_at",
          WorkItemQueryFieldType.DATETIME,
          QueryOperator.BEFORE,
          QueryValue.Literal(JsonPrimitive("2026-07-04T00:00:00Z")),
        )
        .sql shouldContain "<"
      compiler
        .compile(
          "i.updated_at",
          WorkItemQueryFieldType.DATETIME,
          QueryOperator.AFTER,
          QueryValue.Literal(JsonPrimitive("2026-07-04T00:00:00Z")),
        )
        .sql shouldContain ">"
      compiler
        .compile(
          "i.updated_at",
          WorkItemQueryFieldType.DATETIME,
          QueryOperator.ON_OR_BEFORE,
          QueryValue.Literal(JsonPrimitive("2026-07-04T00:00:00Z")),
        )
        .sql shouldContain "<="
      compiler
        .compile(
          "i.updated_at",
          WorkItemQueryFieldType.DATETIME,
          QueryOperator.ON_OR_AFTER,
          QueryValue.Literal(JsonPrimitive("2026-07-04T00:00:00Z")),
        )
        .sql shouldContain ">="
      compiler
        .compile(
          "i.updated_at",
          WorkItemQueryFieldType.DATETIME,
          QueryOperator.WITHIN,
          QueryValue.RelativeDate(
            amount = 3,
            unit = RelativeDateUnit.WEEK,
            direction = DateDirection.FUTURE,
            anchor = "date.today",
          ),
        )
        .sql shouldContain "current_date"
    }

    "compile contains on json uses jsonb containment" {
      val fragment =
        compiler.compile(
          valueSql = "ipv.value_json",
          type = WorkItemQueryFieldType.JSON,
          op = QueryOperator.CONTAINS,
          value = QueryValue.Literal(JsonObject(mapOf("tier" to JsonPrimitive("gold")))),
        )

      fragment.sql shouldContain "@>"
    }

    "operand helpers convert json primitives and objects" {
      jsonToJdbcValue(JsonPrimitive(true)) shouldBe true
      jsonToJdbcValue(JsonNull) shouldBe null
      val jsonb = jsonToJdbcValue(JsonObject(mapOf("a" to JsonPrimitive(1)))) as PGobject
      jsonb.type shouldBe "jsonb"
    }
  })
