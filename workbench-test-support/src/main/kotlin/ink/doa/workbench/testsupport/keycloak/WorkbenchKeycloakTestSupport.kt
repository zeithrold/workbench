package ink.doa.workbench.testsupport.keycloak

import dasniko.testcontainers.keycloak.KeycloakContainer

object WorkbenchKeycloakTestSupport {
  fun sharedContainer(): KeycloakContainer = SharedKeycloakContainer.container()
}
