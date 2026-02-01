-- Add customer limit field to dealers table
-- This represents the total number of customers (kits) a dealer can register
ALTER TABLE dealers 
ADD COLUMN IF NOT EXISTS customer_limit INTEGER DEFAULT 0;

-- Add comment
COMMENT ON COLUMN dealers.customer_limit IS 'Maximum number of customers (kits) this dealer can register';
