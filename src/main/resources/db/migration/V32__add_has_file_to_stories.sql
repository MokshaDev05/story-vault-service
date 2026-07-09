ALTER TABLE stories ADD COLUMN has_file BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE stories SET has_file = TRUE
  WHERE id IN (SELECT story_id FROM story_files);
