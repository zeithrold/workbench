package ink.doa.workbench.agile.testfixtures

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.project.model.ProjectRecord
import java.util.UUID

object AgileProjectFixtures {
  fun sampleProject(
    tenantId: UUID = UUID.randomUUID(),
    projectId: UUID = UUID.randomUUID(),
    identifier: String = "CORE",
    name: String = "Core Platform",
  ): ProjectRecord =
    ProjectRecord(
      id = projectId,
      apiId = PublicId.new("prj"),
      tenantId = tenantId,
      identifier = identifier,
      name = name,
      description = null,
    )
}
