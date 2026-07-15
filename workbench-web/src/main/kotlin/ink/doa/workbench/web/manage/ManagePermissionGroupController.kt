package ink.doa.workbench.web.manage

import ink.doa.workbench.application.permission.PermissionGroupManagementService
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import ink.doa.workbench.web.api.context.TenantRequestContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
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
class ManagePermissionGroupController(
  private val permissionGroupManagementService: PermissionGroupManagementService
) {
  @GetMapping("/groups")
  @Authorize(action = "tenant.read", resource = "tenant")
  @Operation(summary = "List permission groups")
  suspend fun listGroups(tenantContext: TenantRequestContext): List<PermissionGroupResponse> =
    permissionGroupManagementService.listGroups(tenantContext.tenant.id).map {
      PermissionGroupResponse.from(it)
    }

  @PostMapping("/groups")
  @Authorize(action = "permission.group.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create permission group")
  suspend fun createGroup(
    @Valid @RequestBody request: CreatePermissionGroupRequest,
    tenantContext: TenantRequestContext,
  ): PermissionGroupResponse =
    PermissionGroupResponse.from(
      permissionGroupManagementService.createGroup(
        tenantId = tenantContext.tenant.id,
        code = request.code,
        name = request.name,
        description = request.description,
      )
    )

  @GetMapping("/groups/{id}")
  @Authorize(action = "tenant.read", resource = "tenant")
  @Operation(summary = "Get permission group")
  suspend fun getGroup(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ): PermissionGroupResponse =
    PermissionGroupResponse.from(
      permissionGroupManagementService.getGroup(tenantContext.tenant.id, id)
    )

  @PatchMapping("/groups/{id}")
  @Authorize(action = "permission.group.manage", resource = "permission")
  @Operation(summary = "Update permission group")
  suspend fun updateGroup(
    @PathVariable id: String,
    @Valid @RequestBody request: UpdatePermissionGroupRequest,
    tenantContext: TenantRequestContext,
  ): PermissionGroupResponse =
    PermissionGroupResponse.from(
      permissionGroupManagementService.updateGroup(
        tenantId = tenantContext.tenant.id,
        publicId = id,
        name = request.name,
        description = request.description,
      )
    )

  @DeleteMapping("/groups/{id}")
  @Authorize(action = "permission.group.manage", resource = "permission")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete permission group")
  suspend fun deleteGroup(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ) {
    permissionGroupManagementService.deleteGroup(tenantContext.tenant.id, id)
  }

  @GetMapping("/groups/{id}/members")
  @Authorize(action = "tenant.read", resource = "tenant")
  @Operation(summary = "List group members")
  suspend fun listGroupMembers(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ): List<GroupMemberResponse> =
    permissionGroupManagementService.listGroupMembers(tenantContext.tenant.id, id).map {
      GroupMemberResponse.from(it)
    }

  @PostMapping("/groups/{id}/members")
  @Authorize(action = "permission.group.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add group member")
  suspend fun addGroupMember(
    @PathVariable id: String,
    @Valid @RequestBody request: AddGroupMemberRequest,
    tenantContext: TenantRequestContext,
  ): GroupMemberResponse =
    GroupMemberResponse.from(
      permissionGroupManagementService.addGroupMember(
        tenantId = tenantContext.tenant.id,
        groupPublicId = id,
        userPublicId = request.userId,
      )
    )

  @DeleteMapping("/groups/{id}/members/{userId}")
  @Authorize(action = "permission.group.manage", resource = "permission")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove group member")
  suspend fun removeGroupMember(
    @PathVariable id: String,
    @PathVariable userId: String,
    tenantContext: TenantRequestContext,
  ) {
    permissionGroupManagementService.removeGroupMember(tenantContext.tenant.id, id, userId)
  }
}
