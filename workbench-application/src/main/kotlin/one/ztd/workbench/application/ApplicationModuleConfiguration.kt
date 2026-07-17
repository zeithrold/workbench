package one.ztd.workbench.application

import one.ztd.workbench.agile.AgileModuleConfiguration
import one.ztd.workbench.application.jobs.messaging.MessagingProperties
import one.ztd.workbench.identity.IdentityModuleConfiguration
import one.ztd.workbench.identity.invitation.InvitationLinkProperties
import one.ztd.workbench.notification.NotificationModuleConfiguration
import one.ztd.workbench.tenant.TenantModuleConfiguration
import one.ztd.workbench.tenant.instance.InstanceProperties
import org.springframework.boot.context.TypeExcludeFilter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import

@Configuration(proxyBeanMethods = false)
@ComponentScan(
  basePackages = ["one.ztd.workbench.application"],
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
