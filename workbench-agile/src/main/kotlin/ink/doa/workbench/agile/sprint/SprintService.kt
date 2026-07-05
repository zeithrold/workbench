package ink.doa.workbench.agile.sprint

import ink.doa.workbench.agile.project.ProjectOperationalGuard
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.sprint.SprintRepository
import ink.doa.workbench.core.sprint.model.ArchiveSprintCommand
import ink.doa.workbench.core.sprint.model.CloseSprintCommand
import ink.doa.workbench.core.sprint.model.CreateSprintCommand
import ink.doa.workbench.core.sprint.model.DeleteSprintCommand
import ink.doa.workbench.core.sprint.model.SprintRecord
import ink.doa.workbench.core.sprint.model.SprintStatus
import ink.doa.workbench.core.sprint.model.StartSprintCommand
import ink.doa.workbench.core.sprint.model.UpdateSprintCommand
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
  private val clock: Clock,
) {
  suspend fun list(
    tenantId: UUID,
    projectId: UUID,
    status: SprintStatus?,
  ): List<SprintView> = sprints.list(tenantId, projectId, status).map { assemble(it) }

  suspend fun get(tenantId: UUID, projectId: UUID, sprintApiId: String): SprintView =
    assemble(requireSprint(tenantId, projectId, sprintApiId))

  suspend fun create(command: CreateSprintCommand): SprintView {
    projectOperationalGuard.ensureWritable(command.tenantId, command.projectId)
    validateDateRange(command.startAt, command.endAt)
    return assemble(sprints.create(command))
  }

  suspend fun update(command: UpdateSprintCommand): SprintView {
    projectOperationalGuard.ensureWritable(command.tenantId, command.projectId)
    val existing = requireSprint(command.tenantId, command.projectId, command.sprintApiId)
    when (existing.status) {
      SprintStatus.CLOSED -> {
        if (command.name != null || command.startAt != null || command.endAt != null) {
          throw InvalidRequestException(WorkbenchErrorCode.SPRINT_CLOSED_IMMUTABLE)
        }
      }
      SprintStatus.ACTIVE -> {
        if (command.startAt != null) {
          throw InvalidRequestException(WorkbenchErrorCode.SPRINT_CLOSED_IMMUTABLE)
        }
      }
      SprintStatus.PLANNED -> Unit
    }
    val nextStartAt = command.startAt ?: existing.startAt
    val nextEndAt = command.endAt ?: existing.endAt
    validateDateRange(nextStartAt, nextEndAt)
    return assemble(sprints.update(command))
  }

  suspend fun start(command: StartSprintCommand): SprintView {
    projectOperationalGuard.ensureWritable(command.tenantId, command.projectId)
    val existing = requireSprint(command.tenantId, command.projectId, command.sprintApiId)
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

  suspend fun close(command: CloseSprintCommand): SprintView {
    projectOperationalGuard.ensureWritable(command.tenantId, command.projectId)
    val existing = requireSprint(command.tenantId, command.projectId, command.sprintApiId)
    if (existing.status != SprintStatus.ACTIVE) {
      throw InvalidRequestException(WorkbenchErrorCode.SPRINT_STATUS_INVALID_TRANSITION)
    }
    return assemble(
      sprints.markClosed(
        tenantId = command.tenantId,
        projectId = command.projectId,
        sprintApiId = command.sprintApiId,
        closedAt = OffsetDateTime.now(clock),
        actorUserId = command.actorUserId,
      )
    )
  }

  suspend fun archive(command: ArchiveSprintCommand): SprintView {
    projectOperationalGuard.ensureWritable(command.tenantId, command.projectId)
    requireSprint(command.tenantId, command.projectId, command.sprintApiId)
    return assemble(sprints.markArchived(command))
  }

  suspend fun delete(command: DeleteSprintCommand) {
    projectOperationalGuard.ensureWritable(command.tenantId, command.projectId)
    requireSprint(command.tenantId, command.projectId, command.sprintApiId)
    sprints.softDelete(command)
  }

  private suspend fun requireSprint(
    tenantId: UUID,
    projectId: UUID,
    sprintApiId: String,
  ): SprintRecord =
    sprints.findByApiId(tenantId, projectId, sprintApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_SPRINT_NOT_FOUND)

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

  private fun validateDateRange(startAt: OffsetDateTime?, endAt: OffsetDateTime?) {
    if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
      throw InvalidRequestException(WorkbenchErrorCode.SPRINT_DATE_RANGE_INVALID)
    }
  }
}
