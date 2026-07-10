package ink.doa.workbench.data.messaging

import ink.doa.workbench.core.messaging.DomainEventEncoder
import ink.doa.workbench.core.messaging.DomainEventEnvelope
import ink.doa.workbench.core.messaging.DomainEventSpec
import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.port.messaging.DomainEventOutbox
import ink.doa.workbench.data.persistence.postgres.workitem.DomainOutboxTable
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
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
    transaction(db = database) {
      DomainOutboxTable.insert {
        it[DomainOutboxTable.id] = UUID.randomUUID().toKotlinUuid()
        it[DomainOutboxTable.eventId] = envelope.eventId
        it[DomainOutboxTable.eventType] = envelope.type
        it[DomainOutboxTable.eventVersion] = envelope.version
        it[DomainOutboxTable.topic] = spec.topic
        it[DomainOutboxTable.partitionKey] = key
        it[DomainOutboxTable.tenantId] = envelope.tenantId
        it[DomainOutboxTable.payload] = json.parseToJsonElement(encoded)
        it[DomainOutboxTable.status] = "PENDING"
        it[DomainOutboxTable.createdAt] = now
        it[DomainOutboxTable.updatedAt] = now
        it[DomainOutboxTable.nextAttemptAt] = now
        it[DomainOutboxTable.attempts] = 0
      }
    }
  }
}
