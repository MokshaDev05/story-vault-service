ALTER TABLE stories ADD COLUMN source_work_id VARCHAR(100);

CREATE UNIQUE INDEX idx_stories_source_work_id_user
    ON stories (platform, source_work_id, user_id)
    WHERE source_work_id IS NOT NULL;
