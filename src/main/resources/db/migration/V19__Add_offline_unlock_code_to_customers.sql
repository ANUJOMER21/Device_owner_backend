-- Add offline unlock code to customers table
-- This is a unique code associated with each customer and shown in admin panel.

ALTER TABLE customers
ADD COLUMN IF NOT EXISTS offline_unlock_code VARCHAR(32);

-- Unique index (allow nulls for old rows until backfilled)
CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_offline_unlock_code
ON customers(offline_unlock_code);

COMMENT ON COLUMN customers.offline_unlock_code IS 'Unique offline unlock code for customer (admin-visible)';

