@file:Suppress("LargeClass")

package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.PermissionDeniedException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.core.workitem.template.FieldParticipation
import ink.doa.workbench.core.workitem.template.FieldWriteGrant
import ink.doa.workbench.core.workitem.template.TemplateField
import ink.doa.workbench.core.workitem.template.TemplateValueExpression
import ink.doa.workbench.core.workitem.template.TransitionFieldSpec
import ink.doa.workbench.core.workitem.template.UnauthorizedMutationBehavior
import ink.doa.workbench.core.workitem.template.WorkItemTransitionFieldsTemplate
import ink.doa.workbench.core.workitem.template.WorkItemValueTemplateContext
import ink.doa.workbench.core.workitem.template.WorkItemValueTemplateTarget
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class WorkItemFieldMutationReconcilerTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-04T10:15:30Z"), ZoneOffset.UTC)
    val fieldPermissions = mockk<WorkItemFieldPermissionService>()
    val reconciler = WorkItemFieldMutationReconciler(fieldPermissions, clock)

    beforeTest {
      coEvery { fieldPermissions.canWriteField(any(), any()) } returns true
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns true
    }

    "transition_writable allows user override without field permission" {
      coEvery { fieldPermissions.canWriteField(any(), any()) } returns false
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns true
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))
      val result =
        reconciler.reconcileTransition(
          template =
            template(
              TemplateField.Property(apiId = null, code = "resolution") to
                TransitionFieldSpec(
                  participation = FieldParticipation.REQUIRED,
                  value = TemplateValueExpression.Literal(JsonPrimitive("fixed")),
                  writeGrant = FieldWriteGrant.TRANSITION_WRITABLE,
                )
            ),
          config = config,
          templateContext = templateContext(),
          currentProperties = emptyMap(),
          userProperties = mapOf("resolution" to JsonPrimitive("wont_fix")),
          permissionContext = permissionContext(),
        )

      result.propertyValues["resolution"] shouldBe JsonPrimitive("wont_fix")
    }

    "preserve_current rejects unauthorized user input at request hygiene" {
      coEvery { fieldPermissions.canWriteField(any(), any()) } returns false
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns false
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<PermissionDeniedException> {
          reconciler.reconcileTransition(
            template =
              template(
                TemplateField.Property(apiId = null, code = "resolution") to
                  TransitionFieldSpec(
                    participation = FieldParticipation.OPTIONAL,
                    onUnauthorized = UnauthorizedMutationBehavior.PRESERVE_CURRENT,
                  )
              ),
            config = config,
            templateContext = templateContext(),
            currentProperties = mapOf("resolution" to JsonPrimitive("existing")),
            userProperties = mapOf("resolution" to JsonPrimitive("wont_fix")),
            permissionContext = permissionContext(),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "create applies automatic defaults" {
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))
      val result =
        reconciler.reconcileCreate(
          template =
            createTemplate(
              TemplateField.System("title") to
                TransitionFieldSpec(participation = FieldParticipation.REQUIRED),
              TemplateField.Property(apiId = null, code = "resolution") to
                TransitionFieldSpec(
                  participation = FieldParticipation.AUTOMATIC,
                  value = TemplateValueExpression.Literal(JsonPrimitive("auto")),
                ),
            ),
          config = config,
          templateContext = templateContext(),
          userProperties = mapOf("title" to JsonPrimitive("Task")),
          permissionContext = permissionContext(FieldPermissionOperation.CREATE),
        )

      result.systemFields["title"] shouldBe "Task"
      result.propertyValues["resolution"] shouldBe JsonPrimitive("auto")
    }

    "A1 create automatic default applies when user omits field" {
      mockPropertyWriteDenied(fieldPermissions)
      val config = configWithProperties(listOf(property("dueDate", WorkItemPropertyDataType.DATE)))
      val result =
        reconciler.reconcileCreate(
          template =
            createTemplate(
              TemplateField.System("title") to
                TransitionFieldSpec(participation = FieldParticipation.REQUIRED),
              TemplateField.Property(apiId = null, code = "dueDate") to
                TransitionFieldSpec(
                  participation = FieldParticipation.AUTOMATIC,
                  value =
                    TemplateValueExpression.RelativeDate(
                      amount = 3,
                      unit = ink.doa.workbench.core.workitem.template.TemplateRelativeDateUnit.DAY,
                      direction =
                        ink.doa.workbench.core.workitem.template.TemplateDateDirection.FUTURE,
                      anchor = "date.today",
                    ),
                ),
            ),
          config = config,
          templateContext = templateContext(),
          userProperties = mapOf("title" to JsonPrimitive("Task")),
          permissionContext = permissionContext(FieldPermissionOperation.CREATE),
        )

      result.propertyValues["dueDate"] shouldBe JsonPrimitive("2026-07-07")
    }

    "A2 create automatic field rejects explicit literal submission" {
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns false
      val config = configWithProperties(listOf(property("dueDate", WorkItemPropertyDataType.DATE)))

      shouldThrow<PermissionDeniedException> {
          reconciler.reconcileCreate(
            template =
              createTemplate(
                TemplateField.System("title") to
                  TransitionFieldSpec(participation = FieldParticipation.REQUIRED),
                TemplateField.Property(apiId = null, code = "dueDate") to
                  TransitionFieldSpec(
                    participation = FieldParticipation.AUTOMATIC,
                    value = TemplateValueExpression.Literal(JsonPrimitive("2026-07-10")),
                  ),
              ),
            config = config,
            templateContext = templateContext(),
            userProperties =
              mapOf(
                "title" to JsonPrimitive("Task"),
                "dueDate" to JsonPrimitive("2026-07-10"),
              ),
            permissionContext = permissionContext(FieldPermissionOperation.CREATE),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "A3 create automatic field allows JsonNull submission and applies default" {
      mockTitleOnlyEditable(fieldPermissions)
      val config = configWithProperties(listOf(property("dueDate", WorkItemPropertyDataType.DATE)))
      val result =
        reconciler.reconcileCreate(
          template =
            createTemplate(
              TemplateField.System("title") to
                TransitionFieldSpec(participation = FieldParticipation.REQUIRED),
              TemplateField.Property(apiId = null, code = "dueDate") to
                TransitionFieldSpec(
                  participation = FieldParticipation.AUTOMATIC,
                  value = TemplateValueExpression.Literal(JsonPrimitive("2026-07-07")),
                ),
            ),
          config = config,
          templateContext = templateContext(),
          userProperties =
            mapOf(
              "title" to JsonPrimitive("Task"),
              "dueDate" to JsonNull,
            ),
          permissionContext = permissionContext(FieldPermissionOperation.CREATE),
        )

      result.propertyValues["dueDate"] shouldBe JsonPrimitive("2026-07-07")
    }

    "A4 create optional inherit without permission applies default when user omits" {
      mockPropertyWriteDenied(fieldPermissions)
      val config = configWithProperties(listOf(property("labels", WorkItemPropertyDataType.TEXT)))
      val result =
        reconciler.reconcileCreate(
          template =
            createTemplate(
              TemplateField.System("title") to
                TransitionFieldSpec(participation = FieldParticipation.REQUIRED),
              TemplateField.Property(apiId = null, code = "labels") to
                TransitionFieldSpec(
                  participation = FieldParticipation.OPTIONAL,
                  value = TemplateValueExpression.Literal(JsonPrimitive("default-label")),
                ),
            ),
          config = config,
          templateContext = templateContext(),
          userProperties = mapOf("title" to JsonPrimitive("Task")),
          permissionContext = permissionContext(FieldPermissionOperation.CREATE),
        )

      result.propertyValues["labels"] shouldBe JsonPrimitive("default-label")
    }

    "A5 create optional inherit without permission rejects explicit submission" {
      coEvery { fieldPermissions.canWriteField(any(), any()) } returns false
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns false
      val config = configWithProperties(listOf(property("labels", WorkItemPropertyDataType.TEXT)))

      shouldThrow<PermissionDeniedException> {
          reconciler.reconcileCreate(
            template =
              createTemplate(
                TemplateField.System("title") to
                  TransitionFieldSpec(participation = FieldParticipation.REQUIRED),
                TemplateField.Property(apiId = null, code = "labels") to
                  TransitionFieldSpec(
                    participation = FieldParticipation.OPTIONAL,
                    value = TemplateValueExpression.Literal(JsonPrimitive("default-label")),
                  ),
              ),
            config = config,
            templateContext = templateContext(),
            userProperties =
              mapOf(
                "title" to JsonPrimitive("Task"),
                "labels" to JsonPrimitive("custom"),
              ),
            permissionContext = permissionContext(FieldPermissionOperation.CREATE),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "A6 create required inherit rejects when user omits and no default exists" {
      val config = configWithProperties(listOf(property("summary", WorkItemPropertyDataType.TEXT)))

      shouldThrow<InvalidRequestException> {
          reconciler.reconcileCreate(
            template =
              createTemplate(
                TemplateField.System("title") to
                  TransitionFieldSpec(participation = FieldParticipation.REQUIRED),
                TemplateField.Property(apiId = null, code = "summary") to
                  TransitionFieldSpec(participation = FieldParticipation.REQUIRED),
              ),
            config = config,
            templateContext = templateContext(),
            userProperties = mapOf("title" to JsonPrimitive("Task")),
            permissionContext = permissionContext(FieldPermissionOperation.CREATE),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_PROPERTY_REQUIRED
    }

    "A7 create rejects unknown property in request" {
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<InvalidRequestException> {
          reconciler.reconcileCreate(
            template =
              createTemplate(
                TemplateField.System("title") to
                  TransitionFieldSpec(participation = FieldParticipation.REQUIRED)
              ),
            config = config,
            templateContext = templateContext(),
            userProperties =
              mapOf(
                "title" to JsonPrimitive("Task"),
                "unknown" to JsonPrimitive("value"),
              ),
            permissionContext = permissionContext(FieldPermissionOperation.CREATE),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_UNEXPECTED_FIELD
    }

    "A8 create rejects non-editable property submitted by apiId" {
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns false
      val resolution = property("resolution", WorkItemPropertyDataType.TEXT)
      val config = configWithProperties(listOf(resolution))

      shouldThrow<PermissionDeniedException> {
          reconciler.reconcileCreate(
            template =
              createTemplate(
                TemplateField.System("title") to
                  TransitionFieldSpec(participation = FieldParticipation.REQUIRED),
                TemplateField.Property(
                  apiId = resolution.propertyApiId.value,
                  code = "resolution",
                ) to
                  TransitionFieldSpec(
                    participation = FieldParticipation.AUTOMATIC,
                    value = TemplateValueExpression.Literal(JsonPrimitive("auto")),
                  ),
              ),
            config = config,
            templateContext = templateContext(),
            userProperties =
              mapOf(
                "title" to JsonPrimitive("Task"),
                resolution.propertyApiId.value to JsonPrimitive("manual"),
              ),
            permissionContext = permissionContext(FieldPermissionOperation.CREATE),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "A9 create rejects non-editable property submitted by code" {
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns false
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<PermissionDeniedException> {
          reconciler.reconcileCreate(
            template =
              createTemplate(
                TemplateField.System("title") to
                  TransitionFieldSpec(participation = FieldParticipation.REQUIRED),
                TemplateField.Property(apiId = null, code = "resolution") to
                  TransitionFieldSpec(
                    participation = FieldParticipation.AUTOMATIC,
                    value = TemplateValueExpression.Literal(JsonPrimitive("auto")),
                  ),
              ),
            config = config,
            templateContext = templateContext(),
            userProperties =
              mapOf(
                "title" to JsonPrimitive("Task"),
                "resolution" to JsonPrimitive("manual"),
              ),
            permissionContext = permissionContext(FieldPermissionOperation.CREATE),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "A11 create rejects non-editable system assignee submission" {
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } answers
        {
          val field = secondArg<TemplateField>()
          field is TemplateField.System && field.canonicalName == "title"
        }
      val config = configWithProperties(emptyList())

      shouldThrow<PermissionDeniedException> {
          reconciler.reconcileCreate(
            template =
              createTemplate(
                TemplateField.System("title") to
                  TransitionFieldSpec(participation = FieldParticipation.REQUIRED),
                TemplateField.System("assignee") to
                  TransitionFieldSpec(
                    participation = FieldParticipation.OPTIONAL,
                    value = TemplateValueExpression.Variable("user.currentUser"),
                    writeGrant = FieldWriteGrant.SYSTEM_ONLY,
                  ),
              ),
            config = config,
            templateContext = templateContext(),
            userProperties =
              mapOf(
                "title" to JsonPrimitive("Task"),
                "assignee" to JsonPrimitive("usr_other"),
              ),
            permissionContext = permissionContext(FieldPermissionOperation.CREATE),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "B2 transition inherit without permission rejects override" {
      coEvery { fieldPermissions.canWriteField(any(), any()) } returns false
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns false
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<PermissionDeniedException> {
          reconciler.reconcileTransition(
            template =
              template(
                TemplateField.Property(apiId = null, code = "resolution") to
                  TransitionFieldSpec(participation = FieldParticipation.OPTIONAL)
              ),
            config = config,
            templateContext = templateContext(),
            currentProperties = emptyMap(),
            userProperties = mapOf("resolution" to JsonPrimitive("wont_fix")),
            permissionContext = permissionContext(),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "B3 transition immutable rejects user override" {
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns false
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<PermissionDeniedException> {
          reconciler.reconcileTransition(
            template =
              template(
                TemplateField.Property(apiId = null, code = "resolution") to
                  TransitionFieldSpec(
                    participation = FieldParticipation.OPTIONAL,
                    writeGrant = FieldWriteGrant.IMMUTABLE,
                  )
              ),
            config = config,
            templateContext = templateContext(),
            currentProperties = mapOf("resolution" to JsonPrimitive("existing")),
            userProperties = mapOf("resolution" to JsonPrimitive("wont_fix")),
            permissionContext = permissionContext(),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "B4 transition system_only rejects user override" {
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns false
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<PermissionDeniedException> {
          reconciler.reconcileTransition(
            template =
              template(
                TemplateField.Property(apiId = null, code = "resolution") to
                  TransitionFieldSpec(
                    participation = FieldParticipation.OPTIONAL,
                    value = TemplateValueExpression.Literal(JsonPrimitive("fixed")),
                    writeGrant = FieldWriteGrant.SYSTEM_ONLY,
                  )
              ),
            config = config,
            templateContext = templateContext(),
            currentProperties = emptyMap(),
            userProperties = mapOf("resolution" to JsonPrimitive("wont_fix")),
            permissionContext = permissionContext(),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "B5 transition applies var default when user omits" {
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns false
      val config =
        configWithProperties(listOf(property("resolvedAt", WorkItemPropertyDataType.DATETIME)))
      val result =
        reconciler.reconcileTransition(
          template =
            template(
              TemplateField.Property(apiId = null, code = "resolvedAt") to
                TransitionFieldSpec(
                  participation = FieldParticipation.AUTOMATIC,
                  value = TemplateValueExpression.Variable("date.now"),
                )
            ),
          config = config,
          templateContext = templateContext(),
          currentProperties = emptyMap(),
          userProperties = emptyMap(),
          permissionContext = permissionContext(),
        )

      result.propertyValues["resolvedAt"] shouldBe JsonPrimitive("2026-07-04T10:15:30Z")
    }

    "B7 transition allows JsonNull on non-editable field and preserves current" {
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns false
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))
      val result =
        reconciler.reconcileTransition(
          template =
            template(
              TemplateField.Property(apiId = null, code = "resolution") to
                TransitionFieldSpec(
                  participation = FieldParticipation.OPTIONAL,
                  writeGrant = FieldWriteGrant.IMMUTABLE,
                )
            ),
          config = config,
          templateContext = templateContext(),
          currentProperties = mapOf("resolution" to JsonPrimitive("existing")),
          userProperties = mapOf("resolution" to JsonNull),
          permissionContext = permissionContext(),
        )

      result.propertyValues["resolution"] shouldBe JsonPrimitive("existing")
    }

    "B8 transition rejects unknown property in request" {
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<InvalidRequestException> {
          reconciler.reconcileTransition(
            template =
              template(
                TemplateField.Property(apiId = null, code = "resolution") to
                  TransitionFieldSpec(participation = FieldParticipation.OPTIONAL)
              ),
            config = config,
            templateContext = templateContext(),
            currentProperties = emptyMap(),
            userProperties = mapOf("unknown" to JsonPrimitive("value")),
            permissionContext = permissionContext(),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_UNEXPECTED_FIELD
    }

    "C1 optional inherit with permission prefers user literal over default" {
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))
      val result =
        reconciler.reconcileTransition(
          template =
            template(
              TemplateField.Property(apiId = null, code = "resolution") to
                TransitionFieldSpec(
                  participation = FieldParticipation.OPTIONAL,
                  value = TemplateValueExpression.Literal(JsonPrimitive("fixed")),
                )
            ),
          config = config,
          templateContext = templateContext(),
          currentProperties = emptyMap(),
          userProperties = mapOf("resolution" to JsonPrimitive("wont_fix")),
          permissionContext = permissionContext(),
        )

      result.propertyValues["resolution"] shouldBe JsonPrimitive("wont_fix")
    }

    "C3 optional inherit preserves current when user omits and no default" {
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))
      val result =
        reconciler.reconcileTransition(
          template =
            template(
              TemplateField.Property(apiId = null, code = "resolution") to
                TransitionFieldSpec(participation = FieldParticipation.OPTIONAL)
            ),
          config = config,
          templateContext = templateContext(),
          currentProperties = mapOf("resolution" to JsonPrimitive("existing")),
          userProperties = emptyMap(),
          permissionContext = permissionContext(),
        )

      result.propertyValues["resolution"] shouldBe JsonPrimitive("existing")
    }

    "C4 automatic applies default without write permission when user omits" {
      mockPropertyWriteDenied(fieldPermissions)
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))
      val result =
        reconciler.reconcileCreate(
          template =
            createTemplate(
              TemplateField.System("title") to
                TransitionFieldSpec(participation = FieldParticipation.REQUIRED),
              TemplateField.Property(apiId = null, code = "resolution") to
                TransitionFieldSpec(
                  participation = FieldParticipation.AUTOMATIC,
                  value = TemplateValueExpression.Literal(JsonPrimitive("auto")),
                ),
            ),
          config = config,
          templateContext = templateContext(),
          userProperties = mapOf("title" to JsonPrimitive("Task")),
          permissionContext = permissionContext(FieldPermissionOperation.CREATE),
        )

      result.propertyValues["resolution"] shouldBe JsonPrimitive("auto")
    }

    "C6 immutable keeps current even when template default exists" {
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))
      val result =
        reconciler.reconcileTransition(
          template =
            template(
              TemplateField.Property(apiId = null, code = "resolution") to
                TransitionFieldSpec(
                  participation = FieldParticipation.OPTIONAL,
                  value = TemplateValueExpression.Literal(JsonPrimitive("fixed")),
                  writeGrant = FieldWriteGrant.IMMUTABLE,
                )
            ),
          config = config,
          templateContext = templateContext(),
          currentProperties = mapOf("resolution" to JsonPrimitive("existing")),
          userProperties = emptyMap(),
          permissionContext = permissionContext(),
        )

      result.propertyValues["resolution"] shouldBe JsonPrimitive("existing")
    }

    "C7 required immutable without current value fails" {
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<InvalidRequestException> {
          reconciler.reconcileTransition(
            template =
              template(
                TemplateField.Property(apiId = null, code = "resolution") to
                  TransitionFieldSpec(
                    participation = FieldParticipation.REQUIRED,
                    writeGrant = FieldWriteGrant.IMMUTABLE,
                  )
              ),
            config = config,
            templateContext = templateContext(),
            currentProperties = emptyMap(),
            userProperties = emptyMap(),
            permissionContext = permissionContext(),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELD_IMMUTABLE_BUT_REQUIRED
    }

    "D transition applies user.currentUser to assignee system field" {
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns false
      val config = configWithProperties(emptyList())
      val result =
        reconciler.reconcileTransition(
          template =
            template(
              TemplateField.System("assignee") to
                TransitionFieldSpec(
                  participation = FieldParticipation.AUTOMATIC,
                  value = TemplateValueExpression.Variable("user.currentUser"),
                )
            ),
          config = config,
          templateContext = templateContext(),
          currentProperties = emptyMap(),
          userProperties = emptyMap(),
          permissionContext = permissionContext(),
        )

      result.systemFields["assignee"] shouldBe "usr_test"
    }

    "reconciles title and description together on transition" {
      val config = configWithProperties(emptyList())
      val result =
        reconciler.reconcileTransition(
          template =
            template(
              TemplateField.System("title") to
                TransitionFieldSpec(
                  participation = FieldParticipation.OPTIONAL,
                  writeGrant = FieldWriteGrant.TRANSITION_WRITABLE,
                ),
              TemplateField.System("description") to
                TransitionFieldSpec(
                  participation = FieldParticipation.OPTIONAL,
                  writeGrant = FieldWriteGrant.TRANSITION_WRITABLE,
                ),
            ),
          config = config,
          templateContext = templateContext(),
          currentProperties = emptyMap(),
          userProperties =
            mapOf(
              "title" to JsonPrimitive("Updated title"),
              "description" to JsonPrimitive("<p>Updated body</p>"),
            ),
          permissionContext = permissionContext(),
        )

      result.systemFields["title"] shouldBe "Updated title"
      result.systemFields["description"] shouldBe "<p>Updated body</p>"
    }

    "reconcileTransitionComment uses template default when user comment absent" {
      val body =
        reconciler.reconcileTransitionComment(
          spec =
            ink.doa.workbench.core.workitem.template.CommentFieldSpec(
              participation = FieldParticipation.OPTIONAL,
              template = TemplateValueExpression.Literal(JsonPrimitive("Resolved via transition")),
            ),
          templateContext = templateContext(),
          userComment = null,
        )

      body shouldBe "Resolved via transition"
    }

    "reconcileTransitionComment accepts html user comment" {
      val body =
        reconciler.reconcileTransitionComment(
          spec =
            ink.doa.workbench.core.workitem.template.CommentFieldSpec(
              participation = FieldParticipation.OPTIONAL,
              template = null,
            ),
          templateContext = templateContext(),
          userComment = "<p>Ship it</p>",
        )

      body shouldBe "<p>Ship it</p>"
    }

    "reconcileTransitionComment rejects unexpected comment when disabled" {
      shouldThrow<InvalidRequestException> {
          reconciler.reconcileTransitionComment(
            spec = null,
            templateContext = templateContext(),
            userComment = "unexpected",
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_UNEXPECTED_FIELD
    }
  })

private fun mockTitleOnlyEditable(fieldPermissions: WorkItemFieldPermissionService) {
  coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } answers
    {
      val field = secondArg<TemplateField>()
      field is TemplateField.System && field.canonicalName == "title"
    }
}

private fun mockPropertyWriteDenied(fieldPermissions: WorkItemFieldPermissionService) {
  coEvery { fieldPermissions.canWriteField(any(), any()) } answers
    {
      secondArg<TemplateField>() is TemplateField.System
    }
  mockTitleOnlyEditable(fieldPermissions)
}

private fun permissionContext(
  operation: FieldPermissionOperation = FieldPermissionOperation.UPDATE
) =
  WorkItemFieldPermissionContext(
    tenantId = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    actorUserId = UUID.randomUUID(),
    operation = operation,
  )

private fun templateContext() =
  WorkItemValueTemplateContext(
    tenantId = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    currentUserApiId = "usr_test",
    currentProjectApiId = "prj_test",
    actorUserId = UUID.randomUUID(),
  )

private fun template(vararg fields: Pair<TemplateField, TransitionFieldSpec>) =
  WorkItemTransitionFieldsTemplate(fields = fields.toMap())

private fun createTemplate(vararg fields: Pair<TemplateField, TransitionFieldSpec>) =
  WorkItemTransitionFieldsTemplate(
    target = WorkItemValueTemplateTarget.CREATE,
    fields = fields.toMap(),
  )

private fun configWithProperties(
  properties: List<IssueTypeConfigPropertyRecord>
): IssueTypeConfigDetails {
  val tenantId = UUID.randomUUID()
  val configId = UUID.randomUUID()
  return IssueTypeConfigDetails(
    config =
      IssueTypeConfigRecord(
        id = configId,
        apiId = PublicId.new("itc"),
        tenantId = tenantId,
        scope = WorkItemConfigScope.TENANT,
        projectId = null,
        issueTypeId = UUID.randomUUID(),
        issueTypeApiId = PublicId.new("typ"),
        workflowId = UUID.randomUUID(),
        workflowApiId = PublicId.new("wfl"),
        version = 1,
        nameOverride = null,
        iconOverride = null,
        colorOverride = null,
        rank = 100,
        isActive = true,
        validFrom = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        validTo = null,
        createdBy = null,
        createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        createFields = JsonObject(emptyMap()),
      ),
    statuses = emptyList(),
    properties = properties,
  )
}

private fun property(code: String, type: WorkItemPropertyDataType): IssueTypeConfigPropertyRecord {
  val tenantId = UUID.randomUUID()
  val configId = UUID.randomUUID()
  return IssueTypeConfigPropertyRecord(
    id = UUID.randomUUID(),
    tenantId = tenantId,
    issueTypeConfigId = configId,
    propertyId = UUID.randomUUID(),
    propertyApiId = PublicId.new("fld"),
    code = code,
    name = code,
    dataType = type,
    validationOverride = JsonObject(emptyMap()),
    rank = 100,
    displayConfig = JsonObject(emptyMap()),
  )
}
