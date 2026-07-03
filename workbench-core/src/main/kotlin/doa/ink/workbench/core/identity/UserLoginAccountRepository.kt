package doa.ink.workbench.core.identity

import doa.ink.workbench.core.identity.model.LinkUserLoginAccountCommand
import doa.ink.workbench.core.identity.model.UserLoginAccountRecord
import doa.ink.workbench.core.identity.model.UserRecord
import java.time.OffsetDateTime
import java.util.UUID

interface UserLoginAccountRepository {
  suspend fun linkUser(command: LinkUserLoginAccountCommand): UserLoginAccountRecord

  suspend fun unlink(loginAccountId: UUID, unlinkedAt: OffsetDateTime): Boolean

  suspend fun findLinkedUser(loginAccountId: UUID): UserRecord?
}
