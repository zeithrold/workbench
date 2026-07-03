package doa.ink.workbench.security

import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfiguration(private val authenticationFilter: WorkbenchAuthenticationFilter) {
  @Bean
  fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
    http
      .csrf { it.disable() }
      .httpBasic { it.disable() }
      .formLogin { it.disable() }
      .logout { it.disable() }
      .exceptionHandling {
        it.authenticationEntryPoint { _, response, _ ->
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
        }
      }
      .authorizeHttpRequests {
        it.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
        it.requestMatchers("/actuator/health", "/scalar/**", "/v3/api-docs/**").permitAll()
        it
          .requestMatchers(
            "/api/auth/login",
            "/api/auth/login-options",
            "/api/auth/login-discovery",
            "/api/auth/federated/**",
            "/api/auth/oauth2/callback",
            "/api/auth/saml2/**",
            "/api/auth/magic-link/**",
            "/api/instance/setup-status",
            "/api/instance/setup",
            "/api/invitations/**",
          )
          .permitAll()
        it.anyRequest().authenticated()
      }
      .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
      .build()
}
