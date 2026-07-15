package ink.doa.workbench.application.operations

import java.time.OffsetDateTime

data class DeliveryStatusCount(val status: String, val count: Long)

data class DeliveryTrendPoint(
  val bucketAt: OffsetDateTime,
  val succeeded: Long,
  val failed: Long,
)

interface OperationsDataStore {
  fun deliveryStatusCounts(): List<DeliveryStatusCount>

  fun deliveryTrendSince(since: OffsetDateTime): List<DeliveryTrendPoint>
}
