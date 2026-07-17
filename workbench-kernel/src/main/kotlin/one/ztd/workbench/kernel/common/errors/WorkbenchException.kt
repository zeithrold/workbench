package one.ztd.workbench.kernel.common.errors

sealed class WorkbenchException(
  val errorCode: WorkbenchErrorCode,
  detail: String? = null,
) : RuntimeException(detail ?: errorCode.defaultMessage)

class ResourceNotFoundException(
  errorCode: WorkbenchErrorCode,
  detail: String? = null,
) : WorkbenchException(errorCode, detail)

class PermissionDeniedException(
  errorCode: WorkbenchErrorCode,
  detail: String? = null,
) : WorkbenchException(errorCode, detail)

class AuthenticationFailedException(
  errorCode: WorkbenchErrorCode,
  detail: String? = null,
) : WorkbenchException(errorCode, detail)

class InvalidRequestException(
  errorCode: WorkbenchErrorCode,
  detail: String? = null,
) : WorkbenchException(errorCode, detail)

class TenantNotSelectedException(
  errorCode: WorkbenchErrorCode = WorkbenchErrorCode.TENANT_NOT_SELECTED,
  detail: String? = null,
) : WorkbenchException(errorCode, detail)

class ResourceConflictException(
  errorCode: WorkbenchErrorCode,
  detail: String? = null,
) : WorkbenchException(errorCode, detail)

class TenantDestroyingException(
  errorCode: WorkbenchErrorCode = WorkbenchErrorCode.TENANT_DESTROYING,
  detail: String? = null,
) : WorkbenchException(errorCode, detail)

class ProjectDestroyingException(
  errorCode: WorkbenchErrorCode = WorkbenchErrorCode.PROJECT_DESTROYING,
  detail: String? = null,
) : WorkbenchException(errorCode, detail)

class ProjectArchivedException(
  errorCode: WorkbenchErrorCode = WorkbenchErrorCode.PROJECT_ARCHIVED,
  detail: String? = null,
) : WorkbenchException(errorCode, detail)

class InstanceAlreadyInitializedException(
  errorCode: WorkbenchErrorCode = WorkbenchErrorCode.INSTANCE_ALREADY_INITIALIZED,
  detail: String? = null,
) : WorkbenchException(errorCode, detail)

class SetupTokenInvalidException(
  errorCode: WorkbenchErrorCode = WorkbenchErrorCode.INSTANCE_SETUP_TOKEN_INVALID,
  detail: String? = null,
) : WorkbenchException(errorCode, detail)

class InfrastructureUnavailableException(
  val component: String,
  detail: String? = null,
  errorCode: WorkbenchErrorCode = WorkbenchErrorCode.INFRASTRUCTURE_UNAVAILABLE,
  cause: Throwable? = null,
) : WorkbenchException(errorCode, detail) {
  init {
    if (cause != null) initCause(cause)
  }
}
