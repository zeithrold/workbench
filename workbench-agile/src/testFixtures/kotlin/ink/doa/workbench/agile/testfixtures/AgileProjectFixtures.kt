package ink.doa.workbench.agile.testfixtures

import ink.doa.workbench.agile.project.model.ProjectRecord
import ink.doa.workbench.kernel.common.ids.PublicId
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
