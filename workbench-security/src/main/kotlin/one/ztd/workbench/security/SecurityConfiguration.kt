package one.ztd.workbench.security

import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.util.matcher.RequestMatcher

@Configuration
@ConditionalOnWebApplication
class SecurityConfiguration(private val authenticationFilter: WorkbenchAuthenticationFilter) {
  @Bean
  fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
    http
      .csrf { it.requireCsrfProtectionMatcher(CrossSiteSessionRequestMatcher) }
      .httpBasic { it.disable() }
      .formLogin { it.disable() }
      .logout { it.disable() }
      .exceptionHandling {
        it.authenticationEntryPoint { _, response, _ ->
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
        }
      }
      .authorizeHttpRequests {
        it.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
        it
          .requestMatchers(
            "/api/error",
            "/api/actuator/health",
            "/api/scalar/**",
            "/api/openapi/**",
          )
          .permitAll()
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

internal object CrossSiteSessionRequestMatcher : RequestMatcher {
  private val safeMethods = setOf("GET", "HEAD", "OPTIONS", "TRACE")

  override fun matches(request: HttpServletRequest): Boolean =
    request.method !in safeMethods &&
      request.hasSessionCookie() &&
      request.getHeader("Sec-Fetch-Site").equals("cross-site", ignoreCase = true)

  private fun HttpServletRequest.hasSessionCookie(): Boolean =
    cookies?.any { it.name == WORKBENCH_SESSION_COOKIE_NAME } == true
}
