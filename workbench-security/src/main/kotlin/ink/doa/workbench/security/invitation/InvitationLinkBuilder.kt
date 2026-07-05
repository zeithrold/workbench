package ink.doa.workbench.security.invitation

import ink.doa.workbench.core.common.context.RequestHost
import ink.doa.workbench.core.invitation.InvitationLinkPaths
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.stereotype.Component

/** Public base URL settings for invitation links sent by email or other channels. */
@ConfigurationProperties(prefix = "workbench.invitation-link")
data class InvitationLinkProperties
@ConstructorBinding
constructor(
  /** Absolute base URL prepended to invitation paths when the request host is unavailable. */
  val publicBaseUrl: String?
)

@Component
class InvitationLinkBuilder(private val properties: InvitationLinkProperties) {
  fun build(path: String, variables: Map<String, String>, requestHost: RequestHost?): String {
    val resolvedPath =
      variables.entries.fold(path) { current, (key, value) ->
        current.replace("{$key}", value)
      }
    val baseUrl = resolveBaseUrl(requestHost)
    return "$baseUrl$resolvedPath"
  }

  fun buildInvitationLink(token: String, requestHost: RequestHost?): String =
    build(
      InvitationLinkPaths.INVITATION,
      mapOf("token" to token),
      requestHost,
    )

  private fun resolveBaseUrl(requestHost: RequestHost?): String {
    properties.publicBaseUrl
      ?.takeIf { it.isNotBlank() }
      ?.let {
        return it.trimEnd('/')
      }
    requireNotNull(requestHost) {
      "workbench.invitation-link.public-base-url is not configured and request host is unavailable."
    }
    return "${requestHost.scheme}://${requestHost.host}"
  }
}
