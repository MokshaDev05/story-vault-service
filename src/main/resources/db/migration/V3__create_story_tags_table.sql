CREATE TABLE story_tags (
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    tag_id   BIGINT NOT NULL REFERENCES tags(id)   ON DELETE CASCADE,
    PRIMARY KEY (story_id, tag_id)
);

CREATE INDEX idx_story_tags_story_id ON story_tags(story_id);
CREATE INDEX idx_story_tags_tag_id   ON story_tags(tag_id);
