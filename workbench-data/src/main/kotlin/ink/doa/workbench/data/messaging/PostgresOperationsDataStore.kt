package ink.doa.workbench.data.messaging

import ink.doa.workbench.application.operations.DeliveryStatusCount
import ink.doa.workbench.application.operations.DeliveryTrendPoint
import ink.doa.workbench.application.operations.OperationsDataStore
import java.time.OffsetDateTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class PostgresOperationsDataStore(private val jdbc: JdbcTemplate) : OperationsDataStore {
  override fun deliveryStatusCounts(): List<DeliveryStatusCount> =
    jdbc.query(
      "SELECT status, COUNT(*) AS count FROM domain_event_deliveries GROUP BY status",
      { rs, _ -> DeliveryStatusCount(rs.getString("status"), rs.getLong("count")) },
    )

  override fun deliveryTrendSince(since: OffsetDateTime): List<DeliveryTrendPoint> =
    jdbc.query(
      """
      SELECT date_trunc('hour', updated_at) AS bucket_at,
             COUNT(*) FILTER (WHERE status = 'SUCCEEDED') AS succeeded,
             COUNT(*) FILTER (WHERE status IN ('RETRY', 'DEAD')) AS failed
      FROM domain_event_deliveries
      WHERE updated_at >= ?
      GROUP BY bucket_at
      ORDER BY bucket_at
      """
        .trimIndent(),
      { rs, _ ->
        DeliveryTrendPoint(
          bucketAt = rs.getObject("bucket_at", OffsetDateTime::class.java),
          succeeded = rs.getLong("succeeded"),
          failed = rs.getLong("failed"),
        )
      },
      since,
    )
}
