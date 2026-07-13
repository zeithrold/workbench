package ink.doa.workbench.agile.workitem.template

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class WorkItemTemplateEnumsCoverageTest :
  StringSpec({
    "field participation resolves wire names" {
      FieldParticipation.fromWireName("required") shouldBe FieldParticipation.REQUIRED
      FieldParticipation.fromWireName("optional") shouldBe FieldParticipation.OPTIONAL
      FieldParticipation.fromWireName("automatic") shouldBe FieldParticipation.AUTOMATIC
      FieldParticipation.fromWireName("missing").shouldBeNull()
    }

    "field write grant resolves wire names" {
      FieldWriteGrant.fromWireName("inherit") shouldBe FieldWriteGrant.INHERIT
      FieldWriteGrant.fromWireName("transition_writable") shouldBe
        FieldWriteGrant.TRANSITION_WRITABLE
      FieldWriteGrant.fromWireName("system_only") shouldBe FieldWriteGrant.SYSTEM_ONLY
      FieldWriteGrant.fromWireName("immutable") shouldBe FieldWriteGrant.IMMUTABLE
      FieldWriteGrant.fromWireName("missing").shouldBeNull()
    }

    "unauthorized mutation behavior resolves wire names" {
      UnauthorizedMutationBehavior.fromWireName("reject") shouldBe
        UnauthorizedMutationBehavior.REJECT
      UnauthorizedMutationBehavior.fromWireName("apply_default_only") shouldBe
        UnauthorizedMutationBehavior.APPLY_DEFAULT_ONLY
      UnauthorizedMutationBehavior.fromWireName("preserve_current") shouldBe
        UnauthorizedMutationBehavior.PRESERVE_CURRENT
      UnauthorizedMutationBehavior.fromWireName("missing").shouldBeNull()
    }

    "value template target resolves wire names" {
      WorkItemValueTemplateTarget.fromWireName("create") shouldBe WorkItemValueTemplateTarget.CREATE
      WorkItemValueTemplateTarget.fromWireName("transition") shouldBe
        WorkItemValueTemplateTarget.TRANSITION
      WorkItemValueTemplateTarget.fromWireName("missing").shouldBeNull()
    }

    "relative date unit resolves wire names" {
      TemplateRelativeDateUnit.fromWireName("day") shouldBe TemplateRelativeDateUnit.DAY
      TemplateRelativeDateUnit.fromWireName("week") shouldBe TemplateRelativeDateUnit.WEEK
      TemplateRelativeDateUnit.fromWireName("month") shouldBe TemplateRelativeDateUnit.MONTH
      TemplateRelativeDateUnit.fromWireName("year") shouldBe TemplateRelativeDateUnit.YEAR
      TemplateRelativeDateUnit.fromWireName("missing").shouldBeNull()
    }

    "date direction resolves wire names" {
      TemplateDateDirection.fromWireName("past") shouldBe TemplateDateDirection.PAST
      TemplateDateDirection.fromWireName("future") shouldBe TemplateDateDirection.FUTURE
      TemplateDateDirection.fromWireName("missing").shouldBeNull()
    }
  })
