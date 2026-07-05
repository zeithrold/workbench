ALTER TABLE work_item_views
    ADD COLUMN query_ast JSONB NOT NULL DEFAULT '{"version":1,"resource":"work_item"}';

UPDATE work_item_views
SET query_ast = jsonb_strip_nulls(
    jsonb_build_object(
        'version', 1,
        'resource', 'work_item',
        'where', CASE WHEN filter_ast = '{}'::jsonb THEN NULL ELSE filter_ast END,
        'sort', CASE WHEN sort_ast = '[]'::jsonb THEN '[]'::jsonb ELSE sort_ast END,
        'group', CASE WHEN group_ast = '{}'::jsonb THEN NULL ELSE group_ast END
    )
);

ALTER TABLE work_item_views
    DROP COLUMN filter_ast,
    DROP COLUMN sort_ast,
    DROP COLUMN group_ast;
