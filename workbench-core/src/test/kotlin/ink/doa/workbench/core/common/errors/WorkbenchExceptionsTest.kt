package ink.doa.workbench.core.common.errors

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class WorkbenchExceptionsTest :
  StringSpec({
    "exception subclasses expose errorCode and detail message" {
      ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND, "missing")
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND
      ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND).message shouldBe
        WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND.defaultMessage

      PermissionDeniedException(WorkbenchErrorCode.AUTH_PERMISSION_DENIED, "denied")
        .message shouldBe "denied"
      PermissionDeniedException(WorkbenchErrorCode.AUTH_PERMISSION_DENIED).message shouldBe
        WorkbenchErrorCode.AUTH_PERMISSION_DENIED.defaultMessage
      AuthenticationFailedException(WorkbenchErrorCode.AUTH_AUTHENTICATION_REQUIRED)
        .errorCode shouldBe WorkbenchErrorCode.AUTH_AUTHENTICATION_REQUIRED

      InvalidRequestException(WorkbenchErrorCode.SESSION_ACTIVE_NOT_FOUND, "bad").message shouldBe
        "bad"
      TenantNotSelectedException().errorCode shouldBe WorkbenchErrorCode.TENANT_NOT_SELECTED
      ResourceConflictException(WorkbenchErrorCode.USER_EMAIL_ALREADY_EXISTS).message shouldBe
        WorkbenchErrorCode.USER_EMAIL_ALREADY_EXISTS.defaultMessage

      TenantDestroyingException(detail = "busy").message shouldBe "busy"
      ProjectDestroyingException().errorCode shouldBe WorkbenchErrorCode.PROJECT_DESTROYING
      ProjectArchivedException(detail = "archived").message shouldBe "archived"

      InstanceAlreadyInitializedException().errorCode shouldBe
        WorkbenchErrorCode.INSTANCE_ALREADY_INITIALIZED
      SetupTokenInvalidException(detail = "expired").message shouldBe "expired"

      val cause = IllegalStateException("down")
      InfrastructureUnavailableException("postgres", "offline", cause = cause).let { ex ->
        ex.component shouldBe "postgres"
        ex.message shouldBe "offline"
        ex.cause shouldBe cause
      }
    }

    "requireValid throws InvalidRequestException when condition is false" {
      shouldThrow<InvalidRequestException> {
          requireValid(false, WorkbenchErrorCode.SESSION_ACTIVE_NOT_FOUND, "invalid")
        }
        .message shouldBe "invalid"
    }

    "requireValid and requireFound succeed when condition is true" {
      requireValid(true, WorkbenchErrorCode.SESSION_ACTIVE_NOT_FOUND)
      requireFound(true, WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)
    }

    "requireFound throws ResourceNotFoundException when condition is false" {
      shouldThrow<ResourceNotFoundException> {
          requireFound(false, WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND, "missing")
        }
        .message shouldBe "missing"
    }

    "SerializationParseSupport wraps parse failures" {
      shouldThrow<InvalidRequestException> {
        SerializationParseSupport.parseOrThrow(
          { kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>("""not-json""") },
          { InvalidRequestException(WorkbenchErrorCode.REQUEST_VALIDATION_FAILED, it.message) },
        )
      }
    }

    "SerializationParseSupport propagates non-serialization exceptions" {
      shouldThrow<IllegalStateException> {
        SerializationParseSupport.parseOrThrow(
          { throw IllegalStateException("boom") },
          { InvalidRequestException(WorkbenchErrorCode.REQUEST_VALIDATION_FAILED, it.message) },
        )
      }
    }
  })
