package ink.doa.workbench.web.workitem

import ink.doa.workbench.core.workitem.query.WorkItemGroupLabel
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class WorkItemGroupLabelResponseTest :
  StringSpec({
    "from maps text label" {
      val response = WorkItemGroupLabelResponse.from(WorkItemGroupLabel.Text("High"))
      response.shouldBeInstanceOf<WorkItemGroupLabelTextResponse>()
      response.kind shouldBe "text"
      response.text shouldBe "High"
    }

    "from maps message label" {
      val response =
        WorkItemGroupLabelResponse.from(
          WorkItemGroupLabel.Message(
            code = "work_item.group.empty.assignee",
            defaultMessage = "Unassigned",
          )
        )
      response.shouldBeInstanceOf<WorkItemGroupLabelMessageResponse>()
      response.kind shouldBe "message"
      response.code shouldBe "work_item.group.empty.assignee"
      response.defaultMessage shouldBe "Unassigned"
    }
  })
