-- Allow 'uninstalled' as a valid customer status
-- Postgres default name for the inline check constraint is usually customers_status_check

ALTER TABLE customers
DROP CONSTRAINT IF EXISTS customers_status_check;

ALTER TABLE customers
ADD CONSTRAINT customers_status_check
CHECK (status IN ('active', 'inactive', 'blocked', 'uninstalled'));

