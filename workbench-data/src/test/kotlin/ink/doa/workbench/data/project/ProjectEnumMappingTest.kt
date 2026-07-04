package ink.doa.workbench.data.project

import ink.doa.workbench.core.project.model.NonMemberJoinPolicy
import ink.doa.workbench.core.project.model.NonMemberVisibility
import ink.doa.workbench.core.project.model.ProjectStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

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
