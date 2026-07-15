package ink.doa.workbench.web.admin

import ink.doa.workbench.application.permission.AccessGrantManagementService
import ink.doa.workbench.identity.permission.GrantScope
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.InstanceScoped
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.context.InstanceRequestContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Authenticated
@SessionSecured
@StandardErrorResponses
@Tag(name = "Admin Users", description = "Administrator and access grant management.")
class InstanceAccessGrantController(private val accessGrantService: AccessGrantManagementService) {
  @GetMapping("/api/admin/grants")
  @InstanceScoped
  @Authorize(action = "instance.read", resource = "instance")
  @Operation(
    summary = "List explicit instance access grants",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Active instance grants",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun listInstanceGrants(
    instanceContext: InstanceRequestContext
  ): List<AccessGrantResponse> {
    instanceContext.requestId
    return accessGrantService
      .listGrants(scope = GrantScope.INSTANCE, tenantId = null, subjectUserId = null)
      .map { AccessGrantResponse.from(it) }
  }
}
