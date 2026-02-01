-- Add GST number field to dealers table
ALTER TABLE dealers 
ADD COLUMN IF NOT EXISTS gst_number VARCHAR(15) UNIQUE;

-- Create index on GST number for faster lookups
CREATE INDEX IF NOT EXISTS idx_dealers_gst_number ON dealers(gst_number);

-- Add comment
COMMENT ON COLUMN dealers.gst_number IS 'GST Identification Number (15 characters, alphanumeric)';
