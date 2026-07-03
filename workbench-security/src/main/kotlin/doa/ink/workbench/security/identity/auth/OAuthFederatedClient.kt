@file:Suppress("ReturnCount", "ThrowsCount")

package doa.ink.workbench.security.identity.auth

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.identity.auth.SecretResolver
import doa.ink.workbench.core.identity.model.LoginMethodKind
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.springframework.stereotype.Component

@Component
class OAuthFederatedClient(private val secretResolver: SecretResolver) {
  private val json = Json { ignoreUnknownKeys = true }
  private val http = HttpClient.newHttpClient()

  fun pkceChallenge(verifier: String): String {
    val digest =
      MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
  }

  fun buildAuthorizeUrl(
    config: JsonObject,
    redirectUri: String,
    state: String,
    challenge: String,
    kind: LoginMethodKind,
  ): String {
    val clientId =
      config.stringValue("client_id")
        ?: throw InvalidRequestException("client_id missing in config.")
    val issuer = config.stringValue("issuer") ?: config.stringValue("authorization_endpoint_base")
    val authorizeEndpoint =
      when {
        config.stringValue("authorization_endpoint") != null ->
          config.stringValue("authorization_endpoint")!!
        issuer != null && kind == LoginMethodKind.OIDC -> "$issuer/oauth2/v2.0/authorize"
        issuer != null -> "$issuer/oauth2/authorize"
        else -> throw InvalidRequestException("authorization endpoint missing in config.")
      }
    val scope =
      config.stringValue("scope")
        ?: if (kind == LoginMethodKind.OIDC) "openid profile email" else "read"
    val params =
      linkedMapOf(
        "response_type" to "code",
        "client_id" to clientId,
        "redirect_uri" to redirectUri,
        "scope" to scope,
        "state" to state,
        "code_challenge" to challenge,
        "code_challenge_method" to "S256",
      )
    return authorizeEndpoint +
      "?" +
      params.entries.joinToString("&") { encodeQueryParam(it.key, it.value) }
  }

  fun exchangeAuthorizationCode(
    config: JsonObject,
    secretRef: String?,
    code: String,
    redirectUri: String,
    verifier: String?,
  ): JsonObject {
    val clientId =
      config.stringValue("client_id") ?: throw InvalidRequestException("client_id missing.")
    val clientSecret = secretRef?.let(secretResolver::resolve)
    val tokenEndpoint =
      config.stringValue("token_endpoint")
        ?: config.stringValue("issuer")?.let { "$it/oauth2/v2.0/token" }
        ?: throw InvalidRequestException("token_endpoint missing.")
    val body = buildString {
      append("grant_type=authorization_code")
      append("&code=").append(encodeQueryValue(code))
      append("&redirect_uri=").append(encodeQueryValue(redirectUri))
      append("&client_id=").append(encodeQueryValue(clientId))
      if (clientSecret != null) append("&client_secret=").append(encodeQueryValue(clientSecret))
      if (verifier != null) append("&code_verifier=").append(encodeQueryValue(verifier))
    }
    val request =
      HttpRequest.newBuilder()
        .uri(URI.create(tokenEndpoint))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() !in 200..299) {
      throw InvalidRequestException("Token exchange failed.")
    }
    return json.parseToJsonElement(response.body()).jsonObject
  }

  fun resolveSubject(
    config: JsonObject,
    tokenResponse: JsonObject,
    kind: LoginMethodKind,
  ): String {
    if (kind != LoginMethodKind.OAUTH2) {
      subjectFromIdToken(tokenResponse)?.let {
        return it
      }
    }
    return subjectFromUserInfo(config, tokenResponse, kind)
  }

  private fun subjectFromIdToken(tokenResponse: JsonObject): String? {
    val idToken = tokenResponse.stringValue("id_token") ?: return null
    val payload = idToken.split(".").getOrNull(1) ?: return null
    val decoded = String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8)
    val claims = json.parseToJsonElement(decoded).jsonObject
    return claims.stringValue("email") ?: claims.stringValue("sub")
  }

  private fun subjectFromUserInfo(
    config: JsonObject,
    tokenResponse: JsonObject,
    kind: LoginMethodKind,
  ): String {
    val accessToken =
      tokenResponse.stringValue("access_token")
        ?: throw InvalidRequestException("access_token missing.")
    val userInfoEndpoint =
      config.stringValue("userinfo_endpoint")
        ?: config.stringValue("issuer")?.let {
          if (kind == LoginMethodKind.OIDC) "$it/oidc/userinfo" else null
        }
        ?: throw InvalidRequestException("userinfo_endpoint missing.")
    val request =
      HttpRequest.newBuilder()
        .uri(URI.create(userInfoEndpoint))
        .header("Authorization", "Bearer $accessToken")
        .GET()
        .build()
    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() in 200..299) {
      val body = json.parseToJsonElement(response.body()).jsonObject
      body.stringValue("email")?.let {
        return it
      }
      body.stringValue("preferred_username")?.let {
        return it
      }
      body.stringValue("sub")?.let {
        return it
      }
    }
    throw InvalidRequestException("Unable to resolve federated subject.")
  }
}
