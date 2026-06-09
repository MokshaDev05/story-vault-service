CREATE TABLE labels (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_label_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_labels_user_id ON labels(user_id);

CREATE TABLE story_labels (
    story_id BIGINT    NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    label_id BIGINT    NOT NULL REFERENCES labels(id)  ON DELETE CASCADE,
    added_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (story_id, label_id)
);

CREATE INDEX idx_story_labels_label_id ON story_labels(label_id);
CREATE INDEX idx_story_labels_story_id ON story_labels(story_id);
