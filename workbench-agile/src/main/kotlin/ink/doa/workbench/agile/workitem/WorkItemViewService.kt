package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.summary.ProjectSummary
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.project.ProjectRepository
import ink.doa.workbench.core.workitem.view.CreateWorkItemViewCommand
import ink.doa.workbench.core.workitem.view.DeleteWorkItemViewCommand
import ink.doa.workbench.core.workitem.view.UpdateWorkItemViewCommand
import ink.doa.workbench.core.workitem.view.WorkItemViewConfigurationValidator
import ink.doa.workbench.core.workitem.view.WorkItemViewRecord
import ink.doa.workbench.core.workitem.view.WorkItemViewRepository
import ink.doa.workbench.core.workitem.view.WorkItemViewVisibility
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import org.springframework.stereotype.Service

data class WorkItemViewView(
  val id: String,
  val name: String,
  val description: String?,
  val visibility: WorkItemViewVisibility,
  val owner: UserSummary,
  val project: ProjectSummary?,
  val query: JsonElement,
  val displayFields: JsonElement,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

@Service
class WorkItemViewService(
  private val views: WorkItemViewRepository,
  private val access: WorkItemViewAccessService,
  private val users: UserRepository,
  private val projects: ProjectRepository,
  private val validator: WorkItemViewConfigurationValidator = WorkItemViewConfigurationValidator(),
) {
  suspend fun list(
    tenantId: UUID,
    projectId: UUID?,
    actorUserId: UUID,
  ): List<WorkItemViewView> {
    val records =
      if (projectId == null) views.listTenantScoped(tenantId)
      else views.listByProject(tenantId, projectId)
    return records.filter { access.canRead(it, actorUserId) }.map { assemble(it) }
  }

  suspend fun get(
    tenantId: UUID,
    projectId: UUID?,
    viewApiId: String,
    actorUserId: UUID,
  ): WorkItemViewView {
    val view = requireView(tenantId, viewApiId, projectId)
    access.requireRead(view, actorUserId)
    return assemble(view)
  }

  suspend fun create(command: CreateWorkItemViewCommand): WorkItemViewView {
    access.requireCreate(command.tenantId, command.projectId, command.ownerId)
    validateLayout(command)
    return assemble(views.create(command))
  }

  suspend fun update(command: UpdateWorkItemViewCommand): WorkItemViewView {
    val existing = requireView(command.tenantId, command.viewApiId, command.projectId)
    access.requireManage(existing, command.actorUserId)
    val nextVisibility = command.visibility ?: existing.visibility
    validator.validateVisibility(command.projectId, nextVisibility)
    validator.validateLayout(
      queryAst = command.queryAst ?: existing.queryAst,
      displayFields = command.displayFields ?: existing.displayFields,
    )
    return assemble(views.update(command))
  }

  suspend fun delete(command: DeleteWorkItemViewCommand) {
    val view = requireView(command.tenantId, command.viewApiId, command.projectId)
    access.requireManage(view, command.actorUserId)
    views.delete(command)
  }

  private fun validateLayout(command: CreateWorkItemViewCommand) {
    validator.validateVisibility(command.projectId, command.visibility)
    validator.validateLayout(
      queryAst = command.queryAst,
      displayFields = command.displayFields,
    )
  }

  private suspend fun requireView(
    tenantId: UUID,
    viewApiId: String,
    projectId: UUID?,
  ): WorkItemViewRecord =
    views.findByApiId(tenantId, viewApiId, projectId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_VIEW_NOT_FOUND)

  private suspend fun assemble(record: WorkItemViewRecord): WorkItemViewView {
    val owner =
      users.findById(record.ownerId)?.let(UserSummary::from)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)
    val project =
      record.projectId?.let { projectId ->
        projects.findById(record.tenantId, projectId)?.let(ProjectSummary::from)
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
      }
    return WorkItemViewView(
      id = record.apiId.value,
      name = record.name,
      description = record.description,
      visibility = record.visibility,
      owner = owner,
      project = project,
      query = record.queryAst,
      displayFields = record.displayFields,
      createdAt = record.createdAt,
      updatedAt = record.updatedAt,
    )
  }
}
