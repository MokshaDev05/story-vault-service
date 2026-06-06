ALTER TABLE reading_history
    ADD COLUMN IF NOT EXISTS user_id    BIGINT,
    ADD COLUMN IF NOT EXISTS work_id    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS event_type VARCHAR(50);

-- Backfill from stories table so existing rows stay queryable.
UPDATE reading_history
SET user_id    = s.user_id,
    work_id    = s.source_work_id,
    event_type = 'PAGE_LOAD'
FROM stories s
WHERE reading_history.story_id = s.id;
