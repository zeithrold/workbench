package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.workitem.template.FieldParticipation
import ink.doa.workbench.core.workitem.template.FieldWriteGrant
import ink.doa.workbench.core.workitem.template.TransitionFieldSpec
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

internal object TransitionFieldReconcileSupport {
  private typealias GrantHandler =
    (
      spec: TransitionFieldSpec,
      currentValue: JsonElement?,
      templateValue: JsonElement?,
      userValue: JsonElement?,
      canWrite: Boolean,
      handleUnauthorized:
        (
          spec: TransitionFieldSpec,
          currentValue: JsonElement?,
          templateValue: JsonElement?,
          userSubmitted: Boolean,
        ) -> JsonElement?,
    ) -> JsonElement?

  private val grantHandlers: Map<FieldWriteGrant, GrantHandler> =
    mapOf(
      FieldWriteGrant.IMMUTABLE to { _, currentValue, _, _, _, _ -> currentValue },
      FieldWriteGrant.SYSTEM_ONLY to
        { _, currentValue, templateValue, _, _, _ ->
          templateValue ?: currentValue
        },
      FieldWriteGrant.TRANSITION_WRITABLE to
        { _, currentValue, templateValue, userValue, _, _ ->
          firstNonNullValue(userValue, templateValue, currentValue)
        },
      FieldWriteGrant.INHERIT to
        { spec, currentValue, templateValue, userValue, canWrite, handleUnauthorized ->
          val userSubmitted = userValue != null && userValue !is JsonNull
          if (canWrite) {
            firstNonNullValue(userValue, templateValue, currentValue)
          } else if (userSubmitted) {
            handleUnauthorized(spec, currentValue, templateValue, true)
          } else {
            templateValue ?: currentValue
          }
        },
    )

  fun reconcileField(
    spec: TransitionFieldSpec,
    currentValue: JsonElement?,
    templateValue: JsonElement?,
    userValue: JsonElement?,
    canWrite: Boolean,
    handleUnauthorized:
      (
        spec: TransitionFieldSpec,
        currentValue: JsonElement?,
        templateValue: JsonElement?,
        userSubmitted: Boolean,
      ) -> JsonElement?,
  ): JsonElement? {
    if (
      spec.participation == FieldParticipation.AUTOMATIC ||
        spec.writeGrant == FieldWriteGrant.SYSTEM_ONLY
    ) {
      return templateValue ?: currentValue
    }
    return grantHandlers.getValue(spec.writeGrant)(
      spec,
      currentValue,
      templateValue,
      userValue,
      canWrite,
      handleUnauthorized,
    )
  }

  private fun firstNonNullValue(
    userValue: JsonElement?,
    templateValue: JsonElement?,
    currentValue: JsonElement?,
  ): JsonElement? {
    val userSubmitted = userValue != null && userValue !is JsonNull
    return when {
      userSubmitted -> userValue
      templateValue != null && templateValue !is JsonNull -> templateValue
      else -> currentValue
    }
  }
}
