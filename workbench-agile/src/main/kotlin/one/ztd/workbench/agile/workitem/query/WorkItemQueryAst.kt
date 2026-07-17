package one.ztd.workbench.agile.workitem.query

import kotlinx.serialization.json.JsonElement

data class WorkItemGroupTerm(
  val field: QueryField,
  val direction: SortDirection = SortDirection.ASC,
)

data class WorkItemQuery(
  val version: Int = 1,
  val resource: String = RESOURCE,
  val where: ConditionNode? = null,
  val sort: List<SortTerm> = emptyList(),
  val group: WorkItemGroupTerm? = null,
) {
  companion object {
    const val RESOURCE = "work_item"
    const val CURRENT_VERSION = 1
  }
}

sealed interface ConditionNode {
  data class And(val args: List<ConditionNode>) : ConditionNode

  data class Or(val args: List<ConditionNode>) : ConditionNode

  data class Not(val arg: ConditionNode) : ConditionNode

  data class Predicate(
    val field: QueryField,
    val op: QueryOperator,
    val value: QueryValue? = null,
  ) : ConditionNode
}

sealed interface QueryField {
  val canonicalName: String

  data class System(override val canonicalName: String) : QueryField

  data class Property(val apiId: String?, val code: String?) : QueryField {
    init {
      require(!apiId.isNullOrBlank() || !code.isNullOrBlank()) {
        "Property field requires apiId or code."
      }
    }

    override val canonicalName: String = "property.${apiId ?: code}"
  }
}

enum class QueryOperator(val wireName: String) {
  EQ("eq"),
  NEQ("neq"),
  IN("in"),
  NOT_IN("not_in"),
  LT("lt"),
  LTE("lte"),
  GT("gt"),
  GTE("gte"),
  BETWEEN("between"),
  CONTAINS("contains"),
  NOT_CONTAINS("not_contains"),
  STARTS_WITH("starts_with"),
  ENDS_WITH("ends_with"),
  MATCHES("matches"),
  IS_EMPTY("is_empty"),
  IS_NOT_EMPTY("is_not_empty"),
  BEFORE("before"),
  ON_OR_BEFORE("on_or_before"),
  AFTER("after"),
  ON_OR_AFTER("on_or_after"),
  WITHIN("within"),
  HAS_ANY("has_any"),
  HAS_ALL("has_all"),
  HAS_NONE("has_none");

  companion object {
    fun fromWireName(value: String): QueryOperator? = entries.firstOrNull { it.wireName == value }
  }
}

sealed interface QueryValue {
  data class Literal(val value: JsonElement) : QueryValue

  data class Variable(val name: String) : QueryValue

  data class RelativeDate(
    val amount: Int,
    val unit: RelativeDateUnit,
    val direction: DateDirection,
    val anchor: String,
  ) : QueryValue

  data class Between(val from: JsonElement?, val to: JsonElement?) : QueryValue
}

enum class RelativeDateUnit(val wireName: String) {
  DAY("day"),
  WEEK("week"),
  MONTH("month"),
  YEAR("year");

  companion object {
    fun fromWireName(value: String): RelativeDateUnit? = entries.firstOrNull {
      it.wireName == value
    }
  }
}

enum class DateDirection(val wireName: String) {
  PAST("past"),
  FUTURE("future");

  companion object {
    fun fromWireName(value: String): DateDirection? = entries.firstOrNull { it.wireName == value }
  }
}

data class SortTerm(
  val field: QueryField,
  val direction: SortDirection,
  val nulls: NullOrdering? = null,
)

enum class SortDirection(val wireName: String) {
  ASC("asc"),
  DESC("desc");

  companion object {
    fun fromWireName(value: String): SortDirection? = entries.firstOrNull {
      it.wireName == value.lowercase()
    }
  }
}

enum class NullOrdering(val wireName: String) {
  FIRST("first"),
  LAST("last");

  companion object {
    fun fromWireName(value: String): NullOrdering? = entries.firstOrNull {
      it.wireName == value.lowercase()
    }
  }
}
