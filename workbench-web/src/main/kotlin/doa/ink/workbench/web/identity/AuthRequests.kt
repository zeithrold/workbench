package doa.ink.workbench.web.identity

import doa.ink.workbench.core.identity.model.LoginMethodKind
import doa.ink.workbench.web.api.OpenApiExamples
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Sign-in payload. Required fields depend on the chosen method.")
data class LoginRequest(
  @field:Schema(
    description = "Authentication protocol step.",
    example = "PASSWORD",
    allowableValues = ["PASSWORD", "BEARER", "MAGIC_LINK", "FEDERATED"],
  )
  val method: LoginMethodKind,
  @field:Schema(
    description = "Public login method id (`lmg_…`). Required for password and federated flows.",
    example = OpenApiExamples.LOGIN_METHOD_ID,
  )
  val loginMethodId: String? = null,
  @field:Schema(
    description = "Public tenant id (`ten_…`). Required when multiple tenants are available.",
    example = OpenApiExamples.TENANT_ID,
  )
  val tenantId: String? = null,
  @field:Schema(
    description = "User identifier such as email or username. Required for password login.",
    example = "user@example.com",
  )
  val subject: String? = null,
  @field:Schema(description = "Password for PASSWORD method.", example = "secret")
  val password: String? = null,
  @field:Schema(description = "Opaque bearer token for BEARER method.")
  val token: String? = null,
  @field:Schema(description = "Email address for MAGIC_LINK method.", example = "user@example.com")
  val email: String? = null,
  @field:Schema(
    description = "When true, also returns a long-lived bearer token in the login response.",
    example = "false",
  )
  val issueBearerToken: Boolean = false,
)

@Schema(description = "Request to issue a long-lived bearer token for API access.")
data class CreateTokenRequest(
  @field:Schema(
    description = "Tenant scope for the token. Defaults to the active session tenant.",
    example = OpenApiExamples.TENANT_ID,
  )
  val tenantId: String? = null,
  @field:Schema(description = "Human-readable label for the token.", example = "CI pipeline")
  val name: String? = null,
  @field:Schema(description = "Granted scopes.", example = "[\"workbench.api\"]")
  val scopes: List<String> = listOf("workbench.api"),
)

@Schema(description = "Begin OAuth or SAML federated authorization.")
data class FederatedAuthorizeRequest(
  @field:Schema(description = "Public login method id.", example = OpenApiExamples.LOGIN_METHOD_ID)
  val loginMethodId: String,
  @field:Schema(description = "Public tenant id.", example = OpenApiExamples.TENANT_ID)
  val tenantId: String,
  @field:Schema(
    description = "URL to return the user to after provider login completes.",
    example = "https://app.example.com/auth/callback",
  )
  val returnUrl: String,
  @field:Schema(
    description = "OAuth redirect URI override. Defaults to the server callback endpoint.",
    example = "https://api.example.com/api/auth/oauth2/callback",
  )
  val redirectUri: String? = null,
)

@Schema(description = "Request a passwordless magic-link email.")
data class MagicLinkRequest(
  @field:Schema(description = "Recipient email address.", example = "user@example.com")
  val email: String,
  @field:Schema(description = "Public tenant id.", example = OpenApiExamples.TENANT_ID)
  val tenantId: String,
  @field:Schema(description = "Public magic-link login method id.", example = OpenApiExamples.LOGIN_METHOD_ID)
  val loginMethodId: String,
)

fun LoginRequest.toCommand(
  client: doa.ink.workbench.service.identity.ClientContext
): doa.ink.workbench.core.identity.model.LoginCommand =
  doa.ink.workbench.core.identity.model.LoginCommand(
    method = method,
    loginMethodId = loginMethodId,
    tenantId = tenantId,
    subject = subject,
    password = password,
    token = token,
    email = email,
    issueBearerToken = issueBearerToken,
    ipAddress = client.ipAddress,
    userAgent = client.userAgent,
  )
