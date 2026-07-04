package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class WorkItemConfigSupportTest :
  StringSpec({
    "rejectIfInUse throws when resource is referenced" {
      shouldThrow<InvalidRequestException> {
          rejectIfInUse(
            inUse = true,
            message = "Status is still used by an active issue type config.",
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_CONFIG_RESOURCE_IN_USE
    }

    "rejectIfInUse allows unused resources" {
      rejectIfInUse(inUse = false, message = "unused")
    }
  })
