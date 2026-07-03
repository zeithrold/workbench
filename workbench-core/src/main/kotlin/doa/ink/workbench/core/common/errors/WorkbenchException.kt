package doa.ink.workbench.core.common.errors

sealed class WorkbenchException(message: String) : RuntimeException(message)

class ResourceNotFoundException(message: String) : WorkbenchException(message)

class PermissionDeniedException(message: String) : WorkbenchException(message)

class AuthenticationFailedException(message: String) : WorkbenchException(message)

class InvalidRequestException(message: String) : WorkbenchException(message)

class TenantNotSelectedException(message: String) : WorkbenchException(message)

class ResourceConflictException(message: String) : WorkbenchException(message)

class InstanceAlreadyInitializedException(message: String) : WorkbenchException(message)

class SetupTokenInvalidException(message: String) : WorkbenchException(message)

class InfrastructureUnavailableException(
  val component: String,
  message: String,
  cause: Throwable? = null,
) : WorkbenchException(message) {
  init {
    if (cause != null) initCause(cause)
  }
}
