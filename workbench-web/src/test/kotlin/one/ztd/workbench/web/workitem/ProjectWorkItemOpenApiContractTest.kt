package one.ztd.workbench.web.workitem

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import one.ztd.workbench.web.api.http.WORKBENCH_NEXT_CURSOR_HEADER

class ProjectWorkItemOpenApiContractTest :
  StringSpec({
    "search documents its successful array response and cursor header" {
      val operation = operation("search")
      val success = operation.responses.single { it.responseCode == "200" }

      success.headers.single().name shouldBe WORKBENCH_NEXT_CURSOR_HEADER
      success.content.single().array.schema.implementation shouldBe WorkItemResponse::class
    }

    "group search documents its successful page response" {
      val success = operation("searchGroups").responses.single { it.responseCode == "200" }

      success.content.single().schema.implementation shouldBe
        WorkItemSearchGroupsPageResponse::class
    }

    "work item response marks stable list fields as required" {
      val schema =
        ModelConverters.getInstance()
          .read(WorkItemResponse::class.java)
          .getValue("WorkItemResponse")
      val openApi =
        OpenAPI().components(Components().schemas(mutableMapOf("WorkItemResponse" to schema)))
      WorkItemOpenApiCustomizer().customise(openApi)

      schema.required.containsAll(
        listOf("id", "title", "issueType", "status", "updatedAt")
      ) shouldBe true
      schema.properties.getValue("priority").anyOf.last().types shouldBe setOf("null")
      schema.properties.getValue("assignee").anyOf.last().types shouldBe setOf("null")
      schema.properties.getValue("sprint").anyOf.last().types shouldBe setOf("null")
    }
  })

private fun operation(name: String): Operation =
  ProjectWorkItemController::class
    .java
    .declaredMethods
    .single { it.name == name }
    .getAnnotation(Operation::class.java)
