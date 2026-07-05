package ink.doa.workbench.web.api.warning

import ink.doa.workbench.core.common.warning.WorkbenchWarningCollector
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.core.Ordered
import org.springframework.web.context.WebApplicationContext

@Configuration
class WorkbenchWarningConfiguration {
  @Bean fun workbenchWarningSupport(): WorkbenchWarningSupport = WorkbenchWarningSupport()

  @Bean
  @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
  fun workbenchWarningCollector(): WorkbenchWarningCollector =
    RequestScopedWorkbenchWarningCollector()

  @Bean
  fun workbenchWarningFilterRegistration(
    warningCollectorProvider:
      org.springframework.beans.factory.ObjectProvider<WorkbenchWarningCollector>,
    warningSupport: WorkbenchWarningSupport,
  ): FilterRegistrationBean<WorkbenchWarningFilter> =
    FilterRegistrationBean(WorkbenchWarningFilter(warningCollectorProvider, warningSupport)).apply {
      order = Ordered.LOWEST_PRECEDENCE
    }
}
