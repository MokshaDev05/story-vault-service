-- AO3 chapter ID (from the /chapters/{id} URL segment) and reading mode
ALTER TABLE reading_history ADD COLUMN chapter_ao3_id VARCHAR(50);
ALTER TABLE reading_history ADD COLUMN reading_mode   VARCHAR(20);
