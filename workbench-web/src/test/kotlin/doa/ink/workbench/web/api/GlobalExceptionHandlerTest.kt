package doa.ink.workbench.web.api

import doa.ink.workbench.core.common.context.ApiVersion
import doa.ink.workbench.core.common.errors.InvalidRequestException
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus

class GlobalExceptionHandlerTest :
  StringSpec({
    "invalid requests are rendered as problem details" {
      val problem = GlobalExceptionHandler().invalid(InvalidRequestException("bad input"))

      problem.status shouldBe HttpStatus.BAD_REQUEST.value()
      problem.title shouldBe "Invalid Request"
      problem.detail shouldBe "bad input"
    }

    "default API version uses date header format" {
      ApiVersion.Default.value shouldBe "2026-07-03"
      ApiVersion.HeaderName shouldBe "X-Workbench-API-Version"
    }
  })
