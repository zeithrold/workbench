package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.template.FieldParticipation
import ink.doa.workbench.agile.workitem.template.FieldWriteGrant
import ink.doa.workbench.agile.workitem.template.TransitionFieldSpec
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

internal data class ReconcileFieldParams(
  val spec: TransitionFieldSpec,
  val currentValue: JsonElement?,
  val templateValue: JsonElement?,
  val userValue: JsonElement?,
  val canWrite: Boolean,
  val handleUnauthorized:
    (
      spec: TransitionFieldSpec,
      currentValue: JsonElement?,
      templateValue: JsonElement?,
      userSubmitted: Boolean,
    ) -> JsonElement?,
)

internal object TransitionFieldReconcileSupport {
  private typealias GrantHandler = (ReconcileFieldParams) -> JsonElement?

  private val grantHandlers: Map<FieldWriteGrant, GrantHandler> =
    mapOf(
      FieldWriteGrant.IMMUTABLE to { params -> params.currentValue },
      FieldWriteGrant.SYSTEM_ONLY to
        { params ->
          params.templateValue ?: params.currentValue
        },
      FieldWriteGrant.TRANSITION_WRITABLE to
        { params ->
          firstNonNullValue(params.userValue, params.templateValue, params.currentValue)
        },
      FieldWriteGrant.INHERIT to
        { params ->
          val userSubmitted = params.userValue != null && params.userValue !is JsonNull
          if (params.canWrite) {
            firstNonNullValue(params.userValue, params.templateValue, params.currentValue)
          } else if (userSubmitted) {
            params.handleUnauthorized(
              params.spec,
              params.currentValue,
              params.templateValue,
              true,
            )
          } else {
            params.templateValue ?: params.currentValue
          }
        },
    )

  fun reconcileField(params: ReconcileFieldParams): JsonElement? {
    if (
      params.spec.participation == FieldParticipation.AUTOMATIC ||
        params.spec.writeGrant == FieldWriteGrant.SYSTEM_ONLY
    ) {
      return params.templateValue ?: params.currentValue
    }
    return grantHandlers.getValue(params.spec.writeGrant)(params)
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
