package doa.ink.workbench.web.identity

import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.service.identity.AuthApplicationService
import doa.ink.workbench.web.api.OpenApiExamples
import doa.ink.workbench.web.api.SessionSecured
import doa.ink.workbench.web.api.StandardErrorResponses
import doa.ink.workbench.web.api.http.HttpClientContext
import doa.ink.workbench.web.api.http.toServiceContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@Tag(
  name = "Auth",
  description =
    "Authentication and session protocol. Public endpoints establish sessions; secured endpoints require WORKBENCH_SESSION.",
)
@StandardErrorResponses
class BearerTokenAuthController(private val authApplicationService: AuthApplicationService) {
  @PostMapping("/tokens")
  @SessionSecured
  @Operation(
    summary = "Issue bearer token",
    description =
      "Creates a long-lived bearer token for API access. Requires an active session. The token secret is returned only once.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Token issued",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = IssuedTokenResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.ISSUED_TOKEN,
                    )
                  ],
              )
            ],
        )
      ],
  )
  suspend fun createToken(
    @Valid @RequestBody request: CreateTokenRequest,
    principal: AuthenticatedPrincipal,
    servletRequest: HttpServletRequest,
  ): IssuedTokenResponse {
    val client = HttpClientContext.from(servletRequest).toServiceContext()
    return IssuedTokenResponse.from(
      authApplicationService.issueBearerToken(
        principal = principal,
        tenantId = request.tenantId,
        name = request.name,
        scopes = request.scopes,
        client = client,
      )
    )
  }

  @DeleteMapping("/tokens/{id}")
  @SessionSecured
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Revoke bearer token",
    description = "Revokes a bearer token by public id. Returns 204 with an empty body.",
    responses =
      [
        ApiResponse(responseCode = "204", description = "Token revoked"),
        ApiResponse(
          responseCode = "404",
          description = "Token not found",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "notFound",
                      value = OpenApiExamples.RESOURCE_NOT_FOUND,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun revokeToken(
    @Parameter(description = "Public bearer token id.", example = OpenApiExamples.BEARER_TOKEN_ID)
    @PathVariable
    id: String,
    principal: AuthenticatedPrincipal,
    servletRequest: HttpServletRequest,
  ) {
    val client = HttpClientContext.from(servletRequest).toServiceContext()
    authApplicationService.revokeBearerToken(principal, id, client)
  }
}
