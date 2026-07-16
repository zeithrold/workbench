package ink.doa.workbench.application.permission

import ink.doa.workbench.application.identity.PublicIdResolver
import ink.doa.workbench.identity.permission.CreatePermissionBindingCommand
import ink.doa.workbench.identity.permission.PermissionBindingRepository
import ink.doa.workbench.identity.permission.PermissionPrincipalType
import ink.doa.workbench.identity.permission.model.PermissionEffect
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class PermissionBindingManagementService(
  private val bindings: PermissionBindingRepository,
  private val bindingViews: PermissionBindingViewAssembler,
  private val groupManagement: PermissionGroupManagementService,
  private val policyManagement: PermissionPolicyManagementService,
  private val publicIds: PublicIdResolver,
  private val clock: Clock,
) {
  suspend fun listBindings(tenantId: UUID): List<PermissionBindingView> =
    bindings.listByTenant(tenantId).map { bindingViews.assemble(tenantId, it) }

  suspend fun createBinding(command: CreateManagedPermissionBindingCommand): PermissionBindingView {
    if (command.effect != null && command.effect != PermissionEffect.ALLOW) {
      throw InvalidRequestException(
        WorkbenchErrorCode.PERMISSION_BINDING_EFFECT_OVERRIDE_UNSUPPORTED
      )
    }
    val userId = command.userPublicId?.let { publicIds.resolveUser(it).id }
    val groupId =
      command.groupPublicId?.let { groupManagement.requireGroup(command.tenantId, it).id }
    val policy = policyManagement.requirePolicy(command.tenantId, command.policyPublicId)
    val projectId =
      command.projectPublicId?.let { publicIds.resolveProject(command.tenantId, it).id }
    validatePrincipal(command.principalType, userId, groupId)
    val now = OffsetDateTime.now(clock)
    if (command.validTo != null && !command.validTo.isAfter(now)) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_BINDING_EXPIRATION_INVALID)
    }
    val binding =
      bindings.create(
        CreatePermissionBindingCommand(
          tenantId = command.tenantId,
          projectId = projectId,
          principalType = command.principalType,
          principalUserId = userId,
          principalGroupId = groupId,
          policyId = policy.id,
          validFrom = now,
          validTo = command.validTo,
          createdBy = command.actorUserId,
        )
      )
    return bindingViews.assemble(command.tenantId, binding)
  }

  suspend fun expireBinding(tenantId: UUID, publicId: String): Boolean {
    val binding =
      bindings.findByApiId(tenantId, publicId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_BINDING_NOT_FOUND)
    return bindings.expire(tenantId, binding.id, OffsetDateTime.now(clock))
  }

  private fun validatePrincipal(
    principalType: PermissionPrincipalType,
    userId: UUID?,
    groupId: UUID?,
  ) {
    val errorCode =
      when (principalType) {
        PermissionPrincipalType.USER ->
          if (userId == null || groupId != null) {
            WorkbenchErrorCode.PERMISSION_BINDING_USER_TARGET_INVALID
          } else {
            null
          }
        PermissionPrincipalType.GROUP ->
          if (groupId == null || userId != null) {
            WorkbenchErrorCode.PERMISSION_BINDING_GROUP_TARGET_INVALID
          } else {
            null
          }
        PermissionPrincipalType.TENANT_MEMBER ->
          if (userId != null || groupId != null) {
            WorkbenchErrorCode.PERMISSION_BINDING_TENANT_MEMBER_TARGET_INVALID
          } else {
            null
          }
      }
    if (errorCode != null) {
      throw InvalidRequestException(errorCode)
    }
  }
}
