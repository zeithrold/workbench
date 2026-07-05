@file:Suppress("TooManyFunctions")

package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.WorkItemAttachmentRepository
import ink.doa.workbench.core.workitem.model.AttachmentPurpose
import ink.doa.workbench.core.workitem.model.AttachmentUploadStatus
import ink.doa.workbench.core.workitem.model.DeleteWorkItemAttachmentCommand
import ink.doa.workbench.core.workitem.model.InitiateWorkItemAttachmentUploadCommand
import ink.doa.workbench.core.workitem.model.WorkItemAttachmentRecord
import ink.doa.workbench.data.persistence.AttachmentsTable
import ink.doa.workbench.data.persistence.IssueCommentsTable
import ink.doa.workbench.data.persistence.IssuesTable
import ink.doa.workbench.data.persistence.UsersTable
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedWorkItemAttachmentRepository(private val database: Database) :
  WorkItemAttachmentRepository {
  override suspend fun listByWorkItem(
    tenantId: UUID,
    issueId: UUID,
    purpose: AttachmentPurpose?,
    commentApiId: String?,
    limit: Int,
    offset: Long,
  ): List<WorkItemAttachmentRecord> =
    suspendTransaction(db = database) {
      val tenantUuid = tenantId.toKotlinUuid()
      val issueUuid = issueId.toKotlinUuid()
      val query =
        if (commentApiId != null) {
          (AttachmentsTable innerJoin IssueCommentsTable).selectAll().where {
            (AttachmentsTable.tenantId eq tenantUuid) and
              (AttachmentsTable.issueId eq issueUuid) and
              AttachmentsTable.deletedAt.isNull() and
              (AttachmentsTable.uploadStatus eq AttachmentUploadStatus.COMPLETED.wireValue) and
              (IssueCommentsTable.apiId eq commentApiId) and
              (purpose?.let { AttachmentsTable.purpose eq it.wireValue } ?: Op.TRUE)
          }
        } else {
          AttachmentsTable.selectAll().where {
            (AttachmentsTable.tenantId eq tenantUuid) and
              (AttachmentsTable.issueId eq issueUuid) and
              AttachmentsTable.deletedAt.isNull() and
              (AttachmentsTable.uploadStatus eq AttachmentUploadStatus.COMPLETED.wireValue) and
              (purpose?.let { AttachmentsTable.purpose eq it.wireValue } ?: Op.TRUE)
          }
        }
      query.orderBy(AttachmentsTable.createdAt to SortOrder.DESC).limit(limit).offset(offset).map {
        it.toRecord()
      }
    }

  override suspend fun createPending(
    command: InitiateWorkItemAttachmentUploadCommand,
    issueId: UUID,
    commentId: UUID?,
    attachmentId: UUID,
    apiId: PublicId,
    storageKey: String,
  ): WorkItemAttachmentRecord =
    suspendTransaction(db = database) {
      val now = now()
      AttachmentsTable.insert {
        it[AttachmentsTable.id] = attachmentId.toKotlinUuid()
        it[AttachmentsTable.apiId] = apiId.value
        it[AttachmentsTable.tenantId] = command.tenantId.toKotlinUuid()
        it[AttachmentsTable.issueId] = issueId.toKotlinUuid()
        it[AttachmentsTable.commentId] = commentId?.toKotlinUuid()
        it[AttachmentsTable.uploadedBy] = command.uploadedBy.toKotlinUuid()
        it[AttachmentsTable.filename] = command.filename
        it[AttachmentsTable.contentType] = command.contentType
        it[AttachmentsTable.byteSize] = command.declaredByteSize
        it[AttachmentsTable.checksum] = null
        it[AttachmentsTable.storageKey] = storageKey
        it[AttachmentsTable.purpose] = command.purpose.wireValue
        it[AttachmentsTable.uploadStatus] = AttachmentUploadStatus.PENDING.wireValue
        it[AttachmentsTable.metadata] = metadataFor(command.purpose)
        it[AttachmentsTable.createdAt] = now
      }
      WorkItemAttachmentRecord(
        id = attachmentId,
        apiId = apiId,
        tenantId = command.tenantId,
        issueId = issueId,
        commentId = commentId,
        commentApiId = commentId?.let { requireCommentApiId(it) },
        uploadedBy = command.uploadedBy,
        uploadedByApiId = requireUserApiId(command.uploadedBy),
        filename = command.filename,
        contentType = command.contentType,
        byteSize = command.declaredByteSize,
        checksum = null,
        storageKey = storageKey,
        purpose = command.purpose,
        uploadStatus = AttachmentUploadStatus.PENDING,
        createdAt = now,
      )
    }

  override suspend fun completePending(
    tenantId: UUID,
    issueId: UUID,
    attachmentApiId: String,
    uploadedBy: UUID,
    byteSize: Long,
    checksum: String,
  ): WorkItemAttachmentRecord =
    suspendTransaction(db = database) {
      val row =
        AttachmentsTable.selectAll()
          .where {
            (AttachmentsTable.tenantId eq tenantId.toKotlinUuid()) and
              (AttachmentsTable.issueId eq issueId.toKotlinUuid()) and
              (AttachmentsTable.apiId eq attachmentApiId) and
              (AttachmentsTable.uploadedBy eq uploadedBy.toKotlinUuid()) and
              (AttachmentsTable.uploadStatus eq AttachmentUploadStatus.PENDING.wireValue) and
              AttachmentsTable.deletedAt.isNull()
          }
          .singleOrNull()
          ?: throw ResourceNotFoundException(
            WorkbenchErrorCode.RESOURCE_WORK_ITEM_ATTACHMENT_NOT_FOUND
          )
      AttachmentsTable.update({ AttachmentsTable.id eq row[AttachmentsTable.id] }) {
        it[AttachmentsTable.byteSize] = byteSize
        it[AttachmentsTable.checksum] = checksum
        it[AttachmentsTable.uploadStatus] = AttachmentUploadStatus.COMPLETED.wireValue
      }
      AttachmentsTable.selectAll()
        .where { AttachmentsTable.id eq row[AttachmentsTable.id] }
        .single()
        .toRecord()
    }

  override suspend fun findByApiId(
    tenantId: UUID,
    issueId: UUID,
    attachmentApiId: String,
  ): WorkItemAttachmentRecord? =
    suspendTransaction(db = database) {
      AttachmentsTable.selectAll()
        .where {
          (AttachmentsTable.tenantId eq tenantId.toKotlinUuid()) and
            (AttachmentsTable.issueId eq issueId.toKotlinUuid()) and
            (AttachmentsTable.apiId eq attachmentApiId) and
            (AttachmentsTable.uploadStatus eq AttachmentUploadStatus.COMPLETED.wireValue) and
            AttachmentsTable.deletedAt.isNull()
        }
        .singleOrNull()
        ?.toRecord()
    }

  override suspend fun findPendingByApiId(
    tenantId: UUID,
    issueId: UUID,
    attachmentApiId: String,
    uploadedBy: UUID,
  ): WorkItemAttachmentRecord? =
    suspendTransaction(db = database) {
      AttachmentsTable.selectAll()
        .where {
          (AttachmentsTable.tenantId eq tenantId.toKotlinUuid()) and
            (AttachmentsTable.issueId eq issueId.toKotlinUuid()) and
            (AttachmentsTable.apiId eq attachmentApiId) and
            (AttachmentsTable.uploadedBy eq uploadedBy.toKotlinUuid()) and
            (AttachmentsTable.uploadStatus eq AttachmentUploadStatus.PENDING.wireValue) and
            AttachmentsTable.deletedAt.isNull()
        }
        .singleOrNull()
        ?.toRecord()
    }

  override suspend fun softDelete(
    command: DeleteWorkItemAttachmentCommand,
    issueId: UUID,
  ): WorkItemAttachmentRecord =
    suspendTransaction(db = database) {
      val row =
        AttachmentsTable.selectAll()
          .where {
            (AttachmentsTable.tenantId eq command.tenantId.toKotlinUuid()) and
              (AttachmentsTable.issueId eq issueId.toKotlinUuid()) and
              (AttachmentsTable.apiId eq command.attachmentApiId) and
              (AttachmentsTable.uploadStatus eq AttachmentUploadStatus.COMPLETED.wireValue) and
              AttachmentsTable.deletedAt.isNull()
          }
          .singleOrNull()
          ?: throw ResourceNotFoundException(
            WorkbenchErrorCode.RESOURCE_WORK_ITEM_ATTACHMENT_NOT_FOUND
          )
      val now = now()
      AttachmentsTable.update({ AttachmentsTable.id eq row[AttachmentsTable.id] }) {
        it[AttachmentsTable.deletedAt] = now
        it[AttachmentsTable.deletedBy] = command.actorUserId.toKotlinUuid()
        it[AttachmentsTable.deleteReason] = command.deleteReason
      }
      AttachmentsTable.selectAll()
        .where { AttachmentsTable.id eq row[AttachmentsTable.id] }
        .single()
        .toRecord(includeDeleted = true)
    }

  override suspend fun resolveIssueId(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
  ): UUID? =
    suspendTransaction(db = database) {
      IssuesTable.selectAll()
        .where {
          (IssuesTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssuesTable.projectId eq projectId.toKotlinUuid()) and
            (IssuesTable.apiId eq workItemApiId) and
            IssuesTable.archivedAt.isNull() and
            IssuesTable.deletedAt.isNull()
        }
        .singleOrNull()
        ?.get(IssuesTable.id)
        ?.toJavaUuid()
    }

  override suspend fun resolveCommentId(
    tenantId: UUID,
    issueId: UUID,
    commentApiId: String,
  ): UUID? =
    suspendTransaction(db = database) {
      IssueCommentsTable.selectAll()
        .where {
          (IssueCommentsTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssueCommentsTable.issueId eq issueId.toKotlinUuid()) and
            (IssueCommentsTable.apiId eq commentApiId) and
            IssueCommentsTable.deletedAt.isNull()
        }
        .singleOrNull()
        ?.get(IssueCommentsTable.id)
        ?.toJavaUuid()
    }

  private fun ResultRow.toRecord(includeDeleted: Boolean = false): WorkItemAttachmentRecord {
    if (!includeDeleted && this[AttachmentsTable.deletedAt] != null) {
      throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_ATTACHMENT_NOT_FOUND)
    }
    val uploadedBy = this[AttachmentsTable.uploadedBy].toJavaUuid()
    val commentId = this[AttachmentsTable.commentId]?.toJavaUuid()
    return WorkItemAttachmentRecord(
      id = this[AttachmentsTable.id].toJavaUuid(),
      apiId = PublicId(this[AttachmentsTable.apiId]),
      tenantId = this[AttachmentsTable.tenantId].toJavaUuid(),
      issueId =
        this[AttachmentsTable.issueId]?.toJavaUuid()
          ?: throw ResourceNotFoundException(
            WorkbenchErrorCode.RESOURCE_WORK_ITEM_ATTACHMENT_NOT_FOUND
          ),
      commentId = commentId,
      commentApiId = commentId?.let { requireCommentApiId(it) },
      uploadedBy = uploadedBy,
      uploadedByApiId = requireUserApiId(uploadedBy),
      filename = this[AttachmentsTable.filename],
      contentType = this[AttachmentsTable.contentType],
      byteSize = this[AttachmentsTable.byteSize],
      checksum = this[AttachmentsTable.checksum],
      storageKey = this[AttachmentsTable.storageKey],
      purpose = parsePurpose(this[AttachmentsTable.purpose]),
      uploadStatus = parseUploadStatus(this[AttachmentsTable.uploadStatus]),
      createdAt = this[AttachmentsTable.createdAt],
    )
  }

  private fun requireUserApiId(userId: UUID): PublicId =
    UsersTable.selectAll()
      .where { UsersTable.id eq userId.toKotlinUuid() }
      .singleOrNull()
      ?.get(UsersTable.apiId)
      ?.let(::PublicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)

  private fun requireCommentApiId(commentId: UUID): PublicId =
    IssueCommentsTable.selectAll()
      .where { IssueCommentsTable.id eq commentId.toKotlinUuid() }
      .singleOrNull()
      ?.get(IssueCommentsTable.apiId)
      ?.let(::PublicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_COMMENT_NOT_FOUND)

  private fun metadataFor(purpose: AttachmentPurpose): JsonObject = buildJsonObject {
    put("purpose", JsonPrimitive(purpose.wireValue))
  }

  private fun parsePurpose(wire: String): AttachmentPurpose =
    AttachmentPurpose.fromWire(wire) ?: AttachmentPurpose.STANDALONE

  private fun parseUploadStatus(wire: String): AttachmentUploadStatus =
    AttachmentUploadStatus.fromWire(wire) ?: AttachmentUploadStatus.COMPLETED

  private fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
}
