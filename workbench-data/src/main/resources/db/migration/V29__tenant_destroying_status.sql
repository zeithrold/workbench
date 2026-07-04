ALTER TABLE tenants
    DROP CONSTRAINT IF EXISTS tenants_status_check;

ALTER TABLE tenants
    ADD CONSTRAINT tenants_status_chk
        CHECK (status IN ('active', 'pending_activation', 'destroying'));
