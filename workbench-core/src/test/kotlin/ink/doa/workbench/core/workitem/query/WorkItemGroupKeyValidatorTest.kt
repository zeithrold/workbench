package ink.doa.workbench.core.workitem.query

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive

class WorkItemGroupKeyValidatorTest :
  StringSpec({
    val validator = WorkItemGroupKeyValidator()
    val groupField = QueryField.System("statusGroup")

    fun eqKey(field: QueryField = groupField, value: String = "todo") =
      ConditionNode.Predicate(
        field = field,
        op = QueryOperator.EQ,
        value = QueryValue.Literal(JsonPrimitive(value)),
      )

    "accepts valid eq group key" {
      validator.validateGroupKey(eqKey(), groupField)
    }

    "accepts valid is_empty group key" {
      validator.validateGroupKey(
        ConditionNode.Predicate(field = groupField, op = QueryOperator.IS_EMPTY, value = null),
        groupField,
      )
    }

    "rejects group key field mismatch" {
      shouldThrow<InvalidRequestException> {
          validator.validateGroupKey(eqKey(field = QueryField.System("priority")), groupField)
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_QUERY_GROUP_KEY_FIELD_MISMATCH
    }

    "rejects unsupported group key operator" {
      shouldThrow<InvalidRequestException> {
          validator.validateGroupKey(
            ConditionNode.Predicate(
              field = groupField,
              op = QueryOperator.IN,
              value = QueryValue.Literal(JsonPrimitive("todo")),
            ),
            groupField,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_QUERY_GROUP_KEY_OPERATOR_UNSUPPORTED
    }

    "rejects non-groupable field" {
      val titleField = QueryField.System("title")

      shouldThrow<InvalidRequestException> {
          validator.validateGroupKey(eqKey(field = titleField), titleField)
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_QUERY_FIELD_NOT_GROUPABLE
    }

    "rejects value on is_empty group key" {
      shouldThrow<InvalidRequestException> {
          validator.validateGroupKey(
            ConditionNode.Predicate(
              field = groupField,
              op = QueryOperator.IS_EMPTY,
              value = QueryValue.Literal(JsonPrimitive("todo")),
            ),
            groupField,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_VALUE_FORBIDDEN
    }

    "accepts empty group scope" {
      validator.validateGroupScope(WorkItemSearchGroupScope(), groupField)
    }

    "validates include group keys against query group field" {
      validator.validateGroupScope(
        WorkItemSearchGroupScope(includeGroupKeys = listOf(eqKey())),
        groupField,
      )
    }

    "validates exclude group keys against query group field" {
      validator.validateGroupScope(
        WorkItemSearchGroupScope(
          excludeGroupKeys =
            listOf(
              ConditionNode.Predicate(
                field = groupField,
                op = QueryOperator.IS_EMPTY,
                value = null,
              )
            )
        ),
        groupField,
      )
    }

    "rejects group scope without query group" {
      shouldThrow<InvalidRequestException> {
          validator.validateGroupScope(
            WorkItemSearchGroupScope(includeGroupKeys = listOf(eqKey())),
            groupField = null,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_QUERY_GROUP_REQUIRED
    }
  })
