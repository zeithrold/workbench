ALTER TABLE issue_comments
  ADD COLUMN IF NOT EXISTS body_plain_text TEXT;
