package doa.ink.workbench.web.identity

import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.service.identity.SessionService
import doa.ink.workbench.web.api.SessionSecured
import doa.ink.workbench.web.api.StandardErrorResponses
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/session")
@Tag(name = "Session", description = "Current session aggregate")
@SessionSecured
@StandardErrorResponses
class SessionController(private val sessionService: SessionService) {
  @GetMapping
  @Operation(
    summary = "Get session",
    description = "Returns the authenticated user and active tenant for the current session.",
  )
  suspend fun getSession(principal: AuthenticatedPrincipal): SessionResponse =
    SessionResponse.from(sessionService.getCurrent(principal))

  @PatchMapping
  @Operation(
    summary = "Switch tenant",
    description = "Sets the active tenant for the current session.",
  )
  suspend fun switchTenant(
    @Valid @RequestBody request: SwitchTenantRequest,
    principal: AuthenticatedPrincipal,
  ): SessionResponse =
    SessionResponse.from(sessionService.switchTenant(principal, request.tenantId))
}
