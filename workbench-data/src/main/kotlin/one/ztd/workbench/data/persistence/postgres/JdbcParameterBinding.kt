package one.ztd.workbench.data.persistence.postgres

import org.springframework.jdbc.core.PreparedStatementSetter

internal fun List<Any?>.toPreparedStatementSetter(): PreparedStatementSetter =
  PreparedStatementSetter { statement ->
    forEachIndexed { index, value -> statement.setObject(index + 1, value) }
  }
