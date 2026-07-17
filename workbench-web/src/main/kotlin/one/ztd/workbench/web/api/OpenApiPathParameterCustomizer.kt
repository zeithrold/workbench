package one.ztd.workbench.web.api

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import org.springdoc.core.customizers.OpenApiCustomizer

internal class OpenApiPathParameterCustomizer : OpenApiCustomizer {
  override fun customise(openApi: OpenAPI) {
    openApi.paths.orEmpty().forEach { (path, pathItem) ->
      val templateParameters = PathParameterPattern.findAll(path).map { it.groupValues[1] }.toList()
      if (templateParameters.isEmpty()) return@forEach

      pathItem.readOperations().forEach { operation ->
        addMissingParameters(templateParameters, pathItem, operation)
      }
    }
  }

  private fun addMissingParameters(
    templateParameters: List<String>,
    pathItem: PathItem,
    operation: Operation,
  ) {
    val documentedParameters =
      (pathItem.parameters.orEmpty() + operation.parameters.orEmpty())
        .filter { it.`in` == PathLocation }
        .mapNotNull(Parameter::getName)
        .toSet()

    templateParameters.filterNot(documentedParameters::contains).forEach { name ->
      operation.addParametersItem(
        Parameter().name(name).`in`(PathLocation).required(true).schema(StringSchema())
      )
    }
  }

  private companion object {
    const val PathLocation = "path"
    val PathParameterPattern = Regex("\\{([^{}]+)}")
  }
}
