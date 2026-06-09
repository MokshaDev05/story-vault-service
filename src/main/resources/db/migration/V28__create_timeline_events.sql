CREATE TABLE timeline_events (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    story_id        BIGINT       REFERENCES stories(id) ON DELETE CASCADE,
    event_type      VARCHAR(50)  NOT NULL,
    event_timestamp TIMESTAMP    NOT NULL,
    metadata        TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_timeline_user_time ON timeline_events(user_id, event_timestamp DESC);
CREATE INDEX idx_timeline_story_id  ON timeline_events(story_id);
CREATE INDEX idx_timeline_user_type ON timeline_events(user_id, event_type);
