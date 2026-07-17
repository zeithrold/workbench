package one.ztd.workbench.web.api

import java.net.URI
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.errors.WorkbenchException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

internal object ProblemDetailSupport {
  fun problem(status: HttpStatus, title: String, error: WorkbenchException): ProblemDetail =
    problem(status, title, error.message.orEmpty(), error.errorCode)

  fun problem(status: HttpStatus, title: String, detail: String): ProblemDetail =
    problem(status, title, detail, WorkbenchErrorCode.REQUEST_INVALID)

  fun problem(
    status: HttpStatus,
    title: String,
    detail: String,
    errorCode: WorkbenchErrorCode,
  ): ProblemDetail =
    ProblemDetail.forStatusAndDetail(status, detail).apply {
      this.title = title
      type =
        URI.create("https://api.ztd.one/workbench/problems/${title.lowercase().replace(" ", "-")}")
      setProperty("code", errorCode.code)
    }
}
