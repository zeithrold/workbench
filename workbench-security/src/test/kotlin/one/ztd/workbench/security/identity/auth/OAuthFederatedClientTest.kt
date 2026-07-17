package one.ztd.workbench.security.identity.auth

import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.security.identity.auth.support.MapSecretResolver

class OAuthFederatedClientTest :
  StringSpec({
    val oauthClient =
      DefaultOAuthFederatedClient(MapSecretResolver(mapOf("secret-ref" to "top-secret")))

    "buildAuthorizeUrl uses explicit authorization endpoint" {
      val url =
        oauthClient.buildAuthorizeUrl(
          config =
            JsonObject(
              mapOf(
                "client_id" to JsonPrimitive("client"),
                "authorization_endpoint" to
                  JsonPrimitive("https://idp.example.test/oauth2/authorize"),
                "scope" to JsonPrimitive("openid"),
              )
            ),
          redirectUri = "https://app.example.test/callback",
          state = "state-token",
          challenge = "challenge-token",
          kind = LoginMethodKind.OAUTH2,
        )

      url shouldContain "https://idp.example.test/oauth2/authorize"
      url shouldContain "scope=openid"
    }

    "buildAuthorizeUrl uses oauth2 authorize path for oauth2 issuer" {
      val url =
        oauthClient.buildAuthorizeUrl(
          config =
            JsonObject(
              mapOf(
                "client_id" to JsonPrimitive("client"),
                "authorization_endpoint_base" to JsonPrimitive("https://idp.example.test"),
              )
            ),
          redirectUri = "https://app.example.test/callback",
          state = "state-token",
          challenge = "challenge-token",
          kind = LoginMethodKind.OAUTH2,
        )

      url shouldContain "https://idp.example.test/oauth2/authorize"
      url shouldContain "scope=read"
    }

    "buildAuthorizeUrl throws when authorization endpoint missing" {
      shouldThrow<InvalidRequestException> {
          oauthClient.buildAuthorizeUrl(
            JsonObject(mapOf("client_id" to JsonPrimitive("client"))),
            "https://app.example.test/callback",
            "state",
            "challenge",
            LoginMethodKind.OAUTH2,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_OAUTH_AUTHORIZATION_ENDPOINT_MISSING
    }

    "exchangeAuthorizationCode returns token response" {
      val server = HttpServer.create(InetSocketAddress(0), 0)
      server.createContext("/oauth2/token") { exchange ->
        val response = """{"access_token":"access-token","token_type":"Bearer"}"""
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
      }
      server.start()
      try {
        val port = server.address.port
        val tokenResponse =
          oauthClient.exchangeAuthorizationCode(
            config =
              JsonObject(
                mapOf(
                  "client_id" to JsonPrimitive("client"),
                  "token_endpoint" to JsonPrimitive("http://127.0.0.1:$port/oauth2/token"),
                )
              ),
            secretRef = "secret-ref",
            code = "auth-code",
            redirectUri = "https://app.example.test/callback",
            verifier = "pkce-verifier",
          )

        tokenResponse["access_token"]?.toString() shouldBe "\"access-token\""
      } finally {
        server.stop(0)
      }
    }

    "exchangeAuthorizationCode throws when token endpoint returns error" {
      val server = HttpServer.create(InetSocketAddress(0), 0)
      server.createContext("/oauth2/token") { exchange ->
        val response = """{"error":"invalid_grant"}"""
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(400, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
      }
      server.start()
      try {
        val port = server.address.port
        shouldThrow<InvalidRequestException> {
            oauthClient.exchangeAuthorizationCode(
              config =
                JsonObject(
                  mapOf(
                    "client_id" to JsonPrimitive("client"),
                    "token_endpoint" to JsonPrimitive("http://127.0.0.1:$port/oauth2/token"),
                  )
                ),
              secretRef = null,
              code = "auth-code",
              redirectUri = "https://app.example.test/callback",
              verifier = null,
            )
          }
          .errorCode shouldBe WorkbenchErrorCode.IDENTITY_OAUTH_TOKEN_EXCHANGE_FAILED
      } finally {
        server.stop(0)
      }
    }

    "resolveSubject reads sub from id token when email missing" {
      val payload =
        Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString("""{"sub":"subject-123"}""".toByteArray(StandardCharsets.UTF_8))
      val idToken = "header.$payload.signature"

      oauthClient.resolveSubject(
        JsonObject(emptyMap()),
        JsonObject(mapOf("id_token" to JsonPrimitive(idToken))),
        LoginMethodKind.OIDC,
      ) shouldBe "subject-123"
    }

    "resolveSubject reads email from userinfo for oauth2" {
      val server = HttpServer.create(InetSocketAddress(0), 0)
      server.createContext("/userinfo") { exchange ->
        val response = """{"email":"ada@example.test"}"""
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
      }
      server.start()
      try {
        val port = server.address.port
        oauthClient.resolveSubject(
          JsonObject(
            mapOf("userinfo_endpoint" to JsonPrimitive("http://127.0.0.1:$port/userinfo"))
          ),
          JsonObject(mapOf("access_token" to JsonPrimitive("access-token"))),
          LoginMethodKind.OAUTH2,
        ) shouldBe "ada@example.test"
      } finally {
        server.stop(0)
      }
    }

    "resolveSubject reads preferred username from userinfo" {
      val server = HttpServer.create(InetSocketAddress(0), 0)
      server.createContext("/oidc/userinfo") { exchange ->
        val response = """{"preferred_username":"ada"}"""
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
      }
      server.start()
      try {
        val port = server.address.port
        oauthClient.resolveSubject(
          JsonObject(
            mapOf("userinfo_endpoint" to JsonPrimitive("http://127.0.0.1:$port/oidc/userinfo"))
          ),
          JsonObject(mapOf("access_token" to JsonPrimitive("access-token"))),
          LoginMethodKind.OIDC,
        ) shouldBe "ada"
      } finally {
        server.stop(0)
      }
    }

    "resolveSubject throws when userinfo cannot resolve subject" {
      val server = HttpServer.create(InetSocketAddress(0), 0)
      server.createContext("/userinfo") { exchange ->
        val response = """{"name":"Ada"}"""
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
      }
      server.start()
      try {
        val port = server.address.port
        shouldThrow<InvalidRequestException> {
            oauthClient.resolveSubject(
              JsonObject(
                mapOf("userinfo_endpoint" to JsonPrimitive("http://127.0.0.1:$port/userinfo"))
              ),
              JsonObject(mapOf("access_token" to JsonPrimitive("access-token"))),
              LoginMethodKind.OAUTH2,
            )
          }
          .errorCode shouldBe WorkbenchErrorCode.IDENTITY_FEDERATED_SUBJECT_UNRESOLVED
      } finally {
        server.stop(0)
      }
    }

    "exchangeAuthorizationCode uses issuer token endpoint when token endpoint omitted" {
      val server = HttpServer.create(InetSocketAddress(0), 0)
      server.createContext("/oauth2/v2.0/token") { exchange ->
        val response = """{"access_token":"issuer-token"}"""
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
      }
      server.start()
      try {
        val port = server.address.port
        val tokenResponse =
          oauthClient.exchangeAuthorizationCode(
            config =
              JsonObject(
                mapOf(
                  "client_id" to JsonPrimitive("client"),
                  "issuer" to JsonPrimitive("http://127.0.0.1:$port"),
                )
              ),
            secretRef = null,
            code = "auth-code",
            redirectUri = "https://app.example.test/callback",
            verifier = null,
          )

        tokenResponse["access_token"]?.toString() shouldBe "\"issuer-token\""
      } finally {
        server.stop(0)
      }
    }
  })
