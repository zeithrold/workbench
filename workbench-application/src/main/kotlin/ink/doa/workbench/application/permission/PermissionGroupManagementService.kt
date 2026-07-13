package ink.doa.workbench.application.permission

import ink.doa.workbench.application.identity.PublicIdResolver
import ink.doa.workbench.identity.UserRepository
import ink.doa.workbench.identity.common.summary.UserSummary
import ink.doa.workbench.identity.permission.AddGroupMemberCommand
import ink.doa.workbench.identity.permission.CreatePermissionGroupCommand
import ink.doa.workbench.identity.permission.PermissionGroupRecord
import ink.doa.workbench.identity.permission.PermissionGroupRepository
import ink.doa.workbench.identity.permission.UpdatePermissionGroupCommand
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class PermissionGroupManagementService(
  private val groups: PermissionGroupRepository,
  private val users: UserRepository,
  private val publicIds: PublicIdResolver,
  private val clock: Clock,
) {
  suspend fun listGroups(tenantId: UUID): List<PermissionGroupView> =
    groups.list(tenantId).map { PermissionGroupView.from(it) }

  suspend fun createGroup(
    tenantId: UUID,
    code: String,
    name: String,
    description: String?,
  ): PermissionGroupView =
    PermissionGroupView.from(
      groups.create(
        CreatePermissionGroupCommand(
          tenantId = tenantId,
          code = code,
          name = name,
          description = description,
        )
      )
    )

  suspend fun getGroup(tenantId: UUID, publicId: String): PermissionGroupView =
    PermissionGroupView.from(requireGroup(tenantId, publicId))

  suspend fun updateGroup(
    tenantId: UUID,
    publicId: String,
    name: String?,
    description: String?,
  ): PermissionGroupView {
    val group = requireGroup(tenantId, publicId)
    if (group.builtin) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_GROUP_BUILTIN_UPDATE_FORBIDDEN)
    }
    return PermissionGroupView.from(
      groups.update(UpdatePermissionGroupCommand(group.id, name, description))
    )
  }

  suspend fun deleteGroup(tenantId: UUID, publicId: String): Boolean {
    val group = requireGroup(tenantId, publicId)
    if (group.builtin) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_GROUP_BUILTIN_DELETE_FORBIDDEN)
    }
    return groups.delete(tenantId, group.id)
  }

  suspend fun listGroupMembers(tenantId: UUID, groupPublicId: String): List<GroupMemberView> {
    val group = requireGroup(tenantId, groupPublicId)
    return groups.listMembers(group.id).map { member ->
      GroupMemberView(
        id = member.apiId.value,
        user = UserSummary.from(requireUser(member.userId)),
      )
    }
  }

  suspend fun addGroupMember(
    tenantId: UUID,
    groupPublicId: String,
    userPublicId: String,
  ): GroupMemberView {
    val group = requireGroup(tenantId, groupPublicId)
    val user = publicIds.resolveUser(userPublicId)
    val member = groups.addMember(AddGroupMemberCommand(groupId = group.id, userId = user.id))
    return GroupMemberView(id = member.apiId.value, user = UserSummary.from(user))
  }

  suspend fun removeGroupMember(
    tenantId: UUID,
    groupPublicId: String,
    userPublicId: String,
  ): Boolean {
    val group = requireGroup(tenantId, groupPublicId)
    val user = publicIds.resolveUser(userPublicId)
    return groups.removeMember(group.id, user.id, OffsetDateTime.now(clock))
  }

  suspend fun requireGroup(tenantId: UUID, publicId: String): PermissionGroupRecord =
    groups.findByApiId(tenantId, publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_GROUP_NOT_FOUND)

  private suspend fun requireUser(userId: UUID) =
    users.findById(userId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)
}
