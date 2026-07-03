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
  })
