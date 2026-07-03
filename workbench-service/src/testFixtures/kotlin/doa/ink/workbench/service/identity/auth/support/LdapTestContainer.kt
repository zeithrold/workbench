package doa.ink.workbench.service.identity.auth.support

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
  }

  fun ldapHost(container: GenericContainer<*>): String = container.host

  fun ldapPort(container: GenericContainer<*>): Int = container.getMappedPort(389)
}
