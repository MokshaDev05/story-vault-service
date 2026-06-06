CREATE TABLE stories (
    id            BIGSERIAL     PRIMARY KEY,
    title         VARCHAR(500)  NOT NULL,
    author        VARCHAR(255)  NOT NULL,
    fandom        VARCHAR(255)  NOT NULL,
    platform      VARCHAR(50)   NOT NULL,
    status        VARCHAR(50)   NOT NULL DEFAULT 'ONGOING',
    rating        VARCHAR(50)   NOT NULL DEFAULT 'NOT_RATED',
    summary       TEXT,
    original_url  VARCHAR(2048),
    word_count    INTEGER,
    chapter_count INTEGER,
    completed_at  DATE,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stories_fandom   ON stories(fandom);
CREATE INDEX idx_stories_platform ON stories(platform);
CREATE INDEX idx_stories_status   ON stories(status);
CREATE INDEX idx_stories_rating   ON stories(rating);
