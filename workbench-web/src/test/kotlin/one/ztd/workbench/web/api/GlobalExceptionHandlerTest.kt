package one.ztd.workbench.web.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
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
import one.ztd.workbench.web.api.context.ApiVersion
import one.ztd.workbench.web.instance.CreateTenantRequest
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpStatus
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException

class GlobalExceptionHandlerTest :
  StringSpec({
    "invalid requests are rendered as problem details" {
      val problem =
        GlobalExceptionHandler()
          .invalid(InvalidRequestException(WorkbenchErrorCode.REQUEST_INVALID, "bad input"))

      problem.status shouldBe HttpStatus.BAD_REQUEST.value()
      problem.title shouldBe "Invalid Request"
      problem.detail shouldBe "bad input"
      problem.properties?.get("code") shouldBe "request.invalid"
    }

    "illegal arguments use the generic invalid request code" {
      val problem = GlobalExceptionHandler().invalid(IllegalArgumentException("bad input"))

      problem.status shouldBe HttpStatus.BAD_REQUEST.value()
      problem.properties?.get("code") shouldBe "request.invalid"
    }

    "database failures use the database unavailable code" {
      val problem = GlobalExceptionHandler().database(DataAccessResourceFailureException("down"))

      problem.status shouldBe HttpStatus.SERVICE_UNAVAILABLE.value()
      problem.title shouldBe "Database Unavailable"
      problem.properties?.get("code") shouldBe "infrastructure.database_unavailable"
    }

    "permission denies render mapped authorization codes" {
      val code = WorkbenchErrorCode.fromAuthorizationReason("no_matching_binding")
      val problem =
        GlobalExceptionHandler().denied(PermissionDeniedException(code, "No active binding."))

      problem.status shouldBe HttpStatus.FORBIDDEN.value()
      problem.properties?.get("code") shouldBe "auth.permission.no_matching_binding"
    }

    "resource not found renders 404 problem details" {
      val problem =
        GlobalExceptionHandler()
          .notFound(ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND))

      problem.status shouldBe HttpStatus.NOT_FOUND.value()
      problem.title shouldBe "Resource Not Found"
      problem.properties?.get("code") shouldBe "resource.tenant.not_found"
    }

    "tenant not selected renders conflict problem details" {
      val problem = GlobalExceptionHandler().tenantNotSelected(TenantNotSelectedException())

      problem.status shouldBe HttpStatus.CONFLICT.value()
      problem.title shouldBe "Tenant Not Selected"
      problem.properties?.get("code") shouldBe "tenant.not_selected"
    }

    "resource conflicts render conflict problem details" {
      val problem =
        GlobalExceptionHandler()
          .conflict(ResourceConflictException(WorkbenchErrorCode.TENANT_SLUG_IN_USE, "in use"))

      problem.status shouldBe HttpStatus.CONFLICT.value()
      problem.properties?.get("code") shouldBe "tenant.slug.in_use"
    }

    "instance already initialized renders conflict problem details" {
      val problem =
        GlobalExceptionHandler().conflict(InstanceAlreadyInitializedException(detail = "done"))

      problem.status shouldBe HttpStatus.CONFLICT.value()
      problem.properties?.get("code") shouldBe "instance.already_initialized"
    }

    "tenant destroying renders conflict problem details" {
      val problem = GlobalExceptionHandler().conflict(TenantDestroyingException())

      problem.status shouldBe HttpStatus.CONFLICT.value()
      problem.properties?.get("code") shouldBe "tenant.destroying"
    }

    "project destroying renders conflict problem details" {
      val problem = GlobalExceptionHandler().conflict(ProjectDestroyingException())

      problem.status shouldBe HttpStatus.CONFLICT.value()
      problem.properties?.get("code") shouldBe "project.destroying"
    }

    "project archived renders conflict problem details" {
      val problem = GlobalExceptionHandler().conflict(ProjectArchivedException())

      problem.status shouldBe HttpStatus.CONFLICT.value()
      problem.properties?.get("code") shouldBe "project.archived"
    }

    "setup token invalid renders forbidden problem details" {
      val problem = GlobalExceptionHandler().setupTokenInvalid(SetupTokenInvalidException())

      problem.status shouldBe HttpStatus.FORBIDDEN.value()
      problem.properties?.get("code") shouldBe "instance.setup_token.invalid"
    }

    "authentication failures render unauthorized problem details" {
      val problem =
        GlobalExceptionHandler()
          .authenticationFailed(
            AuthenticationFailedException(WorkbenchErrorCode.AUTH_AUTHENTICATION_REQUIRED)
          )

      problem.status shouldBe HttpStatus.UNAUTHORIZED.value()
      problem.properties?.get("code") shouldBe "auth.authentication_required"
    }

    "validation failures render validation problem details" {
      val bindingResult = BeanPropertyBindingResult(CreateTenantRequest::class.java, "request")
      bindingResult.addError(FieldError("request", "slug", "must match pattern"))
      val exception = mockk<MethodArgumentNotValidException>()
      every { exception.bindingResult } returns bindingResult

      val problem = GlobalExceptionHandler().validation(exception)

      problem.status shouldBe HttpStatus.BAD_REQUEST.value()
      problem.title shouldBe "Validation Failed"
      problem.detail shouldBe "slug: must match pattern"
      problem.properties?.get("code") shouldBe "request.validation_failed"
    }

    "infrastructure unavailable includes component property" {
      val problem =
        GlobalExceptionHandler()
          .infrastructure(InfrastructureUnavailableException(component = "valkey", detail = "down"))

      problem.status shouldBe HttpStatus.SERVICE_UNAVAILABLE.value()
      problem.title shouldBe "Infrastructure Unavailable"
      problem.properties?.get("code") shouldBe "infrastructure.unavailable"
      problem.properties?.get("component") shouldBe "valkey"
    }

    "default API version uses date header format" {
      ApiVersion.Default.value shouldBe "2026-07-15"
      ApiVersion.HeaderName shouldBe "X-Workbench-API-Version"
    }
  })
