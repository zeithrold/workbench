package one.ztd.workbench.data.persistence.postgres

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table

@Suppress("UNCHECKED_CAST")
internal fun <T> Table.requireColumn(name: String): Column<T> =
  columns.single { it.name == name } as Column<T>

@Suppress("UNCHECKED_CAST")
internal fun <T> Table.findColumn(name: String): Column<T>? =
  columns.singleOrNull { it.name == name } as Column<T>?
