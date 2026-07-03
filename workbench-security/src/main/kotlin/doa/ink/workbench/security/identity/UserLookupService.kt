package doa.ink.workbench.security.identity

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.errors.WorkbenchErrorCode
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.model.UserRecord
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
