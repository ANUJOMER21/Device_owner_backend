-- DB-based login status flag: reliable source of truth independent of token refresh/expiry
ALTER TABLE dealers
ADD COLUMN IF NOT EXISTS is_logged_in BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN dealers.is_logged_in IS 'Explicit login state: true when dealer has active session, false on logout. Used for admin display and login gate.';

-- Backfill: sync with existing current_token_id state
UPDATE dealers
SET is_logged_in = true
WHERE current_token_id IS NOT NULL AND TRIM(current_token_id) != '';
