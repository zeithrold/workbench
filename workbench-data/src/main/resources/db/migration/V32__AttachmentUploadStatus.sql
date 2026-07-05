ALTER TABLE attachments
    ADD COLUMN upload_status TEXT NOT NULL DEFAULT 'completed';

CREATE INDEX idx_attachments_pending_cleanup
    ON attachments (created_at)
    WHERE upload_status = 'pending' AND deleted_at IS NULL;
