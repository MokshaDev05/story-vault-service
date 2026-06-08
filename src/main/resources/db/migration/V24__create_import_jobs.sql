CREATE TABLE import_jobs (
    id               BIGSERIAL    PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform         VARCHAR(30)  NOT NULL,
    import_type      VARCHAR(30)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    started_at       TIMESTAMP,
    completed_at     TIMESTAMP,
    items_processed  INTEGER      NOT NULL DEFAULT 0,
    error_message    TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_import_jobs_user ON import_jobs(user_id);
