package ink.doa.workbench.data.persistence.postgres.workitem.query

import ink.doa.workbench.agile.workitem.query.QueryValue
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class QueryValueSqlExtensionsTest :
  StringSpec({
    "toOperand wraps literal primitives" {
      QueryValue.Literal(JsonPrimitive(3)).toOperand().sql shouldBe "?"
      QueryValue.Literal(JsonPrimitive("x")).toOperand().params.single() shouldBe "x"
    }

    "toOperand maps trusted date variables" {
      QueryValue.Variable("date.now").toOperand().sql shouldBe "now()"
      QueryValue.Variable("date.today").toOperand().sql shouldBe "current_date"
    }

    "asLiteralArray extracts json array elements" {
      QueryValue.Literal(JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))))
        .asLiteralArray() shouldBe listOf(JsonPrimitive("a"), JsonPrimitive("b"))
    }

    "requireStringLiteral rejects non-literal values" {
      shouldThrow<InvalidRequestException> {
        QueryValue.Variable("date.now").requireStringLiteral()
      }
    }

    "requireStringLiteral rejects array literals" {
      shouldThrow<InvalidRequestException> {
        QueryValue.Literal(JsonArray(listOf(JsonPrimitive("a")))).requireStringLiteral()
      }
    }

    "jsonToJdbcValue converts primitives" {
      jsonToJdbcValue(JsonPrimitive(true)) shouldBe true
      jsonToJdbcValue(JsonPrimitive(2.5)) shouldBe 2.5
      jsonToJdbcValue(JsonPrimitive("text")) shouldBe "text"
      jsonToJdbcValue(kotlinx.serialization.json.JsonNull) shouldBe null
    }

    "toOperand rejects unknown trusted variables" {
      shouldThrow<InvalidRequestException> {
        QueryValue.Variable("unknown.var").toOperand()
      }
    }
  })
