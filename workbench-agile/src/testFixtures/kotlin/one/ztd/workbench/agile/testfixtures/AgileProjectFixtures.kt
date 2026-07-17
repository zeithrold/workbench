package one.ztd.workbench.agile.testfixtures

import java.util.UUID
import one.ztd.workbench.agile.project.model.ProjectRecord
import one.ztd.workbench.kernel.common.ids.PublicId

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
