package ink.doa.workbench.web.api.warning

import ink.doa.workbench.core.common.warning.WorkbenchWarningCollector
import ink.doa.workbench.core.common.warning.WorkbenchWarningConstants
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
