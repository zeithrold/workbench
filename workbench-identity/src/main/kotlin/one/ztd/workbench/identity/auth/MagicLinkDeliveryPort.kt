package one.ztd.workbench.identity.auth

import one.ztd.workbench.tenant.tenantconfig.model.MailSmtpTenantConfig

interface MagicLinkDeliveryPort {
  suspend fun send(command: SendMagicLinkCommand)
}

data class SendMagicLinkCommand(
  val to: String,
  val token: String,
  val mailConfig: MailSmtpTenantConfig,
)
