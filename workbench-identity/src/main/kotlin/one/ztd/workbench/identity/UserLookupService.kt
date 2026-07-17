package one.ztd.workbench.identity

import java.util.UUID
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
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
