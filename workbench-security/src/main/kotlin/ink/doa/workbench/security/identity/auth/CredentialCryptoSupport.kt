package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.identity.auth.CredentialHasher
import ink.doa.workbench.core.identity.auth.CredentialSecretGenerator
import org.springframework.stereotype.Component

@Component
class CredentialCryptoSupport(
  val secretGenerator: CredentialSecretGenerator,
  val credentialHasher: CredentialHasher,
)
