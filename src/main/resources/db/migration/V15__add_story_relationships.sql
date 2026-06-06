CREATE TABLE story_relationships (
    story_id     BIGINT       NOT NULL,
    relationship VARCHAR(500) NOT NULL,
    CONSTRAINT fk_story_relationships_story
        FOREIGN KEY (story_id) REFERENCES stories (id) ON DELETE CASCADE
);
