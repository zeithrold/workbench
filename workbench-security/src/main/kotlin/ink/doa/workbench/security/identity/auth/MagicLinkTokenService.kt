package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.identity.model.MagicLinkTokenRecord
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class MagicLinkTokenIssuer(
  private val repositories: MagicLinkAuthRepositories,
  private val crypto: CredentialCryptoSupport,
  private val clock: Clock,
) {
  suspend fun issue(
    loginMethodId: UUID,
    tenantId: UUID,
    normalizedSubject: String,
    ttl: Duration,
  ): IssuedMagicLinkToken {
    val secret = crypto.secretGenerator.generate()
    val now = OffsetDateTime.now(clock)
    val record =
      repositories.magicLinkTokens.create(
        tokenHash = crypto.credentialHasher.hash(secret),
        loginMethodId = loginMethodId,
        tenantId = tenantId,
        normalizedSubject = normalizedSubject,
        expiresAt = now.plus(ttl),
      )
    return IssuedMagicLinkToken(secret = secret, record = record)
  }
}

@Component
class MagicLinkTokenVerifier(
  private val repositories: MagicLinkAuthRepositories,
  private val crypto: CredentialCryptoSupport,
  private val clock: Clock,
) {
  suspend fun verifyAndConsume(token: String): MagicLinkTokenRecord {
    val now = OffsetDateTime.now(clock)
    val record =
      repositories.magicLinkTokens.findActiveByHash(crypto.credentialHasher.hash(token), now)
        ?: throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_MAGIC_LINK_INVALID)
    repositories.magicLinkTokens.consume(record.id, now)
    return record
  }
}

data class IssuedMagicLinkToken(
  val secret: String,
  val record: MagicLinkTokenRecord,
)
