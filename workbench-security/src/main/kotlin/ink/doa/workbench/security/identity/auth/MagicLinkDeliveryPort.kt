package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.tenantconfig.model.MailSmtpTenantConfig

interface MagicLinkDeliveryPort {
  suspend fun send(command: SendMagicLinkCommand)
}

data class SendMagicLinkCommand(
  val to: String,
  val token: String,
  val mailConfig: MailSmtpTenantConfig,
)
