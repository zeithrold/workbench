package ink.doa.workbench.data.persistence.postgres

import ink.doa.workbench.data.persistence.postgres.project.ProjectsTable
import ink.doa.workbench.data.persistence.postgres.workitem.IssuesTable
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TablesTest :
  StringSpec({
    "project table maps to Workbench database naming" {
      ProjectsTable.tableName shouldBe "projects"
      IssuesTable.tableName shouldBe "issues"
    }
  })
