package ink.doa.workbench.core.workitem.activity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WorkItemActivityStatusRef(
  val id: String,
  val name: String,
  val group: String,
)

@Serializable
data class WorkItemActivityEntityRef(
  val id: String,
  val display: String? = null,
)

@Serializable
data class WorkItemActivityFieldChange(
  val path: String,
  val label: String,
  val from: JsonElement? = null,
  val to: JsonElement? = null,
)

@Serializable
data class WorkItemActivityStatusSnapshot(
  val from: WorkItemActivityStatusRef? = null,
  val to: WorkItemActivityStatusRef,
  val transition: WorkItemActivityEntityRef? = null,
)

@Serializable
data class WorkItemCreatedPayload(
  val status: WorkItemActivityStatusSnapshot,
  val issueType: WorkItemActivityEntityRef,
)

@Serializable data class WorkItemUpdatedPayload(val fields: List<WorkItemActivityFieldChange>)

@Serializable data class WorkItemStatusChangedPayload(val status: WorkItemActivityStatusSnapshot)

@Serializable
data class WorkItemActivityCommentRef(
  val id: String,
  val plainTextPreview: String,
)

@Serializable data class WorkItemCommentCreatedPayload(val comment: WorkItemActivityCommentRef)

@Serializable data class WorkItemCommentEditedPayload(val comment: WorkItemActivityCommentRef)

@Serializable
data class WorkItemCommentDeletedPayload(
  val comment: WorkItemActivityCommentRef,
  val deleteReason: String? = null,
)
