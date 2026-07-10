package ink.doa.workbench.data.messaging

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ProcessedDomainEventRepository(private val jdbc: JdbcTemplate) {
  fun tryClaim(consumerName: String, eventId: String): Boolean =
    jdbc.update(
      """
      INSERT INTO processed_domain_events (consumer_name, event_id)
      VALUES (?, ?)
      ON CONFLICT (consumer_name, event_id) DO NOTHING
      """
        .trimIndent(),
      consumerName,
      eventId,
    ) > 0
}
