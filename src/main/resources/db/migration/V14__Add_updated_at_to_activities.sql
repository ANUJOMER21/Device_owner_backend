-- Add updated_at column to activities table
-- This column is required by BaseEntity which all entities extend
ALTER TABLE activities 
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Update existing rows to have updated_at set to created_at if it's null
UPDATE activities 
SET updated_at = created_at 
WHERE updated_at IS NULL;

-- Make updated_at NOT NULL after setting default values
ALTER TABLE activities 
ALTER COLUMN updated_at SET NOT NULL;

-- Add comment
COMMENT ON COLUMN activities.updated_at IS 'Last modification timestamp';
