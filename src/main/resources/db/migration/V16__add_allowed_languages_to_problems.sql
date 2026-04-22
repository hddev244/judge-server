-- NULL = all languages allowed (backward compatible)
ALTER TABLE problems ADD COLUMN allowed_languages VARCHAR(100) DEFAULT NULL;
