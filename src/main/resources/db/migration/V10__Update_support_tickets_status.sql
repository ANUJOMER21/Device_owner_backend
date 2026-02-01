-- Update support_tickets status to include 'waiting_for_response'
ALTER TABLE support_tickets DROP CONSTRAINT IF EXISTS support_tickets_status_check;
ALTER TABLE support_tickets ADD CONSTRAINT support_tickets_status_check 
CHECK (status IN ('open', 'in_progress', 'waiting_for_response', 'resolved', 'closed'));

-- Add category field to support_tickets if not exists (optional enhancement)
ALTER TABLE support_tickets 
ADD COLUMN IF NOT EXISTS category VARCHAR(50);

-- Add description field to support_tickets if not exists
ALTER TABLE support_tickets 
ADD COLUMN IF NOT EXISTS description TEXT;

-- Create index on category for filtering
CREATE INDEX IF NOT EXISTS idx_support_tickets_category ON support_tickets(category) WHERE category IS NOT NULL;

-- Add comments
COMMENT ON COLUMN support_tickets.category IS 'Ticket category (e.g., technical, billing, general)';
COMMENT ON COLUMN support_tickets.description IS 'Ticket description/details';
