package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.workitem.template.FieldParticipation
import ink.doa.workbench.core.workitem.template.FieldWriteGrant
import ink.doa.workbench.core.workitem.template.TransitionFieldSpec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

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

      TransitionFieldReconcileSupport.reconcileField(
        spec = automatic,
        currentValue = JsonPrimitive("current"),
        templateValue = JsonPrimitive("template"),
        userValue = JsonPrimitive("user"),
        canWrite = true,
        handleUnauthorized = { _, _, _, _ -> null },
      ) shouldBe JsonPrimitive("template")
    }

    "immutable grant preserves current value" {
      TransitionFieldReconcileSupport.reconcileField(
        spec = spec.copy(writeGrant = FieldWriteGrant.IMMUTABLE),
        currentValue = JsonPrimitive("current"),
        templateValue = JsonPrimitive("template"),
        userValue = JsonPrimitive("user"),
        canWrite = true,
        handleUnauthorized = { _, _, _, _ -> null },
      ) shouldBe JsonPrimitive("current")
    }

    "transition writable prefers user then template then current" {
      TransitionFieldReconcileSupport.reconcileField(
        spec = spec.copy(writeGrant = FieldWriteGrant.TRANSITION_WRITABLE),
        currentValue = JsonPrimitive("current"),
        templateValue = JsonPrimitive("template"),
        userValue = JsonPrimitive("user"),
        canWrite = false,
        handleUnauthorized = { _, _, _, _ -> null },
      ) shouldBe JsonPrimitive("user")
    }

    "inherit without write permission delegates unauthorized user submissions" {
      var unauthorizedCalled = false
      TransitionFieldReconcileSupport.reconcileField(
        spec = spec,
        currentValue = JsonPrimitive("current"),
        templateValue = JsonPrimitive("template"),
        userValue = JsonPrimitive("user"),
        canWrite = false,
        handleUnauthorized = { _, _, _, userSubmitted ->
          unauthorizedCalled = userSubmitted
          JsonPrimitive("blocked")
        },
      ) shouldBe JsonPrimitive("blocked")
      unauthorizedCalled shouldBe true
    }

    "inherit without write uses template when user did not submit" {
      TransitionFieldReconcileSupport.reconcileField(
        spec = spec,
        currentValue = JsonPrimitive("current"),
        templateValue = JsonPrimitive("template"),
        userValue = null,
        canWrite = false,
        handleUnauthorized = { _, _, _, _ -> null },
      ) shouldBe JsonPrimitive("template")
    }

    "null user submission is ignored for transition writable" {
      TransitionFieldReconcileSupport.reconcileField(
        spec = spec.copy(writeGrant = FieldWriteGrant.TRANSITION_WRITABLE),
        currentValue = JsonPrimitive("current"),
        templateValue = JsonPrimitive("template"),
        userValue = JsonNull,
        canWrite = true,
        handleUnauthorized = { _, _, _, _ -> null },
      ) shouldBe JsonPrimitive("template")
    }
  })
