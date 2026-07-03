package doa.ink.workbench.agile.project

import doa.ink.workbench.core.project.ProjectDestructionRepository
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.core.project.model.ProjectStatus
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ProjectDestructionService(
  private val projects: ProjectRepository,
  private val destruction: ProjectDestructionRepository,
  private val clock: Clock,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Suppress("ReturnCount")
  suspend fun execute(
    tenantId: UUID,
    projectId: UUID,
    deletedBy: UUID,
    deleteReason: String?,
  ): Boolean {
    val project =
      projects.findById(tenantId, projectId)
        ?: run {
          logger.warn("project_destroy_skipped projectId={} reason=not_found", projectId)
          return false
        }
    if (project.status != ProjectStatus.DESTROYING) {
      logger.warn(
        "project_destroy_skipped projectId={} reason=invalid_status status={}",
        project.apiId.value,
        project.status,
      )
      return false
    }

    val now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
    logger.info("project_destroy_started projectId={}", project.apiId.value)

    destruction.expireBindingsByProject(tenantId, projectId, now)
    destruction.softDeleteProjectScopedData(
      tenantId = tenantId,
      projectId = projectId,
      deletedAt = now,
      deletedBy = deletedBy,
      deleteReason = deleteReason,
    )

    val finalized =
      projects.finalizeDestroy(
        tenantId = tenantId,
        projectId = projectId,
        deletedAt = now,
        deletedBy = deletedBy,
        deleteReason = deleteReason,
      )
    if (finalized) {
      logger.info("project_destroy_completed projectId={}", project.apiId.value)
      return true
    }
    logger.warn(
      "project_destroy_finalize_skipped projectId={} reason=already_deleted",
      project.apiId.value,
    )
    return false
  }
}
