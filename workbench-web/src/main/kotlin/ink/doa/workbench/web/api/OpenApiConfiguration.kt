package ink.doa.workbench.web.api

import ink.doa.workbench.core.common.context.InstanceContextSummary
import ink.doa.workbench.core.common.context.InstanceRequestContext
import ink.doa.workbench.core.common.context.ProjectContextSummary
import ink.doa.workbench.core.common.context.ProjectRequestContext
import ink.doa.workbench.core.common.context.RequestContext
import ink.doa.workbench.core.common.context.TenantContextSummary
import ink.doa.workbench.core.common.context.TenantRequestContext
import ink.doa.workbench.core.common.context.UserContextSummary
import ink.doa.workbench.core.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.core.identity.model.UserRecord
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
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
    SpringDocUtils.getConfig()
      .addRequestWrapperToIgnore(*injectedTypes)
      .removeFromSchemaMap(*injectedTypes)
  }

  @Bean
  fun workbenchOpenApi(): OpenAPI =
    OpenAPI()
      .info(
        Info()
          .title("Workbench API")
          .version("2026-07-03")
          .description(
            "Multi-tenant work management API. Use X-Workbench-API-Version for date-based API versioning."
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
