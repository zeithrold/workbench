package ink.doa.workbench.security.identity

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.model.UserRecord
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class UserLookupService(private val users: UserRepository) {
  suspend fun requireUser(userId: UUID): UserRecord =
    users.findById(userId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)

  suspend fun requireAuthenticatedUser(userId: UUID): UserRecord =
    users.findById(userId)
      ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
}
