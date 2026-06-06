CREATE TABLE notes (
    id         BIGSERIAL PRIMARY KEY,
    story_id   BIGINT    NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    content    TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notes_story_id ON notes(story_id);
