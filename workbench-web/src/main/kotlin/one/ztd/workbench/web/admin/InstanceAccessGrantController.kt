package one.ztd.workbench.web.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import one.ztd.workbench.application.permission.AccessGrantManagementService
import one.ztd.workbench.identity.permission.GrantScope
import one.ztd.workbench.web.api.Authenticated
import one.ztd.workbench.web.api.Authorize
import one.ztd.workbench.web.api.InstanceScoped
import one.ztd.workbench.web.api.SessionSecured
import one.ztd.workbench.web.api.StandardErrorResponses
import one.ztd.workbench.web.api.context.InstanceRequestContext
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
