-- Prevent duplicate entries for the same work.
-- Uniqueness is defined as: same title AND same author, case-insensitively.
CREATE UNIQUE INDEX idx_stories_title_author_lower
    ON stories (lower(title), lower(author));
