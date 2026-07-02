@file:Suppress("ReturnCount", "ThrowsCount", "UnusedParameter")

package doa.ink.workbench.service.identity.auth

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.auth.AuthLoginStateRepository
import doa.ink.workbench.core.identity.auth.CredentialHasher
import doa.ink.workbench.core.identity.auth.CredentialSecretGenerator
import doa.ink.workbench.core.identity.auth.SecretResolver
import doa.ink.workbench.core.identity.model.AuthenticatedIdentity
import doa.ink.workbench.core.identity.model.CreateAuthLoginStateCommand
import doa.ink.workbench.core.identity.model.LoginMethodKind
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Service

@Service
class FederatedAuthService(
  private val loginAccounts: LoginAccountRepository,
  private val tenants: TenantRepository,
  private val loginStates: AuthLoginStateRepository,
  private val secretGenerator: CredentialSecretGenerator,
  private val credentialHasher: CredentialHasher,
  private val secretResolver: SecretResolver,
  private val clock: Clock,
) {
  private val json = Json { ignoreUnknownKeys = true }
  private val http = HttpClient.newHttpClient()
  private val stateTtl = Duration.ofMinutes(10)

  suspend fun beginAuthorize(
    loginMethodCode: String,
    tenantApiId: String,
    returnUrl: String,
    redirectUri: String,
  ): FederatedAuthorizeResult {
    val tenant =
      tenants.findByApiId(tenantApiId)
        ?: throw InvalidRequestException("Unknown tenant: $tenantApiId")
    val method =
      loginAccounts.findLoginMethodByCode(loginMethodCode)
        ?: throw InvalidRequestException("Unknown login method: $loginMethodCode")
    if (method.kind !in setOf(LoginMethodKind.OAUTH2, LoginMethodKind.OIDC, LoginMethodKind.SAML)) {
      throw InvalidRequestException(
        "Login method $loginMethodCode does not support federated authorize."
      )
    }
    val setting = loginAccounts.findTenantSetting(tenant.id, method.id)
    if (setting?.isEnabled != true) {
      throw InvalidRequestException("Login method is disabled for this tenant.")
    }

    val state = secretGenerator.generate()
    val verifier = secretGenerator.generate()
    val challenge = pkceChallenge(verifier)
    val now = OffsetDateTime.now(clock)
    loginStates.create(
      CreateAuthLoginStateCommand(
        stateHash = credentialHasher.hash(state),
        tenantId = tenant.id,
        loginMethodId = method.id,
        redirectUri = redirectUri,
        pkceVerifier = verifier,
        returnUrl = returnUrl,
        expiresAt = now.plus(stateTtl),
      )
    )

    val config = setting.config as? JsonObject ?: JsonObject(emptyMap())
    val authorizationUrl =
      when (method.kind) {
        LoginMethodKind.SAML -> buildSamlAuthorizeUrl(config, redirectUri, state)
        LoginMethodKind.OAUTH2,
        LoginMethodKind.OIDC ->
          buildOAuthAuthorizeUrl(
            config,
            setting.secretRef,
            redirectUri,
            state,
            challenge,
            method.kind,
          )
        else -> throw InvalidRequestException("Login method does not support federated authorize.")
      }
    return FederatedAuthorizeResult(authorizationUrl = authorizationUrl, state = state)
  }

  suspend fun completeOAuthCallback(
    code: String,
    state: String,
    redirectUri: String,
  ): AuthenticatedIdentity {
    val now = OffsetDateTime.now(clock)
    val loginState =
      loginStates.findActiveByStateHash(credentialHasher.hash(state), now)
        ?: throw InvalidRequestException("OAuth state is invalid or expired.")
    loginStates.consume(loginState.id, now)

    val methodRecord =
      loginAccounts.findLoginMethodById(loginState.loginMethodId)
        ?: throw InvalidRequestException("Login method no longer exists.")
    val setting =
      loginAccounts.findTenantSetting(loginState.tenantId, loginState.loginMethodId)
        ?: throw InvalidRequestException("Tenant login settings not found.")
    val config = setting.config as? JsonObject ?: JsonObject(emptyMap())
    val tokenResponse =
      exchangeAuthorizationCode(
        config,
        setting.secretRef,
        code,
        redirectUri,
        loginState.pkceVerifier,
      )
    val subject = fetchSubject(config, setting.secretRef, tokenResponse, methodRecord.kind)
    val account =
      loginAccounts.findLoginAccountByMethodAndSubject(methodRecord.code, normalizeSubject(subject))
        ?: throw InvalidRequestException("No linked account for federated identity.")
    val user =
      loginAccounts.findLinkedUser(account.id)
        ?: throw InvalidRequestException("No user linked for federated identity.")
    return AuthenticatedIdentity(user = user, loginAccount = account)
  }

  suspend fun completeSamlAcs(samlResponse: String, relayState: String): AuthenticatedIdentity {
    val now = OffsetDateTime.now(clock)
    val loginState =
      loginStates.findActiveByStateHash(credentialHasher.hash(relayState), now)
        ?: throw InvalidRequestException("SAML relay state is invalid or expired.")
    loginStates.consume(loginState.id, now)
    val methodRecord =
      loginAccounts.findLoginMethodById(loginState.loginMethodId)
        ?: throw InvalidRequestException("Login method no longer exists.")
    val subject = SamlResponseParser.parseNameId(samlResponse)
    val account =
      loginAccounts.findLoginAccountByMethodAndSubject(methodRecord.code, normalizeSubject(subject))
        ?: throw InvalidRequestException("No linked account for SAML identity.")
    val user =
      loginAccounts.findLinkedUser(account.id)
        ?: throw InvalidRequestException("No user linked for SAML identity.")
    return AuthenticatedIdentity(user = user, loginAccount = account)
  }

  private fun buildOAuthAuthorizeUrl(
    config: JsonObject,
    secretRef: String?,
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
    return authorizeEndpoint + "?" + params.entries.joinToString("&") { encode(it.key, it.value) }
  }

  private fun buildSamlAuthorizeUrl(
    config: JsonObject,
    redirectUri: String,
    relayState: String,
  ): String {
    val idpSsoUrl =
      config.stringValue("idp_sso_url")
        ?: throw InvalidRequestException("idp_sso_url missing in SAML config.")
    return idpSsoUrl + "?" + encode("RelayState", relayState) + "&" + encode("ACS", redirectUri)
  }

  private fun exchangeAuthorizationCode(
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
      append("&code=").append(encodeValue(code))
      append("&redirect_uri=").append(encodeValue(redirectUri))
      append("&client_id=").append(encodeValue(clientId))
      if (clientSecret != null) append("&client_secret=").append(encodeValue(clientSecret))
      if (verifier != null) append("&code_verifier=").append(encodeValue(verifier))
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

  private fun fetchSubject(
    config: JsonObject,
    secretRef: String?,
    tokenResponse: JsonObject,
    kind: LoginMethodKind,
  ): String {
    tokenResponse.stringValue("id_token")?.let { idToken ->
      val payload = idToken.split(".").getOrNull(1) ?: return@let null
      val decoded = String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8)
      val claims = json.parseToJsonElement(decoded).jsonObject
      claims.stringValue("email")?.let {
        return it
      }
      claims.stringValue("sub")?.let {
        return it
      }
    }
    val accessToken =
      tokenResponse.stringValue("access_token")
        ?: throw InvalidRequestException("access_token missing.")
    val userInfoEndpoint =
      config.stringValue("userinfo_endpoint")
        ?: config.stringValue("issuer")?.let {
          if (kind == LoginMethodKind.OIDC) "$it/oidc/userinfo" else null
        }
    if (userInfoEndpoint != null) {
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
        body.stringValue("sub")?.let {
          return it
        }
      }
    }
    throw InvalidRequestException("Unable to resolve federated subject.")
  }

  private fun pkceChallenge(verifier: String): String {
    val digest =
      MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
  }

  private fun JsonObject.stringValue(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

  private fun encode(key: String, value: String): String =
    "${encodeValue(key)}=${encodeValue(value)}"

  private fun encodeValue(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}

data class FederatedAuthorizeResult(val authorizationUrl: String, val state: String)

object SamlResponseParser {
  fun parseNameId(samlResponse: String): String {
    val decoded = String(Base64.getDecoder().decode(samlResponse), StandardCharsets.UTF_8)
    val regex = Regex("""NameID[^>]*>([^<]+)</NameID>""")
    return regex.find(decoded)?.groupValues?.get(1)?.trim()
      ?: throw InvalidRequestException("Unable to parse SAML NameID.")
  }
}
