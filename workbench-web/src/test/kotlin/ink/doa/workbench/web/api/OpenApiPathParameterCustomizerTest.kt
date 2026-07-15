package ink.doa.workbench.web.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter

class OpenApiPathParameterCustomizerTest :
  StringSpec({
    "adds missing path parameters in template order" {
      val operation = Operation()
      val openApi = openApi("/api/projects/{id}/work-items/{workItemId}", operation)

      OpenApiPathParameterCustomizer().customise(openApi)

      operation.parameters.map(Parameter::getName) shouldContainExactly listOf("id", "workItemId")
      operation.parameters.forEach { parameter ->
        parameter.`in` shouldBe "path"
        parameter.required shouldBe true
        parameter.schema.type shouldBe "string"
      }
    }

    "preserves explicitly documented path parameters" {
      val explicitSchema = StringSchema().format("uuid")
      val explicit =
        Parameter()
          .name("id")
          .`in`("path")
          .required(true)
          .description("Explicit project identifier")
          .schema(explicitSchema)
      val operation = Operation().addParametersItem(explicit)
      val openApi = openApi("/api/projects/{id}", operation)

      OpenApiPathParameterCustomizer().customise(openApi)

      operation.parameters shouldContainExactly listOf(explicit)
      operation.parameters.single().description shouldBe "Explicit project identifier"
      operation.parameters.single().schema shouldBe explicitSchema
    }

    "recognizes shared path item parameters" {
      val shared = Parameter().name("id").`in`("path").required(true).schema(StringSchema())
      val operation = Operation()
      val pathItem = PathItem().addParametersItem(shared).get(operation)
      val openApi = OpenAPI().path("/api/projects/{id}", pathItem)

      OpenApiPathParameterCustomizer().customise(openApi)

      pathItem.parameters shouldContainExactly listOf(shared)
      operation.parameters.orEmpty() shouldContainExactly emptyList()
    }

    "does not treat query parameters as matching path parameters" {
      val query = Parameter().name("id").`in`("query").schema(StringSchema())
      val operation = Operation().addParametersItem(query)
      val openApi = openApi("/api/projects/{id}", operation)

      OpenApiPathParameterCustomizer().customise(openApi)

      operation.parameters.map { it.name to it.`in` } shouldContainExactly
        listOf("id" to "query", "id" to "path")
    }

    "leaves operations without path templates unchanged" {
      val operation = Operation()
      val openApi = openApi("/api/projects", operation)

      OpenApiPathParameterCustomizer().customise(openApi)

      operation.parameters.orEmpty() shouldContainExactly emptyList()
    }
  })

private fun openApi(path: String, operation: Operation): OpenAPI =
  OpenAPI().path(path, PathItem().get(operation))
