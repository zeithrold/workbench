package doa.ink.workbench.infrastructure.persistence

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TablesTest :
  StringSpec({
    "project table maps to Workbench database naming" {
      ProjectsTable.tableName shouldBe "projects"
      IssuesTable.tableName shouldBe "issues"
    }
  })
