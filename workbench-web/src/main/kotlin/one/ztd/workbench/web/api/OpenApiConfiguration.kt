package one.ztd.workbench.web.api

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.context.InstanceContextSummary
import one.ztd.workbench.web.api.context.InstanceRequestContext
import one.ztd.workbench.web.api.context.ProjectContextSummary
import one.ztd.workbench.web.api.context.ProjectRequestContext
import one.ztd.workbench.web.api.context.RequestContext
import one.ztd.workbench.web.api.context.TenantContextSummary
import one.ztd.workbench.web.api.context.TenantRequestContext
import one.ztd.workbench.web.api.context.UserContextSummary
import one.ztd.workbench.web.workitem.WorkItemOpenApiCustomizer
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {
  init {
    val injectedTypes =
      arrayOf(
        RequestContext::class.java,
        InstanceRequestContext::class.java,
        TenantRequestContext::class.java,
        ProjectRequestContext::class.java,
        AuthenticatedPrincipal::class.java,
        UserContextSummary::class.java,
        TenantContextSummary::class.java,
        ProjectContextSummary::class.java,
        InstanceContextSummary::class.java,
        UserRecord::class.java,
      )
    val config = SpringDocUtils.getConfig()
    injectedTypes.forEach { type ->
      config.addRequestWrapperToIgnore(type)
      config.removeFromSchemaMap(type)
    }
  }

  @Bean fun pathParameterCustomizer(): OpenApiCustomizer = OpenApiPathParameterCustomizer()

  @Bean fun workItemOpenApiCustomizer(): OpenApiCustomizer = WorkItemOpenApiCustomizer()

  @Bean
  fun workbenchOpenApi(): OpenAPI =
    OpenAPI()
      .info(
        Info()
          .title("Workbench API")
          .version("2026-07-17")
          .description(
            "Multi-tenant work management API. Use X-Workbench-API-Version for date-based API versioning. " +
              "Successful responses may include X-Workbench-Warning for non-blocking business risks."
          )
      )
      .components(
        Components()
          .addSecuritySchemes(
            "SessionAuth",
            SecurityScheme()
              .type(SecurityScheme.Type.APIKEY)
              .`in`(SecurityScheme.In.COOKIE)
              .name("WORKBENCH_SESSION"),
          )
          .addSecuritySchemes(
            "BearerAuth",
            SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("opaque"),
          )
      )
}
