ALTER TABLE domain_event_deliveries
    ADD COLUMN transport_notified_at timestamp with time zone;

CREATE INDEX idx_domain_event_deliveries_transport_ready
    ON domain_event_deliveries (next_attempt_at, created_at)
    WHERE transport_notified_at IS NOT NULL
      AND status IN ('PENDING', 'RETRY', 'PROCESSING');

DELETE FROM outbox_transport_publications
WHERE backend = 'KAFKA';

ALTER TABLE outbox_transport_publications
    DROP CONSTRAINT chk_outbox_transport_publication_backend;

ALTER TABLE outbox_transport_publications
    ADD CONSTRAINT chk_outbox_transport_publication_backend
        CHECK (backend = 'REDIS_STREAMS');

DROP INDEX IF EXISTS idx_domain_outbox_pending;

ALTER TABLE domain_outbox
    DROP COLUMN status,
    DROP COLUMN attempts,
    DROP COLUMN next_attempt_at,
    DROP COLUMN locked_until,
    DROP COLUMN published_at,
    DROP COLUMN last_error,
    DROP COLUMN updated_at;

COMMENT ON TABLE domain_outbox IS
    'Immutable domain event source of truth; Kafka publication is performed by Debezium CDC.';

COMMENT ON COLUMN domain_event_deliveries.transport_notified_at IS
    'Set when an external transport has signaled that this outbox event is available for execution.';
