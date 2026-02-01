-- Refund amount and medium for kit reversal / removal transactions
ALTER TABLE dealer_payments
ADD COLUMN IF NOT EXISTS refund_amount DECIMAL(15, 2) NULL,
ADD COLUMN IF NOT EXISTS refund_medium VARCHAR(50) NULL;

COMMENT ON COLUMN dealer_payments.refund_amount IS 'Refund amount (money) when reversing/removing kits; null for normal payments';
COMMENT ON COLUMN dealer_payments.refund_medium IS 'Medium of refund (e.g. cash, UPI, bank_transfer) when reversing kits';
