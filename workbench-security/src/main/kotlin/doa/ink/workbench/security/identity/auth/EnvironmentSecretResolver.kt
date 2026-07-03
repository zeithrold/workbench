package doa.ink.workbench.security.identity.auth

import doa.ink.workbench.core.identity.auth.SecretResolver
import org.springframework.stereotype.Component

@Component
class EnvironmentSecretResolver : SecretResolver {
  override fun resolve(secretRef: String): String? = System.getenv(secretRef)
}
