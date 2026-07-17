package one.ztd.workbench.web.api.warning

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import one.ztd.workbench.kernel.common.warning.WorkbenchWarningCollector
import one.ztd.workbench.kernel.common.warning.WorkbenchWarningConstants
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.filter.OncePerRequestFilter

class WorkbenchWarningFilter(
  private val warningCollectorProvider: ObjectProvider<WorkbenchWarningCollector>,
  private val warningSupport: WorkbenchWarningSupport,
) : OncePerRequestFilter() {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    filterChain.doFilter(request, response)
    if (response.status !in SUCCESS_STATUS_RANGE) {
      return
    }
    val collector = warningCollectorProvider.ifAvailable ?: return
    val warnings = collector.drain()
    if (warnings.isEmpty()) {
      return
    }
    val headerValue = warningSupport.toHeaderValue(warnings)
    response.setHeader(WorkbenchWarningConstants.HeaderName, headerValue)
    log.info(
      "workbench_warnings count={} codes={}",
      warnings.size,
      warnings.joinToString(",") { it.code.code },
    )
  }

  private companion object {
    val SUCCESS_STATUS_RANGE = 200..299
  }
}
