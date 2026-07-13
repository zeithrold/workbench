package ink.doa.workbench.agile.workitem.query

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class WorkItemGroupLabelCodeTest :
  StringSpec({
    "toLabel builds message with code args and defaultMessage" {
      WorkItemGroupLabelCode.EMPTY_PROPERTY_OPTION.toLabel(
        mapOf("propertyName" to "Severity")
      ) shouldBe
        WorkItemGroupLabel.Message(
          code = "work_item.group.empty.property_option",
          args = mapOf("propertyName" to "Severity"),
          defaultMessage = "No option",
        )
    }
  })
