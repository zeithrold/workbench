package one.ztd.workbench.data.repository.workitem

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import one.ztd.workbench.agile.workitem.WorkItemAttachmentRepository
import one.ztd.workbench.agile.workitem.model.AttachmentPurpose
import one.ztd.workbench.agile.workitem.model.AttachmentUploadStatus
import one.ztd.workbench.agile.workitem.model.CompletePendingAttachmentCommand
import one.ztd.workbench.agile.workitem.model.CreatePendingAttachmentCommand
import one.ztd.workbench.agile.workitem.model.DeleteWorkItemAttachmentCommand
import one.ztd.workbench.agile.workitem.model.ListWorkItemAttachmentsQuery
import one.ztd.workbench.agile.workitem.model.WorkItemAttachmentRecord
import one.ztd.workbench.data.persistence.postgres.identity.UsersTable
import one.ztd.workbench.data.persistence.postgres.workitem.AttachmentsTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueCommentsTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssuesTable
import one.ztd.workbench.data.persistence.postgres.workitem.now
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.ids.PublicId
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
    query: ListWorkItemAttachmentsQuery
  ): List<WorkItemAttachmentRecord> =
    suspendTransaction(db = database) {
      val tenantUuid = query.tenantId.toKotlinUuid()
      val issueUuid = query.issueId.toKotlinUuid()
      val purpose = query.purpose
      val commentApiId = query.commentApiId
      val limit = query.limit
      val offset = query.offset
      val selectQuery =
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
      selectQuery
        .orderBy(AttachmentsTable.createdAt to SortOrder.DESC)
        .limit(limit)
        .offset(offset)
        .map {
          it.toRecord()
        }
    }

  override suspend fun createPending(
    command: CreatePendingAttachmentCommand
  ): WorkItemAttachmentRecord =
    suspendTransaction(db = database) {
      val upload = command.upload
      val issueId = command.issueId
      val commentId = command.commentId
      val attachmentId = command.attachmentId
      val apiId = command.apiId
      val storageKey = command.storageKey
      val now = now()
      AttachmentsTable.insert {
        it[AttachmentsTable.id] = attachmentId.toKotlinUuid()
        it[AttachmentsTable.apiId] = apiId.value
        it[AttachmentsTable.tenantId] = upload.tenantId.toKotlinUuid()
        it[AttachmentsTable.issueId] = issueId.toKotlinUuid()
        it[AttachmentsTable.commentId] = commentId?.toKotlinUuid()
        it[AttachmentsTable.uploadedBy] = upload.uploadedBy.toKotlinUuid()
        it[AttachmentsTable.filename] = upload.filename
        it[AttachmentsTable.contentType] = upload.contentType
        it[AttachmentsTable.byteSize] = upload.declaredByteSize
        it[AttachmentsTable.checksum] = null
        it[AttachmentsTable.storageKey] = storageKey
        it[AttachmentsTable.purpose] = upload.purpose.wireValue
        it[AttachmentsTable.uploadStatus] = AttachmentUploadStatus.PENDING.wireValue
        it[AttachmentsTable.metadata] = metadataFor(upload.purpose)
        it[AttachmentsTable.createdAt] = now
      }
      WorkItemAttachmentRecord(
        id = attachmentId,
        apiId = apiId,
        tenantId = upload.tenantId,
        issueId = issueId,
        commentId = commentId,
        commentApiId = commentId?.let { requireCommentApiId(it) },
        uploadedBy = upload.uploadedBy,
        uploadedByApiId = requireUserApiId(upload.uploadedBy),
        filename = upload.filename,
        contentType = upload.contentType,
        byteSize = upload.declaredByteSize,
        checksum = null,
        storageKey = storageKey,
        purpose = upload.purpose,
        uploadStatus = AttachmentUploadStatus.PENDING,
        createdAt = now,
      )
    }

  override suspend fun completePending(
    command: CompletePendingAttachmentCommand
  ): WorkItemAttachmentRecord =
    suspendTransaction(db = database) {
      val tenantId = command.tenantId
      val issueId = command.issueId
      val attachmentApiId = command.attachmentApiId
      val uploadedBy = command.uploadedBy
      val byteSize = command.byteSize
      val checksum = command.checksum
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
