package one.ztd.workbench.agile.workitem.richtext

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AttachmentContentUrlTest :
  StringSpec({
    "builds the work item attachment content url" {
      AttachmentContentUrl.build("prj_01", "iss_01", "att_01") shouldBe
        "/api/projects/prj_01/work-items/iss_01/attachments/att_01/content"
    }
  })
