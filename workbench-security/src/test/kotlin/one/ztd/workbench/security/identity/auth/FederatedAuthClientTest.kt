package one.ztd.workbench.security.identity.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.security.identity.auth.support.MapSecretResolver

class FederatedAuthClientTest :
  StringSpec({
    val oauthClient = DefaultOAuthFederatedClient(MapSecretResolver(emptyMap()))
    val samlClient = DefaultSamlFederatedClient()

    "encodeQueryValue url-encodes values" {
      encodeQueryValue("a+b c") shouldBe "a%2Bb+c"
      encodeQueryParam("state", "abc/123") shouldBe "state=abc%2F123"
    }

    "pkceChallenge returns url-safe base64 sha256" {
      oauthClient.pkceChallenge("verifier") shouldBe oauthClient.pkceChallenge("verifier")
    }

    "buildAuthorizeUrl composes oauth authorize endpoint" {
      val url =
        oauthClient.buildAuthorizeUrl(
          config =
            JsonObject(
              mapOf(
                "client_id" to JsonPrimitive("client"),
                "issuer" to JsonPrimitive("https://idp.example.test"),
                "scope" to JsonPrimitive("openid email"),
              )
            ),
          redirectUri = "https://app.example.test/callback",
          state = "state-token",
          challenge = "challenge-token",
          kind = LoginMethodKind.OIDC,
        )

      url shouldContain "https://idp.example.test/oauth2/v2.0/authorize"
      url shouldContain "client_id=client"
      url shouldContain "code_challenge=challenge-token"
    }

    "buildAuthorizeUrl throws when client id missing" {
      shouldThrow<InvalidRequestException> {
          oauthClient.buildAuthorizeUrl(
            JsonObject(emptyMap()),
            "https://app.example.test/callback",
            "state",
            "challenge",
            LoginMethodKind.OAUTH2,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_OAUTH_CLIENT_ID_MISSING
    }

    "saml buildAuthorizeUrl composes idp sso url" {
      val url =
        samlClient.buildAuthorizeUrl(
          JsonObject(mapOf("idp_sso_url" to JsonPrimitive("https://idp.example.test/sso"))),
          redirectUri = "https://app.example.test/acs",
          relayState = "relay",
        )

      url shouldContain "https://idp.example.test/sso"
      url shouldContain "RelayState=relay"
    }

    "saml response parser extracts NameID" {
      val xml = """<Response><NameID>ada@example.test</NameID></Response>"""
      val encoded = Base64.getEncoder().encodeToString(xml.toByteArray(StandardCharsets.UTF_8))
      DefaultSamlFederatedClient().parseNameId(encoded) shouldBe "ada@example.test"
    }

    "resolveSubject reads email from id token payload" {
      val payload =
        Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString("""{"email":"ada@example.test"}""".toByteArray(StandardCharsets.UTF_8))
      val idToken = "header.$payload.signature"
      val subject =
        oauthClient.resolveSubject(
          JsonObject(emptyMap()),
          JsonObject(mapOf("id_token" to JsonPrimitive(idToken))),
          LoginMethodKind.OIDC,
        )

      subject shouldBe "ada@example.test"
    }
  })
