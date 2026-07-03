package ink.doa.workbench.web.api

import ink.doa.workbench.core.common.context.ApiVersion
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.PermissionDeniedException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpStatus

class GlobalExceptionHandlerTest :
  StringSpec({
    "invalid requests are rendered as problem details" {
      val problem =
        GlobalExceptionHandler()
          .invalid(InvalidRequestException(WorkbenchErrorCode.REQUEST_INVALID, "bad input"))

      problem.status shouldBe HttpStatus.BAD_REQUEST.value()
      problem.title shouldBe "Invalid Request"
      problem.detail shouldBe "bad input"
      problem.properties?.get("code") shouldBe "request.invalid"
    }

    "illegal arguments use the generic invalid request code" {
      val problem = GlobalExceptionHandler().invalid(IllegalArgumentException("bad input"))

      problem.status shouldBe HttpStatus.BAD_REQUEST.value()
      problem.properties?.get("code") shouldBe "request.invalid"
    }

    "database failures use the database unavailable code" {
      val problem = GlobalExceptionHandler().database(DataAccessResourceFailureException("down"))

      problem.status shouldBe HttpStatus.SERVICE_UNAVAILABLE.value()
      problem.title shouldBe "Database Unavailable"
      problem.properties?.get("code") shouldBe "infrastructure.database_unavailable"
    }

    "permission denies render mapped authorization codes" {
      val code = WorkbenchErrorCode.fromAuthorizationReason("no_matching_binding")
      val problem =
        GlobalExceptionHandler().denied(PermissionDeniedException(code, "No active binding."))

      problem.status shouldBe HttpStatus.FORBIDDEN.value()
      problem.properties?.get("code") shouldBe "auth.permission.no_matching_binding"
    }

    "default API version uses date header format" {
      ApiVersion.Default.value shouldBe "2026-07-03"
      ApiVersion.HeaderName shouldBe "X-Workbench-API-Version"
    }
  })
