package ink.doa.workbench.testsupport.keycloak

import dasniko.testcontainers.keycloak.KeycloakContainer
import java.time.Duration

internal object SharedKeycloakContainer {
  private val container: KeycloakContainer =
    KeycloakContainer("quay.io/keycloak/keycloak:26.4")
      .withRealmImportFile("keycloak/workbench-test-realm.json")
      .withStartupTimeout(Duration.ofMinutes(3))

  @Volatile private var started = false

  init {
    Runtime.getRuntime().addShutdownHook(Thread { stopIfRunning() })
  }

  fun container(): KeycloakContainer {
    ensureRunning()
    return container
  }

  fun ensureRunning() {
    if (started) {
      return
    }
    synchronized(this) {
      if (!started) {
        container.start()
        started = true
      }
    }
  }

  private fun stopIfRunning() {
    synchronized(this) {
      if (started) {
        container.stop()
        started = false
      }
    }
  }
}
