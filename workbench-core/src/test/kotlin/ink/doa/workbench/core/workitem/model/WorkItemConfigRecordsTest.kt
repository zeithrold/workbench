package ink.doa.workbench.core.workitem.model

import ink.doa.workbench.core.common.errors.InvalidRequestException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class WorkItemConfigRecordsTest :
  StringSpec({
    "maps database values to work item configuration enums" {
      WorkItemConfigScope.fromDbValue("tenant") shouldBe WorkItemConfigScope.TENANT
      WorkItemStatusGroup.fromDbValue("in_progress") shouldBe WorkItemStatusGroup.IN_PROGRESS
      WorkItemPropertyDataType.fromDbValue("single_select") shouldBe
        WorkItemPropertyDataType.SINGLE_SELECT
    }

    "rejects unknown enum values" {
      shouldThrow<InvalidRequestException> { WorkItemConfigScope.fromDbValue("workspace") }
      shouldThrow<InvalidRequestException> { WorkItemStatusGroup.fromDbValue("review") }
      shouldThrow<InvalidRequestException> { WorkItemPropertyDataType.fromDbValue("currency") }
    }

    "create workflow command carries tenant scoped metadata" {
      val tenantId = java.util.UUID.randomUUID()
      val actorId = java.util.UUID.randomUUID()
      val command =
        CreateWorkflowCommand(
          tenantId = tenantId,
          code = "default",
          name = "Default",
          description = "Primary",
          createdBy = actorId,
        )

      command.tenantId shouldBe tenantId
      command.createdBy shouldBe actorId
    }

    "maps all work item property data types from database values" {
      WorkItemPropertyDataType.fromDbValue("text") shouldBe WorkItemPropertyDataType.TEXT
      WorkItemPropertyDataType.fromDbValue("long_text") shouldBe WorkItemPropertyDataType.LONG_TEXT
      WorkItemPropertyDataType.fromDbValue("multi_user") shouldBe
        WorkItemPropertyDataType.MULTI_USER
      WorkItemPropertyDataType.fromDbValue("json") shouldBe WorkItemPropertyDataType.JSON
    }

    "maps work item config scope from database values" {
      WorkItemConfigScope.fromDbValue("project") shouldBe WorkItemConfigScope.PROJECT
    }
  })
