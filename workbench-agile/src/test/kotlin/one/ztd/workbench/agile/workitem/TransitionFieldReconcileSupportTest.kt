package one.ztd.workbench.agile.workitem

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.agile.workitem.template.FieldParticipation
import one.ztd.workbench.agile.workitem.template.FieldWriteGrant
import one.ztd.workbench.agile.workitem.template.TransitionFieldSpec

class TransitionFieldReconcileSupportTest :
  StringSpec({
    val spec =
      TransitionFieldSpec(
        participation = FieldParticipation.OPTIONAL,
        writeGrant = FieldWriteGrant.INHERIT,
      )

    "automatic participation prefers template value" {
      val automatic =
        spec.copy(
          participation = FieldParticipation.AUTOMATIC,
          writeGrant = FieldWriteGrant.TRANSITION_WRITABLE,
        )

      reconcileField(
        ReconcileFieldParams(
          spec = automatic,
          currentValue = JsonPrimitive("current"),
          templateValue = JsonPrimitive("template"),
          userValue = JsonPrimitive("user"),
          canWrite = true,
          handleUnauthorized = { _, _, _, _ -> null },
        )
      ) shouldBe JsonPrimitive("template")
    }

    "immutable grant preserves current value" {
      reconcileField(
        ReconcileFieldParams(
          spec = spec.copy(writeGrant = FieldWriteGrant.IMMUTABLE),
          currentValue = JsonPrimitive("current"),
          templateValue = JsonPrimitive("template"),
          userValue = JsonPrimitive("user"),
          canWrite = true,
          handleUnauthorized = { _, _, _, _ -> null },
        )
      ) shouldBe JsonPrimitive("current")
    }

    "transition writable prefers user then template then current" {
      reconcileField(
        ReconcileFieldParams(
          spec = spec.copy(writeGrant = FieldWriteGrant.TRANSITION_WRITABLE),
          currentValue = JsonPrimitive("current"),
          templateValue = JsonPrimitive("template"),
          userValue = JsonPrimitive("user"),
          canWrite = false,
          handleUnauthorized = { _, _, _, _ -> null },
        )
      ) shouldBe JsonPrimitive("user")
    }

    "inherit without write permission delegates unauthorized user submissions" {
      var unauthorizedCalled = false
      reconcileField(
        ReconcileFieldParams(
          spec = spec,
          currentValue = JsonPrimitive("current"),
          templateValue = JsonPrimitive("template"),
          userValue = JsonPrimitive("user"),
          canWrite = false,
          handleUnauthorized = { _, _, _, userSubmitted ->
            unauthorizedCalled = userSubmitted
            JsonPrimitive("blocked")
          },
        )
      ) shouldBe JsonPrimitive("blocked")
      unauthorizedCalled shouldBe true
    }

    "inherit without write uses template when user did not submit" {
      reconcileField(
        ReconcileFieldParams(
          spec = spec,
          currentValue = JsonPrimitive("current"),
          templateValue = JsonPrimitive("template"),
          userValue = null,
          canWrite = false,
          handleUnauthorized = { _, _, _, _ -> null },
        )
      ) shouldBe JsonPrimitive("template")
    }

    "null user submission is ignored for transition writable" {
      reconcileField(
        ReconcileFieldParams(
          spec = spec.copy(writeGrant = FieldWriteGrant.TRANSITION_WRITABLE),
          currentValue = JsonPrimitive("current"),
          templateValue = JsonPrimitive("template"),
          userValue = JsonNull,
          canWrite = true,
          handleUnauthorized = { _, _, _, _ -> null },
        )
      ) shouldBe JsonPrimitive("template")
    }
  })

private fun reconcileField(params: ReconcileFieldParams): JsonElement? =
  TransitionFieldReconcileSupport.reconcileField(params)
