package ink.doa.workbench.data.persistence

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.postgresql.util.PGobject

private class PostgresInetColumnType : ColumnType<String>() {
  override fun sqlType(): String = "INET"

  override fun valueFromDB(value: Any): String =
    when (value) {
      is PGobject -> value.value.orEmpty()
      else -> value.toString()
    }

  override fun notNullValueToDB(value: String): Any =
    PGobject().apply {
      type = "inet"
      this.value = value
    }
}

internal fun Table.inet(name: String): Column<String> =
  registerColumn(name, PostgresInetColumnType())
