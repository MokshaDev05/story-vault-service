ALTER TABLE stories ADD COLUMN reading_status     VARCHAR(50);
ALTER TABLE stories ADD COLUMN current_chapter    INTEGER;
ALTER TABLE stories ADD COLUMN current_chapter_url VARCHAR(2048);
ALTER TABLE stories ADD COLUMN last_accessed_at   TIMESTAMP;
