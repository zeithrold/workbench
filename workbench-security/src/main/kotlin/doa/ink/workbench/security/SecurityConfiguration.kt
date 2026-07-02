package doa.ink.workbench.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfiguration {
  @Bean
  fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
    http
      .csrf { it.disable() }
      .authorizeHttpRequests {
        it.requestMatchers("/actuator/health", "/scalar/**", "/v3/api-docs/**").permitAll()
        it.anyRequest().permitAll()
      }
      .build()
}
