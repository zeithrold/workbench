package ink.doa.workbench.agile.workitem.query

import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

@Tags("fuzz")
class WorkItemQueryFuzzTest :
  StringSpec({
    val parser = WorkItemQueryParser()
    val validator = WorkItemQueryValidator()

    "canonical bounded queries parse and validate" {
      checkAll(iterations = FUZZ_ITERATIONS, validQueryPayloads) { payload ->
        validator.validate(parser.parse(payload))
      }
    }

    "directed malformed queries are rejected with domain errors" {
      checkAll(iterations = FUZZ_ITERATIONS, Arb.element(directedMalformedQueries)) { payload ->
        shouldThrowDomainError { validator.validate(parser.parse(payload)) }
      }
    }

    "bounded arbitrary text is rejected with domain errors" {
      checkAll(iterations = FUZZ_ITERATIONS, Arb.string(0..MAX_ARBITRARY_TEXT_LENGTH)) { text ->
        shouldThrowDomainError { parser.parse("$text!") }
      }
    }

    "query depth and predicate limits keep their boundary contract" {
      checkAll(iterations = FUZZ_ITERATIONS, Arb.element(queryLimitBoundaryCases)) { boundary ->
        val outcome = runCatching { validator.validate(parser.parse(boundary.payload)) }
        if (boundary.accepted) {
          outcome.exceptionOrNull() shouldBe null
        } else {
          outcome.exceptionOrNull().shouldBeInstanceOfDomainError()
        }
      }
    }
  })

private const val FUZZ_ITERATIONS = 1_000
private const val MAX_ARBITRARY_TEXT_LENGTH = 512

private val validQueryPayloads =
  Arb.bind(Arb.int(0..7), Arb.int(1..64), Arb.boolean()) { nestedNotCount, predicateCount, grouped
    ->
    canonicalQuery(nestedNotCount, predicateCount, grouped)
  }

private val directedMalformedQueries =
  listOf(
    """{"version":1,"resource":"work_item","where":{"field":{"kind":"property"},"op":"eq","value":"bug"}}""",
    """{"version":1,"resource":"work_item","where":{"field":null,"op":"eq","value":"bug"}}""",
    """{"version":1,"resource":"work_item","where":{"field":"title","op":"xor","value":"bug"}}""",
    """{"version":1,"resource":"work_item","where":{"op":"and","args":"not-an-array"}}""",
    """{"version":"one","resource":"work_item"}""",
    """{"version":1,"resource":"work_item","sort":[{"field":"createdAt","direction":"sideways"}]}""",
    """{"version":1,"resource":"work_item","where":{"field":{"kind":"unknown","apiId":"fld_1"},"op":"eq","value":"bug"}}""",
  )

private val canonicalPredicates =
  listOf(
    """{"field":"statusGroup","op":"eq","value":"todo"}""",
    """{"field":"title","op":"contains","value":"bug"}""",
    """{"field":"assignee","op":"eq","value":{"var":"user.currentUser"}}""",
  )

private val queryLimitBoundaryCases =
  listOf(
    QueryLimitBoundaryCase(nestedPredicateQuery(depth = 8), accepted = true),
    QueryLimitBoundaryCase(nestedPredicateQuery(depth = 9), accepted = false),
    QueryLimitBoundaryCase(
      canonicalQuery(nestedNotCount = 0, predicateCount = 64, grouped = false),
      accepted = true,
    ),
    QueryLimitBoundaryCase(
      canonicalQuery(nestedNotCount = 0, predicateCount = 65, grouped = false),
      accepted = false,
    ),
  )

private data class QueryLimitBoundaryCase(val payload: String, val accepted: Boolean)

private fun canonicalQuery(nestedNotCount: Int, predicateCount: Int, grouped: Boolean): String {
  val predicates =
    (1..predicateCount).joinToString(",") { index ->
      canonicalPredicates[(index - 1) % canonicalPredicates.size]
    }
  var condition = """{"op":"and","args":[$predicates]}"""
  repeat(nestedNotCount) { condition = """{"op":"not","arg":$condition}""" }
  val group = if (grouped) ",\"group\":{\"field\":\"statusGroup\",\"direction\":\"asc\"}" else ""
  return """{"version":1,"resource":"work_item","where":$condition,"sort":[{"field":"createdAt","direction":"desc"}]$group}"""
}

private fun nestedPredicateQuery(depth: Int): String {
  var condition = """{"field":"statusGroup","op":"eq","value":"todo"}"""
  repeat(depth) { condition = """{"op":"not","arg":$condition}""" }
  return """{"version":1,"resource":"work_item","where":$condition}"""
}

private fun shouldThrowDomainError(block: () -> Unit) {
  runCatching(block).exceptionOrNull().shouldBeInstanceOfDomainError()
}

private fun Throwable?.shouldBeInstanceOfDomainError() {
  (this is InvalidRequestException) shouldBe true
}
