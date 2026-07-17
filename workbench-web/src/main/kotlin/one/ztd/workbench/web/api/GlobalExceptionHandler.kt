package one.ztd.workbench.web.api

import one.ztd.workbench.kernel.common.errors.AuthenticationFailedException
import one.ztd.workbench.kernel.common.errors.InfrastructureUnavailableException
import one.ztd.workbench.kernel.common.errors.InstanceAlreadyInitializedException
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.PermissionDeniedException
import one.ztd.workbench.kernel.common.errors.ProjectArchivedException
import one.ztd.workbench.kernel.common.errors.ProjectDestroyingException
import one.ztd.workbench.kernel.common.errors.ResourceConflictException
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.SetupTokenInvalidException
import one.ztd.workbench.kernel.common.errors.TenantDestroyingException
import one.ztd.workbench.kernel.common.errors.TenantNotSelectedException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.errors.WorkbenchException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
  private val logger = LoggerFactory.getLogger(javaClass)

  @ExceptionHandler(ResourceNotFoundException::class)
  fun notFound(error: ResourceNotFoundException): ProblemDetail =
    ProblemDetailSupport.problem(HttpStatus.NOT_FOUND, "Resource Not Found", error)

  @ExceptionHandler(TenantNotSelectedException::class)
  fun tenantNotSelected(error: TenantNotSelectedException): ProblemDetail =
    ProblemDetailSupport.problem(HttpStatus.CONFLICT, "Tenant Not Selected", error)

  @ExceptionHandler(
    ResourceConflictException::class,
    InstanceAlreadyInitializedException::class,
    TenantDestroyingException::class,
    ProjectDestroyingException::class,
    ProjectArchivedException::class,
  )
  fun conflict(error: WorkbenchException): ProblemDetail =
    ProblemDetailSupport.problem(HttpStatus.CONFLICT, "Conflict", error)

  @ExceptionHandler(SetupTokenInvalidException::class)
  fun setupTokenInvalid(error: SetupTokenInvalidException): ProblemDetail =
    ProblemDetailSupport.problem(HttpStatus.FORBIDDEN, "Setup Token Invalid", error)

  @ExceptionHandler(PermissionDeniedException::class)
  fun denied(error: PermissionDeniedException): ProblemDetail =
    ProblemDetailSupport.problem(HttpStatus.FORBIDDEN, "Permission Denied", error)

  @ExceptionHandler(AuthenticationFailedException::class)
  fun authenticationFailed(error: AuthenticationFailedException): ProblemDetail =
    ProblemDetailSupport.problem(HttpStatus.UNAUTHORIZED, "Authentication Failed", error)

  @ExceptionHandler(InvalidRequestException::class, IllegalArgumentException::class)
  fun invalid(error: RuntimeException): ProblemDetail {
    if (error is InvalidRequestException) {
      return ProblemDetailSupport.problem(HttpStatus.BAD_REQUEST, "Invalid Request", error)
    }
    return ProblemDetailSupport.problem(
      HttpStatus.BAD_REQUEST,
      "Invalid Request",
      error.message.orEmpty(),
      WorkbenchErrorCode.REQUEST_INVALID,
    )
  }

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun validation(error: MethodArgumentNotValidException): ProblemDetail =
    ProblemDetailSupport.problem(
      HttpStatus.BAD_REQUEST,
      "Validation Failed",
      error.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" },
      WorkbenchErrorCode.REQUEST_VALIDATION_FAILED,
    )

  @ExceptionHandler(DataAccessException::class)
  fun database(error: DataAccessException): ProblemDetail {
    logger.debug("Database access failed", error)
    return ProblemDetailSupport.problem(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Database Unavailable",
      WorkbenchErrorCode.INFRASTRUCTURE_DATABASE_UNAVAILABLE.defaultMessage,
      WorkbenchErrorCode.INFRASTRUCTURE_DATABASE_UNAVAILABLE,
    )
  }

  @ExceptionHandler(InfrastructureUnavailableException::class)
  fun infrastructure(error: InfrastructureUnavailableException): ProblemDetail =
    ProblemDetailSupport.problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Infrastructure Unavailable",
        error,
      )
      .apply {
        setProperty("component", error.component)
      }
}
