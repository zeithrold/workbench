package ink.doa.workbench.agile.project.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ProjectEnumsTest :
  StringSpec({
    "project status maps to database values" {
      ProjectStatus.ACTIVE.dbValue shouldBe "active"
      ProjectStatus.DESTROYING.dbValue shouldBe "destroying"
      NonMemberVisibility.READ_ONLY.dbValue shouldBe "read_only"
      NonMemberJoinPolicy.ADMIN_ONLY.dbValue shouldBe "admin_only"
    }
  })
