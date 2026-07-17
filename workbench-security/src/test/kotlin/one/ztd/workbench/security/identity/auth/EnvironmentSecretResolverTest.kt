package one.ztd.workbench.security.identity.auth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class EnvironmentSecretResolverTest :
  StringSpec({
    "resolve returns environment variable value when present" {
      val resolver = EnvironmentSecretResolver()

      resolver.resolve("PATH").shouldNotBeNull()
    }

    "resolve returns null for missing environment variable" {
      val resolver = EnvironmentSecretResolver()

      resolver.resolve("WORKBENCH_MISSING_SECRET_${System.nanoTime()}") shouldBe null
    }
  })
