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
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class WorkItemFieldMutationEngineTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-04T10:15:30Z"), ZoneOffset.UTC)
    val fieldPermissions = mockk<WorkItemFieldPermissionService>()
    val engine = WorkItemFieldMutationEngine(fieldPermissions, clock)

    beforeTest {
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
        FieldMutationPolicy(allowsUserSubmission = true, bindingAllowsWrite = true)
    }

    "transition_writable allows user override without field permission" {
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
        FieldMutationPolicy(allowsUserSubmission = true, bindingAllowsWrite = false)
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))
      val result =
        engine.applyTemplate(
          transitionContext(
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
            properties =
              FieldReconciliationPropertyMaps(
                user = mapOf("resolution" to JsonPrimitive("wont_fix"))
              ),
            permissionContext = permissionContext(),
          )
        )

      result.propertyValues["resolution"] shouldBe JsonPrimitive("wont_fix")
    }

    "preserve_current rejects unauthorized user input at request hygiene" {
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
        FieldMutationPolicy(allowsUserSubmission = false, bindingAllowsWrite = false)
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<PermissionDeniedException> {
          engine.applyTemplate(
            transitionContext(
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
              properties =
                FieldReconciliationPropertyMaps(
                  current = mapOf("resolution" to JsonPrimitive("existing")),
                  user = mapOf("resolution" to JsonPrimitive("wont_fix")),
                ),
              permissionContext = permissionContext(),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "create applies automatic defaults" {
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))
      val result =
        engine.applyTemplate(
          createContext(
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
            properties =
              FieldReconciliationPropertyMaps(user = mapOf("title" to JsonPrimitive("Task"))),
            permissionContext = permissionContext(FieldPermissionOperation.CREATE),
          )
        )

      result.systemFields["title"] shouldBe "Task"
      result.propertyValues["resolution"] shouldBe JsonPrimitive("auto")
    }

    "A1 create automatic default applies when user omits field" {
      mockPropertyWriteDenied(fieldPermissions)
      val config = configWithProperties(listOf(property("dueDate", WorkItemPropertyDataType.DATE)))
      val result =
        engine.applyTemplate(
          createContext(
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
                        unit =
                          ink.doa.workbench.core.workitem.template.TemplateRelativeDateUnit.DAY,
                        direction =
                          ink.doa.workbench.core.workitem.template.TemplateDateDirection.FUTURE,
                        anchor = "date.today",
                      ),
                  ),
              ),
            config = config,
            templateContext = templateContext(),
            properties =
              FieldReconciliationPropertyMaps(user = mapOf("title" to JsonPrimitive("Task"))),
            permissionContext = permissionContext(FieldPermissionOperation.CREATE),
          )
        )

      result.propertyValues["dueDate"] shouldBe JsonPrimitive("2026-07-07")
    }

    "A2 create automatic field rejects explicit literal submission" {
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
        FieldMutationPolicy(allowsUserSubmission = false, bindingAllowsWrite = true)
      val config = configWithProperties(listOf(property("dueDate", WorkItemPropertyDataType.DATE)))

      shouldThrow<PermissionDeniedException> {
          engine.applyTemplate(
            createContext(
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
              properties =
                FieldReconciliationPropertyMaps(
                  user =
                    mapOf(
                      "title" to JsonPrimitive("Task"),
                      "dueDate" to JsonPrimitive("2026-07-10"),
                    )
                ),
              permissionContext = permissionContext(FieldPermissionOperation.CREATE),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "A3 create automatic field allows JsonNull submission and applies default" {
      mockTitleOnlyEditable(fieldPermissions)
      val config = configWithProperties(listOf(property("dueDate", WorkItemPropertyDataType.DATE)))
      val result =
        engine.applyTemplate(
          createContext(
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
            properties =
              FieldReconciliationPropertyMaps(
                user =
                  mapOf(
                    "title" to JsonPrimitive("Task"),
                    "dueDate" to JsonNull,
                  )
              ),
            permissionContext = permissionContext(FieldPermissionOperation.CREATE),
          )
        )

      result.propertyValues["dueDate"] shouldBe JsonPrimitive("2026-07-07")
    }

    "A4 create optional inherit without permission applies default when user omits" {
      mockPropertyWriteDenied(fieldPermissions)
      val config = configWithProperties(listOf(property("labels", WorkItemPropertyDataType.TEXT)))
      val result =
        engine.applyTemplate(
          createContext(
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
            properties =
              FieldReconciliationPropertyMaps(user = mapOf("title" to JsonPrimitive("Task"))),
            permissionContext = permissionContext(FieldPermissionOperation.CREATE),
          )
        )

      result.propertyValues["labels"] shouldBe JsonPrimitive("default-label")
    }

    "A5 create optional inherit without permission rejects explicit submission" {
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
        FieldMutationPolicy(allowsUserSubmission = false, bindingAllowsWrite = false)
      val config = configWithProperties(listOf(property("labels", WorkItemPropertyDataType.TEXT)))

      shouldThrow<PermissionDeniedException> {
          engine.applyTemplate(
            createContext(
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
              properties =
                FieldReconciliationPropertyMaps(
                  user =
                    mapOf(
                      "title" to JsonPrimitive("Task"),
                      "labels" to JsonPrimitive("custom"),
                    )
                ),
              permissionContext = permissionContext(FieldPermissionOperation.CREATE),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "A6 create required inherit rejects when user omits and no default exists" {
      val config = configWithProperties(listOf(property("summary", WorkItemPropertyDataType.TEXT)))

      shouldThrow<InvalidRequestException> {
          engine.applyTemplate(
            createContext(
              template =
                createTemplate(
                  TemplateField.System("title") to
                    TransitionFieldSpec(participation = FieldParticipation.REQUIRED),
                  TemplateField.Property(apiId = null, code = "summary") to
                    TransitionFieldSpec(participation = FieldParticipation.REQUIRED),
                ),
              config = config,
              templateContext = templateContext(),
              properties =
                FieldReconciliationPropertyMaps(user = mapOf("title" to JsonPrimitive("Task"))),
              permissionContext = permissionContext(FieldPermissionOperation.CREATE),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_PROPERTY_REQUIRED
    }

    "A7 create rejects unknown property in request" {
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<InvalidRequestException> {
          engine.applyTemplate(
            createContext(
              template =
                createTemplate(
                  TemplateField.System("title") to
                    TransitionFieldSpec(participation = FieldParticipation.REQUIRED)
                ),
              config = config,
              templateContext = templateContext(),
              properties =
                FieldReconciliationPropertyMaps(
                  user =
                    mapOf(
                      "title" to JsonPrimitive("Task"),
                      "unknown" to JsonPrimitive("value"),
                    )
                ),
              permissionContext = permissionContext(FieldPermissionOperation.CREATE),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_UNEXPECTED_FIELD
    }

    "A8 create rejects non-editable property submitted by apiId" {
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
        FieldMutationPolicy(allowsUserSubmission = false, bindingAllowsWrite = true)
      val resolution = property("resolution", WorkItemPropertyDataType.TEXT)
      val config = configWithProperties(listOf(resolution))

      shouldThrow<PermissionDeniedException> {
          engine.applyTemplate(
            createContext(
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
              properties =
                FieldReconciliationPropertyMaps(
                  user =
                    mapOf(
                      "title" to JsonPrimitive("Task"),
                      resolution.propertyApiId.value to JsonPrimitive("manual"),
                    )
                ),
              permissionContext = permissionContext(FieldPermissionOperation.CREATE),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "A9 create rejects non-editable property submitted by code" {
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
        FieldMutationPolicy(allowsUserSubmission = false, bindingAllowsWrite = true)
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<PermissionDeniedException> {
          engine.applyTemplate(
            createContext(
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
              properties =
                FieldReconciliationPropertyMaps(
                  user =
                    mapOf(
                      "title" to JsonPrimitive("Task"),
                      "resolution" to JsonPrimitive("manual"),
                    )
                ),
              permissionContext = permissionContext(FieldPermissionOperation.CREATE),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "A11 create rejects non-editable system assignee submission" {
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } answers
        {
          val field = secondArg<TemplateField>()
          val allowed = field is TemplateField.System && field.canonicalName == "title"
          FieldMutationPolicy(allowsUserSubmission = allowed, bindingAllowsWrite = allowed)
        }
      val config = configWithProperties(emptyList())

      shouldThrow<PermissionDeniedException> {
          engine.applyTemplate(
            createContext(
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
              properties =
                FieldReconciliationPropertyMaps(
                  user =
                    mapOf(
                      "title" to JsonPrimitive("Task"),
                      "assignee" to JsonPrimitive("usr_other"),
                    )
                ),
              permissionContext = permissionContext(FieldPermissionOperation.CREATE),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "B2 transition inherit without permission rejects override" {
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
        FieldMutationPolicy(allowsUserSubmission = false, bindingAllowsWrite = false)
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<PermissionDeniedException> {
          engine.applyTemplate(
            transitionContext(
              template =
                template(
                  TemplateField.Property(apiId = null, code = "resolution") to
                    TransitionFieldSpec(participation = FieldParticipation.OPTIONAL)
                ),
              config = config,
              templateContext = templateContext(),
              properties =
                FieldReconciliationPropertyMaps(
                  user = mapOf("resolution" to JsonPrimitive("wont_fix"))
                ),
              permissionContext = permissionContext(),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "B3 transition immutable rejects user override" {
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
        FieldMutationPolicy(allowsUserSubmission = false, bindingAllowsWrite = true)
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<PermissionDeniedException> {
          engine.applyTemplate(
            transitionContext(
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
              properties =
                FieldReconciliationPropertyMaps(
                  current = mapOf("resolution" to JsonPrimitive("existing")),
                  user = mapOf("resolution" to JsonPrimitive("wont_fix")),
                ),
              permissionContext = permissionContext(),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "B4 transition system_only rejects user override" {
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
        FieldMutationPolicy(allowsUserSubmission = false, bindingAllowsWrite = true)
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<PermissionDeniedException> {
          engine.applyTemplate(
            transitionContext(
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
              properties =
                FieldReconciliationPropertyMaps(
                  user = mapOf("resolution" to JsonPrimitive("wont_fix"))
                ),
              permissionContext = permissionContext(),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }

    "B5 transition applies var default when user omits" {
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
        FieldMutationPolicy(allowsUserSubmission = false, bindingAllowsWrite = true)
      val config =
        configWithProperties(listOf(property("resolvedAt", WorkItemPropertyDataType.DATETIME)))
      val result =
        engine.applyTemplate(
          transitionContext(
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
            properties = FieldReconciliationPropertyMaps(),
            permissionContext = permissionContext(),
          )
        )

      result.propertyValues["resolvedAt"] shouldBe JsonPrimitive("2026-07-04T10:15:30Z")
    }

    "B7 transition allows JsonNull on non-editable field and preserves current" {
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
        FieldMutationPolicy(allowsUserSubmission = false, bindingAllowsWrite = true)
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))
      val result =
        engine.applyTemplate(
          transitionContext(
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
            properties =
              FieldReconciliationPropertyMaps(
                current = mapOf("resolution" to JsonPrimitive("existing")),
                user = mapOf("resolution" to JsonNull),
              ),
            permissionContext = permissionContext(),
          )
        )

      result.propertyValues["resolution"] shouldBe JsonPrimitive("existing")
    }

    "B8 transition rejects unknown property in request" {
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<InvalidRequestException> {
          engine.applyTemplate(
            transitionContext(
              template =
                template(
                  TemplateField.Property(apiId = null, code = "resolution") to
                    TransitionFieldSpec(participation = FieldParticipation.OPTIONAL)
                ),
              config = config,
              templateContext = templateContext(),
              properties =
                FieldReconciliationPropertyMaps(user = mapOf("unknown" to JsonPrimitive("value"))),
              permissionContext = permissionContext(),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_UNEXPECTED_FIELD
    }

    "C1 optional inherit with permission prefers user literal over default" {
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))
      val result =
        engine.applyTemplate(
          transitionContext(
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
            properties =
              FieldReconciliationPropertyMaps(
                user = mapOf("resolution" to JsonPrimitive("wont_fix"))
              ),
            permissionContext = permissionContext(),
          )
        )

      result.propertyValues["resolution"] shouldBe JsonPrimitive("wont_fix")
    }

    "C3 optional inherit preserves current when user omits and no default" {
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))
      val result =
        engine.applyTemplate(
          transitionContext(
            template =
              template(
                TemplateField.Property(apiId = null, code = "resolution") to
                  TransitionFieldSpec(participation = FieldParticipation.OPTIONAL)
              ),
            config = config,
            templateContext = templateContext(),
            properties =
              FieldReconciliationPropertyMaps(
                current = mapOf("resolution" to JsonPrimitive("existing"))
              ),
            permissionContext = permissionContext(),
          )
        )

      result.propertyValues["resolution"] shouldBe JsonPrimitive("existing")
    }

    "C4 automatic applies default without write permission when user omits" {
      mockPropertyWriteDenied(fieldPermissions)
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))
      val result =
        engine.applyTemplate(
          createContext(
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
            properties =
              FieldReconciliationPropertyMaps(user = mapOf("title" to JsonPrimitive("Task"))),
            permissionContext = permissionContext(FieldPermissionOperation.CREATE),
          )
        )

      result.propertyValues["resolution"] shouldBe JsonPrimitive("auto")
    }

    "C6 immutable keeps current even when template default exists" {
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))
      val result =
        engine.applyTemplate(
          transitionContext(
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
            properties =
              FieldReconciliationPropertyMaps(
                current = mapOf("resolution" to JsonPrimitive("existing"))
              ),
            permissionContext = permissionContext(),
          )
        )

      result.propertyValues["resolution"] shouldBe JsonPrimitive("existing")
    }

    "C7 required immutable without current value fails" {
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<InvalidRequestException> {
          engine.applyTemplate(
            transitionContext(
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
              properties = FieldReconciliationPropertyMaps(),
              permissionContext = permissionContext(),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TRANSITION_FIELD_IMMUTABLE_BUT_REQUIRED
    }

    "D transition applies user.currentUser to assignee system field" {
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
        FieldMutationPolicy(allowsUserSubmission = false, bindingAllowsWrite = true)
      val config = configWithProperties(emptyList())
      val result =
        engine.applyTemplate(
          transitionContext(
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
            properties = FieldReconciliationPropertyMaps(),
            permissionContext = permissionContext(),
          )
        )

      result.systemFields["assignee"] shouldBe "usr_test"
    }

    "reconciles title and description together on transition" {
      val config = configWithProperties(emptyList())
      val result =
        engine.applyTemplate(
          transitionContext(
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
            properties =
              FieldReconciliationPropertyMaps(
                user =
                  mapOf(
                    "title" to JsonPrimitive("Updated title"),
                    "description" to JsonPrimitive("<p>Updated body</p>"),
                  )
              ),
            permissionContext = permissionContext(),
          )
        )

      result.systemFields["title"] shouldBe "Updated title"
      result.systemFields["description"] shouldBe "<p>Updated body</p>"
    }

    "reconcileTransitionComment uses template default when user comment absent" {
      val body =
        engine.reconcileTransitionComment(
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
        engine.reconcileTransitionComment(
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
          engine.reconcileTransitionComment(
            spec = null,
            templateContext = templateContext(),
            userComment = "unexpected",
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_UNEXPECTED_FIELD
    }

    "assertPatch throws when property write permission denied" {
      coEvery { fieldPermissions.resolvePatchPolicy(any(), any()) } returns
        FieldMutationPolicy(allowsUserSubmission = false, bindingAllowsWrite = false)
      val resolution = property("resolution", WorkItemPropertyDataType.TEXT)
      val config = configWithProperties(listOf(resolution))

      shouldThrow<PermissionDeniedException> {
          engine.assertPatch(
            PatchMutationContext(
              config = config,
              permissionContext = permissionContext(),
              propertyInputs = mapOf("resolution" to JsonPrimitive("blocked")),
              systemFieldInputs = emptyMap(),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_FIELD_WRITE_DENIED
    }

    "assertPatch denies protected system fields" {
      coEvery { fieldPermissions.resolvePatchPolicy(any(), any()) } answers
        {
          val field = secondArg<TemplateField>()
          val allowed = field is TemplateField.System && field.canonicalName == "title"
          FieldMutationPolicy(allowsUserSubmission = allowed, bindingAllowsWrite = allowed)
        }

      shouldThrow<PermissionDeniedException> {
          engine.assertPatch(
            PatchMutationContext(
              config = configWithProperties(emptyList()),
              permissionContext = permissionContext(),
              propertyInputs = emptyMap(),
              systemFieldInputs = mapOf("title" to "Allowed", "assignee" to "usr_other"),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_FIELD_WRITE_DENIED
    }

    "planForm returns editable metadata with defaults" {
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))
      val plan =
        engine.planForm(
          template =
            template(
              TemplateField.Property(apiId = null, code = "resolution") to
                TransitionFieldSpec(
                  participation = FieldParticipation.OPTIONAL,
                  value = TemplateValueExpression.Literal(JsonPrimitive("default")),
                )
            ),
          config = config,
          templateContext = templateContext(),
          permissionContext = permissionContext(),
        )

      plan.fieldMeta.single().path shouldBe "property.resolution"
      plan.fieldMeta.single().defaultValue shouldBe JsonPrimitive("default")
      plan.fieldMeta.single().editable shouldBe true
    }

    "buildCommentMeta returns null when comment spec absent" {
      engine.buildCommentMeta(spec = null, templateContext = templateContext()) shouldBe null
    }

    "buildCommentMeta returns editable metadata with default template" {
      val meta =
        engine.buildCommentMeta(
          spec =
            ink.doa.workbench.core.workitem.template.CommentFieldSpec(
              participation = FieldParticipation.OPTIONAL,
              template = TemplateValueExpression.Literal(JsonPrimitive("Auto comment")),
            ),
          templateContext = templateContext(),
        )

      meta?.defaultTemplate shouldBe "Auto comment"
      meta?.editable shouldBe true
    }

    "reconcileTransitionComment rejects automatic participation" {
      shouldThrow<InvalidRequestException> {
          engine.reconcileTransitionComment(
            spec =
              ink.doa.workbench.core.workitem.template.CommentFieldSpec(
                participation = FieldParticipation.AUTOMATIC,
                template = TemplateValueExpression.Literal(JsonPrimitive("auto")),
              ),
            templateContext = templateContext(),
            userComment = null,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TRANSITION_COMMENT_PARTICIPATION_INVALID
    }

    "reconcileTransitionComment requires body when participation is required" {
      shouldThrow<InvalidRequestException> {
          engine.reconcileTransitionComment(
            spec =
              ink.doa.workbench.core.workitem.template.CommentFieldSpec(
                participation = FieldParticipation.REQUIRED,
                template = null,
              ),
            templateContext = templateContext(),
            userComment = "   ",
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TRANSITION_COMMENT_REQUIRED
    }

    "transition reject unauthorized mutation throws when user overrides" {
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
        FieldMutationPolicy(allowsUserSubmission = true, bindingAllowsWrite = false)
      val config =
        configWithProperties(listOf(property("resolution", WorkItemPropertyDataType.TEXT)))

      shouldThrow<PermissionDeniedException> {
          engine.applyTemplate(
            transitionContext(
              template =
                template(
                  TemplateField.Property(apiId = null, code = "resolution") to
                    TransitionFieldSpec(
                      participation = FieldParticipation.OPTIONAL,
                      onUnauthorized = UnauthorizedMutationBehavior.REJECT,
                    )
                ),
              config = config,
              templateContext = templateContext(),
              properties =
                FieldReconciliationPropertyMaps(
                  current = mapOf("resolution" to JsonPrimitive("existing")),
                  user = mapOf("resolution" to JsonPrimitive("wont_fix")),
                ),
              permissionContext = permissionContext(),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TRANSITION_UNAUTHORIZED_FIELD_MUTATION
    }

    "transition required missing value throws property required" {
      val config = configWithProperties(listOf(property("summary", WorkItemPropertyDataType.TEXT)))

      shouldThrow<InvalidRequestException> {
          engine.applyTemplate(
            transitionContext(
              template =
                template(
                  TemplateField.Property(apiId = null, code = "summary") to
                    TransitionFieldSpec(participation = FieldParticipation.REQUIRED)
                ),
              config = config,
              templateContext = templateContext(),
              properties = FieldReconciliationPropertyMaps(),
              permissionContext = permissionContext(),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_PROPERTY_REQUIRED
    }

    "transition resolves current system fields from work item context" {
      val config = configWithProperties(emptyList())
      val issue = workItemRecord()
      val context =
        WorkItemValueTemplateContext(
          tenantId = issue.tenantId,
          projectId = issue.projectId,
          currentUserApiId = "usr_test",
          currentProjectApiId = "prj_test",
          actorUserId = UUID.randomUUID(),
          workItem = issue,
        )
      val result =
        engine.applyTemplate(
          transitionContext(
            template =
              template(
                TemplateField.System("title") to
                  TransitionFieldSpec(
                    participation = FieldParticipation.OPTIONAL,
                    writeGrant = FieldWriteGrant.TRANSITION_WRITABLE,
                  ),
                TemplateField.System("assignee") to
                  TransitionFieldSpec(
                    participation = FieldParticipation.OPTIONAL,
                    writeGrant = FieldWriteGrant.TRANSITION_WRITABLE,
                  ),
              ),
            config = config,
            templateContext = context,
            properties = FieldReconciliationPropertyMaps(),
            permissionContext = permissionContext(),
          )
        )

      result.systemFields["title"] shouldBe issue.title
      result.systemFields["assignee"] shouldBe issue.assigneeApiId?.value
    }

    "transition accepts property submission by api id key" {
      val resolution = property("resolution", WorkItemPropertyDataType.TEXT)
      val config = configWithProperties(listOf(resolution))
      val result =
        engine.applyTemplate(
          transitionContext(
            template =
              template(
                TemplateField.Property(
                  apiId = resolution.propertyApiId.value,
                  code = "resolution",
                ) to
                  TransitionFieldSpec(
                    participation = FieldParticipation.OPTIONAL,
                    writeGrant = FieldWriteGrant.TRANSITION_WRITABLE,
                  )
              ),
            config = config,
            templateContext = templateContext(),
            properties =
              FieldReconciliationPropertyMaps(
                user = mapOf(resolution.propertyApiId.value to JsonPrimitive("done"))
              ),
            permissionContext = permissionContext(),
          )
        )

      result.propertyValues["resolution"] shouldBe JsonPrimitive("done")
    }
  })

private fun mockTitleOnlyEditable(fieldPermissions: WorkItemFieldPermissionService) {
  coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } answers
    {
      val field = secondArg<TemplateField>()
      val allowed = field is TemplateField.System && field.canonicalName == "title"
      FieldMutationPolicy(allowsUserSubmission = allowed, bindingAllowsWrite = allowed)
    }
}

private fun mockPropertyWriteDenied(fieldPermissions: WorkItemFieldPermissionService) {
  coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } answers
    {
      val field = secondArg<TemplateField>()
      val bindingAllowsWrite = field is TemplateField.System
      val allowsUserSubmission = field is TemplateField.System && field.canonicalName == "title"
      FieldMutationPolicy(allowsUserSubmission, bindingAllowsWrite)
    }
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

private fun workItemRecord(): WorkItemRecord =
  WorkItemRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("iss"),
    tenantId = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    issueTypeApiId = PublicId.new("typ"),
    issueTypeConfigApiId = PublicId.new("itc"),
    key = "CORE-1",
    title = "Existing title",
    description = "<p>Body</p>",
    statusId = UUID.randomUUID(),
    statusApiId = PublicId.new("sts"),
    statusGroup = WorkItemStatusGroup.TODO,
    reporterId = UUID.randomUUID(),
    assigneeId = UUID.randomUUID(),
    priorityApiId = PublicId.new("pri"),
    reporterApiId = PublicId.new("usr"),
    assigneeApiId = PublicId.new("usr"),
    sprintApiId = PublicId.new("spr"),
    properties = JsonObject(emptyMap()),
    createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
  )

private data class FieldReconciliationPropertyMaps(
  val current: Map<String, JsonElement> = emptyMap(),
  val user: Map<String, JsonElement> = emptyMap(),
)

private fun transitionContext(
  template: WorkItemTransitionFieldsTemplate,
  config: IssueTypeConfigDetails,
  templateContext: WorkItemValueTemplateContext = templateContext(),
  properties: FieldReconciliationPropertyMaps = FieldReconciliationPropertyMaps(),
  permissionContext: WorkItemFieldPermissionContext = permissionContext(),
) =
  FieldReconciliationContext(
    template = template,
    expectedTarget = WorkItemValueTemplateTarget.TRANSITION,
    config = config,
    templateContext = templateContext,
    currentProperties = properties.current,
    userProperties = properties.user,
    permissionContext = permissionContext.copy(operation = FieldPermissionOperation.UPDATE),
  )

private fun createContext(
  template: WorkItemTransitionFieldsTemplate,
  config: IssueTypeConfigDetails,
  templateContext: WorkItemValueTemplateContext = templateContext(),
  properties: FieldReconciliationPropertyMaps = FieldReconciliationPropertyMaps(),
  permissionContext: WorkItemFieldPermissionContext =
    permissionContext(FieldPermissionOperation.CREATE),
) =
  FieldReconciliationContext(
    template = template,
    expectedTarget = WorkItemValueTemplateTarget.CREATE,
    config = config,
    templateContext = templateContext,
    currentProperties = emptyMap(),
    userProperties = properties.user,
    permissionContext = permissionContext.copy(operation = FieldPermissionOperation.CREATE),
  )
