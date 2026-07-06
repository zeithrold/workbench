package ink.doa.workbench.core.workitem.template

import kotlinx.serialization.json.JsonElement

data class WorkItemTransitionFieldsTemplate(
  val version: Int = CURRENT_VERSION,
  val resource: String = RESOURCE,
  val target: WorkItemValueTemplateTarget = WorkItemValueTemplateTarget.TRANSITION,
  val fields: Map<TemplateField, TransitionFieldSpec> = emptyMap(),
  val comment: CommentFieldSpec? = null,
) {
  companion object {
    const val RESOURCE = "work_item"
    const val CURRENT_VERSION = 1
  }
}

data class CommentFieldSpec(
  val participation: FieldParticipation = FieldParticipation.OPTIONAL,
  val template: TemplateValueExpression? = null,
)

data class TransitionFieldSpec(
  val participation: FieldParticipation = FieldParticipation.OPTIONAL,
  val value: TemplateValueExpression? = null,
  val writeGrant: FieldWriteGrant = FieldWriteGrant.INHERIT,
  val onUnauthorized: UnauthorizedMutationBehavior = UnauthorizedMutationBehavior.PRESERVE_CURRENT,
)

enum class FieldParticipation(val wireName: String) {
  REQUIRED("required"),
  OPTIONAL("optional"),
  AUTOMATIC("automatic");

  companion object {
    fun fromWireName(value: String): FieldParticipation? = entries.firstOrNull {
      it.wireName == value
    }
  }
}

enum class FieldWriteGrant(val wireName: String) {
  INHERIT("inherit"),
  TRANSITION_WRITABLE("transition_writable"),
  SYSTEM_ONLY("system_only"),
  IMMUTABLE("immutable");

  companion object {
    fun fromWireName(value: String): FieldWriteGrant? = entries.firstOrNull { it.wireName == value }
  }
}

enum class UnauthorizedMutationBehavior(val wireName: String) {
  REJECT("reject"),
  APPLY_DEFAULT_ONLY("apply_default_only"),
  PRESERVE_CURRENT("preserve_current");

  companion object {
    fun fromWireName(value: String): UnauthorizedMutationBehavior? = entries.firstOrNull {
      it.wireName == value
    }
  }
}

fun TemplateField.toPermissionResourceId(): String =
  when (this) {
    is TemplateField.System -> "issue:field:system.$canonicalName"
    is TemplateField.Property -> "issue:field:property.${apiId ?: code}"
  }

fun TemplateField.toWirePath(): String =
  when (this) {
    is TemplateField.System -> canonicalName
    is TemplateField.Property -> "property.${apiId ?: code}"
  }

fun JsonElement?.isNonNullValue(): Boolean =
  this != null && this !is kotlinx.serialization.json.JsonNull
