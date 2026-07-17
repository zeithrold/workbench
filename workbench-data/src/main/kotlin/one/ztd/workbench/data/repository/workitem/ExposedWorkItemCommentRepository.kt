package one.ztd.workbench.data.repository.workitem

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import one.ztd.workbench.agile.workitem.WorkItemCommentRepository
import one.ztd.workbench.agile.workitem.model.CreateWorkItemCommentCommand
import one.ztd.workbench.agile.workitem.model.DeleteWorkItemCommentCommand
import one.ztd.workbench.agile.workitem.model.UpdateWorkItemCommentCommand
import one.ztd.workbench.agile.workitem.model.WorkItemCommentCreateResult
import one.ztd.workbench.agile.workitem.model.WorkItemCommentRecord
import one.ztd.workbench.agile.workitem.stream.WorkItemEventCodec
import one.ztd.workbench.data.persistence.postgres.identity.UsersTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueCommentsTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssuesTable
import one.ztd.workbench.data.persistence.postgres.workitem.appendWorkItemEvent
import one.ztd.workbench.data.persistence.postgres.workitem.now
import one.ztd.workbench.data.persistence.postgres.workitem.softDeleteTimelineByCommentSource
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.ids.PublicId
import org.jetbrains.exposed.v1.core.ResultRow
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
class ExposedWorkItemCommentRepository(
  private val database: Database,
  private val eventFactory: WorkItemEventFactory,
  private val eventCodec: WorkItemEventCodec,
) : WorkItemCommentRepository {
  override suspend fun create(
    command: CreateWorkItemCommentCommand,
    issueId: UUID,
  ): WorkItemCommentCreateResult =
    suspendTransaction(db = database) {
      val now = now()
      val commentId = UUID.randomUUID()
      val apiId = PublicId.new("icm")
      IssueCommentsTable.insert {
        it[IssueCommentsTable.id] = commentId.toKotlinUuid()
        it[IssueCommentsTable.apiId] = apiId.value
        it[IssueCommentsTable.tenantId] = command.tenantId.toKotlinUuid()
        it[IssueCommentsTable.issueId] = issueId.toKotlinUuid()
        it[IssueCommentsTable.authorId] = command.authorId.toKotlinUuid()
        it[IssueCommentsTable.body] = command.body
        it[IssueCommentsTable.bodyPlainText] = command.bodyPlainText
        it[IssueCommentsTable.transitionId] = command.transitionId?.toKotlinUuid()
        it[IssueCommentsTable.statusHistoryId] = command.statusHistoryId?.toKotlinUuid()
        it[IssueCommentsTable.createdAt] = now
        it[IssueCommentsTable.updatedAt] = now
      }
      val event =
        appendWorkItemEvent(
          eventCodec,
          eventFactory.commentAdded(
            WorkItemCommentCreatedInput(
              context =
                WorkItemActivityContext(
                  tenantId = command.tenantId,
                  projectId = command.projectId,
                  workItemId = issueId,
                  actorUserId = command.authorId,
                  occurredAt = now,
                ),
              commentApiId = apiId.value,
              plainTextPreview = command.bodyPlainText,
            )
          ),
        )
      WorkItemCommentCreateResult(
        record =
          WorkItemCommentRecord(
            id = commentId,
            apiId = apiId,
            tenantId = command.tenantId,
            issueId = issueId,
            authorId = command.authorId,
            authorApiId = requireUserApiId(command.authorId),
            body = command.body,
            bodyPlainText = command.bodyPlainText,
            transitionId = command.transitionId,
            statusHistoryId = command.statusHistoryId,
            editedAt = null,
            createdAt = now,
            updatedAt = now,
          ),
        eventId = event.id,
        eventApiId = event.apiId,
      )
    }

  override suspend fun findByApiId(
    tenantId: UUID,
    issueId: UUID,
    commentApiId: String,
  ): WorkItemCommentRecord? =
    suspendTransaction(db = database) {
      IssueCommentsTable.selectAll()
        .where {
          (IssueCommentsTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssueCommentsTable.issueId eq issueId.toKotlinUuid()) and
            (IssueCommentsTable.apiId eq commentApiId) and
            IssueCommentsTable.deletedAt.isNull()
        }
        .singleOrNull()
        ?.toRecord()
    }

  override suspend fun update(
    command: UpdateWorkItemCommentCommand,
    issueId: UUID,
  ): WorkItemCommentRecord =
    suspendTransaction(db = database) {
      val row =
        IssueCommentsTable.selectAll()
          .where {
            (IssueCommentsTable.tenantId eq command.tenantId.toKotlinUuid()) and
              (IssueCommentsTable.issueId eq issueId.toKotlinUuid()) and
              (IssueCommentsTable.apiId eq command.commentApiId) and
              IssueCommentsTable.deletedAt.isNull()
          }
          .singleOrNull()
          ?: throw ResourceNotFoundException(
            WorkbenchErrorCode.RESOURCE_WORK_ITEM_COMMENT_NOT_FOUND
          )
      val now = now()
      IssueCommentsTable.update({ IssueCommentsTable.id eq row[IssueCommentsTable.id] }) {
        it[IssueCommentsTable.body] = command.body
        it[IssueCommentsTable.bodyPlainText] = command.bodyPlainText
        it[IssueCommentsTable.editedAt] = now
        it[IssueCommentsTable.updatedAt] = now
      }
      appendWorkItemEvent(
        eventCodec,
        eventFactory.commentEdited(
          WorkItemCommentCreatedInput(
            context =
              WorkItemActivityContext(
                tenantId = command.tenantId,
                projectId = command.projectId,
                workItemId = issueId,
                actorUserId = command.actorUserId,
                occurredAt = now,
              ),
            commentApiId = command.commentApiId,
            plainTextPreview = command.bodyPlainText,
          )
        ),
      )
      row
        .toRecord()
        .copy(
          body = command.body,
          bodyPlainText = command.bodyPlainText,
          editedAt = now,
          updatedAt = now,
        )
    }

  override suspend fun softDelete(
    command: DeleteWorkItemCommentCommand,
    issueId: UUID,
  ): WorkItemCommentRecord =
    suspendTransaction(db = database) {
      val row =
        IssueCommentsTable.selectAll()
          .where {
            (IssueCommentsTable.tenantId eq command.tenantId.toKotlinUuid()) and
              (IssueCommentsTable.issueId eq issueId.toKotlinUuid()) and
              (IssueCommentsTable.apiId eq command.commentApiId) and
              IssueCommentsTable.deletedAt.isNull()
          }
          .singleOrNull()
          ?: throw ResourceNotFoundException(
            WorkbenchErrorCode.RESOURCE_WORK_ITEM_COMMENT_NOT_FOUND
          )
      val now = now()
      IssueCommentsTable.update({ IssueCommentsTable.id eq row[IssueCommentsTable.id] }) {
        it[IssueCommentsTable.deletedAt] = now
        it[IssueCommentsTable.deletedBy] = command.actorUserId.toKotlinUuid()
        it[IssueCommentsTable.deleteReason] = command.deleteReason
        it[IssueCommentsTable.updatedAt] = now
      }
      softDeleteTimelineByCommentSource(
        tenantId = command.tenantId,
        workItemId = issueId,
        commentApiId = command.commentApiId,
        deletedAt = now,
      )
      appendWorkItemEvent(
        eventCodec,
        eventFactory.commentDeleted(
          WorkItemCommentDeletedInput(
            context =
              WorkItemActivityContext(
                tenantId = command.tenantId,
                projectId = command.projectId,
                workItemId = issueId,
                actorUserId = command.actorUserId,
                occurredAt = now,
              ),
            commentApiId = command.commentApiId,
            plainTextPreview = row[IssueCommentsTable.bodyPlainText],
            deleteReason = command.deleteReason,
          )
        ),
      )
      IssueCommentsTable.selectAll()
        .where { IssueCommentsTable.id eq row[IssueCommentsTable.id] }
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

  internal fun loadByApiId(
    tenantId: UUID,
    issueId: UUID,
    commentApiId: String,
  ): WorkItemCommentRecord? =
    IssueCommentsTable.selectAll()
      .where {
        (IssueCommentsTable.tenantId eq tenantId.toKotlinUuid()) and
          (IssueCommentsTable.issueId eq issueId.toKotlinUuid()) and
          (IssueCommentsTable.apiId eq commentApiId) and
          IssueCommentsTable.deletedAt.isNull()
      }
      .singleOrNull()
      ?.toRecord()

  private fun ResultRow.toRecord(includeDeleted: Boolean = false): WorkItemCommentRecord {
    if (!includeDeleted && this[IssueCommentsTable.deletedAt] != null) {
      throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_COMMENT_NOT_FOUND)
    }
    val authorId = this[IssueCommentsTable.authorId].toJavaUuid()
    return WorkItemCommentRecord(
      id = this[IssueCommentsTable.id].toJavaUuid(),
      apiId = PublicId(this[IssueCommentsTable.apiId]),
      tenantId = this[IssueCommentsTable.tenantId].toJavaUuid(),
      issueId = this[IssueCommentsTable.issueId].toJavaUuid(),
      authorId = authorId,
      authorApiId = requireUserApiId(authorId),
      body = this[IssueCommentsTable.body],
      bodyPlainText = this[IssueCommentsTable.bodyPlainText],
      transitionId = this[IssueCommentsTable.transitionId]?.toJavaUuid(),
      statusHistoryId = this[IssueCommentsTable.statusHistoryId]?.toJavaUuid(),
      editedAt = this[IssueCommentsTable.editedAt],
      createdAt = this[IssueCommentsTable.createdAt],
      updatedAt = this[IssueCommentsTable.updatedAt],
    )
  }

  private fun requireUserApiId(userId: UUID): PublicId =
    UsersTable.selectAll()
      .where { UsersTable.id eq userId.toKotlinUuid() }
      .singleOrNull()
      ?.get(UsersTable.apiId)
      ?.let(::PublicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)

  private fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
}
