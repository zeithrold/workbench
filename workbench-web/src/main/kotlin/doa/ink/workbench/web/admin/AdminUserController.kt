package doa.ink.workbench.web.admin

import doa.ink.workbench.core.common.context.InstanceRequestContext
import doa.ink.workbench.core.common.context.TenantRequestContext
import doa.ink.workbench.core.permission.GrantScope
import doa.ink.workbench.core.permission.model.PermissionEffect
import doa.ink.workbench.security.permission.AccessGrantManagementService
import doa.ink.workbench.security.permission.AdminUserService
import doa.ink.workbench.security.permission.PermissionActionService
import doa.ink.workbench.web.api.Authenticated
import doa.ink.workbench.web.api.Authorize
import doa.ink.workbench.web.api.InstanceScoped
import doa.ink.workbench.web.api.OpenApiExamples
import doa.ink.workbench.web.api.SessionSecured
import doa.ink.workbench.web.api.StandardErrorResponses
import doa.ink.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
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
@RequestMapping("/api/admin/users")
@Authenticated
@SessionSecured
@StandardErrorResponses
@Tag(name = "Admin Users", description = "Administrator and access grant management.")
@Suppress("UnusedParameter")
class AdminUserController(
  private val adminUserService: AdminUserService,
  private val accessGrantService: AccessGrantManagementService,
  private val permissionActionService: PermissionActionService,
) {
  @GetMapping("/instance-admins")
  @InstanceScoped
  @Authorize(action = "tenant.read", resource = "tenant")
  @Operation(summary = "List instance administrators")
  suspend fun listInstanceAdmins(instanceContext: InstanceRequestContext): List<AdminUserResponse> =
    adminUserService.listInstanceAdmins().map { AdminUserResponse.from(it) }

  @PostMapping("/instance-admins")
  @InstanceScoped
  @Authorize(action = "tenant.update", resource = "tenant")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Grant instance administrator")
  suspend fun grantInstanceAdmin(
    @Valid @RequestBody request: GrantAdminRequest,
    instanceContext: InstanceRequestContext,
  ): AdminUserResponse =
    AdminUserResponse.from(
      adminUserService.grantInstanceAdmin(request.userId, instanceContext.actor?.id)
    )

  @GetMapping("/tenant-admins")
  @TenantScoped
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @Operation(summary = "List tenant administrators")
  suspend fun listTenantAdmins(tenantContext: TenantRequestContext): List<AdminUserResponse> =
    adminUserService.listTenantAdmins(tenantContext.tenant.id).map { AdminUserResponse.from(it) }

  @PostMapping("/tenant-admins")
  @TenantScoped
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Grant tenant administrator")
  suspend fun grantTenantAdmin(
    @Valid @RequestBody request: GrantAdminRequest,
    tenantContext: TenantRequestContext,
  ): AdminUserResponse =
    AdminUserResponse.from(
      adminUserService.grantTenantAdmin(
        tenantId = tenantContext.tenant.id,
        userPublicId = request.userId,
        actorUserId = tenantContext.actor?.id,
      )
    )

  @DeleteMapping("/{id}")
  @InstanceScoped
  @Authorize(action = "tenant.update", resource = "tenant")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Revoke administrator")
  suspend fun revokeAdmin(
    @Parameter(example = OpenApiExamples.USER_ID) @PathVariable id: String,
    instanceContext: InstanceRequestContext,
  ) {
    adminUserService.revokeAdmin(id)
  }

  @GetMapping("/grants")
  @TenantScoped
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(summary = "List access grants for active tenant")
  suspend fun listGrants(tenantContext: TenantRequestContext): List<AccessGrantResponse> =
    accessGrantService
      .listGrants(scope = null, tenantId = tenantContext.tenant.id, subjectUserId = null)
      .map { AccessGrantResponse.from(it) }

  @PostMapping("/grants")
  @TenantScoped
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create access grant")
  suspend fun createGrant(
    @Valid @RequestBody request: CreateAccessGrantRequest,
    tenantContext: TenantRequestContext,
  ): AccessGrantResponse =
    AccessGrantResponse.from(
      accessGrantService.createGrant(
        scope = request.scope,
        tenantId = tenantContext.tenant.id,
        userPublicId = request.userId,
        actionCode = request.action,
        resourcePattern = request.resourcePattern,
        effect = request.effect,
        projectPublicId = request.projectId,
        actorUserId = tenantContext.actor?.id,
      )
    )

  @DeleteMapping("/grants/{id}")
  @TenantScoped
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Expire access grant")
  suspend fun expireGrant(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ) {
    accessGrantService.expireGrant(id)
  }

  @GetMapping("/actions")
  @TenantScoped
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(summary = "List permission actions")
  suspend fun listActions(tenantContext: TenantRequestContext): List<ActionResponse> =
    permissionActionService.listActions().map { ActionResponse.from(it) }

  @PostMapping("/actions")
  @TenantScoped
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Ensure permission action")
  suspend fun ensureAction(
    @Valid @RequestBody request: EnsureActionRequest,
    tenantContext: TenantRequestContext,
  ): ActionResponse =
    ActionResponse.from(permissionActionService.ensureAction(request.code, request.description))
}

data class GrantAdminRequest(
  @field:NotBlank @field:Schema(example = OpenApiExamples.USER_ID) val userId: String
)

data class CreateAccessGrantRequest(
  @field:Schema(description = "Grant scope.", example = "TENANT")
  val scope: GrantScope = GrantScope.TENANT,
  @field:NotBlank @field:Schema(example = OpenApiExamples.USER_ID) val userId: String,
  @field:NotBlank @field:Schema(example = "project.read") val action: String,
  @field:NotBlank @field:Schema(example = "project:*") val resourcePattern: String,
  @field:Schema(example = "ALLOW") val effect: PermissionEffect = PermissionEffect.ALLOW,
  @field:Schema(example = OpenApiExamples.PROJECT_ID) val projectId: String? = null,
)

data class EnsureActionRequest(
  @field:NotBlank val code: String,
  val description: String? = null,
)

data class AdminUserResponse(
  val id: String,
  val userId: String,
  val scope: String,
  val tenantId: String?,
  val status: String,
) {
  companion object {
    fun from(view: doa.ink.workbench.security.permission.AdminUserView) =
      AdminUserResponse(
        id = view.id,
        userId = view.userId,
        scope = view.scope.dbValue,
        tenantId = view.tenantId,
        status = view.status,
      )
  }
}

data class AccessGrantResponse(
  val id: String,
  val scope: String,
  val userId: String,
  val action: String,
  val resourcePattern: String,
  val effect: String,
) {
  companion object {
    fun from(view: doa.ink.workbench.security.permission.AccessGrantView) =
      AccessGrantResponse(
        id = view.id,
        scope = view.scope.dbValue,
        userId = view.userId,
        action = view.action,
        resourcePattern = view.resourcePattern,
        effect = view.effect.name,
      )
  }
}

data class ActionResponse(val code: String, val description: String?) {
  companion object {
    fun from(view: doa.ink.workbench.security.permission.ActionView) =
      ActionResponse(code = view.code, description = view.description)
  }
}
