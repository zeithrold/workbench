CREATE TABLE domain_event_deliveries (
    outbox_id uuid NOT NULL REFERENCES domain_outbox(id) ON DELETE CASCADE,
    consumer_name text NOT NULL,
    partition_key text NOT NULL,
    status text NOT NULL DEFAULT 'PENDING',
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp with time zone NOT NULL DEFAULT now(),
    locked_until timestamp with time zone,
    last_error text,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY (outbox_id, consumer_name),
    CONSTRAINT chk_domain_event_delivery_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'RETRY', 'DEAD'))
);

CREATE INDEX idx_domain_event_deliveries_ready
    ON domain_event_deliveries (consumer_name, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'RETRY', 'PROCESSING');

CREATE INDEX idx_domain_event_deliveries_partition
    ON domain_event_deliveries (consumer_name, partition_key, created_at);

CREATE INDEX idx_domain_event_deliveries_dead
    ON domain_event_deliveries (updated_at)
    WHERE status = 'DEAD';
