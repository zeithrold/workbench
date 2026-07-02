package doa.ink.workbench.web.permission

import doa.ink.workbench.core.common.context.RequestContext
import doa.ink.workbench.core.common.context.TenantRequestContext
import doa.ink.workbench.service.permission.PermissionManagementService
import doa.ink.workbench.web.api.Authenticated
import doa.ink.workbench.web.api.Authorize
import doa.ink.workbench.web.api.SessionSecured
import doa.ink.workbench.web.api.StandardErrorResponses
import doa.ink.workbench.web.api.TenantScoped
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
@RequestMapping("/api/admin/permissions")
@Authenticated
@TenantScoped
@Tag(name = "Permission Admin", description = "Tenant permission administration")
@SessionSecured
@StandardErrorResponses
@Suppress("UnusedParameter")
class PermissionAdminController(private val service: PermissionManagementService) {
  @GetMapping("/roles")
  @Authorize(action = "permission.role.manage", resource = "permission")
  @Operation(summary = "List roles")
  suspend fun listRoles(tenantContext: TenantRequestContext): List<RoleResponse> =
    service.listRoles(tenantContext.tenantId).map { RoleResponse.from(it) }

  @PostMapping("/roles")
  @Authorize(action = "permission.role.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create role")
  suspend fun createRole(
    @Valid @RequestBody request: CreateRoleRequest,
    tenantContext: TenantRequestContext,
  ): RoleResponse =
    RoleResponse.from(
      service.createRole(
        tenantId = tenantContext.tenantId,
        scope = request.scope,
        code = request.code,
        name = request.name,
        description = request.description,
      )
    )

  @GetMapping("/actions")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(summary = "List permission actions")
  suspend fun listActions(tenantContext: TenantRequestContext): List<ActionResponse> =
    service.listActions().map { ActionResponse.from(it) }

  @PostMapping("/actions")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Ensure permission action",
    description = "Upserts a global permission action registry entry.",
  )
  suspend fun ensureAction(
    @Valid @RequestBody request: EnsureActionRequest,
    tenantContext: TenantRequestContext,
  ): ActionResponse = ActionResponse.from(service.ensureAction(request.code, request.description))

  @GetMapping("/policies")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(summary = "List policies")
  suspend fun listPolicies(tenantContext: TenantRequestContext): List<PolicyResponse> =
    service.listPolicies(tenantContext.tenantId).map { PolicyResponse.from(it) }

  @PostMapping("/policies")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create policy")
  suspend fun createPolicy(
    @Valid @RequestBody request: CreatePolicyRequest,
    tenantContext: TenantRequestContext,
    requestContext: RequestContext,
  ): PolicyResponse =
    PolicyResponse.from(
      service.createPolicy(
        tenantId = tenantContext.tenantId,
        rolePublicId = request.roleId,
        actionCode = request.action,
        effect = request.effect,
        resourcePattern = request.resourcePattern,
        condition = request.condition?.toInput()?.toModel(),
        actorUserId = requestContext.actorUserId,
      )
    )

  @DeleteMapping("/policies/{id}")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Expire policy")
  suspend fun expirePolicy(@PathVariable id: String, tenantContext: TenantRequestContext) {
    service.expirePolicy(tenantContext.tenantId, id)
  }

  @GetMapping("/assignments")
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @Operation(summary = "List role assignments")
  suspend fun listAssignments(tenantContext: TenantRequestContext): List<RoleAssignmentResponse> =
    service.listAssignments(tenantContext.tenantId).map { RoleAssignmentResponse.from(it) }

  @PostMapping("/assignments")
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Assign role")
  suspend fun assignRole(
    @Valid @RequestBody request: AssignRoleRequest,
    tenantContext: TenantRequestContext,
    requestContext: RequestContext,
  ): RoleAssignmentResponse =
    RoleAssignmentResponse.from(
      service.assignRole(
        tenantId = tenantContext.tenantId,
        userPublicId = request.userId,
        rolePublicId = request.roleId,
        projectPublicId = request.projectId,
        actorUserId = requestContext.actorUserId,
      )
    )

  @DeleteMapping("/assignments/{id}")
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Revoke role assignment")
  suspend fun revokeAssignment(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ) {
    service.revokeAssignment(tenantContext.tenantId, id)
  }
}
