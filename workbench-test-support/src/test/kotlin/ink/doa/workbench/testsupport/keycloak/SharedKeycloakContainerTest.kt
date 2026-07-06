package ink.doa.workbench.testsupport.keycloak

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.core.annotation.Tags

@Tags("integration")
class SharedKeycloakContainerTest :
  StringSpec({
    "sharedContainer returns a stable instance for the JVM" {
      val first = WorkbenchKeycloakTestSupport.sharedContainer()
      val second = WorkbenchKeycloakTestSupport.sharedContainer()
      first shouldBe second
      first.authServerUrl.shouldNotBeNull()
    }
  })
