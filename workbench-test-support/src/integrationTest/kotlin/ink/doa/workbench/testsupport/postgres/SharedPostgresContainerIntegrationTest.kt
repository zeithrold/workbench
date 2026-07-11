package ink.doa.workbench.testsupport.postgres

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.sql.DriverManager

class SharedPostgresContainerIntegrationTest :
  StringSpec({
    "openDatabase clones schema from template and drops lease on close" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Core).use { lease ->
        queryGreetingCount(lease.jdbcUrl) shouldBe 1
      }
    }

    "each lease gets an isolated database" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Core).use { first ->
        WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Core).use { second ->
          first.jdbcUrl shouldNotBe second.jdbcUrl
          executeUpdate(first.jdbcUrl, "UPDATE greeting SET message = 'first'")
          executeUpdate(second.jdbcUrl, "UPDATE greeting SET message = 'second'")
          queryGreetingMessage(first.jdbcUrl) shouldBe "first"
          queryGreetingMessage(second.jdbcUrl) shouldBe "second"
        }
      }
    }

    "moduleDatabase returns a stable lease for the JVM" {
      val first = WorkbenchPostgresTestSupport.moduleDatabase(MigrationSpec.Core)
      val second = WorkbenchPostgresTestSupport.moduleDatabase(MigrationSpec.Core)
      first.jdbcUrl shouldBe second.jdbcUrl
      queryGreetingCount(first.jdbcUrl) shouldBe 1
    }
  })

private fun queryGreetingCount(jdbcUrl: String): Int =
  DriverManager.getConnection(
      jdbcUrl,
      SharedPostgresContainer.username,
      SharedPostgresContainer.password,
    )
    .use { connection ->
      connection.createStatement().use { statement ->
        statement.executeQuery("SELECT COUNT(*) FROM greeting").use { result ->
          result.next()
          result.getInt(1)
        }
      }
    }

private fun queryGreetingMessage(jdbcUrl: String): String =
  DriverManager.getConnection(
      jdbcUrl,
      SharedPostgresContainer.username,
      SharedPostgresContainer.password,
    )
    .use { connection ->
      connection.createStatement().use { statement ->
        statement.executeQuery("SELECT message FROM greeting LIMIT 1").use { result ->
          result.next()
          result.getString(1)
        }
      }
    }

private fun executeUpdate(jdbcUrl: String, sql: String) {
  DriverManager.getConnection(
      jdbcUrl,
      SharedPostgresContainer.username,
      SharedPostgresContainer.password,
    )
    .use { connection ->
      connection.createStatement().use { statement ->
        statement.executeUpdate(sql)
      }
    }
}
