-- Add attachment fields to ticket_messages for image/PDF attachments (max 10MB)
ALTER TABLE ticket_messages
ADD COLUMN IF NOT EXISTS attachment_url VARCHAR(500),
ADD COLUMN IF NOT EXISTS attachment_file_name VARCHAR(255);

COMMENT ON COLUMN ticket_messages.attachment_url IS 'URL path to stored attachment (image or PDF, max 10MB)';
COMMENT ON COLUMN ticket_messages.attachment_file_name IS 'Original filename of the attachment';
