package one.ztd.workbench.identity

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.identity.model.LinkUserLoginAccountCommand
import one.ztd.workbench.identity.model.UserLoginAccountRecord
import one.ztd.workbench.identity.model.UserRecord

interface UserLoginAccountRepository {
  suspend fun linkUser(command: LinkUserLoginAccountCommand): UserLoginAccountRecord

  suspend fun unlink(loginAccountId: UUID, unlinkedAt: OffsetDateTime): Boolean

  suspend fun findLinkedUser(loginAccountId: UUID): UserRecord?
}
