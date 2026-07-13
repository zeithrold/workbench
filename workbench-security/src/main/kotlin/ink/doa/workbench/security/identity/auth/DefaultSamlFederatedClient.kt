package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.identity.auth.SamlFederatedClient
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Component

@Component
class DefaultSamlFederatedClient : SamlFederatedClient {
  override fun buildAuthorizeUrl(
    config: JsonObject,
    redirectUri: String,
    relayState: String,
  ): String {
    val idpSsoUrl =
      config.stringValue("idp_sso_url")
        ?: throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_SAML_IDP_SSO_URL_MISSING)
    return idpSsoUrl +
      "?" +
      encodeQueryParam("RelayState", relayState) +
      "&" +
      encodeQueryParam("ACS", redirectUri)
  }

  override fun parseNameId(samlResponse: String): String {
    val decoded = String(Base64.getDecoder().decode(samlResponse), StandardCharsets.UTF_8)
    val regex = Regex("""NameID[^>]*>([^<]+)</NameID>""")
    return regex.find(decoded)?.groupValues?.get(1)?.trim()
      ?: throw InvalidRequestException(
        WorkbenchErrorCode.IDENTITY_FEDERATED_SUBJECT_PARSE_FAILED,
        "Unable to parse SAML NameID.",
      )
  }
}
