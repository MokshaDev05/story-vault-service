CREATE TABLE story_files (
    id           BIGSERIAL    PRIMARY KEY,
    story_id     BIGINT       NOT NULL UNIQUE REFERENCES stories(id) ON DELETE CASCADE,
    filename     VARCHAR(500) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    file_size    BIGINT       NOT NULL,
    file_data    BYTEA        NOT NULL,
    uploaded_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
