package doa.ink.workbench.security.identity.auth

import doa.ink.workbench.core.common.errors.InvalidRequestException
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Component

@Component
class SamlFederatedClient {
  fun buildAuthorizeUrl(
    config: JsonObject,
    redirectUri: String,
    relayState: String,
  ): String {
    val idpSsoUrl =
      config.stringValue("idp_sso_url")
        ?: throw InvalidRequestException("idp_sso_url missing in SAML config.")
    return idpSsoUrl +
      "?" +
      encodeQueryParam("RelayState", relayState) +
      "&" +
      encodeQueryParam("ACS", redirectUri)
  }
}

object SamlResponseParser {
  fun parseNameId(samlResponse: String): String {
    val decoded = String(Base64.getDecoder().decode(samlResponse), StandardCharsets.UTF_8)
    val regex = Regex("""NameID[^>]*>([^<]+)</NameID>""")
    return regex.find(decoded)?.groupValues?.get(1)?.trim()
      ?: throw InvalidRequestException("Unable to parse SAML NameID.")
  }
}
