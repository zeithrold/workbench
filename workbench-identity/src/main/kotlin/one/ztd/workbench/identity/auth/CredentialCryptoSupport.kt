package one.ztd.workbench.identity.auth

import org.springframework.stereotype.Component

@Component
class CredentialCryptoSupport(
  val secretGenerator: CredentialSecretGenerator,
  val credentialHasher: CredentialHasher,
)
