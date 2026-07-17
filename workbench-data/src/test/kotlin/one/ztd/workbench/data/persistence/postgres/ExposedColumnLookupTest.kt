package one.ztd.workbench.data.persistence.postgres

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.uuid.Uuid
import one.ztd.workbench.data.persistence.postgres.project.ProjectsTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueTypesTable

class ExposedColumnLookupTest :
  StringSpec({
    "requireColumn returns typed column by name" {
      ProjectsTable.requireColumn<String>("api_id").name shouldBe "api_id"
      ProjectsTable.requireColumn<Uuid>("id").name shouldBe "id"
      IssueTypesTable.requireColumn<String>("name").name shouldBe "name"
    }

    "findColumn returns column when present" {
      ProjectsTable.findColumn<String>("identifier")?.name shouldBe "identifier"
    }

    "findColumn returns null when column is absent" {
      ProjectsTable.findColumn<String>("missing_column").shouldBeNull()
    }
  })
