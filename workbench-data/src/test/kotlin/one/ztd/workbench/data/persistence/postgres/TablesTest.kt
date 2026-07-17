package one.ztd.workbench.data.persistence.postgres

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.data.persistence.postgres.project.ProjectsTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssuesTable

class TablesTest :
  StringSpec({
    "project table maps to Workbench database naming" {
      ProjectsTable.tableName shouldBe "projects"
      IssuesTable.tableName shouldBe "issues"
    }
  })
