ALTER TABLE import_jobs ADD COLUMN current_page INTEGER      NOT NULL DEFAULT 0;
ALTER TABLE import_jobs ADD COLUMN total_pages  INTEGER;
ALTER TABLE import_jobs ADD COLUMN error_count  INTEGER      NOT NULL DEFAULT 0;
ALTER TABLE import_jobs ADD COLUMN last_error   TEXT;
ALTER TABLE import_jobs ADD COLUMN updated_at   TIMESTAMP    NOT NULL DEFAULT NOW();

CREATE INDEX idx_import_jobs_user_status ON import_jobs(user_id, status);
