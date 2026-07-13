package ink.doa.workbench.web.admin

import ink.doa.workbench.application.permission.AccessGrantManagementService
import ink.doa.workbench.application.permission.AdminUserService
import ink.doa.workbench.application.permission.CreateManagedAccessGrantCommand
import ink.doa.workbench.application.permission.PermissionActionService
import ink.doa.workbench.identity.permission.GrantScope
import ink.doa.workbench.identity.permission.model.PermissionEffect
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.InstanceScoped
import ink.doa.workbench.web.api.OpenApiExamples
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import ink.doa.workbench.web.api.context.InstanceRequestContext
import ink.doa.workbench.web.api.context.TenantRequestContext
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
class AdminUserController(
  private val adminUserService: AdminUserService,
  private val accessGrantService: AccessGrantManagementService,
  private val permissionActionService: PermissionActionService,
) {
  @GetMapping("/instance-admins")
  @InstanceScoped
  @Authorize(action = "tenant.read", resource = "tenant")
  @Operation(summary = "List instance administrators")
  suspend fun listInstanceAdmins(
    @Suppress("UnusedParameter") instanceContext: InstanceRequestContext
  ): List<AdminUserResponse> =
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
    @Suppress("UnusedParameter") instanceContext: InstanceRequestContext,
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
        CreateManagedAccessGrantCommand(
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
    )

  @DeleteMapping("/grants/{id}")
  @TenantScoped
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Expire access grant")
  suspend fun expireGrant(
    @PathVariable id: String,
    @Suppress("UnusedParameter") tenantContext: TenantRequestContext,
  ) {
    accessGrantService.expireGrant(id)
  }

  @GetMapping("/actions")
  @TenantScoped
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(summary = "List permission actions")
  suspend fun listActions(
    @Suppress("UnusedParameter") tenantContext: TenantRequestContext
  ): List<ActionResponse> = permissionActionService.listActions().map { ActionResponse.from(it) }

  @PostMapping("/actions")
  @TenantScoped
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Ensure permission action")
  suspend fun ensureAction(
    @Valid @RequestBody request: EnsureActionRequest,
    @Suppress("UnusedParameter") tenantContext: TenantRequestContext,
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
    fun from(view: ink.doa.workbench.application.permission.AdminUserView) =
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
    fun from(view: ink.doa.workbench.application.permission.AccessGrantView) =
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
    fun from(view: ink.doa.workbench.application.permission.ActionView) =
      ActionResponse(code = view.code, description = view.description)
  }
}
