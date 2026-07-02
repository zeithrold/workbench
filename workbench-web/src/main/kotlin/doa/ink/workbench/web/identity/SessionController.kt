package doa.ink.workbench.web.identity

import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.service.identity.SessionService
import jakarta.validation.Valid
import java.time.OffsetDateTime
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/session")
class SessionController(private val sessionService: SessionService) {
  @GetMapping
  suspend fun getSession(): SessionResponse {
    val view = sessionService.getCurrent(currentPrincipal())
    return SessionResponse.from(view)
  }

  @PatchMapping
  suspend fun switchTenant(@Valid @RequestBody request: SwitchTenantRequest): SessionResponse {
    val view = sessionService.switchTenant(currentPrincipal(), request.tenantApiId)
    return SessionResponse.from(view)
  }

  private fun currentPrincipal(): AuthenticatedPrincipal =
    SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedPrincipal
      ?: throw AuthenticationFailedException("Authentication required.")
}

data class SwitchTenantRequest(val tenantApiId: String?)

data class SessionResponse(
  val user: AuthenticatedUserResponse,
  val activeTenant: TenantSummaryResponse?,
  val sessionExpiresAt: OffsetDateTime,
) {
  companion object {
    fun from(view: doa.ink.workbench.service.identity.SessionView) =
      SessionResponse(
        user =
          AuthenticatedUserResponse(
            id = view.user.id,
            apiId = view.user.apiId.value,
            displayName = view.user.displayName,
            primaryEmail = view.user.primaryEmail,
          ),
        activeTenant =
          view.activeTenant?.let {
            TenantSummaryResponse(
              apiId = it.apiId.value,
              name = it.name,
              slug = it.slug,
            )
          },
        sessionExpiresAt = view.sessionExpiresAt,
      )
  }
}

data class TenantSummaryResponse(
  val apiId: String,
  val name: String,
  val slug: String,
)
