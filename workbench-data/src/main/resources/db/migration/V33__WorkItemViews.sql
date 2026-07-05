ALTER TABLE saved_filters RENAME TO work_item_views;

ALTER TABLE work_item_views
    ADD COLUMN visibility TEXT NOT NULL DEFAULT 'private',
    ADD COLUMN group_ast JSONB NOT NULL DEFAULT '{}',
    ADD COLUMN display_fields JSONB NOT NULL DEFAULT '[]';

UPDATE work_item_views
SET visibility = CASE WHEN is_shared THEN 'project' ELSE 'private' END;

ALTER TABLE work_item_views DROP COLUMN is_shared;

DROP INDEX IF EXISTS idx_saved_filters_owner;
DROP INDEX IF EXISTS idx_saved_filters_project_shared;

CREATE INDEX idx_work_item_views_owner ON work_item_views(tenant_id, owner_id);
CREATE INDEX idx_work_item_views_project_visibility
    ON work_item_views(tenant_id, project_id, visibility)
    WHERE project_id IS NOT NULL;
CREATE INDEX idx_work_item_views_tenant_visibility
    ON work_item_views(tenant_id, visibility)
    WHERE project_id IS NULL;

ALTER TABLE work_item_views
    ADD CONSTRAINT chk_work_item_views_visibility
        CHECK (visibility IN ('private', 'project', 'tenant'));
