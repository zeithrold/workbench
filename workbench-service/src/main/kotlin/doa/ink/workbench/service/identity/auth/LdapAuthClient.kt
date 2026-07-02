@file:Suppress("ThrowsCount")

package doa.ink.workbench.service.identity.auth

import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.identity.model.TenantLoginMethodSettingRecord
import javax.naming.Context
import javax.naming.directory.InitialDirContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

@Component
class LdapAuthClient {
  private val json = Json { ignoreUnknownKeys = true }

  fun authenticate(
    setting: TenantLoginMethodSettingRecord,
    subject: String,
    password: String,
  ): String {
    val config = setting.config as? JsonObject ?: JsonObject(emptyMap())
    val host =
      config.stringValue("host") ?: throw AuthenticationFailedException("Invalid credentials.")
    val port = config.stringValue("port")?.toIntOrNull() ?: 389
    val baseDn =
      config.stringValue("base_dn") ?: throw AuthenticationFailedException("Invalid credentials.")
    val userDn = "uid=$subject,$baseDn"
    val env =
      mapOf(
        Context.INITIAL_CONTEXT_FACTORY to "com.sun.jndi.ldap.LdapCtxFactory",
        Context.PROVIDER_URL to "ldap://$host:$port",
        Context.SECURITY_AUTHENTICATION to "simple",
        Context.SECURITY_PRINCIPAL to userDn,
        Context.SECURITY_CREDENTIALS to password,
      )
    return try {
      InitialDirContext(env.toProperties()).close()
      normalizeSubject(subject)
    } catch (_: Exception) {
      throw AuthenticationFailedException("Invalid credentials.")
    }
  }

  private fun JsonObject.stringValue(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
