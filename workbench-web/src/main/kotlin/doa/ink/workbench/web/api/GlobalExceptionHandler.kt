package doa.ink.workbench.web.api

import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.common.errors.InfrastructureUnavailableException
import doa.ink.workbench.core.common.errors.InstanceAlreadyInitializedException
import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.PermissionDeniedException
import doa.ink.workbench.core.common.errors.ResourceConflictException
import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.errors.SetupTokenInvalidException
import doa.ink.workbench.core.common.errors.TenantDestroyingException
import doa.ink.workbench.core.common.errors.TenantNotSelectedException
import doa.ink.workbench.core.common.errors.WorkbenchException
import java.net.URI
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
  @ExceptionHandler(ResourceNotFoundException::class)
  fun notFound(error: ResourceNotFoundException): ProblemDetail =
    problem(HttpStatus.NOT_FOUND, "Resource Not Found", error.message.orEmpty())

  @ExceptionHandler(TenantNotSelectedException::class)
  fun tenantNotSelected(error: TenantNotSelectedException): ProblemDetail =
    problem(HttpStatus.CONFLICT, "Tenant Not Selected", error.message.orEmpty())

  @ExceptionHandler(
    ResourceConflictException::class,
    InstanceAlreadyInitializedException::class,
    TenantDestroyingException::class,
  )
  fun conflict(error: WorkbenchException): ProblemDetail =
    problem(HttpStatus.CONFLICT, "Conflict", error.message.orEmpty())

  @ExceptionHandler(SetupTokenInvalidException::class)
  fun setupTokenInvalid(error: SetupTokenInvalidException): ProblemDetail =
    problem(HttpStatus.FORBIDDEN, "Setup Token Invalid", error.message.orEmpty())

  @ExceptionHandler(PermissionDeniedException::class)
  fun denied(error: PermissionDeniedException): ProblemDetail =
    problem(HttpStatus.FORBIDDEN, "Permission Denied", error.message.orEmpty())

  @ExceptionHandler(AuthenticationFailedException::class)
  fun authenticationFailed(error: AuthenticationFailedException): ProblemDetail =
    problem(HttpStatus.UNAUTHORIZED, "Authentication Failed", error.message.orEmpty())

  @ExceptionHandler(InvalidRequestException::class, IllegalArgumentException::class)
  fun invalid(error: RuntimeException): ProblemDetail =
    problem(HttpStatus.BAD_REQUEST, "Invalid Request", error.message.orEmpty())

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun validation(error: MethodArgumentNotValidException): ProblemDetail =
    problem(
      HttpStatus.BAD_REQUEST,
      "Validation Failed",
      error.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" },
    )

  @ExceptionHandler(DataAccessException::class)
  fun database(): ProblemDetail =
    problem(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Database Unavailable",
      "The database is temporarily unavailable.",
    )

  @ExceptionHandler(InfrastructureUnavailableException::class)
  fun infrastructure(error: InfrastructureUnavailableException): ProblemDetail =
    problem(HttpStatus.SERVICE_UNAVAILABLE, "Infrastructure Unavailable", error.message.orEmpty())
      .apply {
        setProperty("component", error.component)
      }

  private fun problem(status: HttpStatus, title: String, detail: String): ProblemDetail =
    ProblemDetail.forStatusAndDetail(status, detail).apply {
      this.title = title
      type =
        URI.create("https://api.doa.ink/workbench/problems/${title.lowercase().replace(" ", "-")}")
    }
}
