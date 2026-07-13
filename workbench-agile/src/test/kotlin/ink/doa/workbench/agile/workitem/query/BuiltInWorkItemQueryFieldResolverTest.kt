package ink.doa.workbench.agile.workitem.query

import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class BuiltInWorkItemQueryFieldResolverTest :
  StringSpec({
    "resolves known system fields with type and sortable metadata" {
      val definition = BuiltInWorkItemQueryFieldResolver.resolve(QueryField.System("title"))

      definition.field shouldBe QueryField.System("title")
      definition.type shouldBe WorkItemQueryFieldType.TEXT
      definition.sortable shouldBe true
    }

    "resolves property fields as non-sortable json" {
      val field = QueryField.Property(apiId = "fld_01JABC", code = "storyPoints")
      val definition = BuiltInWorkItemQueryFieldResolver.resolve(field)

      definition.field shouldBe field
      definition.type shouldBe WorkItemQueryFieldType.JSON
      definition.sortable shouldBe false
    }

    "resolves direct child issue type field as single select" {
      val definition =
        BuiltInWorkItemQueryFieldResolver.resolve(QueryField.System("children.issueType"))

      definition.type shouldBe WorkItemQueryFieldType.SINGLE_SELECT
      definition.sortable shouldBe false
    }

    "rejects unknown system fields" {
      shouldThrow<InvalidRequestException> {
          BuiltInWorkItemQueryFieldResolver.resolve(QueryField.System("unknownField"))
        }
        .message shouldBe "Unknown work item query field: unknownField"
    }
  })
