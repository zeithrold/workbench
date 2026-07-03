package doa.ink.workbench.service.identity.auth.support

import dasniko.testcontainers.keycloak.KeycloakContainer
import java.time.Duration

object KeycloakTestContainer {
  const val REALM = "workbench-test"
  const val OIDC_CLIENT_ID = "workbench-oidc"
  const val OIDC_CLIENT_SECRET = "oidc-client-secret"
  const val OIDC_SECRET_REF = "WORKBENCH_OIDC_CLIENT_SECRET"
  const val OIDC_USER = "oidc-user@example.test"
  const val OIDC_PASSWORD = "oidc-test-pass"

  const val OAUTH2_CLIENT_ID = "workbench-oauth2"
  const val OAUTH2_CLIENT_SECRET = "oauth2-client-secret"
  const val OAUTH2_SECRET_REF = "WORKBENCH_OAUTH2_CLIENT_SECRET"
  const val OAUTH2_USER = "oauth-user@example.test"
  const val OAUTH2_PASSWORD = "oauth-test-pass"

  const val REDIRECT_URI = "http://localhost/api/auth/oauth2/callback"

  fun create(): KeycloakContainer =
    KeycloakContainer("quay.io/keycloak/keycloak:26.4")
      .withRealmImportFile("keycloak/workbench-test-realm.json")
      .withStartupTimeout(Duration.ofMinutes(3))

  fun realmBase(container: KeycloakContainer): String = "${container.authServerUrl}/realms/$REALM"

  fun authorizationEndpoint(container: KeycloakContainer): String =
    "${realmBase(container)}/protocol/openid-connect/auth"

  fun tokenEndpoint(container: KeycloakContainer): String =
    "${realmBase(container)}/protocol/openid-connect/token"

  fun userinfoEndpoint(container: KeycloakContainer): String =
    "${realmBase(container)}/protocol/openid-connect/userinfo"
}
