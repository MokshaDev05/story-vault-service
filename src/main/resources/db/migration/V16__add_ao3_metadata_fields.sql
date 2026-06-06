-- AO3-specific date and language fields on stories
ALTER TABLE stories ADD COLUMN ao3_published_date DATE;
ALTER TABLE stories ADD COLUMN ao3_updated_date   DATE;
ALTER TABLE stories ADD COLUMN total_chapters     INTEGER;
ALTER TABLE stories ADD COLUMN language           VARCHAR(100);

-- Characters: separate from freeform tags, AO3-canonical list
CREATE TABLE story_characters (
    story_id       BIGINT       NOT NULL,
    character_name VARCHAR(500) NOT NULL,
    CONSTRAINT fk_story_characters FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);
CREATE INDEX idx_story_characters_sid ON story_characters(story_id);

-- Archive warnings (AO3 canonical warning tags)
CREATE TABLE story_warnings (
    story_id BIGINT       NOT NULL,
    warning  VARCHAR(500) NOT NULL,
    CONSTRAINT fk_story_warnings FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);
CREATE INDEX idx_story_warnings_sid ON story_warnings(story_id);

-- Relationship categories (M/M, F/F, F/M, Gen, Multi, Other)
CREATE TABLE story_categories (
    story_id BIGINT       NOT NULL,
    category VARCHAR(100) NOT NULL,
    CONSTRAINT fk_story_categories FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);
CREATE INDEX idx_story_categories_sid ON story_categories(story_id);
