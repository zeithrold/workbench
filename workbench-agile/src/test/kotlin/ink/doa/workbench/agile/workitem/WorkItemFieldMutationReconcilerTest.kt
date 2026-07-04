package ink.doa.workbench.agile.workitem

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
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class WorkItemFieldMutationReconcilerTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-04T10:15:30Z"), ZoneOffset.UTC)
    val fieldPermissions = mockk<WorkItemFieldPermissionService>()
    val reconciler = WorkItemFieldMutationReconciler(fieldPermissions, clock)

    "transition_writable allows user override without field permission" {
      coEvery { fieldPermissions.canWriteField(any(), any()) } returns false
      val config = configWithResolution()
      val result =
        reconciler.reconcileTransition(
          template =
            WorkItemTransitionFieldsTemplate(
              fields =
                mapOf(
                  TemplateField.Property(apiId = null, code = "resolution") to
                    TransitionFieldSpec(
                      participation = FieldParticipation.REQUIRED,
                      value = TemplateValueExpression.Literal(JsonPrimitive("fixed")),
                      writeGrant = FieldWriteGrant.TRANSITION_WRITABLE,
                    )
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

    "preserve_current ignores unauthorized user input" {
      coEvery { fieldPermissions.canWriteField(any(), any()) } returns false
      val config = configWithResolution()
      val result =
        reconciler.reconcileTransition(
          template =
            WorkItemTransitionFieldsTemplate(
              fields =
                mapOf(
                  TemplateField.Property(apiId = null, code = "resolution") to
                    TransitionFieldSpec(
                      participation = FieldParticipation.OPTIONAL,
                      onUnauthorized = UnauthorizedMutationBehavior.PRESERVE_CURRENT,
                    )
                )
            ),
          config = config,
          templateContext = templateContext(),
          currentProperties = mapOf("resolution" to JsonPrimitive("existing")),
          userProperties = mapOf("resolution" to JsonPrimitive("wont_fix")),
          permissionContext = permissionContext(),
        )

      result.propertyValues["resolution"] shouldBe JsonPrimitive("existing")
    }
  })

private fun permissionContext() =
  WorkItemFieldPermissionContext(
    tenantId = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    actorUserId = UUID.randomUUID(),
    operation = FieldPermissionOperation.UPDATE,
  )

private fun templateContext() =
  WorkItemValueTemplateContext(
    tenantId = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    currentUserApiId = "usr_test",
    currentProjectApiId = "prj_test",
    actorUserId = UUID.randomUUID(),
  )

private fun configWithResolution(): IssueTypeConfigDetails {
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
      ),
    statuses = emptyList(),
    properties =
      listOf(
        IssueTypeConfigPropertyRecord(
          id = UUID.randomUUID(),
          tenantId = tenantId,
          issueTypeConfigId = configId,
          propertyId = UUID.randomUUID(),
          propertyApiId = PublicId.new("fld"),
          code = "resolution",
          name = "resolution",
          dataType = WorkItemPropertyDataType.TEXT,
          isRequired = false,
          defaultValue = null,
          validationOverride = JsonObject(emptyMap()),
          rank = 100,
          displayConfig = JsonObject(emptyMap()),
        )
      ),
  )
}
