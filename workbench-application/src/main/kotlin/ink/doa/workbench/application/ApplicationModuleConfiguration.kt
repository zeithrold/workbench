package ink.doa.workbench.application

import ink.doa.workbench.agile.AgileModuleConfiguration
import ink.doa.workbench.application.jobs.messaging.MessagingProperties
import ink.doa.workbench.identity.IdentityModuleConfiguration
import ink.doa.workbench.identity.invitation.InvitationLinkProperties
import ink.doa.workbench.notification.NotificationModuleConfiguration
import ink.doa.workbench.tenant.TenantModuleConfiguration
import ink.doa.workbench.tenant.instance.InstanceProperties
import org.springframework.boot.context.TypeExcludeFilter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import

@Configuration(proxyBeanMethods = false)
@ComponentScan(
  basePackages = ["ink.doa.workbench.application"],
  excludeFilters =
    [ComponentScan.Filter(type = FilterType.CUSTOM, classes = [TypeExcludeFilter::class])],
)
@EnableConfigurationProperties(
  InstanceProperties::class,
  InvitationLinkProperties::class,
  MessagingProperties::class,
)
@Import(
  AgileModuleConfiguration::class,
  IdentityModuleConfiguration::class,
  NotificationModuleConfiguration::class,
  TenantModuleConfiguration::class,
)
class ApplicationModuleConfiguration
