package doa.ink.workbench.web.api

import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponses(
  value =
    [
      ApiResponse(responseCode = "400", description = "Invalid request"),
      ApiResponse(responseCode = "401", description = "Not authenticated"),
      ApiResponse(responseCode = "403", description = "Permission denied"),
      ApiResponse(responseCode = "404", description = "Resource not found"),
      ApiResponse(responseCode = "409", description = "Conflict"),
    ]
)
annotation class StandardErrorResponses

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SecurityRequirement(name = "SessionAuth")
annotation class SessionSecured
