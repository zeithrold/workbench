package doa.ink.workbench.web.identity

import doa.ink.workbench.core.identity.model.LoginMethodKind

data class LoginRequest(
  val method: LoginMethodKind,
  val loginMethodId: String? = null,
  val tenantId: String? = null,
  val subject: String? = null,
  val password: String? = null,
  val token: String? = null,
  val email: String? = null,
  val issueBearerToken: Boolean = false,
)

data class CreateTokenRequest(
  val tenantId: String? = null,
  val name: String? = null,
  val scopes: List<String> = listOf("workbench.api"),
)

data class FederatedAuthorizeRequest(
  val loginMethodId: String,
  val tenantId: String,
  val returnUrl: String,
  val redirectUri: String? = null,
)

data class MagicLinkRequest(
  val email: String,
  val tenantId: String,
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
