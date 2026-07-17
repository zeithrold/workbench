package one.ztd.workbench.web.api

import io.kotest.matchers.shouldBe
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class ProblemDetailSupportTest {
  @Test
  fun `problem maps workbench exception fields`() {
    val detail =
      ProblemDetailSupport.problem(
        status = HttpStatus.BAD_REQUEST,
        title = "Invalid Request",
        error =
          InvalidRequestException(
            WorkbenchErrorCode.REQUEST_INVALID,
            "Email is required",
          ),
      )

    detail.status shouldBe 400
    detail.title shouldBe "Invalid Request"
    detail.detail shouldBe "Email is required"
    detail.properties?.get("code") shouldBe WorkbenchErrorCode.REQUEST_INVALID.code
  }

  @Test
  fun `problem uses default error code for plain detail`() {
    val detail =
      ProblemDetailSupport.problem(
        status = HttpStatus.CONFLICT,
        title = "Conflict",
        detail = "Tenant slug already exists",
      )

    detail.status shouldBe 409
    detail.properties?.get("code") shouldBe WorkbenchErrorCode.REQUEST_INVALID.code
  }
}
