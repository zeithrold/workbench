package one.ztd.workbench.data.config

import one.ztd.workbench.kernel.common.warning.InMemoryWorkbenchWarningCollector
import one.ztd.workbench.kernel.common.warning.WorkbenchWarningCollector
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WorkbenchWarningFallbackConfiguration {
  @Bean
  @ConditionalOnMissingBean(WorkbenchWarningCollector::class)
  fun workbenchWarningCollector(): WorkbenchWarningCollector = InMemoryWorkbenchWarningCollector()
}
