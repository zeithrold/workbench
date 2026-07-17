package one.ztd.workbench.web.api

import io.micrometer.tracing.Tracer
import io.micrometer.tracing.propagation.Propagator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

@Configuration
@ConditionalOnWebApplication
@ConditionalOnProperty(
  prefix = "workbench.tracing.echo-traceparent",
  name = ["enabled"],
  matchIfMissing = true,
)
@EnableConfigurationProperties(EchoTraceparentProperties::class)
class TraceparentResponseConfiguration {
  @Bean
  fun traceparentResponseFilterRegistration(
    tracer: Tracer,
    propagator: Propagator,
  ): FilterRegistrationBean<TraceparentResponseFilter> =
    FilterRegistrationBean(TraceparentResponseFilter(tracer, propagator)).apply {
      order = Ordered.HIGHEST_PRECEDENCE + 2
    }
}
