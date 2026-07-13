package ink.doa.workbench.tenant

import org.springframework.boot.context.TypeExcludeFilter
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType

@Configuration(proxyBeanMethods = false)
@ComponentScan(
  basePackages = ["ink.doa.workbench.tenant"],
  excludeFilters =
    [ComponentScan.Filter(type = FilterType.CUSTOM, classes = [TypeExcludeFilter::class])],
)
class TenantModuleConfiguration
