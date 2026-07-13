package ink.doa.workbench.data.persistence.postgres.workitem.query

import ink.doa.workbench.agile.workitem.query.QueryField
import ink.doa.workbench.agile.workitem.query.WorkItemQueryFieldType
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
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

    "resolves additional system fields" {
      val assignee =
        resolver.resolvePostgresField(QueryField.System("assignee")) as PostgresWorkItemField.System
      assignee.definition.type shouldBe WorkItemQueryFieldType.USER
      assignee.valueSql shouldContain "assignee.api_id"

      val title =
        resolver.resolvePostgresField(QueryField.System("title")) as PostgresWorkItemField.System
      title.definition.sortable shouldBe true
    }

    "resolves property fields with all configured data types" {
      val resolverWithTypes =
        StaticPostgresWorkItemFieldResolver(
          mapOf(
            "owner" to WorkItemQueryFieldType.USER,
            "linkedProject" to WorkItemQueryFieldType.PROJECT,
            "linkedIssue" to WorkItemQueryFieldType.ISSUE,
            "dueDate" to WorkItemQueryFieldType.DATE,
          )
        )
      (resolverWithTypes.resolvePostgresField(QueryField.Property(apiId = null, code = "owner"))
          as PostgresWorkItemField.Property)
        .valueSql shouldContain "user_value.api_id"
      (resolverWithTypes.resolvePostgresField(
          QueryField.Property(apiId = null, code = "linkedProject")
        ) as PostgresWorkItemField.Property)
        .valueSql shouldContain "project_value.api_id"
      (resolverWithTypes.resolvePostgresField(
          QueryField.Property(apiId = null, code = "linkedIssue")
        ) as PostgresWorkItemField.Property)
        .valueSql shouldContain "issue_value.api_id"
      (resolverWithTypes.resolvePostgresField(QueryField.Property(apiId = null, code = "dueDate"))
          as PostgresWorkItemField.Property)
        .valueSql shouldContain "ipv.value_date"
    }
  })
