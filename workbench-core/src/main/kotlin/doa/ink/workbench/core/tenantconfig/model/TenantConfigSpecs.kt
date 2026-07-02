package doa.ink.workbench.core.tenantconfig.model

import kotlinx.serialization.Serializable

@Serializable
data class MailSmtpTenantConfig(
  val enabled: Boolean = false,
  val fromAddress: String? = null,
  val host: String? = null,
  val port: Int = 587,
  val username: String? = null,
  val passwordSecretRef: String? = null,
)

@Serializable
data class AuthSessionTenantConfig(
  val sessionTtlMinutes: Long = 720,
  val bearerTokenTtlDays: Long = 30,
  val allowBearerTokens: Boolean = true,
)

object TenantConfigSpecs {
  val MailSmtp =
    TenantConfigSpec(
      key = TenantConfigKey("mail.smtp"),
      serializer = MailSmtpTenantConfig.serializer(),
      defaultValue = MailSmtpTenantConfig(),
    )

  val AuthSession =
    TenantConfigSpec(
      key = TenantConfigKey("auth.session"),
      serializer = AuthSessionTenantConfig.serializer(),
      defaultValue = AuthSessionTenantConfig(),
    )
}
