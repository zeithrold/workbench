package doa.ink.workbench.web.manage

import doa.ink.workbench.core.common.context.TenantRequestContext
import doa.ink.workbench.core.common.summary.ProjectSummary
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.permission.PermissionPrincipalType
import doa.ink.workbench.core.permission.model.PermissionEffect
import doa.ink.workbench.service.permission.GroupMemberView
import doa.ink.workbench.service.permission.PermissionBindingView
import doa.ink.workbench.service.permission.PermissionGroupView
import doa.ink.workbench.service.permission.PermissionManagementService
import doa.ink.workbench.service.permission.PermissionPolicyRuleView
import doa.ink.workbench.service.permission.PermissionPolicySummary
import doa.ink.workbench.service.permission.PermissionPolicyView
import doa.ink.workbench.web.api.Authenticated
import doa.ink.workbench.web.api.Authorize
import doa.ink.workbench.web.api.SessionSecured
import doa.ink.workbench.web.api.StandardErrorResponses
import doa.ink.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
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
@Suppress("TooManyFunctions")
class ManagePermissionController(
  private val permissionManagementService: PermissionManagementService
) {
  @GetMapping("/groups")
  @Authorize(action = "permission.group.manage", resource = "permission")
  @Operation(summary = "List permission groups")
  suspend fun listGroups(tenantContext: TenantRequestContext): List<PermissionGroupResponse> =
    permissionManagementService.listGroups(tenantContext.tenant.id).map {
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
      permissionManagementService.createGroup(
        tenantId = tenantContext.tenant.id,
        code = request.code,
        name = request.name,
        description = request.description,
      )
    )

  @GetMapping("/groups/{id}")
  @Authorize(action = "permission.group.manage", resource = "permission")
  @Operation(summary = "Get permission group")
  suspend fun getGroup(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ): PermissionGroupResponse =
    PermissionGroupResponse.from(permissionManagementService.getGroup(tenantContext.tenant.id, id))

  @PatchMapping("/groups/{id}")
  @Authorize(action = "permission.group.manage", resource = "permission")
  @Operation(summary = "Update permission group")
  suspend fun updateGroup(
    @PathVariable id: String,
    @Valid @RequestBody request: UpdatePermissionGroupRequest,
    tenantContext: TenantRequestContext,
  ): PermissionGroupResponse =
    PermissionGroupResponse.from(
      permissionManagementService.updateGroup(
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
    permissionManagementService.deleteGroup(tenantContext.tenant.id, id)
  }

  @GetMapping("/groups/{id}/members")
  @Authorize(action = "permission.group.manage", resource = "permission")
  @Operation(summary = "List group members")
  suspend fun listGroupMembers(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ): List<GroupMemberResponse> =
    permissionManagementService.listGroupMembers(tenantContext.tenant.id, id).map {
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
      permissionManagementService.addGroupMember(
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
    permissionManagementService.removeGroupMember(tenantContext.tenant.id, id, userId)
  }

  @GetMapping("/permission-policies")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(summary = "List permission policies")
  suspend fun listPolicies(tenantContext: TenantRequestContext): List<PermissionPolicyResponse> =
    permissionManagementService.listPolicies(tenantContext.tenant.id).map {
      PermissionPolicyResponse.from(it)
    }

  @GetMapping("/permission-policies/{id}")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(summary = "Get permission policy")
  suspend fun getPolicy(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ): PermissionPolicyResponse =
    PermissionPolicyResponse.from(permissionManagementService.getPolicy(tenantContext.tenant.id, id))

  @PostMapping("/permission-policies")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create custom permission policy")
  suspend fun createPolicy(
    @Valid @RequestBody request: CreatePermissionPolicyRequest,
    tenantContext: TenantRequestContext,
  ): PermissionPolicyResponse =
    PermissionPolicyResponse.from(
      permissionManagementService.createPolicy(
        tenantId = tenantContext.tenant.id,
        code = request.code,
        name = request.name,
        description = request.description,
      )
    )

  @PatchMapping("/permission-policies/{id}")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(summary = "Update custom permission policy")
  suspend fun updatePolicy(
    @PathVariable id: String,
    @Valid @RequestBody request: UpdatePermissionPolicyRequest,
    tenantContext: TenantRequestContext,
  ): PermissionPolicyResponse =
    PermissionPolicyResponse.from(
      permissionManagementService.updatePolicy(
        tenantId = tenantContext.tenant.id,
        publicId = id,
        name = request.name,
        description = request.description,
      )
    )

  @DeleteMapping("/permission-policies/{id}")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete custom permission policy")
  suspend fun deletePolicy(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ) {
    permissionManagementService.deletePolicy(tenantContext.tenant.id, id)
  }

  @PostMapping("/permission-policies/{id}/rules")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add rule to custom permission policy")
  suspend fun addPolicyRule(
    @PathVariable id: String,
    @Valid @RequestBody request: CreatePermissionPolicyRuleRequest,
    tenantContext: TenantRequestContext,
  ): PermissionPolicyResponse =
    PermissionPolicyResponse.from(
      permissionManagementService.addPolicyRule(
        tenantId = tenantContext.tenant.id,
        policyPublicId = id,
        action = request.action,
        resourcePattern = request.resourcePattern,
        effect =
          request.effect?.let { PermissionEffect.valueOf(it.uppercase()) }
            ?: PermissionEffect.ALLOW,
      )
    )

  @GetMapping("/permission-bindings")
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @Operation(summary = "List permission bindings")
  suspend fun listBindings(tenantContext: TenantRequestContext): List<PermissionBindingResponse> =
    permissionManagementService.listBindings(tenantContext.tenant.id).map {
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
      permissionManagementService.createBinding(
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

  @DeleteMapping("/permission-bindings/{id}")
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Expire permission binding")
  suspend fun expireBinding(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ) {
    permissionManagementService.expireBinding(tenantContext.tenant.id, id)
  }
}

data class CreatePermissionGroupRequest(
  @field:NotBlank val code: String,
  @field:NotBlank val name: String,
  val description: String?,
)

data class UpdatePermissionGroupRequest(
  val name: String?,
  val description: String?,
)

data class CreatePermissionPolicyRequest(
  @field:NotBlank val code: String,
  @field:NotBlank val name: String,
  val description: String?,
)

data class UpdatePermissionPolicyRequest(
  val name: String?,
  val description: String?,
)

data class CreatePermissionPolicyRuleRequest(
  @field:NotBlank val action: String,
  @field:NotBlank val resourcePattern: String,
  val effect: String? = null,
)

data class AddGroupMemberRequest(@field:NotBlank val userId: String)

data class CreatePermissionBindingRequest(
  val principalType: PermissionPrincipalType,
  val userId: String?,
  val groupId: String?,
  @field:NotBlank val policyId: String,
  val projectId: String?,
)

data class PermissionGroupResponse(
  val id: String,
  val code: String,
  val name: String,
  val description: String?,
  val builtin: Boolean,
) {
  companion object {
    fun from(view: PermissionGroupView) =
      PermissionGroupResponse(
        id = view.id,
        code = view.code,
        name = view.name,
        description = view.description,
        builtin = view.builtin,
      )
  }
}

data class GroupMemberResponse(val id: String, val user: UserSummary) {
  companion object {
    fun from(view: GroupMemberView) = GroupMemberResponse(id = view.id, user = view.user)
  }
}

data class PermissionPolicyResponse(
  val id: String,
  val code: String,
  val name: String,
  val description: String?,
  val builtin: Boolean,
  val rules: List<PermissionPolicyRuleResponse>,
) {
  companion object {
    fun from(view: PermissionPolicyView) =
      PermissionPolicyResponse(
        id = view.id,
        code = view.code,
        name = view.name,
        description = view.description,
        builtin = view.builtin,
        rules = view.rules.map { PermissionPolicyRuleResponse.from(it) },
      )
  }
}

data class PermissionPolicyRuleResponse(
  val action: String,
  val resourcePattern: String,
  val effect: String,
) {
  companion object {
    fun from(view: PermissionPolicyRuleView) =
      PermissionPolicyRuleResponse(
        action = view.action,
        resourcePattern = view.resourcePattern,
        effect = view.effect,
      )
  }
}

data class PermissionPolicySummaryResponse(
  val id: String,
  val code: String,
  val name: String,
) {
  companion object {
    fun from(view: PermissionPolicySummary) =
      PermissionPolicySummaryResponse(id = view.id, code = view.code, name = view.name)
  }
}

data class PermissionBindingResponse(
  val id: String,
  val principalType: String,
  val user: UserSummary?,
  val group: PermissionGroupResponse?,
  val policy: PermissionPolicySummaryResponse,
  val project: ProjectSummary?,
) {
  companion object {
    fun from(view: PermissionBindingView) =
      PermissionBindingResponse(
        id = view.id,
        principalType = view.principalType,
        user = view.user,
        group = view.group?.let { PermissionGroupResponse.from(it) },
        policy = PermissionPolicySummaryResponse.from(view.policy),
        project = view.project,
      )
  }
}
