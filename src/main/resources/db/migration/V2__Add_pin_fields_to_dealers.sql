-- Add PIN fields to dealers table for authentication
ALTER TABLE dealers 
ADD COLUMN IF NOT EXISTS pin_hash VARCHAR(255),
ADD COLUMN IF NOT EXISTS is_pin_set BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS last_login TIMESTAMP WITH TIME ZONE;

-- Create index on email for faster login lookups (if not already exists)
CREATE INDEX IF NOT EXISTS idx_dealers_email ON dealers(email);
