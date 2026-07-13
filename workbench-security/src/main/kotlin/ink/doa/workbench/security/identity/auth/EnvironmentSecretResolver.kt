package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.identity.auth.SecretResolver
import org.springframework.stereotype.Component

@Component
class EnvironmentSecretResolver : SecretResolver {
  override fun resolve(secretRef: String): String? = System.getenv(secretRef)
}
