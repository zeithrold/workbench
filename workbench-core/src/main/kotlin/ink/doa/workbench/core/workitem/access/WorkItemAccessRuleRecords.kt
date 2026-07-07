package ink.doa.workbench.core.workitem.access

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject

enum class WorkItemAccessSubjectType(val dbValue: String) {
  ANYONE("anyone"),
  USER("user"),
  IN_GROUP("in_group"),
  NOT_IN_GROUP("not_in_group"),
  IN_ROLE("in_role"),
  NOT_IN_ROLE("not_in_role");

  companion object {
    fun fromDbValue(value: String): WorkItemAccessSubjectType = entries.single {
      it.dbValue == value.lowercase()
    }
  }
}

enum class WorkItemAccessActionType(val dbValue: String) {
  TRANSITION("transition"),
  FIELD_WRITE("field_write"),
  FIELD_WRITE_ALL("field_write_all"),
  COMMENT("comment");

  companion object {
    fun fromDbValue(value: String): WorkItemAccessActionType = entries.single {
      it.dbValue == value.lowercase()
    }
  }
}

data class WorkItemAccessRuleRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val issueTypeConfigId: UUID,
  val subjectType: WorkItemAccessSubjectType,
  val subjectUserId: UUID?,
  val subjectGroupId: UUID?,
  val subjectRoleCode: String?,
  val actionType: WorkItemAccessActionType,
  val transitionId: UUID?,
  val fieldKey: String?,
  val effect: PermissionEffect,
  val condition: JsonObject,
  val rank: Int,
  val isActive: Boolean,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class CreateWorkItemAccessRuleCommand(
  val tenantId: UUID,
  val issueTypeConfigId: UUID,
  val subjectType: WorkItemAccessSubjectType,
  val subjectUserId: UUID? = null,
  val subjectGroupId: UUID? = null,
  val subjectRoleCode: String? = null,
  val actionType: WorkItemAccessActionType,
  val transitionId: UUID? = null,
  val fieldKey: String? = null,
  val effect: PermissionEffect,
  val condition: JsonObject = JsonObject(emptyMap()),
  val rank: Int = 100,
)

data class WorkItemAccessActor(
  val userId: UUID,
  val groupIds: Set<UUID>,
  val projectRoles: Set<String>,
)

data class WorkItemAccessEvaluationContext(
  val actor: WorkItemAccessActor,
  val workItem: WorkItemRecord,
  val issueTypeConfigId: UUID,
  val properties: Map<String, kotlinx.serialization.json.JsonElement>,
  val childIssuesNotDone: Long = 0,
)
