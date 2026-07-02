package doa.ink.workbench.workitem.query

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkItemQuery(
  val version: Int = 1,
  val resource: String = "work_item",
  val where: ConditionNode? = null,
  val sort: List<SortTerm> = emptyList(),
)

@Serializable
sealed interface ConditionNode {
  @Serializable @SerialName("and") data class And(val args: List<ConditionNode>) : ConditionNode

  @Serializable @SerialName("or") data class Or(val args: List<ConditionNode>) : ConditionNode

  @Serializable @SerialName("not") data class Not(val arg: ConditionNode) : ConditionNode

  @Serializable
  @SerialName("predicate")
  data class Predicate(val field: String, val op: String, val value: QueryValue? = null) :
    ConditionNode
}

@Serializable
sealed interface QueryValue {
  @Serializable @SerialName("literal") data class Literal(val value: String) : QueryValue

  @Serializable @SerialName("variable") data class Variable(val name: String) : QueryValue
}

@Serializable
data class SortTerm(
  val field: String,
  val direction: SortDirection,
  val nulls: NullOrdering? = null,
)

@Serializable
enum class SortDirection {
  ASC,
  DESC,
}

@Serializable
enum class NullOrdering {
  FIRST,
  LAST,
}
