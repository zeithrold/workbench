ALTER TABLE domain_outbox
    ADD COLUMN retention_until timestamp with time zone NOT NULL
        DEFAULT (now() + interval '30 days');

CREATE TABLE outbox_transport_publications (
    outbox_id uuid NOT NULL REFERENCES domain_outbox(id) ON DELETE CASCADE,
    backend text NOT NULL,
    epoch text NOT NULL,
    status text NOT NULL DEFAULT 'PENDING',
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp with time zone NOT NULL DEFAULT now(),
    locked_until timestamp with time zone,
    published_at timestamp with time zone,
    last_error text,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY (outbox_id, backend, epoch),
    CONSTRAINT chk_outbox_transport_publication_backend
        CHECK (backend IN ('REDIS_STREAMS', 'KAFKA')),
    CONSTRAINT chk_outbox_transport_publication_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'RETRY', 'DEAD'))
);

CREATE INDEX idx_outbox_transport_publications_ready
    ON outbox_transport_publications (backend, epoch, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING', 'RETRY');

CREATE INDEX idx_outbox_transport_publications_dead
    ON outbox_transport_publications (backend, epoch, updated_at)
    WHERE status = 'DEAD';

COMMENT ON TABLE domain_outbox IS
    'Immutable domain event source of truth. Legacy relay columns are retained for V2 compatibility only.';
COMMENT ON TABLE domain_event_deliveries IS
    'Authoritative per-consumer execution state for all messaging backends.';
COMMENT ON TABLE outbox_transport_publications IS
    'Disposable backend/epoch locator publication state; never represents handler completion.';
