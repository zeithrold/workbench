package ink.doa.workbench.agile.workitem.template

import kotlinx.serialization.json.JsonElement

data class WorkItemValueTemplate(
  val version: Int = CURRENT_VERSION,
  val resource: String = RESOURCE,
  val target: WorkItemValueTemplateTarget,
  val values: Map<TemplateField, TemplateValueExpression>,
) {
  companion object {
    const val RESOURCE = "work_item"
    const val CURRENT_VERSION = 1
  }
}

enum class WorkItemValueTemplateTarget(val wireName: String) {
  CREATE("create"),
  TRANSITION("transition");

  companion object {
    fun fromWireName(value: String): WorkItemValueTemplateTarget? = entries.firstOrNull {
      it.wireName == value
    }
  }
}

sealed interface TemplateField {
  val canonicalName: String

  data class System(override val canonicalName: String) : TemplateField

  data class Property(val apiId: String?, val code: String?) : TemplateField {
    init {
      require(!apiId.isNullOrBlank() || !code.isNullOrBlank()) {
        "Property field requires apiId or code."
      }
    }

    override val canonicalName: String = "property.${apiId ?: code}"
  }
}

sealed interface TemplateValueExpression {
  data class Literal(val value: JsonElement) : TemplateValueExpression

  data class Variable(val name: String) : TemplateValueExpression

  data class RelativeDate(
    val amount: Int,
    val unit: TemplateRelativeDateUnit,
    val direction: TemplateDateDirection,
    val anchor: String,
  ) : TemplateValueExpression

  data class Copy(val field: TemplateField) : TemplateValueExpression

  data object Clear : TemplateValueExpression
}

enum class TemplateRelativeDateUnit(val wireName: String) {
  DAY("day"),
  WEEK("week"),
  MONTH("month"),
  YEAR("year");

  companion object {
    fun fromWireName(value: String): TemplateRelativeDateUnit? = entries.firstOrNull {
      it.wireName == value
    }
  }
}

enum class TemplateDateDirection(val wireName: String) {
  PAST("past"),
  FUTURE("future");

  companion object {
    fun fromWireName(value: String): TemplateDateDirection? = entries.firstOrNull {
      it.wireName == value
    }
  }
}
