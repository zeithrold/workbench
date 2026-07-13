package ink.doa.workbench.identity.auth

import org.springframework.stereotype.Component

@Component
class CredentialCryptoSupport(
  val secretGenerator: CredentialSecretGenerator,
  val credentialHasher: CredentialHasher,
)
