package doa.ink.workbench.shared.errors

sealed class WorkbenchException(message: String) : RuntimeException(message)

class ResourceNotFoundException(message: String) : WorkbenchException(message)

class PermissionDeniedException(message: String) : WorkbenchException(message)

class InvalidRequestException(message: String) : WorkbenchException(message)

class InfrastructureUnavailableException(
  val component: String,
  message: String,
  cause: Throwable? = null,
) : WorkbenchException(message) {
  init {
    if (cause != null) initCause(cause)
  }
}
