CREATE TABLE download_records (
    id            BIGSERIAL    PRIMARY KEY,
    story_id      BIGINT       NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    source        VARCHAR(500),
    notes         TEXT,
    downloaded_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_download_records_story_id ON download_records(story_id);
