package doa.ink.workbench.web.api

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.ProblemDetail

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponses(
  value =
    [
      ApiResponse(
        responseCode = "400",
        description = "Invalid request or validation failed",
        content =
          [
            Content(
              mediaType = "application/problem+json",
              schema = Schema(implementation = ProblemDetail::class),
              examples =
                [
                  ExampleObject(
                    name = "validationFailed",
                    summary = "Bean validation failure",
                    value = OpenApiExamples.VALIDATION_FAILED,
                  ),
                  ExampleObject(
                    name = "invalidRequest",
                    summary = "Domain validation failure",
                    value = OpenApiExamples.INVALID_REQUEST,
                  ),
                ],
            )
          ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Not authenticated",
        content =
          [
            Content(
              mediaType = "application/problem+json",
              schema = Schema(implementation = ProblemDetail::class),
              examples =
                [
                  ExampleObject(
                    name = "authenticationFailed",
                    summary = "Invalid or missing credentials",
                    value = OpenApiExamples.AUTHENTICATION_FAILED,
                  )
                ],
            )
          ],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Permission denied",
        content =
          [
            Content(
              mediaType = "application/problem+json",
              schema = Schema(implementation = ProblemDetail::class),
              examples =
                [
                  ExampleObject(
                    name = "permissionDenied",
                    summary = "Missing required permission",
                    value = OpenApiExamples.PERMISSION_DENIED,
                  )
                ],
            )
          ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Resource not found",
        content =
          [
            Content(
              mediaType = "application/problem+json",
              schema = Schema(implementation = ProblemDetail::class),
              examples =
                [
                  ExampleObject(
                    name = "resourceNotFound",
                    summary = "Referenced resource does not exist",
                    value = OpenApiExamples.RESOURCE_NOT_FOUND,
                  )
                ],
            )
          ],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Conflict",
        content =
          [
            Content(
              mediaType = "application/problem+json",
              schema = Schema(implementation = ProblemDetail::class),
              examples =
                [
                  ExampleObject(
                    name = "tenantNotSelected",
                    summary = "Tenant cannot be activated for this session",
                    value = OpenApiExamples.TENANT_NOT_SELECTED,
                  )
                ],
            )
          ],
      ),
    ]
)
annotation class StandardErrorResponses

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SecurityRequirement(name = "SessionAuth")
annotation class SessionSecured
