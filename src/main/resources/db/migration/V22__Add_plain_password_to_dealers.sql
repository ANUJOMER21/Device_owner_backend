-- Add plain_password column to dealers table for admin viewing
ALTER TABLE dealers ADD COLUMN IF NOT EXISTS plain_password VARCHAR(255);
