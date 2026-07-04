package ink.doa.workbench.web.manage

import ink.doa.workbench.core.common.context.TenantRequestContext
import ink.doa.workbench.security.permission.CreateManagedPermissionBindingCommand
import ink.doa.workbench.security.permission.PermissionBindingManagementService
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/manage")
@Authenticated
@TenantScoped
@SessionSecured
@StandardErrorResponses
@Tag(name = "Tenant Management", description = "Current tenant foreground management APIs.")
class ManagePermissionBindingController(
  private val permissionBindingManagementService: PermissionBindingManagementService
) {
  @GetMapping("/permission-bindings")
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @Operation(summary = "List permission bindings")
  suspend fun listBindings(tenantContext: TenantRequestContext): List<PermissionBindingResponse> =
    permissionBindingManagementService.listBindings(tenantContext.tenant.id).map {
      PermissionBindingResponse.from(it)
    }

  @PostMapping("/permission-bindings")
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create permission binding")
  suspend fun createBinding(
    @Valid @RequestBody request: CreatePermissionBindingRequest,
    tenantContext: TenantRequestContext,
  ): PermissionBindingResponse =
    PermissionBindingResponse.from(
      permissionBindingManagementService.createBinding(
        CreateManagedPermissionBindingCommand(
          tenantId = tenantContext.tenant.id,
          principalType = request.principalType,
          userPublicId = request.userId,
          groupPublicId = request.groupId,
          policyPublicId = request.policyId,
          projectPublicId = request.projectId,
          effect = null,
          actorUserId = tenantContext.actor?.id,
        )
      )
    )

  @DeleteMapping("/permission-bindings/{id}")
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Expire permission binding")
  suspend fun expireBinding(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ) {
    permissionBindingManagementService.expireBinding(tenantContext.tenant.id, id)
  }
}
