package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.workitem.query.QueryField
import ink.doa.workbench.core.workitem.query.WorkItemQueryFieldType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class StaticPostgresWorkItemFieldResolverTest :
  StringSpec({
    val resolver =
      StaticPostgresWorkItemFieldResolver(
        mapOf(
          "storyPoints" to WorkItemQueryFieldType.NUMBER,
          "owner" to WorkItemQueryFieldType.USER,
        )
      )

    "resolves known system fields" {
      val field = resolver.resolvePostgresField(QueryField.System("key"))
      field.definition.type shouldBe WorkItemQueryFieldType.TEXT
      field.valueSql shouldContain "i.sequence_no"
    }

    "resolves property fields with configured types" {
      val field =
        resolver.resolvePostgresField(QueryField.Property(apiId = null, code = "storyPoints"))
          as PostgresWorkItemField.Property
      field.definition.type shouldBe WorkItemQueryFieldType.NUMBER
      field.identitySql shouldBe "pd.code = ?"
      field.identityParams shouldBe listOf("storyPoints")
    }

    "defaults unknown property types to json" {
      val field = resolver.resolvePostgresField(QueryField.Property(apiId = null, code = "payload"))
      field.definition.type shouldBe WorkItemQueryFieldType.JSON
    }

    "rejects unknown system fields" {
      shouldThrow<InvalidRequestException> {
        resolver.resolvePostgresField(QueryField.System("unknown"))
      }
    }

    "builds property identity sql from api id and code" {
      val field =
        resolver.resolvePostgresField(QueryField.Property(apiId = "fld_abc", code = "storyPoints"))
          as PostgresWorkItemField.Property
      field.identitySql shouldBe "pd.api_id = ? AND pd.code = ?"
      field.identityParams shouldBe listOf("fld_abc", "storyPoints")
    }
  })
