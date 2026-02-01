-- Add IMEI fields to customers table
-- IMEI1 is required and unique (used as key to register customer device to dealer)
-- IMEI2 is optional

ALTER TABLE customers
ADD COLUMN IF NOT EXISTS imei1 VARCHAR(20) UNIQUE;

ALTER TABLE customers
ADD COLUMN IF NOT EXISTS imei2 VARCHAR(20);

-- Add index for faster lookups
CREATE INDEX IF NOT EXISTS idx_customers_imei1 ON customers(imei1);

-- Add comments
COMMENT ON COLUMN customers.imei1 IS 'Primary IMEI number - required and unique, used as key to register customer device to dealer';
COMMENT ON COLUMN customers.imei2 IS 'Secondary IMEI number - optional';

-- Make IMEI1 NOT NULL after adding the column (for existing records, we'll need to handle separately)
-- For new records, it will be required at application level
