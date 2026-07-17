package one.ztd.workbench.web.identity

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import one.ztd.workbench.application.permission.ManagementNavigationItem
import one.ztd.workbench.application.permission.ManagementNavigationService
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.web.api.AuthenticatedOnly
import one.ztd.workbench.web.api.SessionSecured
import one.ztd.workbench.web.api.StandardErrorResponses
import one.ztd.workbench.web.api.context.RequestContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

enum class TenantContextStatus {
  ACTIVE,
  NOT_SELECTED,
}

data class NavigationItemResponse(val id: ManagementNavigationItem)

data class ManagementNavigationResponse(
  val items: List<NavigationItemResponse>,
  val tenantContextStatus: TenantContextStatus,
)

@RestController
@AuthenticatedOnly
@SessionSecured
@StandardErrorResponses
@Tag(name = "Session Navigation", description = "Navigation available to the current session.")
class SessionNavigationController(
  private val sessions: SessionService,
  private val navigation: ManagementNavigationService,
) {
  @GetMapping("/api/session/navigation")
  @Operation(
    operationId = "getManagementNavigation",
    summary = "Get management navigation",
    description =
      "Returns stable navigation identifiers without exposing roles, permissions, or routes.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Management navigation for the current session",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun get(
    principal: AuthenticatedPrincipal,
    requestContext: RequestContext,
  ): ManagementNavigationResponse {
    val activeTenantId = sessions.activeTenantId(principal)
    return ManagementNavigationResponse(
      items =
        navigation
          .items(principal, activeTenantId, requestContext.requestId)
          .map(::NavigationItemResponse),
      tenantContextStatus =
        if (activeTenantId == null) TenantContextStatus.NOT_SELECTED
        else TenantContextStatus.ACTIVE,
    )
  }
}
