package doa.ink.workbench.web.permission

import doa.ink.workbench.core.common.context.TenantRequestContext
import doa.ink.workbench.core.permission.PermissionActionRecord
import doa.ink.workbench.core.permission.PermissionPolicyRecord
import doa.ink.workbench.core.permission.RoleAssignmentRecord
import doa.ink.workbench.core.permission.RoleRecord
import doa.ink.workbench.core.permission.RoleScope
import doa.ink.workbench.core.permission.model.PermissionCondition
import doa.ink.workbench.core.permission.model.PermissionEffect
import doa.ink.workbench.service.permission.PermissionManagementService
import doa.ink.workbench.web.api.Authenticated
import doa.ink.workbench.web.api.Authorize
import doa.ink.workbench.web.api.TenantScoped
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
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
@Suppress("UnusedParameter")
class PermissionAdminController(private val service: PermissionManagementService) {
  @GetMapping("/roles")
  @Authorize(action = "permission.role.manage", resource = "permission")
  suspend fun listRoles(tenantContext: TenantRequestContext): List<RoleResponse> =
    service.listRoles(tenantContext.tenantId).map { RoleResponse.from(it) }

  @PostMapping("/roles")
  @Authorize(action = "permission.role.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  suspend fun createRole(
    @Valid @RequestBody request: CreateRoleRequest,
    tenantContext: TenantRequestContext,
  ): RoleResponse =
    service
      .createRole(
        tenantId = tenantContext.tenantId,
        scope = request.scope,
        code = request.code,
        name = request.name,
        description = request.description,
      )
      .let { RoleResponse.from(it) }

  @GetMapping("/actions")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  suspend fun listActions(tenantContext: TenantRequestContext): List<ActionResponse> =
    service.listActions().map { ActionResponse.from(it) }

  @PostMapping("/actions")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  suspend fun ensureAction(
    @Valid @RequestBody request: EnsureActionRequest,
    tenantContext: TenantRequestContext,
  ): ActionResponse =
    service.ensureAction(request.code, request.description).let { ActionResponse.from(it) }

  @GetMapping("/policies")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  suspend fun listPolicies(tenantContext: TenantRequestContext): List<PolicyResponse> =
    service.listPolicies(tenantContext.tenantId).map { PolicyResponse.from(it) }

  @PostMapping("/policies")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  suspend fun createPolicy(
    @Valid @RequestBody request: CreatePolicyRequest,
    tenantContext: TenantRequestContext,
  ): PolicyResponse =
    service
      .createPolicy(
        tenantId = tenantContext.tenantId,
        roleId = request.roleId,
        actionCode = request.action,
        effect = request.effect,
        resourcePattern = request.resourcePattern,
        condition = request.condition?.toModel(),
        actorUserId = currentUserId(),
      )
      .let { PolicyResponse.from(it) }

  @DeleteMapping("/policies/{id}")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  suspend fun expirePolicy(
    @PathVariable id: UUID,
    tenantContext: TenantRequestContext,
  ) {
    service.expirePolicy(id)
  }

  @GetMapping("/assignments")
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  suspend fun listAssignments(tenantContext: TenantRequestContext): List<RoleAssignmentResponse> =
    service.listAssignments(tenantContext.tenantId).map { RoleAssignmentResponse.from(it) }

  @PostMapping("/assignments")
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  suspend fun assignRole(
    @Valid @RequestBody request: AssignRoleRequest,
    tenantContext: TenantRequestContext,
  ): RoleAssignmentResponse =
    service
      .assignRole(
        tenantId = tenantContext.tenantId,
        userId = request.userId,
        roleId = request.roleId,
        projectId = request.projectId,
        actorUserId = currentUserId(),
      )
      .let { RoleAssignmentResponse.from(it) }

  @DeleteMapping("/assignments/{id}")
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  suspend fun revokeAssignment(
    @PathVariable id: UUID,
    tenantContext: TenantRequestContext,
  ) {
    service.revokeAssignment(id)
  }

  private fun currentUserId(): UUID? =
    (SecurityContextHolder.getContext().authentication?.principal
        as? doa.ink.workbench.core.identity.model.AuthenticatedPrincipal)
      ?.user
      ?.id
}

data class CreateRoleRequest(
  val scope: RoleScope = RoleScope.TENANT,
  @field:NotBlank val code: String,
  @field:NotBlank val name: String,
  val description: String? = null,
)

data class EnsureActionRequest(@field:NotBlank val code: String, val description: String? = null)

data class CreatePolicyRequest(
  val roleId: UUID,
  @field:NotBlank val action: String,
  val effect: PermissionEffect = PermissionEffect.ALLOW,
  @field:NotBlank val resourcePattern: String,
  val condition: PermissionConditionRequest? = null,
)

data class AssignRoleRequest(
  val userId: UUID,
  val roleId: UUID,
  val projectId: UUID? = null,
)

data class PermissionConditionRequest(
  val field: String? = null,
  val expected: String? = null,
  val allOf: List<PermissionConditionRequest> = emptyList(),
) {
  fun toModel(): PermissionCondition? =
    if (allOf.isNotEmpty()) {
      PermissionCondition.AllOf(allOf.mapNotNull { it.toModel() })
    } else if (field != null && expected != null) {
      PermissionCondition.FieldEquals(field, expected)
    } else {
      null
    }
}

data class RoleResponse(
  val id: UUID,
  val apiId: String,
  val tenantId: UUID?,
  val scope: RoleScope,
  val code: String,
  val name: String,
  val description: String?,
  val isBuiltin: Boolean,
) {
  companion object {
    fun from(record: RoleRecord) =
      RoleResponse(
        id = record.id,
        apiId = record.apiId.value,
        tenantId = record.tenantId,
        scope = record.scope,
        code = record.code,
        name = record.name,
        description = record.description,
        isBuiltin = record.isBuiltin,
      )
  }
}

data class ActionResponse(val id: UUID, val code: String, val description: String?) {
  companion object {
    fun from(record: PermissionActionRecord) =
      ActionResponse(record.id, record.code.code, record.description)
  }
}

data class PolicyResponse(
  val id: UUID,
  val apiId: String,
  val roleId: UUID,
  val action: String,
  val effect: PermissionEffect,
  val resourcePattern: String,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime?,
) {
  companion object {
    fun from(record: PermissionPolicyRecord) =
      PolicyResponse(
        id = record.id,
        apiId = record.apiId.value,
        roleId = record.roleId,
        action = record.action.code,
        effect = record.effect,
        resourcePattern = record.resourcePattern,
        validFrom = record.validFrom,
        validTo = record.validTo,
      )
  }
}

data class RoleAssignmentResponse(
  val id: UUID,
  val apiId: String,
  val userId: UUID,
  val roleId: UUID,
  val projectId: UUID?,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime?,
) {
  companion object {
    fun from(record: RoleAssignmentRecord) =
      RoleAssignmentResponse(
        id = record.id,
        apiId = record.apiId.value,
        userId = record.userId,
        roleId = record.roleId,
        projectId = record.projectId,
        validFrom = record.validFrom,
        validTo = record.validTo,
      )
  }
}
