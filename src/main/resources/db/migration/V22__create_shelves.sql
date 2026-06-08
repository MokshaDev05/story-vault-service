CREATE TABLE shelves (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_shelf_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_shelves_user_id ON shelves(user_id);

CREATE TABLE shelf_stories (
    shelf_id BIGINT    NOT NULL REFERENCES shelves(id)  ON DELETE CASCADE,
    story_id BIGINT    NOT NULL REFERENCES stories(id)  ON DELETE CASCADE,
    added_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (shelf_id, story_id)
);

CREATE INDEX idx_shelf_stories_shelf_id ON shelf_stories(shelf_id);
CREATE INDEX idx_shelf_stories_story_id ON shelf_stories(story_id);
