package ink.doa.workbench.identity

import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
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
