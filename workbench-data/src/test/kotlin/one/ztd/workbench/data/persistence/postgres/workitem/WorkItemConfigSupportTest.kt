package one.ztd.workbench.data.persistence.postgres.workitem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

class WorkItemConfigSupportTest :
  StringSpec({
    "rejectIfInUse throws when resource is referenced" {
      shouldThrow<InvalidRequestException> {
          ExposedWorkItemConfigUsageChecks.rejectIfInUse(
            inUse = true,
            message = "Status is still used by an active issue type config.",
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_CONFIG_RESOURCE_IN_USE
    }

    "rejectIfInUse allows unused resources" {
      ExposedWorkItemConfigUsageChecks.rejectIfInUse(inUse = false, message = "unused")
    }
  })
