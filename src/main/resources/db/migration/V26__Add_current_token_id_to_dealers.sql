-- Single-device login: store current valid token id per dealer; only this token is accepted.
ALTER TABLE dealers
ADD COLUMN IF NOT EXISTS current_token_id VARCHAR(255) NULL;

COMMENT ON COLUMN dealers.current_token_id IS 'JWT jti of the current valid session; login from another device invalidates previous token';
