package one.ztd.workbench.security

import one.ztd.workbench.identity.auth.PasswordHasher
import one.ztd.workbench.identity.auth.PasswordVerifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class AuthenticationSupport {
  @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

  @Bean
  fun passwordVerifier(passwordEncoder: PasswordEncoder): PasswordVerifier =
    object : PasswordVerifier {
      override fun verify(rawPassword: String, passwordHash: String): Boolean =
        passwordEncoder.matches(rawPassword, passwordHash)
    }

  @Bean
  fun passwordHasher(passwordEncoder: PasswordEncoder): PasswordHasher =
    object : PasswordHasher {
      override fun hash(rawPassword: String): String =
        passwordEncoder.encode(rawPassword) ?: error("Password encoding failed.")
    }
}
