package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.testfixtures.AgileWorkItemFixtures
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.template.WorkItemTransitionFieldsTemplate
import ink.doa.workbench.core.workitem.template.WorkItemValueTemplateContext
import ink.doa.workbench.core.workitem.template.WorkItemValueTemplateTarget
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.util.UUID
import kotlinx.coroutines.runBlocking

class WorkItemFieldMutationSupportTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val actorId = UUID.randomUUID()
    val config = AgileWorkItemFixtures.sampleConfig(tenantId)

    "reconcileCreate delegates with create target and permission operation" {
      val reconciler = mockk<WorkItemFieldMutationReconciler>()
      val permissions = mockk<WorkItemFieldPermissionService>()
      val descriptionAttachments = mockk<WorkItemDescriptionAttachmentValidator>(relaxed = true)
      val support = WorkItemFieldMutationSupport(reconciler, permissions, descriptionAttachments)
      val contextSlot = slot<FieldReconciliationContext>()
      val expected =
        TransitionFieldReconcileResult(propertyValues = emptyMap(), systemFields = emptyMap())
      coEvery { reconciler.reconcileFields(capture(contextSlot)) } returns expected
      val template = WorkItemTransitionFieldsTemplate()
      val templateContext = mockk<WorkItemValueTemplateContext>(relaxed = true)
      val permissionContext =
        WorkItemFieldPermissionContext(
          tenantId,
          projectId,
          actorId,
          FieldPermissionOperation.UPDATE,
        )
      val command =
        CreateWorkItemCommand(
          tenantId = tenantId,
          projectId = projectId,
          issueTypeApiId = config.config.issueTypeApiId.value,
          title = "Task",
          description = null,
          reporterId = actorId,
          actorUserId = actorId,
        )

      val result = runBlocking {
        support.reconcileCreate(
          command = command,
          config = config,
          fieldsTemplate = template,
          templateContext = templateContext,
          permissionContext = permissionContext,
        )
      }

      result shouldBe expected
      coVerify(exactly = 1) { reconciler.reconcileFields(any()) }
      contextSlot.captured.expectedTarget shouldBe WorkItemValueTemplateTarget.CREATE
      contextSlot.captured.permissionContext.operation shouldBe FieldPermissionOperation.CREATE
      contextSlot.captured.config shouldBe config
      contextSlot.captured.template shouldBe template
    }
  })
