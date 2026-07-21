package one.ztd.workbench.data.repository.workitem

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.sql.ResultSet
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import one.ztd.workbench.agile.workitem.WorkItemFieldOption
import one.ztd.workbench.agile.workitem.WorkItemFieldOptionKind
import one.ztd.workbench.agile.workitem.WorkItemFieldOptionQuery
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementSetter
import org.springframework.jdbc.core.RowMapper

class JdbcWorkItemFieldOptionRepositoryTest :
  StringSpec({
    "builds and executes a parameterized option query for every supported field kind" {
      val jdbc = mockk<JdbcTemplate>()
      val resultSet = mockk<ResultSet>()
      every { resultSet.getString(any<String>()) } answers { firstArg() }
      every {
        jdbc.query(
          any<String>(),
          any<PreparedStatementSetter>(),
          any<RowMapper<WorkItemFieldOption>>(),
        )
      } answers
        {
          listOf(thirdArg<RowMapper<WorkItemFieldOption>>().mapRow(resultSet, 0))
        }
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()

      Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
        val repository = JdbcWorkItemFieldOptionRepository(jdbc, dispatcher)
        WorkItemFieldOptionKind.entries.forEach { kind ->
          val rows = runBlocking {
            repository.list(
              WorkItemFieldOptionQuery(
                tenantId = tenantId,
                projectId = projectId,
                propertyId = UUID.randomUUID(),
                kind = kind,
                search = "alex",
                offset = 10,
                limit = 20,
              )
            )
          }
          rows shouldHaveSize 1
        }
      }

      verify(exactly = WorkItemFieldOptionKind.entries.size) {
        jdbc.query(
          any<String>(),
          any<PreparedStatementSetter>(),
          any<RowMapper<WorkItemFieldOption>>(),
        )
      }
    }
  })
