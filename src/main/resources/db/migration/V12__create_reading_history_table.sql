CREATE TABLE reading_history (
    id              BIGSERIAL    PRIMARY KEY,
    story_id        BIGINT       NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    accessed_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    chapter_number  INTEGER,
    chapter_title   VARCHAR(500),
    chapter_url     VARCHAR(2048),
    source_platform VARCHAR(50)
);

CREATE INDEX idx_reading_history_story_id    ON reading_history(story_id);
CREATE INDEX idx_reading_history_accessed_at ON reading_history(accessed_at DESC);
