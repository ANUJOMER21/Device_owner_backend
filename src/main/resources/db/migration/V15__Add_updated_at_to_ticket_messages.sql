-- Add updated_at column to ticket_messages table
-- This column is required by BaseEntity which all entities extend
ALTER TABLE ticket_messages 
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Update existing rows to have updated_at set to created_at if it's null
UPDATE ticket_messages 
SET updated_at = created_at 
WHERE updated_at IS NULL;

-- Make updated_at NOT NULL after setting default values
ALTER TABLE ticket_messages 
ALTER COLUMN updated_at SET NOT NULL;

-- Add trigger to automatically update updated_at on row updates
CREATE TRIGGER update_ticket_messages_updated_at
    BEFORE UPDATE ON ticket_messages
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Add comment
COMMENT ON COLUMN ticket_messages.updated_at IS 'Last modification timestamp';
