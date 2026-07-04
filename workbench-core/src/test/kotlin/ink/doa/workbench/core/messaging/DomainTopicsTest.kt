package ink.doa.workbench.core.messaging

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DomainTopicsTest :
  StringSpec({
    "domain topics use workbench namespace" {
      DomainTopics.TENANT shouldBe "workbench.tenant"
      DomainTopics.PROJECT shouldBe "workbench.project"
      DomainTopics.IDENTITY shouldBe "workbench.identity"
      DomainTopics.WORK_ITEM shouldBe "workbench.work_item"
    }
  })
