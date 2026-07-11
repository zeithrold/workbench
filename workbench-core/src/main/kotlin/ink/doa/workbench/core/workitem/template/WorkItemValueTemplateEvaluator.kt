package ink.doa.workbench.core.workitem.template

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyRecord
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

data class WorkItemValueTemplateContext(
  val tenantId: UUID,
  val projectId: UUID,
  val currentUserApiId: String,
  val currentProjectApiId: String,
  val actorUserId: UUID,
  val reporterUserId: UUID? = null,
  val workItem: WorkItemRecord? = null,
  val currentProperties: Map<String, JsonElement> = emptyMap(),
)

class WorkItemValueTemplateEvaluator(private val clock: Clock = Clock.systemUTC()) {
  fun evaluate(
    template: WorkItemValueTemplate,
    config: IssueTypeConfigDetails,
    context: WorkItemValueTemplateContext,
  ): Map<String, JsonElement> {
    WorkItemValueTemplateValidator.validate(template, config)
    return template.values
      .map { (field, expression) ->
        val property =
          (field as? TemplateField.Property)?.let {
            WorkItemValueTemplateValidator.resolveProperty(it, config)
          }
        field.outputKey(config) to evaluateExpression(expression, property, context)
      }
      .toMap()
  }

  fun evaluatePropertyDefault(
    property: IssueTypeConfigPropertyRecord,
    expression: TemplateValueExpression,
    config: IssueTypeConfigDetails,
    context: WorkItemValueTemplateContext,
  ): JsonElement {
    WorkItemValueTemplateValidator.validateExpression(expression, config, property)
    return evaluateExpression(expression, property, context)
  }

  fun evaluateExpression(
    expression: TemplateValueExpression,
    targetProperty: IssueTypeConfigPropertyRecord?,
    context: WorkItemValueTemplateContext,
  ): JsonElement =
    when (expression) {
      is TemplateValueExpression.Literal -> expression.value
      is TemplateValueExpression.Variable -> resolveVariable(expression.name, context)
      is TemplateValueExpression.RelativeDate -> resolveRelativeDate(expression, targetProperty)
      is TemplateValueExpression.Copy -> resolveCopy(expression.field, context)
      is TemplateValueExpression.Clear -> JsonNull
    }

  private fun resolveVariable(name: String, context: WorkItemValueTemplateContext): JsonElement =
    when (name) {
      "user.currentUser" -> JsonPrimitive(context.currentUserApiId)
      "project.currentProject" -> JsonPrimitive(context.currentProjectApiId)
      "date.now" -> JsonPrimitive(OffsetDateTime.now(clock).toString())
      "date.today" -> JsonPrimitive(LocalDate.now(clock).toString())
      "date.startOfWeek" -> JsonPrimitive(today().startOfWeek().toString())
      "date.endOfWeek" -> JsonPrimitive(today().endOfWeek().toString())
      else ->
        if (name.startsWith("workItem.current.")) {
          resolveCopy(
            WorkItemValueTemplateParser().parseFieldPath(name.removePrefix("workItem.current.")),
            context,
          )
        } else if (name.startsWith("workItem.previous.")) {
          resolveCopy(
            WorkItemValueTemplateParser().parseFieldPath(name.removePrefix("workItem.previous.")),
            context,
          )
        } else {
          throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_TEMPLATE_VARIABLE_UNKNOWN,
            "Unknown work item value template variable: $name",
          )
        }
    }

  private fun resolveRelativeDate(
    expression: TemplateValueExpression.RelativeDate,
    targetProperty: IssueTypeConfigPropertyRecord?,
  ): JsonElement {
    if (expression.amount <= 0) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_TEMPLATE_RELATIVE_DATE_AMOUNT_POSITIVE
      )
    }
    val dateLike =
      when (expression.anchor) {
        "date.now" -> OffsetDateTime.now(clock)
        "date.today" -> LocalDate.now(clock)
        "date.startOfWeek" -> today().startOfWeek()
        "date.endOfWeek" -> today().endOfWeek()
        else ->
          throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_TEMPLATE_RELATIVE_DATE_ANCHOR_UNKNOWN,
            "Unknown relative date anchor: ${expression.anchor}",
          )
      }
    val shifted =
      when (dateLike) {
        is OffsetDateTime -> dateLike.shift(expression)
        is LocalDate -> dateLike.shift(expression)
        else -> dateLike
      }
    return when (targetProperty?.dataType) {
      WorkItemPropertyDataType.DATETIME ->
        JsonPrimitive(
          (shifted as? OffsetDateTime
              ?: (shifted as LocalDate).atStartOfDay().atOffset(OffsetDateTime.now(clock).offset))
            .toString()
        )
      else ->
        JsonPrimitive(
          (shifted as? LocalDate ?: (shifted as OffsetDateTime).toLocalDate()).toString()
        )
    }
  }

  private fun resolveCopy(
    field: TemplateField,
    context: WorkItemValueTemplateContext,
  ): JsonElement =
    when (field) {
      is TemplateField.Property ->
        field.code?.let { context.currentProperties[it] }
          ?: field.apiId?.let { context.currentProperties[it] }
          ?: context.workItem?.properties?.get(field.code)
          ?: context.workItem?.properties?.get(field.apiId)
          ?: JsonNull
      is TemplateField.System -> resolveSystemField(field.canonicalName, context)
    }

  private fun resolveSystemField(name: String, context: WorkItemValueTemplateContext): JsonElement =
    when (name) {
      "title" -> context.workItem?.title?.let(::JsonPrimitive) ?: JsonNull
      "description" -> context.workItem?.description?.let(::JsonPrimitive) ?: JsonNull
      "assignee" -> context.workItem?.assigneeApiId?.value?.let(::JsonPrimitive) ?: JsonNull
      "priority" -> context.workItem?.priorityApiId?.value?.let(::JsonPrimitive) ?: JsonNull
      "sprint" -> context.workItem?.sprintApiId?.value?.let(::JsonPrimitive) ?: JsonNull
      "project" -> JsonPrimitive(context.currentProjectApiId)
      else -> JsonNull
    }

  private fun TemplateField.outputKey(config: IssueTypeConfigDetails): String =
    when (this) {
      is TemplateField.System -> canonicalName
      is TemplateField.Property -> WorkItemValueTemplateValidator.resolveProperty(this, config).code
    }

  private fun OffsetDateTime.shift(
    expression: TemplateValueExpression.RelativeDate
  ): OffsetDateTime {
    val amount = expression.signedAmount()
    return when (expression.unit) {
      TemplateRelativeDateUnit.DAY -> plusDays(amount)
      TemplateRelativeDateUnit.WEEK -> plusWeeks(amount)
      TemplateRelativeDateUnit.MONTH -> plusMonths(amount)
      TemplateRelativeDateUnit.YEAR -> plusYears(amount)
    }
  }

  private fun LocalDate.shift(expression: TemplateValueExpression.RelativeDate): LocalDate {
    val amount = expression.signedAmount()
    return when (expression.unit) {
      TemplateRelativeDateUnit.DAY -> plusDays(amount)
      TemplateRelativeDateUnit.WEEK -> plusWeeks(amount)
      TemplateRelativeDateUnit.MONTH -> plusMonths(amount)
      TemplateRelativeDateUnit.YEAR -> plusYears(amount)
    }
  }

  private fun today(): LocalDate = LocalDate.now(clock)
}

private fun TemplateValueExpression.RelativeDate.signedAmount(): Long =
  when (direction) {
    TemplateDateDirection.PAST -> -amount.toLong()
    TemplateDateDirection.FUTURE -> amount.toLong()
  }

private fun LocalDate.startOfWeek(): LocalDate = minusDays((dayOfWeek.value - 1).toLong())

private fun LocalDate.endOfWeek(): LocalDate = plusDays((7 - dayOfWeek.value).toLong())
