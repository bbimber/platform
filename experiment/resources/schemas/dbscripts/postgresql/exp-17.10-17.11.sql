ALTER TABLE exp.list DROP COLUMN IF EXISTS FileAttachmentIndex CASCADE;
ALTER TABLE exp.list ADD FileAttachmentIndex BOOLEAN NOT NULL DEFAULT FALSE;