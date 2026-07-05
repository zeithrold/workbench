package ink.doa.workbench.web.identity

import ink.doa.workbench.core.identity.LoginDiscoveryRepository
import ink.doa.workbench.security.identity.LoginDiscoveryService
import ink.doa.workbench.security.identity.LoginOptionView
import ink.doa.workbench.security.identity.auth.normalizeSubject
import ink.doa.workbench.web.api.OpenApiExamples
import ink.doa.workbench.web.api.PublicEndpoint
import ink.doa.workbench.web.api.StandardErrorResponses
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@Tag(
  name = "Auth",
  description =
    "Authentication and session protocol. Public endpoints establish sessions; secured endpoints require WORKBENCH_SESSION.",
)
@StandardErrorResponses
class LoginDiscoveryController(
  private val loginDiscoveryService: LoginDiscoveryService,
  private val loginDiscovery: LoginDiscoveryRepository,
) {
  @GetMapping("/login-options")
  @PublicEndpoint
  @Operation(
    summary = "List login options",
    description =
      "Discovers tenant and login method options for an identifier such as an email address. " +
        "Public endpoint used before sign-in to render the login UI.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Available login options",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = LoginOptionResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.LOGIN_OPTIONS,
                    )
                  ],
              )
            ],
        )
      ],
  )
  suspend fun loginOptions(
    @Parameter(description = "User identifier such as email.", example = "user@example.com")
    @RequestParam
    identifier: String
  ): List<LoginOptionResponse> =
    loginDiscovery.listLoginOptionsForIdentifier(normalizeSubject(identifier)).map {
      LoginOptionResponse.from(LoginOptionView.from(it))
    }

  @GetMapping("/login-discovery")
  @PublicEndpoint
  @Operation(
    summary = "Discover login flow",
    description =
      "Wizard-oriented login discovery for an identifier. Returns flow type, instance password " +
        "method, or grouped tenant login methods with supported tenants.",
  )
  suspend fun loginDiscovery(
    @Parameter(description = "User identifier such as email.", example = "user@example.com")
    @RequestParam
    identifier: String
  ): LoginDiscoveryResponse =
    LoginDiscoveryResponse.from(loginDiscoveryService.discover(identifier))
}
