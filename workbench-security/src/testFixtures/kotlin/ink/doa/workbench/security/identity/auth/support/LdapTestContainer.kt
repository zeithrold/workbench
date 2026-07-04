package ink.doa.workbench.security.identity.auth.support

import javax.naming.Context
import javax.naming.directory.InitialDirContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

object LdapTestContainer {
  const val BASE_DN = "ou=users,dc=example,dc=org"
  const val ADMIN_DN = "cn=admin,dc=example,dc=org"
  const val ADMIN_PASSWORD = "adminpassword"
  const val TEST_USER = "testuser"
  const val TEST_PASSWORD = "testpass"

  fun create(): GenericContainer<*> =
    GenericContainer(DockerImageName.parse("osixia/openldap:1.5.0"))
      .withEnv("LDAP_ORGANISATION", "Example Inc")
      .withEnv("LDAP_DOMAIN", "example.org")
      .withEnv("LDAP_ADMIN_PASSWORD", ADMIN_PASSWORD)
      .withExposedPorts(389)
      .waitingFor(Wait.forListeningPort())

  fun startAndBootstrap(container: GenericContainer<*>) {
    container.start()
    container.copyFileToContainer(
      MountableFile.forClasspathResource("ldap/test-users.ldif"),
      "/tmp/test-users.ldif",
    )
    val result =
      container.execInContainer(
        "ldapadd",
        "-x",
        "-D",
        ADMIN_DN,
        "-w",
        ADMIN_PASSWORD,
        "-f",
        "/tmp/test-users.ldif",
      )
    check(result.exitCode == 0) {
      "Failed to bootstrap LDAP test users: ${result.stdout}${result.stderr}"
    }
    waitForExternalBind(container)
  }

  private fun waitForExternalBind(container: GenericContainer<*>) {
    val host = ldapHost(container)
    val port = ldapPort(container)
    val userDn = "uid=$TEST_USER,$BASE_DN"
    val deadline = System.currentTimeMillis() + 15_000
    var attempts = 0
    var lastError: String? = null
    while (System.currentTimeMillis() < deadline) {
      attempts++
      try {
        val env =
          mapOf(
            Context.INITIAL_CONTEXT_FACTORY to "com.sun.jndi.ldap.LdapCtxFactory",
            Context.PROVIDER_URL to "ldap://$host:$port",
            Context.SECURITY_AUTHENTICATION to "simple",
            Context.SECURITY_PRINCIPAL to userDn,
            Context.SECURITY_CREDENTIALS to TEST_PASSWORD,
          )
        InitialDirContext(env.toProperties()).close()
        return
      } catch (exception: Exception) {
        lastError = "${exception.javaClass.simpleName}: ${exception.message}"
        Thread.sleep(100)
      }
    }
    error("LDAP external bind not ready after $attempts attempts: $lastError")
  }

  fun ldapHost(container: GenericContainer<*>): String = container.host

  fun ldapPort(container: GenericContainer<*>): Int = container.getMappedPort(389)
}
