package one.ztd.workbench.security.identity.auth.support

import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldif.LDIFReader

class InMemoryLdapTestServer private constructor(private val server: InMemoryDirectoryServer) :
  AutoCloseable {
  val host: String = "localhost"
  val port: Int
    get() = server.listenPort

  override fun close() {
    server.shutDown(true)
  }

  companion object {
    const val BASE_DN = "ou=users,dc=example,dc=org"
    const val DOMAIN_DN = "dc=example,dc=org"
    const val TEST_USER = "testuser"
    const val TEST_PASSWORD = "testpass"
    const val UNLINKED_USER = "unlinkeduser"
    const val UNLINKED_PASSWORD = "unlinkedpass"

    fun start(): InMemoryLdapTestServer {
      val config = InMemoryDirectoryServerConfig(DOMAIN_DN).apply { schema = null }
      val server = InMemoryDirectoryServer(config)
      val ldifStream =
        checkNotNull(javaClass.classLoader.getResourceAsStream("ldap/test-users.ldif")) {
          "Missing classpath resource ldap/test-users.ldif"
        }
      ldifStream.use { stream ->
        server.importFromLDIF(true, LDIFReader(stream))
      }
      server.startListening()
      return InMemoryLdapTestServer(server)
    }
  }
}
