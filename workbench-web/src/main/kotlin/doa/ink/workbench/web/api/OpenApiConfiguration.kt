package doa.ink.workbench.web.api

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {
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
