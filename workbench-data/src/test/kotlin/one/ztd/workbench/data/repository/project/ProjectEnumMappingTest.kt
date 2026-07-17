package one.ztd.workbench.data.repository.project

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.agile.project.model.NonMemberJoinPolicy
import one.ztd.workbench.agile.project.model.NonMemberVisibility
import one.ztd.workbench.agile.project.model.ProjectStatus

class ProjectEnumMappingTest :
  StringSpec({
    "maps project enums from database values" {
      projectStatusOf("active") shouldBe ProjectStatus.ACTIVE
      projectStatusOf("archived") shouldBe ProjectStatus.ARCHIVED
      projectStatusOf("destroying") shouldBe ProjectStatus.DESTROYING
      nonMemberVisibilityOf("read_only") shouldBe NonMemberVisibility.READ_ONLY
      nonMemberJoinPolicyOf("admin_only") shouldBe NonMemberJoinPolicy.ADMIN_ONLY
    }
  })
