ALTER TABLE attachments
    ADD COLUMN purpose TEXT NOT NULL DEFAULT 'standalone';

UPDATE attachments
SET purpose = COALESCE(metadata ->> 'purpose', 'standalone');

CREATE INDEX idx_attachments_issue_purpose
    ON attachments (tenant_id, issue_id, purpose, created_at DESC)
    WHERE deleted_at IS NULL;
