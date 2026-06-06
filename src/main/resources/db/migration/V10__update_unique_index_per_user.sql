DROP INDEX IF EXISTS idx_stories_title_author_lower;

CREATE UNIQUE INDEX idx_stories_title_author_user
    ON stories (lower(title), lower(author), user_id)
    WHERE user_id IS NOT NULL;
