package ink.doa.workbench.agile.sprint

import ink.doa.workbench.agile.project.ProjectOperationalGuard
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.sprint.CreateSprintCloseOperationCommand
import ink.doa.workbench.core.sprint.SprintCloseOperationRepository
import ink.doa.workbench.core.sprint.SprintCloseRetryRequest
import ink.doa.workbench.core.sprint.SprintRepository
import ink.doa.workbench.core.sprint.model.ArchiveSprintCommand
import ink.doa.workbench.core.sprint.model.CloseSprintCommand
import ink.doa.workbench.core.sprint.model.CreateSprintCommand
import ink.doa.workbench.core.sprint.model.DeleteSprintCommand
import ink.doa.workbench.core.sprint.model.SprintRecord
import ink.doa.workbench.core.sprint.model.SprintStatus
import ink.doa.workbench.core.sprint.model.StartSprintCommand
import ink.doa.workbench.core.sprint.model.UpdateSprintCommand
import ink.doa.workbench.core.workitem.WorkItemRepository
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

data class SprintView(
  val id: String,
  val name: String,
  val goal: String?,
  val status: SprintStatus,
  val startAt: OffsetDateTime?,
  val endAt: OffsetDateTime?,
  val closedAt: OffsetDateTime?,
  val createdBy: UserSummary?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

@Service
class SprintService(
  private val sprints: SprintRepository,
  private val users: UserRepository,
  private val projectOperationalGuard: ProjectOperationalGuard,
  private val closeOperations: SprintCloseOperationRepository,
  private val workItems: WorkItemRepository,
  private val clock: Clock,
) {
  suspend fun list(
    tenantId: UUID,
    projectId: UUID,
    status: SprintStatus?,
  ): List<SprintView> = sprints.list(tenantId, projectId, status).map { assemble(it) }

  suspend fun get(tenantId: UUID, projectId: UUID, sprintApiId: String): SprintView =
    assemble(sprints.requireSprint(tenantId, projectId, sprintApiId))

  suspend fun create(command: CreateSprintCommand): SprintView {
    projectOperationalGuard.ensureWritable(command.tenantId, command.projectId)
    validateDateRange(command.startAt, command.endAt)
    return assemble(sprints.create(command))
  }

  suspend fun update(command: UpdateSprintCommand): SprintView {
    projectOperationalGuard.ensureWritable(command.tenantId, command.projectId)
    val existing = sprints.requireSprint(command.tenantId, command.projectId, command.sprintApiId)
    validateUpdate(existing, command)
    val nextStartAt = command.startAt ?: existing.startAt
    val nextEndAt = command.endAt ?: existing.endAt
    validateDateRange(nextStartAt, nextEndAt)
    return assemble(sprints.update(command))
  }

  suspend fun start(command: StartSprintCommand): SprintView {
    projectOperationalGuard.ensureWritable(command.tenantId, command.projectId)
    val existing = sprints.requireSprint(command.tenantId, command.projectId, command.sprintApiId)
    if (existing.status != SprintStatus.PLANNED) {
      throw InvalidRequestException(WorkbenchErrorCode.SPRINT_STATUS_INVALID_TRANSITION)
    }
    if (
      sprints.countActiveByProject(
        command.tenantId,
        command.projectId,
        excludingSprintId = existing.id,
      ) > 0
    ) {
      throw InvalidRequestException(WorkbenchErrorCode.SPRINT_ACTIVE_CONFLICT)
    }
    val startAt = existing.startAt ?: OffsetDateTime.now(clock)
    validateDateRange(startAt, existing.endAt)
    return assemble(
      sprints.markActive(
        tenantId = command.tenantId,
        projectId = command.projectId,
        sprintApiId = command.sprintApiId,
        startAt = startAt,
        actorUserId = command.actorUserId,
      )
    )
  }

  suspend fun close(command: CloseSprintCommand): SprintCloseOperationView {
    projectOperationalGuard.ensureWritable(command.tenantId, command.projectId)
    val existing = sprints.requireSprint(command.tenantId, command.projectId, command.sprintApiId)
    command.idempotencyKey?.let { key ->
      closeOperations
        .findByIdempotencyKey(command.tenantId, command.projectId, existing.id, key)
        ?.let {
          return SprintCloseOperationView.from(it)
        }
    }
    validateCloseable(existing)
    val target = sprints.resolveCloseTarget(command)
    validateCloseTarget(command, existing, target)
    val operation =
      closeOperations.createAndMarkClosing(
        CreateSprintCloseOperationCommand(
          tenantId = command.tenantId,
          projectId = command.projectId,
          sprintId = existing.id,
          sprintApiId = existing.apiId.value,
          targetSprintId = target?.id,
          targetSprintApiId = target?.apiId?.value,
          disposition = command.disposition,
          requestedBy = command.actorUserId,
          idempotencyKey = command.idempotencyKey,
          createdAt = OffsetDateTime.now(clock),
        )
      )
    val totalItems =
      workItems.countUnfinishedBySprint(command.tenantId, command.projectId, existing.id).toInt()
    closeOperations.setTotalItems(
      operation.id,
      totalItems,
    )
    return SprintCloseOperationView.from(operation.copy(totalItems = totalItems))
  }

  suspend fun closeOperation(
    tenantId: UUID,
    projectId: UUID,
    sprintApiId: String,
    operationApiId: String,
  ): SprintCloseOperationView =
    closeOperations
      .findByApiId(tenantId, projectId, sprintApiId, operationApiId)
      ?.let(SprintCloseOperationView::from)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.SPRINT_CLOSE_OPERATION_NOT_FOUND)

  suspend fun retryCloseOperation(
    tenantId: UUID,
    projectId: UUID,
    sprintApiId: String,
    operationApiId: String,
  ): SprintCloseOperationView {
    projectOperationalGuard.ensureWritable(tenantId, projectId)
    val operation =
      closeOperations.findByApiId(tenantId, projectId, sprintApiId, operationApiId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.SPRINT_CLOSE_OPERATION_NOT_FOUND)
    if (operation.status != ink.doa.workbench.core.sprint.model.SprintCloseOperationStatus.FAILED) {
      throw InvalidRequestException(WorkbenchErrorCode.SPRINT_CLOSE_OPERATION_CONFLICT)
    }
    val retried =
      closeOperations.retryAndEnqueue(
        SprintCloseRetryRequest(
          tenantId = tenantId,
          projectId = projectId,
          sprintApiId = sprintApiId,
          operationApiId = operationApiId,
          payload =
            ink.doa.workbench.core.sprint.events.SprintCloseRequestedEvent(
              tenantId = tenantId.toString(),
              projectId = projectId.toString(),
              sprintId = sprintApiId,
              operationId = operation.apiId.value,
              requestedBy = operation.requestedBy.toString(),
            ),
          metadataTenantId = tenantId.toString(),
        )
      )
    return SprintCloseOperationView.from(retried)
  }

  suspend fun archive(command: ArchiveSprintCommand): SprintView {
    projectOperationalGuard.ensureWritable(command.tenantId, command.projectId)
    val sprint = sprints.requireSprint(command.tenantId, command.projectId, command.sprintApiId)
    if (sprint.status == SprintStatus.CLOSING) {
      throw InvalidRequestException(WorkbenchErrorCode.SPRINT_CLOSING)
    }
    return assemble(sprints.markArchived(command))
  }

  suspend fun delete(command: DeleteSprintCommand) {
    projectOperationalGuard.ensureWritable(command.tenantId, command.projectId)
    val sprint = sprints.requireSprint(command.tenantId, command.projectId, command.sprintApiId)
    if (sprint.status == SprintStatus.CLOSING) {
      throw InvalidRequestException(WorkbenchErrorCode.SPRINT_CLOSING)
    }
    sprints.softDelete(command)
  }

  private suspend fun assemble(record: SprintRecord): SprintView {
    val creator =
      record.createdBy?.let { userId ->
        users.findById(userId)?.let(UserSummary::from)
      }
    return SprintView(
      id = record.apiId.value,
      name = record.name,
      goal = record.goal,
      status = record.status,
      startAt = record.startAt,
      endAt = record.endAt,
      closedAt = record.closedAt,
      createdBy = creator,
      createdAt = record.createdAt,
      updatedAt = record.updatedAt,
    )
  }
}

private suspend fun SprintRepository.requireSprint(
  tenantId: UUID,
  projectId: UUID,
  sprintApiId: String,
): SprintRecord =
  findByApiId(tenantId, projectId, sprintApiId)
    ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_SPRINT_NOT_FOUND)

private fun validateDateRange(startAt: OffsetDateTime?, endAt: OffsetDateTime?) {
  if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
    throw InvalidRequestException(WorkbenchErrorCode.SPRINT_DATE_RANGE_INVALID)
  }
}

private fun validateUpdate(existing: SprintRecord, command: UpdateSprintCommand) {
  if (existing.status == SprintStatus.CLOSING) {
    throw InvalidRequestException(WorkbenchErrorCode.SPRINT_CLOSING)
  }
  val changesClosedFields = command.name != null || command.startAt != null || command.endAt != null
  val changesLockedStart = existing.status == SprintStatus.ACTIVE && command.startAt != null
  if ((existing.status == SprintStatus.CLOSED && changesClosedFields) || changesLockedStart) {
    throw InvalidRequestException(WorkbenchErrorCode.SPRINT_CLOSED_IMMUTABLE)
  }
}

private fun validateCloseable(existing: SprintRecord) {
  if (existing.status == SprintStatus.CLOSING) {
    throw InvalidRequestException(WorkbenchErrorCode.SPRINT_CLOSE_OPERATION_CONFLICT)
  }
  if (existing.status != SprintStatus.ACTIVE) {
    throw InvalidRequestException(WorkbenchErrorCode.SPRINT_STATUS_INVALID_TRANSITION)
  }
}

private suspend fun SprintRepository.resolveCloseTarget(
  command: CloseSprintCommand
): SprintRecord? =
  command.targetSprintApiId?.let { targetApiId ->
    findByApiId(command.tenantId, command.projectId, targetApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_SPRINT_NOT_FOUND)
  }

private fun validateCloseTarget(
  command: CloseSprintCommand,
  existing: SprintRecord,
  target: SprintRecord?,
) {
  val expectsTarget =
    command.disposition == ink.doa.workbench.core.sprint.model.SprintCloseDisposition.NEXT_SPRINT
  if (expectsTarget && target == null) {
    throw InvalidRequestException(WorkbenchErrorCode.SPRINT_CLOSE_TARGET_REQUIRED)
  }
  val targetInvalid =
    (!expectsTarget && target != null) ||
      target?.status == SprintStatus.CLOSED ||
      target?.status == SprintStatus.CLOSING ||
      target?.id == existing.id
  if (targetInvalid) {
    throw InvalidRequestException(WorkbenchErrorCode.SPRINT_CLOSE_TARGET_INVALID)
  }
}

data class SprintCloseOperationView(
  val id: String,
  val sprintId: String,
  val targetSprintId: String?,
  val disposition: String,
  val status: String,
  val totalItems: Int,
  val processedItems: Int,
  val failedItems: Int,
  val lastError: String?,
  val createdAt: OffsetDateTime,
  val startedAt: OffsetDateTime?,
  val completedAt: OffsetDateTime?,
) {
  companion object {
    fun from(record: ink.doa.workbench.core.sprint.model.SprintCloseOperationRecord) =
      SprintCloseOperationView(
        id = record.apiId.value,
        sprintId = record.sprintApiId.value,
        targetSprintId = record.targetSprintApiId?.value,
        disposition = record.disposition.name,
        status = record.status.name,
        totalItems = record.totalItems,
        processedItems = record.processedItems,
        failedItems = record.failedItems,
        lastError = record.lastError,
        createdAt = record.createdAt,
        startedAt = record.startedAt,
        completedAt = record.completedAt,
      )
  }
}
