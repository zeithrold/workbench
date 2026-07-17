package one.ztd.workbench.web.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import one.ztd.workbench.application.permission.AccessGrantManagementService
import one.ztd.workbench.application.permission.AdminUserService
import one.ztd.workbench.application.permission.CreateManagedAccessGrantCommand
import one.ztd.workbench.application.permission.PermissionActionService
import one.ztd.workbench.identity.permission.GrantScope
import one.ztd.workbench.identity.permission.model.PermissionEffect
import one.ztd.workbench.web.api.Authenticated
import one.ztd.workbench.web.api.Authorize
import one.ztd.workbench.web.api.InstanceScoped
import one.ztd.workbench.web.api.OpenApiExamples
import one.ztd.workbench.web.api.SessionSecured
import one.ztd.workbench.web.api.StandardErrorResponses
import one.ztd.workbench.web.api.TenantScoped
import one.ztd.workbench.web.api.context.InstanceRequestContext
import one.ztd.workbench.web.api.context.TenantRequestContext
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
@RequestMapping
@Authenticated
@SessionSecured
@StandardErrorResponses
@Tag(name = "Admin Users", description = "Administrator and access grant management.")
class AdminUserController(
  private val adminUserService: AdminUserService,
  private val accessGrantService: AccessGrantManagementService,
  private val permissionActionService: PermissionActionService,
) {
  @GetMapping("/api/admin/instance-admins")
  @InstanceScoped
  @Authorize(action = "instance.read", resource = "instance")
  @Operation(
    summary = "List instance administrators",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Instance administrators",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun listInstanceAdmins(
    @Suppress("UnusedParameter") instanceContext: InstanceRequestContext
  ): List<AdminUserResponse> =
    adminUserService.listInstanceAdmins().map { AdminUserResponse.from(it) }

  @PostMapping("/api/admin/instance-admins")
  @InstanceScoped
  @Authorize(action = "instance.admin.manage", resource = "instance-admin")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Grant instance administrator",
    responses =
      [
        ApiResponse(
          responseCode = "201",
          description = "Instance administrator granted",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun grantInstanceAdmin(
    @Valid @RequestBody request: GrantAdminRequest,
    instanceContext: InstanceRequestContext,
  ): AdminUserResponse =
    AdminUserResponse.from(
      adminUserService.grantInstanceAdmin(request.userId, instanceContext.actor?.id)
    )

  @GetMapping("/api/manage/tenant-admins")
  @TenantScoped
  @Authorize(action = "tenant.read", resource = "tenant")
  @Operation(
    summary = "List tenant administrators",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Tenant administrators",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun listTenantAdmins(tenantContext: TenantRequestContext): List<AdminUserResponse> =
    adminUserService.listTenantAdmins(tenantContext.tenant.id).map { AdminUserResponse.from(it) }

  @PostMapping("/api/manage/tenant-admins")
  @TenantScoped
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Grant tenant administrator",
    responses =
      [
        ApiResponse(
          responseCode = "201",
          description = "Tenant administrator granted",
          useReturnTypeSchema = true,
        )
      ],
  )
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

  @DeleteMapping("/api/admin/instance-admins/{id}")
  @InstanceScoped
  @Authorize(action = "instance.admin.manage", resource = "instance-admin")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Revoke instance administrator",
    responses = [ApiResponse(responseCode = "204", description = "Instance administrator revoked")],
  )
  suspend fun revokeInstanceAdmin(
    @Parameter(example = OpenApiExamples.USER_ID) @PathVariable id: String,
    @Suppress("UnusedParameter") instanceContext: InstanceRequestContext,
  ) {
    adminUserService.revokeInstanceAdmin(id)
  }

  @DeleteMapping("/api/manage/tenant-admins/{id}")
  @TenantScoped
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Revoke tenant administrator",
    responses = [ApiResponse(responseCode = "204", description = "Tenant administrator revoked")],
  )
  suspend fun revokeTenantAdmin(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ) {
    adminUserService.revokeTenantAdmin(tenantContext.tenant.id, id)
  }

  @GetMapping("/api/manage/grants")
  @TenantScoped
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(summary = "List access grants for active tenant")
  suspend fun listGrants(tenantContext: TenantRequestContext): List<AccessGrantResponse> =
    accessGrantService
      .listGrants(scope = null, tenantId = tenantContext.tenant.id, subjectUserId = null)
      .map { AccessGrantResponse.from(it) }

  @PostMapping("/api/manage/grants")
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

  @DeleteMapping("/api/manage/grants/{id}")
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

  @GetMapping("/api/manage/actions")
  @TenantScoped
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(
    summary = "List permission actions",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Tenant capabilities",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun listActions(
    @Suppress("UnusedParameter") tenantContext: TenantRequestContext
  ): List<ActionResponse> =
    permissionActionService.listTenantCapabilities().map { ActionResponse.from(it) }
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

data class AdminUserResponse(
  val id: String,
  val userId: String,
  val scope: String,
  val tenantId: String?,
  val status: String,
) {
  companion object {
    fun from(view: one.ztd.workbench.application.permission.AdminUserView) =
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
    fun from(view: one.ztd.workbench.application.permission.AccessGrantView) =
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

data class ActionResponse(
  val code: String,
  val description: String?,
  val name: String? = null,
  val resourcePattern: String? = null,
) {
  companion object {
    fun from(capability: one.ztd.workbench.application.permission.TenantPermissionCapability) =
      ActionResponse(
        code = capability.action,
        description = capability.description,
        name = capability.name,
        resourcePattern = capability.resourcePattern,
      )
  }
}
