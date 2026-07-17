package one.ztd.workbench.identity.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.springframework.stereotype.Component

@Component
class SecureRandomCredentialSecretGenerator : CredentialSecretGenerator {
  private val random = SecureRandom()
  private val encoder = Base64.getUrlEncoder().withoutPadding()

  override fun generate(): String {
    val bytes = ByteArray(32)
    random.nextBytes(bytes)
    return encoder.encodeToString(bytes)
  }
}

@Component
class Sha256CredentialHasher : CredentialHasher {
  private val encoder = Base64.getUrlEncoder().withoutPadding()

  override fun hash(secret: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8))
    return "sha256:${encoder.encodeToString(digest)}"
  }
}
