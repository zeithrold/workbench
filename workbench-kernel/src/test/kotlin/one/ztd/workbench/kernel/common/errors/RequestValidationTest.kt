package one.ztd.workbench.kernel.common.errors

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RequestValidationTest :
  StringSpec({
    "requireValid throws invalid request exception" {
      shouldThrow<InvalidRequestException> {
          requireValid(false, WorkbenchErrorCode.REQUEST_INVALID, "bad input")
        }
        .message shouldBe "bad input"
    }

    "requireFound throws resource not found exception" {
      shouldThrow<ResourceNotFoundException> {
          requireFound(false, WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND, "missing")
        }
        .message shouldBe "missing"
    }

    "requireValid passes when condition is true" {
      requireValid(true, WorkbenchErrorCode.REQUEST_INVALID)
    }
  })
