CREATE TABLE connected_accounts (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform      VARCHAR(30)  NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    profile_url   VARCHAR(2048),
    account_label VARCHAR(100),
    sync_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    notes         TEXT,
    last_sync_at  TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_connected_accounts_user_id ON connected_accounts(user_id);
