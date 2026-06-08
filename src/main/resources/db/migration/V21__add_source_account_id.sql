ALTER TABLE stories
    ADD COLUMN source_account_id BIGINT REFERENCES connected_accounts(id) ON DELETE SET NULL;

ALTER TABLE reading_history
    ADD COLUMN source_account_id BIGINT REFERENCES connected_accounts(id) ON DELETE SET NULL;
