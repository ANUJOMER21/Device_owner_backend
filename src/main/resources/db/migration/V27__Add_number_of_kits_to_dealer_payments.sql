-- Store kit count per payment for Payment & Kit Assignment History display
ALTER TABLE dealer_payments
ADD COLUMN IF NOT EXISTS number_of_kits INTEGER NULL;

COMMENT ON COLUMN dealer_payments.number_of_kits IS 'Number of kits assigned (positive) or reversed/removed (negative row has positive count here); null = 1 for backward compat';
