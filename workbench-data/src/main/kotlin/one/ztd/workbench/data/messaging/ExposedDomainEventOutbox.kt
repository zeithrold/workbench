package one.ztd.workbench.data.messaging

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import one.ztd.workbench.application.messaging.DomainEventOutbox
import one.ztd.workbench.data.persistence.postgres.workitem.DomainOutboxTable
import one.ztd.workbench.kernel.messaging.DomainEventEncoder
import one.ztd.workbench.kernel.messaging.DomainEventEnvelope
import one.ztd.workbench.kernel.messaging.DomainEventSpec
import one.ztd.workbench.kernel.messaging.EventMetadata
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component

@Component
class ExposedDomainEventOutbox(
  private val database: Database,
  private val encoder: DomainEventEncoder,
) : DomainEventOutbox {
  private val json = DomainEventEncoder.defaultJson

  override fun <T : Any> append(
    spec: DomainEventSpec<T>,
    key: String,
    payload: T,
    metadata: EventMetadata,
  ) {
    val encoded = encoder.encode(spec, payload, metadata)
    val envelope = json.decodeFromString<DomainEventEnvelope>(encoded)
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val current = TransactionManager.currentOrNull()
    if (current == null) {
      transaction(db = database) { insertOutbox(envelope, encoded, key, spec, now) }
    } else {
      insertOutbox(envelope, encoded, key, spec, now)
    }
  }

  private fun <T : Any> insertOutbox(
    envelope: DomainEventEnvelope,
    encoded: String,
    key: String,
    spec: DomainEventSpec<T>,
    now: OffsetDateTime,
  ) {
    DomainOutboxTable.insert {
      it[DomainOutboxTable.id] = UUID.randomUUID().toKotlinUuid()
      it[DomainOutboxTable.eventId] = envelope.eventId
      it[DomainOutboxTable.eventType] = envelope.type
      it[DomainOutboxTable.eventVersion] = envelope.version
      it[DomainOutboxTable.topic] = spec.topic
      it[DomainOutboxTable.partitionKey] = key
      it[DomainOutboxTable.tenantId] = envelope.tenantId
      it[DomainOutboxTable.payload] = json.parseToJsonElement(encoded)
      it[DomainOutboxTable.createdAt] = now
      it[DomainOutboxTable.retentionUntil] = now.plusDays(30)
    }
    TransactionManager.current().exec("SELECT pg_notify('workbench_outbox', '')")
  }
}
