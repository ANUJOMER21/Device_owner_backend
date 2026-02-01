-- Remove inactive and blocked from customer status; migrate existing rows
UPDATE customers SET status = 'active' WHERE status IN ('inactive', 'blocked');

ALTER TABLE customers DROP CONSTRAINT IF EXISTS customers_status_check;

ALTER TABLE customers ADD CONSTRAINT customers_status_check
CHECK (status IN ('active', 'uninstalled', 'pending_activation', 'installed'));
