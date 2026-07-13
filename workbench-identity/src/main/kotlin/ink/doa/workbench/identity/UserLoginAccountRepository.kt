package ink.doa.workbench.identity

import ink.doa.workbench.identity.model.LinkUserLoginAccountCommand
import ink.doa.workbench.identity.model.UserLoginAccountRecord
import ink.doa.workbench.identity.model.UserRecord
import java.time.OffsetDateTime
import java.util.UUID

interface UserLoginAccountRepository {
  suspend fun linkUser(command: LinkUserLoginAccountCommand): UserLoginAccountRecord

  suspend fun unlink(loginAccountId: UUID, unlinkedAt: OffsetDateTime): Boolean

  suspend fun findLinkedUser(loginAccountId: UUID): UserRecord?
}
