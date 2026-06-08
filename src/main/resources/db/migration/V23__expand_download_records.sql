ALTER TABLE download_records RENAME COLUMN source TO source_url;
ALTER TABLE download_records ALTER COLUMN source_url TYPE VARCHAR(2048);
ALTER TABLE download_records ADD COLUMN platform    VARCHAR(30);
ALTER TABLE download_records ADD COLUMN file_type   VARCHAR(20);
ALTER TABLE download_records ADD COLUMN file_name   VARCHAR(500);
ALTER TABLE download_records ADD COLUMN storage_key VARCHAR(1000);
